package com.jaymospowers.rig;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.12.x) Meteor dive: rocket straight up, then crash down where you look. Reuses GravityMeteorFx's look at Rig scale:
 * a crater-less ring, no block damage, a big shake.
 */
public final class RigDive {
	/** Upward launch speed (b/t), downward crash speed (b/t), how high it goes (blocks). */
	static final double UP_SPEED = 1.5, DOWN_SPEED = 2.5, MAX_HEIGHT = 40.0;
	/** Impact radius (blocks), damage, knockback. */
	static final double RADIUS = 4.0, DAMAGE = 20.0, KNOCKBACK = 1.5;
	/** Screen shake strength and range (blocks). */
	static final float SHAKE = 2.0F, SHAKE_RANGE = 32.0F;
	/** The ring at impact. */
	static final float RING = 5.0F;
	/** Charge cost and cooldown (ticks). */
	static final int COST = 200, COOLDOWN = 300;

	private static final class Dive {
		/** 0: ascending, 1: descending, 2: done. */
		int phase;
		Vec3 target;
		long startTime;
		int cooldown;

		Dive(Vec3 target, long startTime) {
			this.phase = 0;
			this.target = target;
			this.startTime = startTime;
			this.cooldown = 0;
		}
	}

	private static final java.util.Map<java.util.UUID, Dive> DIVES = new java.util.HashMap<>();

	private RigDive() {
	}

	/** Called from RigPicks.use. */
	public static void meteorDive(ServerPlayer player) {
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		if (!Rig.spend(player, COST)) {
			RigMind.empty(player);
			return;
		}
		Dive existing = DIVES.get(player.getUUID());
		if (existing != null && existing.phase != 2) {
			// already diving
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 target = player.position().add(look.scale(20.0)).add(0.0, 2.0, 0.0);
		DIVES.put(player.getUUID(), new Dive(target, player.level().getGameTime()));
		RigMoves.start(player, RigMoves.METEOR);
		ServerLevel level = player.level();
		// launch upward
		player.setDeltaMovement(0.0, UP_SPEED, 0.0);
		player.syncVelocity = true;
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), RING, RigActives.FLAME_COLOR);
		MagicFx.blast(level, player.position().add(0.0, 0.3, 0.0), 1.5F, RigActives.FLAME_HOT);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.5F);
		RigMind.sayAlways(player, "meteor", 1.8F);
	}

	/** Every server tick: update active dives. */
	public static void tick(MinecraftServer server) {
		for (var entry : new java.util.ArrayList<>(DIVES.entrySet())) {
			java.util.UUID uuid = entry.getKey();
			Dive dive = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null || !player.isAlive()) {
				DIVES.remove(uuid);
				continue;
			}
			if (dive.cooldown > 0) {
				dive.cooldown--;
				continue;
			}
			ServerLevel level = player.level();
			if (dive.phase == 0) {
				// ascending
				if (player.getY() >= dive.target.y || player.getDeltaMovement().y <= 0.0) {
					// reached apex, start descending
					dive.phase = 1;
					player.setDeltaMovement(0.0, -DOWN_SPEED, 0.0);
					player.syncVelocity = true;
				}
			} else if (dive.phase == 1) {
				// descending
				if (player.onGround() || player.getY() <= level.getMinY() + 2) {
					// impact!
					impact(level, player, dive.target);
					dive.phase = 2;
					dive.cooldown = COOLDOWN;
					DIVES.remove(uuid);
				}
			}
		}
	}

	private static void impact(ServerLevel level, ServerPlayer player, Vec3 target) {
		Vec3 at = player.position();
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), null, RING, 0xF2F6FF, 12);
		MagicFx.blast(level, at.add(0.0, 0.5, 0.0), 2.0F, 0xFFFFFF);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
		Shakes.around(level, at, SHAKE_RANGE, SHAKE);
		// particles: reuse GravityMeteorFx style but smaller
		for (int i = 0; i < 30; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = 0.5 + level.getRandom().nextDouble() * RADIUS;
			BlockPos pos = BlockPos.containing(at.x + Math.cos(angle) * dist, at.y - 0.5, at.z + Math.sin(angle) * dist);
			if (!level.getBlockState(pos).isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), at.x, at.y + 0.1, at.z, 4, 0.5, 0.1, 0.5, 0.1);
			}
		}
		// damage and knockback nearby entities
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS), e -> e != player && e.isAlive())) {
			near.hurtServer(level, level.damageSources().playerAttack(player), (float)DAMAGE);
			Vec3 away = near.position().subtract(at);
			near.knockback(KNOCKBACK, -away.x, -away.z, level.damageSources().playerAttack(player), (float)DAMAGE);
			near.syncVelocity = true;
		}
		RigMoves.start(player, RigMoves.KNEE);
	}

	/** Called from RigPicks.init to register the server tick. */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(RigDive::tick);
	}
}