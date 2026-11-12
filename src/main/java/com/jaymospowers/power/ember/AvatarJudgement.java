package com.jaymospowers.power.ember;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Karma;
import com.jaymospowers.power.smoke.SmokePower;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Judgement: a Smoke move for an Emberborn who has also become a Flame Avatar (sneak + Z, held; the avatar's fire in the
 * hands, the Smoke user's smoke at the eyes). Seize the creature you aim at (within {@link #RANGE} blocks) and hold its
 * eyes: it stands rooted while the harm it has done burns it, a pulse every {@link #PULSE_EVERY} ticks for as long as you
 * hold (up to {@link #MAX_TICKS}). What it deals scales with how guilty the target stands ({@link Karma#guilt}): a player by
 * the weight of their life (killings of villagers and players, crimes that cost a settlement's good name and outlaw
 * standing, all of which Karma already carries), an outlaw (Kingdoms' looters, assassins and camp raiders, through
 * {@link Karma#GUILTY}) a fixed share, and everything else (animals, villagers, monsters, the blameless) none: nothing
 * burns, the hold doesn't start, and it costs no cooldown. It burns through armour. Costs flame; the cooldown is
 * {@link #COOLDOWN}.
 */
public final class AvatarJudgement {
	// ---- tuning ----
	public static final double RANGE = 16.0, KEEP_RANGE = 24.0;
	public static final int MAX_TICKS = 60, PULSE_EVERY = 10, COOLDOWN = 400;
	/** Damage a pulse at full guilt, and the least a guilty one takes. */
	static final float PULSE_MAX = 6.0F, PULSE_MIN = 1.0F;
	/** Seconds a guilty creature is set alight for, each pulse. */
	static final float BURN_SECONDS = 3.0F;
	/** Flame to seize, and a tick held. */
	static final float START_COST = 3.0F, TICK_COST = 0.1F;
	/** The motes thrown at a pulse. */
	static final int PULSE_MOTES = 6;

	private record Hold(int target, long from) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private AvatarJudgement() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AvatarJudgement::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HOLDS.clear();
			READY.clear();
		});
	}

	/** Whether {@code player} can make this move: a Smoke user, of Emberborn blood, and a Flame Avatar. */
	public static boolean available(ServerPlayer player) {
		return Powers.has(player, SmokePower.INSTANCE) && FlameAvatar.is(player);
	}

	public static boolean holding(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	/** The key (sneak + Z), pressed or let go. */
	public static void key(ServerPlayer player, boolean down) {
		if (!down) {
			stop(player);
		} else if (!holding(player) && available(player) && player.isAlive() && !player.isSpectator()) {
			start(player);
		}
	}

	private static void start(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = READY.get(player.getUUID());
		if (ready != null && ready > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.judgement_wait", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(RANGE)),
			player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof ArmorStand) && e != player, RANGE * RANGE);
		if (aimed == null || !(aimed.getEntity() instanceof LivingEntity target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.judgement_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (Karma.guilt(target) <= 0.0F) {
			// the blameless feel nothing
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.judgement_blameless").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
			return;
		}
		if (!FlameAvatar.spend(player, START_COST)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.no_flame").withStyle(ChatFormatting.GRAY));
			return;
		}
		HOLDS.put(player.getUUID(), new Hold(target.getId(), now));
		MagicFx.sigil(level, target.getEyePosition().add(0.0, 0.6, 0.0), 0.8F, MagicFx.EMBER_COLOR, MAX_TICKS);
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.5F);
		if (target instanceof ServerPlayer judged) {
			judged.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.judged").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
	}

	/** Lets go; the cooldown starts. */
	static void stop(ServerPlayer player) {
		if (HOLDS.remove(player.getUUID()) != null) {
			READY.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
		}
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hold> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Hold hold = entry.getValue();
			if (player == null || !player.isAlive() || !available(player)) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long t = level.getGameTime() - hold.from;
			Entity found = level.getEntity(hold.target);
			if (!(found instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > KEEP_RANGE || t > MAX_TICKS
				|| !player.hasLineOfSight(target) || !FlameAvatar.spend(player, TICK_COST)) {
				it.remove();
				READY.put(player.getUUID(), level.getGameTime() + COOLDOWN);
				continue;
			}
			// his eyes held: rooted where he stands
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 3, 6, false, false));
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			Vec3 eyes = target.getEyePosition();
			level.sendParticles(ParticleTypes.SMOKE, eyes.x, eyes.y, eyes.z, 2, 0.15, 0.15, 0.15, 0.01);
			if (t > 0 && t % PULSE_EVERY == 0) {
				float guilt = Karma.guilt(target);
				if (guilt <= 0.0F) {
					it.remove();
					READY.put(player.getUUID(), level.getGameTime() + COOLDOWN);
					continue;
				}
				pulse(player, target, guilt);
			}
		}
	}

	/** One pulse of the harm burning the target. */
	private static void pulse(ServerPlayer player, LivingEntity target, float guilt) {
		ServerLevel level = player.level();
		float damage = PULSE_MIN + (PULSE_MAX - PULSE_MIN) * guilt;
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
		target.igniteForSeconds(BURN_SECONDS);
		Vec3 eyes = target.getEyePosition();
		MagicFx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), eyes, 0.06F, MagicFx.EMBER_COLOR, 6);
		MagicFx.ring(level, eyes, 0.6F, MagicFx.EMBER_COLOR);
		level.sendParticles(MagicFx.mote(MagicFx.EMBER_COLOR, 0.6F), eyes.x, eyes.y, eyes.z, PULSE_MOTES, 0.25, 0.25, 0.25, 0.03);
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
	}
}
