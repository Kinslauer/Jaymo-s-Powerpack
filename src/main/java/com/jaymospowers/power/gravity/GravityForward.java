package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Fall forward (sneak+V while Weightless): for {@link #TICKS} your "down" points where you look, and you fall that
 * way, head first, gathering speed like a real fall; you can steer it by looking. The client flies it
 * ({@code GravityForwardClient}); the server sees what you hit. Creatures in your way take the hit and are thrown
 * along; hitting a wall or the ground at speed throws up its debris and shakes your view, but Weightless keeps you
 * from the damage. Each one burns {@link #WEIGHTLESS_COST} of Weightless's time.
 */
public final class GravityForward {
	/** How long "down" points ahead (ticks), how fast it gathers speed (blocks/tick²), and its top speed. */
	public static final int TICKS = 24;
	public static final double ACCEL = 0.18, MAX_SPEED = 2.2;
	/** Weightless time it uses up (ticks of the 30 s), and the wait before another. */
	static final int WEIGHTLESS_COST = 100, COOLDOWN = 30;
	/** Hitting a creature: damage per block/tick of speed (capped), and how hard it's thrown along. */
	static final float HIT_PER_SPEED = 5.0F, HIT_MAX = 14.0F;
	static final double HIT_THROW = 0.9;
	/** Running into something solid at least this fast (blocks/tick) is an impact. */
	static final double IMPACT_SPEED = 0.6;

	/** On a player: falling forward right now (everyone sees the head-first dive; its owner's client flies it). */
	public static final AttachmentType<Boolean> FALLING = AttachmentRegistry.create(JaymosPowers.id("gravity_forward"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final class Run {
		int ticks;
		Vec3 last;
		double speed;
		final Set<UUID> hit = new HashSet<>();

		Run(Vec3 at) {
			last = at;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private GravityForward() {
	}

	/** Returns false (nothing happens) while one is still going or not yet ready again. */
	static boolean start(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (RUNS.containsKey(player.getUUID()) || now < READY_AT.getOrDefault(player.getUUID(), 0L) || player.isPassenger()
			|| player.isInWater()) {
			return false;
		}
		RUNS.put(player.getUUID(), new Run(player.position()));
		READY_AT.put(player.getUUID(), now + TICKS + COOLDOWN);
		player.setAttached(FALLING, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 2.0F);
		Vec3 look = player.getLookAngle();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
		level.sendParticles(ParticleTypes.END_ROD, player.getX() - look.x, player.getY() + 1.0 - look.y, player.getZ() - look.z, 8, 0.2, 0.2, 0.2, 0.02);
		return true;
	}

	static boolean falling(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** From Gravity's player tick. */
	static void tick(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		ServerLevel level = player.level();
		run.ticks++;
		Vec3 now = player.position();
		double moved = now.distanceTo(run.last);
		run.last = now;
		player.resetFallDistance();
		// in your way: knocked along, and hurt by how fast you were going
		double speed = Math.max(run.speed, moved);
		if (speed > 0.3) {
			Vec3 dir = player.getLookAngle();
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.6),
				e -> e != player && e.isAlive() && !e.isSpectator() && !run.hit.contains(e.getUUID()))) {
				run.hit.add(living.getUUID());
				living.hurtServer(level, player.damageSources().playerAttack(player), Math.min(HIT_MAX, (float)speed * HIT_PER_SPEED));
				Vec3 push = dir.scale(speed * HIT_THROW).add(0.0, 0.2, 0.0);
				living.setDeltaMovement(push);
				living.needsSync = true;
				living.syncVelocity = true;
				GravityPower.touch(living, player);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
		}
		boolean impact = run.ticks > 2 && Math.max(run.speed, moved) >= IMPACT_SPEED && (player.horizontalCollision
			// (skimming along the ground isn't hitting it; diving into it is)
			|| player.onGround() && player.getLookAngle().y < -0.25);
		run.speed = moved;
		if (impact) {
			// whatever stopped you takes the blow; you don't (Weightless)
			Vec3 dir = player.getLookAngle();
			BlockPos at = BlockPos.containing(player.position().add(0.0, 0.9, 0.0).add(dir.scale(1.0)));
			com.jaymospowers.core.Debris.puff(level, at, level.getBlockState(at), 1.8F);
			com.jaymospowers.core.Debris.puff(level, player.blockPosition().below(), level.getBlockState(player.blockPosition().below()), 1.0F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.9F);
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new com.jaymospowers.power.web.WebNet.Shake((float)Mth.clamp(speed * 0.35, 0.2, 0.9)));
			stop(player);
		} else if (run.ticks >= TICKS || player.isInWater() || player.isPassenger()) {
			stop(player);
		} else if (run.ticks % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.9, player.getZ(), 3, 0.25, 0.4, 0.25, 0.02);
		}
	}

	static void stop(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		player.removeAttached(FALLING);
	}

	static void forget(ServerPlayer player) {
		stop(player);
		READY_AT.remove(player.getUUID());
	}

	static void clear() {
		RUNS.clear();
		READY_AT.clear();
	}
}
