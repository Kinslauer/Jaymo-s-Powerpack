package com.jaymospowers.power.gravity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sideways falls: "down" turns on its side for a creature, and it falls toward a wall instead of the floor, picking
 * up speed the way a real fall does, until it hits something and takes the fall in one go. Sneak+G (the sideways
 * Well) drops everything around you outward; sneak and let go of R, and what Lift held falls the way you look. With
 * nothing in the way it falls for {@link #MAX_TICKS} and then its weight comes back the right way up.
 */
final class GravitySideFall {
	/** How fast it gathers speed (blocks/tick², a little more than a real fall's 0.08), its top speed, how long it can fall. */
	static final double ACCEL = 0.1, MAX_SPEED = 2.4;
	static final int MAX_TICKS = 60;
	/** The fall that hurts: the first {@code SAFE} blocks are free, then {@code DAMAGE_PER_BLOCK} for each, up to {@code DAMAGE_MAX}. */
	static final double SAFE = 2.5;
	static final float DAMAGE_PER_BLOCK = 1.2F, DAMAGE_MAX = 24.0F;
	/** It's hit the wall when it moves less than this share of what it should have (after its first few ticks). */
	static final double STOPPED = 0.3;
	/** The sideways Well: how far it reaches. */
	static final double WELL_RADIUS = 8.0;

	private static final class Fall {
		final ResourceKey<Level> dimension;
		final UUID entity, owner;
		final Vec3 dir;
		final boolean hadNoGravity;
		double speed, fallen;
		int ticks;
		Vec3 last;

		Fall(ResourceKey<Level> dimension, Entity entity, UUID owner, Vec3 dir) {
			this.dimension = dimension;
			this.entity = entity.getUUID();
			this.owner = owner;
			this.dir = dir;
			this.hadNoGravity = entity.isNoGravity();
			this.last = entity.position();
		}
	}

	private static final List<Fall> FALLS = new ArrayList<>();

	private GravitySideFall() {
	}

	/** {@code target} falls along {@code dir} (flattened to the horizontal). */
	static void start(ServerPlayer owner, Entity target, Vec3 dir) {
		Vec3 flat = dir.horizontal();
		if (flat.lengthSqr() < 1.0E-4 || !target.isAlive()) {
			return;
		}
		FALLS.removeIf(f -> f.entity.equals(target.getUUID()));
		FALLS.add(new Fall(owner.level().dimension(), target, owner.getUUID(), flat.normalize()));
		target.setNoGravity(true);
		target.resetFallDistance();
		if (target instanceof LivingEntity living) {
			GravityPower.touch(living, owner);
		}
	}

	/** Sneak+G: everything around you falls away from you, outward. */
	static void well(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 me = player.position();
		Vec3 look = player.getLookAngle();
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WELL_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceTo(player) <= WELL_RADIUS)) {
			Vec3 away = living.position().subtract(me).horizontal();
			start(player, living, away.lengthSqr() < 0.01 ? look : away);
		}
		level.playSound(null, me.x, me.y, me.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.4F);
		level.playSound(null, me.x, me.y, me.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.8F);
		// a flat ring of the world's dust thrown outward
		for (int i = 0; i < 24; i++) {
			double a = i * Mth.TWO_PI / 24.0;
			double dx = Math.cos(a), dz = Math.sin(a);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, me.x + dx, me.y + 1.0, me.z + dz, 0, dx, 0.0, dz, 0.6);
		}
		com.jaymospowers.core.Debris.ring(level, player.blockPosition().below(), 2, 1.0F);
	}

	/** Whether this creature is falling sideways right now. */
	static boolean falling(Entity entity) {
		for (Fall fall : FALLS) {
			if (fall.entity.equals(entity.getUUID())) {
				return true;
			}
		}
		return false;
	}

	static void tick(MinecraftServer server) {
		if (FALLS.isEmpty()) {
			return;
		}
		for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
			Fall fall = it.next();
			ServerLevel level = server.getLevel(fall.dimension);
			Entity entity = level == null ? null : level.getEntity(fall.entity);
			if (entity == null || !entity.isAlive()) {
				it.remove();
				continue;
			}
			fall.ticks++;
			Vec3 now = entity.position();
			double moved = now.subtract(fall.last).horizontal().dot(fall.dir);
			fall.last = now;
			fall.fallen += Math.max(0.0, moved);
			boolean stopped = fall.ticks > 3 && (entity.horizontalCollision || moved < fall.speed * STOPPED);
			if (stopped || entity.isInWater() || fall.ticks >= MAX_TICKS) {
				it.remove();
				end(level, entity, fall, stopped);
				continue;
			}
			fall.speed = Math.min(MAX_SPEED, fall.speed + ACCEL);
			Vec3 v = fall.dir.scale(fall.speed);
			entity.setDeltaMovement(v);
			entity.resetFallDistance();
			entity.needsSync = true;
			entity.syncVelocity = true;
			if (entity instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(v));
			}
		}
	}

	/** Its weight comes back the right way up; if it hit something, the whole fall lands on it at once. */
	private static void end(ServerLevel level, Entity entity, Fall fall, boolean hit) {
		entity.setNoGravity(fall.hadNoGravity);
		entity.resetFallDistance();
		entity.setDeltaMovement(Vec3.ZERO);
		entity.needsSync = true;
		entity.syncVelocity = true;
		if (!hit) {
			return;
		}
		float damage = (float)Math.min(DAMAGE_MAX, Math.max(0.0, fall.fallen - SAFE) * DAMAGE_PER_BLOCK);
		if (damage > 0.0F && entity instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().fall(), damage);
		}
		// the wall it hit throws up its dust
		Vec3 front = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).add(fall.dir.scale(entity.getBbWidth() * 0.5 + 0.4));
		BlockPos wall = BlockPos.containing(front);
		BlockState state = level.getBlockState(wall);
		com.jaymospowers.core.Debris.puff(level, wall, state, (float)Mth.clamp(fall.speed, 0.6, 2.2));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), fall.speed > 1.2 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
			SoundSource.PLAYERS, 1.0F, 0.8F);
		if (entity instanceof ServerPlayer player) {
			com.jaymospowers.core.Shakes.heavy(player, front, 10.0, (float)Mth.clamp(fall.speed * 0.4, 0.2, 1.0));
		}
	}

	static void clear() {
		FALLS.clear();
	}
}
