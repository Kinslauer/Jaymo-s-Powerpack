package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Instant Step: with Ki Sense on, raise two fingers to the brow ({@link ZFighterHands#BROW}) and blink behind the foe
 * you have picked or are looking at, leaving a short shimmer at both ends. It only claims B when it can really go
 * (a sensed foe, the ki, a free landing); otherwise B is Flash Step's as ever.
 */
public final class ZFighterInstantStep {
	/** Ticks holding two fingers to the brow before blinking. */
	static final int PREP_TICKS = 4;
	/** Ki cost and cooldown (ticks). */
	static final float STEP_KI = 12.0F;
	static final int STEP_COOLDOWN = 24;
	/** Max distance to lock onto a foe in sights. */
	static final double SIGHT_RANGE = 24.0;
	static final float STEP_EXHAUSTION = 0.6F;
	/** Shimmer colour tint blended with the fighter's ki colour. */
	static final int SHIMMER_COLOR = 0xB8F0FF;

	private record Pending(long start, int targetId) {
	}

	/** Where the blink lands: the feet position and the facing there (toward the foe). */
	private record Spot(Vec3 dest, float yaw, float pitch) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private ZFighterInstantStep() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	static void forget(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	static void clear() {
		PENDING.clear();
	}

	/** Whether this fighter currently has two fingers raised to his brow for Instant Step. */
	static boolean posing(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	/**
	 * Starts the brief brow-touch wind-up when Ki Sense is on, a sensed foe is picked or in sights, the fighter can pay
	 * and there is a free spot behind it. Returns true only if Instant Step claimed the key press; on false nothing was
	 * spent and the press is free for Flash Step.
	 */
	static boolean tryStart(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterKiSense.learned(player) || !ZFighterKiSense.isOn(player) || ZFighterLineage.ape(player) || Powers.silenced(player)) {
			return false;
		}
		if (PENDING.containsKey(player.getUUID())) {
			return true;
		}
		if (!ZFighterPower.ready(player, ZFighterState.STEP)) {
			return false;
		}
		LivingEntity target = sightedFoe(player);
		double sense = ZFighterKiSense.range(player);
		if (target == null || ZFighterKiSense.hidesKi(target) || target.distanceToSqr(player) > sense * sense) {
			return false;
		}
		float cost = STEP_KI * ZFighterAndroid.costFactor(player);
		if (!player.isCreative() && !live.raging() && live.ki < cost && !ZFighterRaces.canCover(player, live, cost)) {
			return false;
		}
		// the landing is found before anything is spent
		Spot spot = spotBehind(player, target);
		if (spot == null || spot.dest().distanceToSqr(player.position()) < 0.25) {
			return false;
		}
		if (!ZFighterHooks.flashStepStamina(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		PENDING.put(player.getUUID(), new Pending(now, target.getId()));
		ZFighterHands.pose(player, ZFighterHands.BROW);
		ServerLevel level = player.level();
		Vec3 brow = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.35)).add(0.0, 0.12, 0.0);
		MagicFx.orb(level, brow, 0.25F, SHIMMER_COLOR, PREP_TICKS + 2);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65F, 1.9F);
		return true;
	}

	/** Completes the blink once the brow-touch ticks finish. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null) {
			return;
		}
		if (!player.isAlive() || Powers.silenced(player) || ZFighterLineage.ape(player)) {
			PENDING.remove(player.getUUID());
			return;
		}
		long now = player.level().getGameTime();
		if (now - pending.start() < PREP_TICKS) {
			return;
		}
		PENDING.remove(player.getUUID());
		execute(player, live, pending);
	}

	private static void execute(ServerPlayer player, ZFighterPower.Live live, Pending pending) {
		ServerLevel level = player.level();
		Vec3 start = player.position();
		LivingEntity target = resolveTarget(player, level, pending.targetId());
		Spot spot = target == null ? null : spotBehind(player, target);
		if (spot == null || spot.dest().distanceToSqr(start) < 0.25) {
			return;
		}
		Vec3 dest = spot.dest();
		float yaw = spot.yaw();
		float pitch = spot.pitch();

		if (!ZFighterPower.spend(player, live, STEP_KI)) {
			return;
		}
		int color = ZFighterPower.auraColor(live);
		shimmer(level, start, player.getBbHeight(), color);
		level.playSound(null, start.x, start.y + 1.0, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.65F);

		live.stepUntil = level.getGameTime() + 10;
		ZFighterPower.cooldown(player, ZFighterState.STEP, STEP_COOLDOWN);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, pitch, false);
		player.resetFallDistance();

		shimmer(level, dest, player.getBbHeight(), color);
		level.playSound(null, dest.x, dest.y + 1.0, dest.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.75F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(STEP_EXHAUSTION);
		}
	}

	/** The first free spot behind (or beside) the foe, facing it; null when there is none. */
	private static @Nullable Spot spotBehind(ServerPlayer player, LivingEntity target) {
		Vec3 start = player.position();
		float facing = target.getYRot() * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(facing), 0.0, Mth.cos(facing));
		double gap = target.getBbWidth() * 0.5 + 0.95;
		Vec3 away = target.position().subtract(start).multiply(1.0, 0.0, 1.0);
		Vec3 alt = away.lengthSqr() < 1.0E-4 ? target.position().subtract(forward.scale(gap))
			: target.position().add(away.normalize().scale(gap));
		for (Vec3 candidate : new Vec3[] {
			target.position().subtract(forward.scale(gap)),
			alt,
			target.position().subtract(forward.scale(gap)).add(0.0, 1.0, 0.0)
		}) {
			if (free(player, candidate)) {
				Vec3 toTarget = target.getEyePosition().subtract(candidate.add(0.0, player.getEyeHeight(), 0.0));
				float yaw = (float)(Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
				float pitch = (float)(-Mth.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * Mth.RAD_TO_DEG);
				return new Spot(candidate, yaw, pitch);
			}
		}
		return null;
	}

	/** Short pale-ki shimmer column at departure and landing. */
	private static void shimmer(ServerLevel level, Vec3 feet, float height, int kiColor) {
		Vec3 mid = feet.add(0.0, height * 0.5, 0.0);
		MagicFx.orb(level, mid, 0.7F, SHIMMER_COLOR, 6);
		MagicFx.ring(level, feet.add(0.0, 0.15, 0.0), 1.35F, SHIMMER_COLOR);
		ParticleOptions wisp = MagicFx.wisp(SHIMMER_COLOR, 1.0F);
		ParticleOptions tint = MagicFx.wisp(kiColor, 0.85F);
		for (double h = 0.15; h < height; h += 0.28) {
			level.sendParticles(wisp, true, false, feet.x, feet.y + h, feet.z, 2, 0.22, 0.04, 0.22, 0.02);
			level.sendParticles(tint, true, false, feet.x, feet.y + h, feet.z, 1, 0.18, 0.04, 0.18, 0.01);
		}
	}

	private static @Nullable LivingEntity resolveTarget(ServerPlayer player, ServerLevel level, int targetId) {
		if (targetId >= 0) {
			Entity e = level.getEntity(targetId);
			if (e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
				&& living.distanceToSqr(player) <= (SIGHT_RANGE + 4.0) * (SIGHT_RANGE + 4.0)) {
				return living;
			}
		}
		return sightedFoe(player);
	}

	private static @Nullable LivingEntity sightedFoe(ServerPlayer player) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && locked.isAlive() && locked.distanceToSqr(player) <= SIGHT_RANGE * SIGHT_RANGE) {
			return locked;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 reach = eye.add(look.scale(SIGHT_RANGE));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, reach,
			player.getBoundingBox().expandTowards(look.scale(SIGHT_RANGE)).inflate(1.2),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != player,
			SIGHT_RANGE * SIGHT_RANGE);
		if (hit != null && hit.getEntity() instanceof LivingEntity living && player.hasLineOfSight(living)) {
			return living;
		}
		return null;
	}

	private static boolean free(ServerPlayer player, Vec3 feet) {
		return player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())));
	}
}
