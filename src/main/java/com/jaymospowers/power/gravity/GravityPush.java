package com.jaymospowers.power.gravity;

import com.jaymospowers.core.Mastery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * R + left click on a held creature: the Push. It's launched straight away from you along your sight, {@code
 * DISTANCE_BASE} blocks fresh and up to {@code DISTANCE_MAX} mastered. Started inside a wall, it goes straight through
 * walls; otherwise loose blocks in its way ({@code LOOSE_HARDNESS} or softer) are shoved out of it, each costing it
 * {@code BLOCK_COST} of its flight and some speed, and anything harder stops it dead (hard). Whatever it passes
 * through is battered aside. It stops when its flight runs out, or it dies.
 */
final class GravityPush {
	static final double DISTANCE_BASE = 20.0, DISTANCE_MAX = 50.0;
	/** Speed (blocks/tick), and what each shoved block costs: flight, and a share of speed. */
	static final double SPEED = 2.0, BLOCK_COST = 3.0, BLOCK_SLOW = 0.85;
	static final float LOOSE_HARDNESS = 2.0F;
	/** Stopped dead by something hard: this, plus per block/tick of speed it had left. Others in the way: this much. */
	static final float IMPACT_BASE = 4.0F, IMPACT_PER_SPEED = 3.0F, PASS_DAMAGE = 5.0F;

	private static final class Push {
		final ResourceKey<Level> dimension;
		final UUID entity, pusher;
		final Vec3 dir;
		double left, speed;
		final boolean phasing;
		final Set<Integer> hit = new HashSet<>();

		Push(ResourceKey<Level> dimension, UUID entity, UUID pusher, Vec3 dir, double left, boolean phasing) {
			this.dimension = dimension;
			this.entity = entity;
			this.pusher = pusher;
			this.dir = dir;
			this.left = left;
			this.speed = SPEED;
			this.phasing = phasing;
		}
	}

	private static final List<Push> PUSHES = new ArrayList<>();

	private GravityPush() {
	}

	/** {@code target} is pushed away from {@code player}. */
	static void push(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		double distance = Mth.lerp(Mastery.unlocked(player) / (float)Mastery.ALL, DISTANCE_BASE, DISTANCE_MAX);
		boolean phasing = !level.noCollision(target, target.getBoundingBox().deflate(0.05));
		PUSHES.removeIf(p -> p.entity.equals(target.getUUID()));
		PUSHES.add(new Push(level.dimension(), target.getUUID(), player.getUUID(), player.getLookAngle(), distance, phasing));
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		target.noPhysics = true;
		target.setNoGravity(true);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Push> it = PUSHES.iterator(); it.hasNext(); ) {
			Push push = it.next();
			ServerLevel level = server.getLevel(push.dimension);
			Entity entity = level == null ? null : level.getEntity(push.entity);
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || push.left <= 0.0) {
				if (entity != null) {
					release(entity);
				}
				it.remove();
				continue;
			}
			ServerPlayer pusher = server.getPlayerList().getPlayer(push.pusher);
			double step = Math.min(push.speed, push.left);
			AABB next = target.getBoundingBox().move(push.dir.scale(step));
			boolean stopped = false;
			if (!push.phasing) {
				for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(next.minX, next.minY, next.minZ),
					BlockPos.containing(next.maxX - 1.0E-3, next.maxY - 1.0E-3, next.maxZ - 1.0E-3))) {
					BlockState state = level.getBlockState(pos);
					if (state.getCollisionShape(level, pos).isEmpty()) {
						continue;
					}
					float hardness = state.getDestroySpeed(level, pos);
					if (hardness >= 0.0F && hardness <= LOOSE_HARDNESS && !state.hasBlockEntity()) {
						// shoved out of the way
						level.destroyBlock(pos, true, pusher);
						push.left -= BLOCK_COST;
						push.speed *= BLOCK_SLOW;
					} else {
						stopped = true;
						break;
					}
				}
			}
			if (stopped) {
				// slammed into something that won't give
				float damage = IMPACT_BASE + IMPACT_PER_SPEED * (float)push.speed;
				target.hurtServer(level, pusher != null ? level.damageSources().mobAttack(pusher) : level.damageSources().flyIntoWall(), damage);
				// crushed against it: it bursts
				com.jaymospowers.core.BladeBlood.crushed(target);
				com.jaymospowers.core.Debris.smash(level, next.getCenter(), push.speed, pusher);
				release(target);
				it.remove();
				continue;
			}
			Vec3 to = target.position().add(push.dir.scale(step));
			target.setPos(to);
			target.setDeltaMovement(push.dir.scale(step));
			target.syncVelocity = true;
			target.resetFallDistance();
			push.left -= step;
			if (target instanceof ServerPlayer player) {
				ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(push.dir.scale(step)));
			}
			// anything it passes through is battered aside
			for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(0.4),
				e -> e != target && e != pusher && e.isAlive() && !e.isSpectator() && !push.hit.contains(e.getId()))) {
				push.hit.add(other.getId());
				other.hurtServer(level, pusher != null ? level.damageSources().mobAttack(pusher) : level.damageSources().flyIntoWall(), PASS_DAMAGE);
				Vec3 side = other.position().subtract(target.position()).horizontal();
				side = side.lengthSqr() < 1.0E-4 ? push.dir : side.normalize();
				other.setDeltaMovement(side.scale(0.8).add(push.dir.scale(0.6)).add(0.0, 0.3, 0.0));
				other.syncVelocity = true;
			}
			level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
		}
	}

	/** It's done flying: its weight back, and its own collisions. */
	private static void release(Entity entity) {
		entity.noPhysics = false;
		entity.setNoGravity(false);
	}

	static void clear() {
		PUSHES.clear();
	}
}
