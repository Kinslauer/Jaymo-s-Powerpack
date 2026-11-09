package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.37, Ultimates patch 2) Lurk's Ultimate, Nightfall: the tall stalker grown to 1.3x, its arms longer and plated in bone-black, a
 * ridge of thin spikes down its back and four eye domes, with a health bar of its own on top of Lurk's. Its model is
 * {@code UltimateNightfall}. Marks, the step chain, Deep Fade's strike count and the night itself are kept per player in {@link #STATES}
 * (server thread only) and cleared in {@link #onExit}.
 * <ul>
 * <li>R, Mark of Night: up to {@link #MARKS} creatures within {@link #MARK_REACH} glow for {@link #MARK_TICKS} and lose track of you.</li>
 * <li>G, Nightstep: step out of the dark behind the creature you look at (or the first mark) within {@link #STEP_REACH} and strike for
 * {@link #STEP_DAMAGE}, doubled if it wasn't looking your way. G again within {@link #CHAIN_TICKS} ticks steps behind the next mark.</li>
 * <li>V, Terror: everything looking at you and every mark is struck with darkness, Slowness II and Weakness II; monsters drop their target
 * and are shoved back.</li>
 * <li>B, Deep Fade: unseen, with Speed I; the first strike does not break it, the second (or B again) does.</li>
 * <li>C, Nightfall: for {@link #NIGHT_TICKS} ticks everything else within {@link #NIGHT_RADIUS} is blinded, and glows on your screen alone.</li>
 * </ul>
 */
public final class Nightfall extends XenoUltimate {
	// ---- tuning ----
	/** Mark of Night: how many marks, how far, how long each glows, cooldown. */
	public static final int MARKS = 3, MARK_TICKS = 600, MARK_COOLDOWN = 60;
	public static final double MARK_REACH = 64.0;
	/** Nightstep: reach, how far behind the target you land (blocks, beyond its half width), damage, the unseen multiplier, cooldown, ticks G again may chain. */
	public static final double STEP_REACH = 32.0, STEP_BEHIND = 1.2;
	public static final float STEP_DAMAGE = 10.0F, STEP_UNSEEN_TIMES = 2.0F;
	public static final int STEP_COOLDOWN = 100, CHAIN_TICKS = 40;
	/** Terror: radius, how directly a creature must look at you (dot product), ticks of the effects, ticks of Blindness, cooldown, the shove on monsters. */
	public static final double TERROR_RADIUS = 24.0, TERROR_LOOK = 0.8, TERROR_SHOVE = 0.9;
	public static final int TERROR_TICKS = 160, TERROR_BLIND_TICKS = 40, TERROR_COOLDOWN = 300;
	/** Deep Fade: cooldown, strikes that break it, ticks between refreshes of its invisibility and Speed, how far hunters forget you. */
	public static final int FADE_COOLDOWN = 200, FADE_BREAKS_AT = 2, FADE_REFRESH = 10;
	public static final double FADE_FORGET = 32.0;
	/** Nightfall: its reach, length, cooldown (40 s), ticks its Darkness and Blindness are given for and refreshed at, ticks between outlines and ink rings, points in the ring. */
	public static final double NIGHT_RADIUS = 16.0;
	public static final int NIGHT_TICKS = 160, NIGHT_COOLDOWN = 800, NIGHT_EFFECT_TICKS = 40, NIGHT_REFRESH = 10, OUTLINE_EVERY = 5, RING_EVERY = 10, RING_POINTS = 36;
	/** The most creatures outlined per sweep. */
	public static final int OUTLINE_MAX = 48;

	/** One mark: the creature and the game time its glow ends. */
	private record Mark(UUID id, long until) {
	}

	/** The night in progress: where, until when, and who it dimmed (so the effects can be taken off at its end). */
	private static final class Night {
		final ServerLevel level;
		final long end;
		final Set<UUID> dimmed = new HashSet<>();

		Night(ServerLevel level, long end) {
			this.level = level;
			this.end = end;
		}
	}

	private static final class State {
		final List<Mark> marks = new ArrayList<>();
		/** Marks already stepped to in this chain. */
		final Set<UUID> stepped = new HashSet<>();
		long chainUntil;
		int strikes;
		@Nullable Night night;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public Nightfall(XenoForm base) {
		super(base, 1.3F, 12.0F, 8.0, 0.15, 0.4, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.ENDER_EYE, Items.ENDER_PEARL, Items.SCULK_SHRIEKER, Items.ECHO_SHARD, Items.CLOCK};
	}

	/** The change-in film zooms on these in turn (model part names): the long plated forearms and hands, the spined back, the neck, the head, then the new pair of eyes. */
	@Override
	public String[] morphParts() {
		return new String[] {"right_forearm+left_forearm+right_hand+left_hand", "torso", "neck", "head+right_eye+left_eye", "right_eye_top+left_eye_top"};
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		STATES.put(player.getUUID(), new State());
		live.faded = false;
		burst(player.level(), ParticleTypes.SQUID_INK, player.position().add(0.0, 1.2, 0.0), 50, 0.8, 0.05);
		sound(player, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 0.5F);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		State st = STATES.remove(player.getUUID());
		unfade(player, live);
		if (st != null) {
			if (st.night != null) {
				endNight(st);
			}
			ServerLevel level = player.level();
			for (Mark mark : st.marks) {
				unmark(level, mark);
			}
		}
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		State st = state(player);
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					mark(player, live, level, st);
				}
			}
			case ABILITY_2 -> nightstep(player, live, level, st);
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					terror(player, level, st);
					cooldown(player, live, V, TERROR_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (live.faded) {
					unfade(player, live);
					cooldown(player, live, B, FADE_COOLDOWN);
				} else if (ready(player, live, B)) {
					live.faded = true;
					st.strikes = 0;
					burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.2, 0.0), 24, 0.4, 0.02);
					sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.4F);
					cooldown(player, live, B, 1);
				}
			}
			default -> {
			}
		}
	}

	/** C: Nightfall. Tapping it again while the night lasts does nothing. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		State st = state(player);
		if (st.night != null || !ready(player, live, C)) {
			return;
		}
		ServerLevel level = player.level();
		st.night = new Night(level, level.getGameTime() + NIGHT_TICKS);
		ring(level, ParticleTypes.SQUID_INK, player.position(), NIGHT_RADIUS, RING_POINTS * 2);
		burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.2, 0.0), 60, 1.0, 0.06);
		sound(player, SoundEvents.ELDER_GUARDIAN_CURSE, 1.5F, 0.4F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.5F);
		cooldown(player, live, C, NIGHT_COOLDOWN);
	}

	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		if (live.faded && ++state(player).strikes >= FADE_BREAKS_AT) {
			unfade(player, live);
			cooldown(player, live, B, FADE_COOLDOWN);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		State st = state(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.faded) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FADE_REFRESH, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, FADE_REFRESH, 0, false, false));
			if (player.tickCount % 10 == 0) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FADE_FORGET), m -> m.getTarget() == player)) {
					mob.setTarget(null);
				}
			}
		}
		if (st.chainUntil != 0L && now > st.chainUntil) {
			st.chainUntil = 0L;
			st.stepped.clear();
		}
		if (player.tickCount % 10 == 0) {
			keepMarks(player, level, st, now);
		}
		if (st.night != null) {
			tickNight(player, st, now);
		}
	}

	// ---- R: Mark of Night ----

	private static void mark(ServerPlayer player, XenoformPower.Live live, ServerLevel level, State st) {
		LivingEntity target = aimed(player, MARK_REACH, e -> harmable(player, e));
		if (target == null) {
			return;
		}
		st.marks.removeIf(m -> m.id().equals(target.getUUID()));
		st.marks.add(new Mark(target.getUUID(), level.getGameTime() + MARK_TICKS));
		while (st.marks.size() > MARKS) {
			unmark(level, st.marks.remove(0));
		}
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false), player);
		forget(target, player);
		beam(level, player.getEyePosition(), target.getEyePosition(), ParticleTypes.SQUID_INK, 20);
		sound(player, SoundEvents.ENDERMAN_STARE, 0.6F, 1.4F);
		cooldown(player, live, R, MARK_COOLDOWN);
	}

	/** The mark's glow comes off (only the one Mark of Night gave: unseen particles, and no longer than a mark lasts). */
	private static void unmark(ServerLevel level, Mark mark) {
		if (level.getEntity(mark.id()) instanceof LivingEntity e) {
			MobEffectInstance glow = e.getEffect(MobEffects.GLOWING);
			if (glow != null && glow.getAmplifier() == 0 && !glow.isVisible() && !glow.isAmbient() && glow.getDuration() <= MARK_TICKS) {
				e.removeEffect(MobEffects.GLOWING);
			}
		}
	}

	/** Every so often: expired and dead marks go, the rest lose track of you again. */
	private static void keepMarks(ServerPlayer player, ServerLevel level, State st, long now) {
		for (int i = st.marks.size() - 1; i >= 0; i--) {
			Mark mark = st.marks.get(i);
			Entity e = level.getEntity(mark.id());
			if (now >= mark.until() || e instanceof LivingEntity living && !living.isAlive()) {
				unmark(level, mark);
				st.marks.remove(i);
			} else if (e instanceof LivingEntity living) {
				forget(living, player);
			}
		}
	}

	/** The marks that are still there to be struck, in the order they were made. */
	private static @Nullable LivingEntity nextMark(ServerPlayer player, ServerLevel level, State st, double reach) {
		for (Mark mark : st.marks) {
			if (st.stepped.contains(mark.id())) {
				continue;
			}
			if (level.getEntity(mark.id()) instanceof LivingEntity e && e.isAlive() && !spared(player, e) && harmable(player, e) && e.distanceTo(player) <= reach) {
				return e;
			}
		}
		return null;
	}

	// ---- G: Nightstep ----

	private static void nightstep(ServerPlayer player, XenoformPower.Live live, ServerLevel level, State st) {
		long now = level.getGameTime();
		boolean chain = st.chainUntil > now;
		LivingEntity target;
		if (chain) {
			target = nextMark(player, level, st, STEP_REACH);
			if (target == null) {
				st.chainUntil = 0L;
				st.stepped.clear();
				return;
			}
		} else {
			if (!ready(player, live, G)) {
				return;
			}
			st.stepped.clear();
			target = aimed(player, STEP_REACH, e -> harmable(player, e));
			if (target == null) {
				target = nextMark(player, level, st, STEP_REACH);
			}
			if (target == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lurk.no_target").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				return;
			}
		}
		if (!strike(player, live, level, target)) {
			return;
		}
		st.stepped.add(target.getUUID());
		if (!chain) {
			cooldown(player, live, G, STEP_COOLDOWN);
		}
		st.chainUntil = nextMark(player, level, st, STEP_REACH) != null ? now + CHAIN_TICKS : 0L;
		if (st.chainUntil == 0L) {
			st.stepped.clear();
		}
	}

	/** Out of the dark behind {@code target}, and a blow: twice as hard if it wasn't looking your way. False if there is no room to stand behind it. */
	private static boolean strike(ServerPlayer player, XenoformPower.Live live, ServerLevel level, LivingEntity target) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYRot());
		double full = target.getBbWidth() * 0.5 + player.getBbWidth() * 0.5 + STEP_BEHIND;
		Vec3 spot = null;
		for (double share : new double[] {1.0, 0.6, 0.3}) {
			Vec3 at = target.position().subtract(facing.scale(full * share));
			AABB moved = player.getBoundingBox().move(at.x - player.getX(), target.getY() - player.getY(), at.z - player.getZ());
			if (level.noCollision(player, moved)) {
				spot = new Vec3(at.x, target.getY(), at.z);
				break;
			}
		}
		if (spot == null) {
			return false;
		}
		boolean saw = target.getViewVector(1.0F).dot(player.position().subtract(target.position()).normalize()) > 0.3;
		burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.2, 0.0), 20, 0.4, 0.02);
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), target.getYRot(), 10.0F, false);
		player.resetFallDistance();
		float damage = saw ? STEP_DAMAGE : STEP_DAMAGE * STEP_UNSEEN_TIMES;
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		live.hurt = true;
		sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.5F);
		if (!saw) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 16, 0.3, 0.3, 0.3, 0.2);
		}
		return true;
	}

	// ---- V: Terror ----

	private static void terror(ServerPlayer player, ServerLevel level, State st) {
		Set<LivingEntity> caught = new LinkedHashSet<>();
		for (LivingEntity e : around(player, TERROR_RADIUS)) {
			Vec3 toMe = player.getEyePosition().subtract(e.getEyePosition()).normalize();
			if (harmable(player, e) && e.getViewVector(1.0F).dot(toMe) >= TERROR_LOOK && e.hasLineOfSight(player)) {
				caught.add(e);
			}
		}
		for (Mark mark : st.marks) {
			if (level.getEntity(mark.id()) instanceof LivingEntity e && e.isAlive() && !spared(player, e) && harmable(player, e) && e.distanceTo(player) <= MARK_REACH) {
				caught.add(e);
			}
		}
		for (LivingEntity e : caught) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TERROR_TICKS, 0), player);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TERROR_TICKS, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TERROR_TICKS, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TERROR_BLIND_TICKS, 0), player);
			if (e instanceof Mob mob && e instanceof Enemy) {
				mob.setTarget(null);
				double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				shove(e, player.position(), TERROR_SHOVE * resist, 0.25 * resist);
			}
		}
		burst(level, ParticleTypes.SCULK_SOUL, player.getEyePosition(), 14 + caught.size() * 5, 0.8, 0.05);
		ring(level, ParticleTypes.SQUID_INK, player.position(), TERROR_RADIUS * 0.4, 28);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.6F, 0.5F);
		sound(player, SoundEvents.WARDEN_ROAR, 1.0F, 1.4F);
	}

	// ---- C: Nightfall ----

	private static void tickNight(ServerPlayer player, State st, long now) {
		Night night = st.night;
		if (night == null) {
			return;
		}
		ServerLevel level = player.level();
		if (night.level != level || now >= night.end) {
			endNight(st);
			sound(player, SoundEvents.AMBIENT_CAVE.value(), 1.0F, 0.6F);
			return;
		}
		if (player.tickCount % NIGHT_REFRESH == 0) {
			for (LivingEntity e : around(player, NIGHT_RADIUS)) {
				if (harmable(player, e)) {
					e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, NIGHT_EFFECT_TICKS, 0, false, false), player);
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, NIGHT_EFFECT_TICKS, 0, false, false), player);
					night.dimmed.add(e.getUUID());
				}
			}
		}
		if (player.tickCount % OUTLINE_EVERY == 0) {
			outline(player, level);
		}
		if (player.tickCount % RING_EVERY == 0) {
			ring(level, ParticleTypes.SQUID_INK, player.position(), NIGHT_RADIUS, RING_POINTS);
		}
	}

	/** Every creature inside outlined in pale violet points, sent to this player alone. */
	private static void outline(ServerPlayer player, ServerLevel level) {
		int drawn = 0;
		for (LivingEntity e : around(player, NIGHT_RADIUS)) {
			if (drawn++ >= OUTLINE_MAX) {
				break;
			}
			AABB b = e.getBoundingBox();
			double mid = (b.minY + b.maxY) * 0.5;
			for (double y : new double[] {b.minY, mid, b.maxY}) {
				level.sendParticles(player, ParticleTypes.WITCH, true, false, b.minX, y, b.minZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.WITCH, true, false, b.maxX, y, b.minZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.WITCH, true, false, b.minX, y, b.maxZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.WITCH, true, false, b.maxX, y, b.maxZ, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** The night ends: the Darkness and Blindness it gave (and only those) come off whoever is still in reach. */
	private static void endNight(State st) {
		Night night = st.night;
		st.night = null;
		if (night == null) {
			return;
		}
		for (UUID id : night.dimmed) {
			if (night.level.getEntity(id) instanceof LivingEntity e) {
				takeOff(e, MobEffects.DARKNESS);
				takeOff(e, MobEffects.BLINDNESS);
			}
		}
	}

	private static void takeOff(LivingEntity e, net.minecraft.core.Holder<MobEffect> effect) {
		MobEffectInstance had = e.getEffect(effect);
		if (had != null && had.getAmplifier() == 0 && !had.isVisible() && !had.isAmbient() && had.getDuration() <= NIGHT_EFFECT_TICKS) {
			e.removeEffect(effect);
		}
	}

	// ---- helpers ----

	/** A player the attacker may not hurt (pvp off) is never struck, blinded or slowed. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

	/** A creature hunting {@code player} loses them. */
	private static void forget(LivingEntity e, ServerPlayer player) {
		if (e instanceof Mob mob && mob.getTarget() == player) {
			mob.setTarget(null);
		}
	}

	/** Fade ends: visible again, the Speed it gave off (only that: an unseen, short one). */
	private static void unfade(ServerPlayer player, XenoformPower.Live live) {
		if (!live.faded) {
			return;
		}
		live.faded = false;
		for (net.minecraft.core.Holder<MobEffect> effect : List.of(MobEffects.INVISIBILITY, MobEffects.SPEED)) {
			MobEffectInstance had = player.getEffect(effect);
			if (had != null && !had.isVisible() && !had.isAmbient() && had.getDuration() <= FADE_REFRESH) {
				player.removeEffect(effect);
			}
		}
	}
}
