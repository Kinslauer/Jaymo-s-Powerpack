package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.GreatFoes;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;

/**
 * Cortex in their heads, on the keys beside the moves it has (sneak + the key):
 * <ul>
 * <li>Always, Read: whoever last struck you is shown on the HUD for {@link #FOE_TICKS} ticks: its health, whom it is after, and
 * what it looks about to do (shoot, rush in, strike, swell, guard).</li>
 * <li>Sneak + G, Mind Push: a thought shoves everything ahead of you back.</li>
 * <li>Sneak + V, Visions: fill the head of the creature you aim at with visions. A mob loses its target and wanders and flees for
 * {@link #VISION_TICKS} ticks; a player's screen is clouded. Great foes are not troubled.</li>
 * <li>Sneak + B (held), Stare: every enemy in your view is held and walked slowly to your feet. It breaks if you are hurt, when you
 * let go or after {@link #STARE_TICKS} ticks; great foes are not moved.</li>
 * </ul>
 */
public final class CortexMind {
	// ---- tuning ----
	public static final int FOE_TICKS = 100, READ_EVERY = 5;
	public static final double PUSH_REACH = 9.0, PUSH_DOT = 0.55, PUSH_OUT = 1.7, PUSH_UP = 0.4;
	public static final float PUSH_DAMAGE = 2.0F;
	public static final int PUSH_COOLDOWN = 100;
	public static final double VISION_REACH = 16.0, VISION_WANDER = 8.0, VISION_FLEE_NEAR = 10.0, VISION_SPEED = 1.2;
	public static final int VISION_TICKS = 120, VISION_COOLDOWN = 160, VISION_PLAYER_TICKS = 100;
	public static final double STARE_RANGE = 18.0, STARE_DOT = 0.5, STARE_PULL = 0.08, STARE_STOP = 1.5;
	public static final int STARE_TICKS = 120, STARE_COOLDOWN = 200, STARE_MAX_HELD = 8;
	public static final int COLOR = 0x4FB8B0, VISION_COLOR = 0xB070E0;

	private static final class State {
		int foe = -1;
		long foeAt;
		boolean staring;
		long stareStart;
		final Map<Integer, Long> haunted = new HashMap<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private CortexMind() {
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
		player.removeAttached(XenoMinds.FOE);
	}

	/** The Cortex is about to be hurt: note who by, and a stare breaks. */
	public static void struck(ServerPlayer player, DamageSource source) {
		State s = state(player);
		if (source.getEntity() instanceof LivingEntity foe && foe != player) {
			s.foe = foe.getId();
			s.foeAt = player.level().getGameTime();
		}
		if (s.staring) {
			endStare(player, s, true);
		}
	}

	// ---- push ----

	/** Sneak + G. */
	public static void push(ServerPlayer player) {
		if (!XenoMinds.ready(player, "cortex_push")) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (LivingEntity e : MindsKit.near(player, eye, PUSH_REACH)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= PUSH_DOT) {
				e.hurtServer(level, player.damageSources().indirectMagic(player, player), PUSH_DAMAGE);
				MindsKit.shove(e, player.position(), PUSH_OUT, PUSH_UP);
			}
		}
		MagicFx.wave(level, player.position().add(0.0, 0.1, 0.0), MindsKit.flat(player), PUSH_REACH, 3.0F, COLOR, 14);
		MagicFx.ring(level, eye.add(look.scale(1.0)), look, 1.2F, COLOR, 10);
		MindsKit.sound(player, SoundEvents.GUARDIAN_ATTACK, 1.0F, 0.6F);
		MindsKit.sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.8F);
		XenoMinds.cool(player, "cortex_push", PUSH_COOLDOWN);
	}

	// ---- visions ----

	/** Sneak + V. */
	public static void visions(ServerPlayer player) {
		if (!XenoMinds.ready(player, "cortex_vision")) {
			return;
		}
		LivingEntity target = MindsKit.aimed(player, VISION_REACH, e -> true);
		if (target == null) {
			return;
		}
		if (GreatFoes.is(target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.cortex.too_great").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		if (target instanceof ServerPlayer victim) {
			MindsKit.vignette(victim, VISION_COLOR, VISION_PLAYER_TICKS);
			victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, VISION_PLAYER_TICKS, 0), player);
			victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, VISION_PLAYER_TICKS / 2, 0), player);
		} else if (target instanceof Mob mob) {
			state(player).haunted.put(mob.getId(), level.getGameTime() + VISION_TICKS);
			mob.setTarget(null);
		} else {
			return;
		}
		Vec3 head = target.position().add(0.0, target.getBbHeight() + 0.4, 0.0);
		MagicFx.beam(level, player.getEyePosition(), target.getEyePosition(), 0.1F, VISION_COLOR, 8);
		MagicFx.sigil(level, head, 0.7F, VISION_COLOR, 30);
		MindsKit.sound(player, SoundEvents.GUARDIAN_AMBIENT, 1.0F, 0.5F);
		XenoMinds.cool(player, "cortex_vision", VISION_COOLDOWN);
	}

	private static void haunt(ServerPlayer player, ServerLevel level, State s) {
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Integer, Long>> it = s.haunted.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();
			if (!(level.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isAlive() || now >= entry.getValue()) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			if (player.tickCount % 10 == 0) {
				var rng = mob.getRandom();
				Vec3 to;
				if (mob.distanceTo(player) < VISION_FLEE_NEAR) {
					Vec3 away = mob.position().subtract(player.position()).horizontal();
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
					to = mob.position().add(away.scale(VISION_WANDER));
				} else {
					to = mob.position().add((rng.nextDouble() - 0.5) * 2.0 * VISION_WANDER, 0.0, (rng.nextDouble() - 0.5) * 2.0 * VISION_WANDER);
				}
				mob.getNavigation().moveTo(to.x, to.y, to.z, VISION_SPEED);
			}
			if (player.tickCount % 8 == 0) {
				level.sendParticles(MagicFx.rune(VISION_COLOR, 0.8F), mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}

	// ---- stare ----

	/** Sneak + B down. */
	public static void stare(ServerPlayer player) {
		State s = state(player);
		if (s.staring || !XenoMinds.ready(player, "cortex_stare")) {
			return;
		}
		s.staring = true;
		s.stareStart = player.level().getGameTime();
		MindsKit.sound(player, SoundEvents.GUARDIAN_ATTACK, 1.0F, 0.4F);
		MagicFx.sigil(player.level(), player.position().add(0.0, 0.05, 0.0), 2.0F, COLOR, 20);
	}

	/** B let go. */
	public static void unstare(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.staring) {
			endStare(player, s, false);
		}
	}

	private static void endStare(ServerPlayer player, State s, boolean broken) {
		s.staring = false;
		XenoMinds.cool(player, "cortex_stare", STARE_COOLDOWN);
		if (broken) {
			MagicFx.blast(player.level(), player.getEyePosition(), 0.8F, COLOR, 6);
			MindsKit.sound(player, SoundEvents.GLASS_BREAK, 0.8F, 1.4F);
		}
	}

	private static List<LivingEntity> opponents(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		List<LivingEntity> held = new ArrayList<>();
		for (LivingEntity e : MindsKit.near(player, eye, STARE_RANGE)) {
			if (e instanceof Player || GreatFoes.is(e) || !(e instanceof Enemy || e instanceof Mob m && m.getTarget() == player)) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= STARE_DOT && player.hasLineOfSight(e)) {
				held.add(e);
			}
		}
		held.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		return held.size() > STARE_MAX_HELD ? held.subList(0, STARE_MAX_HELD) : held;
	}

	private static void gaze(ServerPlayer player, ServerLevel level, State s) {
		if (player.level().getGameTime() - s.stareStart >= STARE_TICKS || player.isSpectator()) {
			endStare(player, s, false);
			return;
		}
		boolean beams = player.tickCount % 6 == 0;
		for (LivingEntity e : opponents(player)) {
			if (e instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			Vec3 to = player.position().subtract(e.position());
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			if (flat.length() > STARE_STOP) {
				Vec3 step = flat.normalize().scale(STARE_PULL);
				e.setDeltaMovement(step.x, Math.min(0.0, e.getDeltaMovement().y), step.z);
				e.syncVelocity = true;
			} else {
				e.setDeltaMovement(0.0, Math.min(0.0, e.getDeltaMovement().y), 0.0);
			}
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 6, 3, false, false), player);
			if (beams) {
				MagicFx.beam(level, player.getEyePosition().add(0.0, -0.1, 0.0), e.getEyePosition(), 0.06F, COLOR, 7);
			}
		}
		if (player.tickCount % 10 == 0) {
			MagicFx.sigil(level, player.position().add(0.0, 0.05, 0.0), 2.0F, COLOR, 12);
		}
	}

	// ---- reading ----

	private static String intent(ServerPlayer player, LivingEntity foe) {
		double far = foe.distanceTo(player);
		if (foe instanceof Creeper creeper) {
			return creeper.getSwellDir() > 0 ? "blow" : "close";
		}
		if (foe instanceof Player other) {
			var held = other.getMainHandItem();
			if (other.isBlocking()) {
				return "guard";
			}
			if (held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem) {
				return "shoot";
			}
			return other.isSprinting() ? "rush" : far > 4.0 ? "close" : "strike";
		}
		if (foe instanceof RangedAttackMob && far > 4.0) {
			return "shoot";
		}
		if (far <= 3.5) {
			return "strike";
		}
		return foe instanceof Mob mob && mob.getNavigation().isInProgress() ? "close" : "watch";
	}

	private static void read(ServerPlayer player, ServerLevel level, State s) {
		long now = level.getGameTime();
		Entity found = s.foe < 0 || now - s.foeAt > FOE_TICKS ? null : level.getEntity(s.foe);
		if (!(found instanceof LivingEntity foe) || !foe.isAlive()) {
			XenoMinds.put(player, XenoMinds.FOE, s.staring ? new XenoMinds.FoeHud("", 0.0F, 0.0F, "", "", true) : null);
			return;
		}
		String target = foe instanceof Mob mob && mob.getTarget() != null ? mob.getTarget().getName().getString() : "";
		XenoMinds.put(player, XenoMinds.FOE, new XenoMinds.FoeHud(foe.getName().getString(), foe.getHealth(), foe.getMaxHealth(), target,
			intent(player, foe), s.staring));
	}

	/** Every tick as Cortex. */
	public static void tick(ServerPlayer player) {
		State s = state(player);
		ServerLevel level = player.level();
		if (s.staring) {
			gaze(player, level, s);
		}
		if (!s.haunted.isEmpty()) {
			haunt(player, level, s);
		}
		if (player.tickCount % READ_EVERY == 0) {
			read(player, level, s);
		}
	}

	/** For the key code: whether the stare is on. */
	public static boolean staring(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.staring;
	}
}
