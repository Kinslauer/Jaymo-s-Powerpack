package com.jaymospowers.power.ember;

import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.smoke.SmokeField;
import com.jaymospowers.power.smoke.SmokePower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) Kindle's fireball ({@link EmberKindle}): let go at full heat and the gathered heat is thrown along the look.
 * It flies straight (no drop) until it meets a block or a creature, then bursts: everything within {@link #RADIUS} is
 * set alight ({@link Flames}) and takes fire damage, but no block is broken or set on fire (it lights a campfire, a
 * candle or TNT it hits and melts snow and ice, as the flame does). In water it fizzles out. With the Smoke power,
 * flying into the thrower's own thick smoke (or bursting by it) sets off a flashover ({@link EmberFlashover}).
 * Cooldown {@link #COOLDOWN}. Not an entity: the server moves it and draws it with particles, so nothing is saved.
 */
public final class EmberFireball {
	// ---- tuning ----
	/** Speed (blocks a tick) and how long it flies before it bursts on its own (ticks). */
	static final double SPEED = 1.1;
	static final int LIFE = 40;
	/** The burst's reach (blocks), its damage at the middle (half at the edge) and how long it sets things burning (seconds). */
	static final double RADIUS = 2.5;
	static final float DAMAGE = 6.0F;
	static final int BURN_SECONDS = 5;
	/** Ticks between fireballs. */
	static final int COOLDOWN = 100;

	private static final class Ball {
		final ServerLevel level;
		final ServerPlayer owner;
		Vec3 pos;
		final Vec3 velocity;
		int age;

		Ball(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private static final List<Ball> BALLS = new ArrayList<>();
	/** When each player may throw again (game time). */
	private static final Map<UUID, Long> READY = new HashMap<>();

	private EmberFireball() {
	}

	static void clear() {
		BALLS.clear();
		READY.clear();
	}

	/** Throws a fireball from {@code from} along {@code player}'s look, unless it's cooling down. True if it went. */
	static boolean throwFrom(ServerPlayer player, Vec3 from) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = READY.get(player.getUUID());
		if (ready != null && now < ready && ready - now <= COOLDOWN) {
			return false;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		BALLS.add(new Ball(level, player, from, player.getViewVector(1.0F).scale(SPEED)));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (BALLS.isEmpty()) {
			return;
		}
		for (Iterator<Ball> it = BALLS.iterator(); it.hasNext();) {
			Ball ball = it.next();
			if (step(ball)) {
				it.remove();
			}
		}
	}

	/** One tick of flight; true once it's gone. */
	private static boolean step(Ball ball) {
		ServerLevel level = ball.level;
		ball.age++;
		Vec3 from = ball.pos;
		Vec3 to = from.add(ball.velocity);
		if (!level.isLoaded(BlockPos.containing(to)) || ball.owner.level() != level) {
			return true;
		}
		if (level.getFluidState(BlockPos.containing(to)).is(FluidTags.WATER)) {
			// into water: it fizzles out
			level.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.2F);
			return true;
		}
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ball.owner));
		Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(ball.owner, from, end, new AABB(from, end).inflate(0.6),
			e -> e != ball.owner && !e.isSpectator() && e.isAlive() && e.isPickable(), from.distanceToSqr(end) + 1.0);
		if (hit != null) {
			burst(ball, hit.getLocation(), null);
			return true;
		}
		if (wall instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
			burst(ball, block.getLocation().subtract(ball.velocity.normalize().scale(0.3)), block.getBlockPos());
			return true;
		}
		ball.pos = to;
		// a white-hot ball with a fiery tail
		level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 4, 0.15, 0.15, 0.15, 0.01);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 1, 0.05, 0.05, 0.05, 0.0);
		// through the thrower's own thick smoke: the cloud goes up
		if (Powers.has(ball.owner, SmokePower.INSTANCE)) {
			BlockPos cell = BlockPos.containing(to);
			if (SmokeField.ownedDensity(level, cell.asLong(), ball.owner.getId()) >= EmberKindle.FLASH_LEAST) {
				burst(ball, to, null);
				return true;
			}
		}
		if (ball.age >= LIFE) {
			burst(ball, to, null);
			return true;
		}
		return false;
	}

	/** It bursts at {@code at} (on the block {@code block}, if it hit one): fire and burning round, no block damage. */
	private static void burst(Ball ball, Vec3 at, BlockPos block) {
		ServerLevel level = ball.level;
		ServerPlayer owner = ball.owner;
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, RADIUS * 0.4, RADIUS * 0.4, RADIUS * 0.4, 0.06);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, RADIUS * 0.3, RADIUS * 0.3, RADIUS * 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, RADIUS * 0.3, RADIUS * 0.3, RADIUS * 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F);
		Flames.area(level, at, RADIUS, BURN_SECONDS, owner, e -> true);
		// (0.14.0) freezing fog in the burst thaws at once
		SmokeField.thaw(level, at, RADIUS + 0.5);
		double r2 = RADIUS * RADIUS;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS),
			e -> e != owner && e.isAlive() && !e.isSpectator() && e.getBoundingBox().distanceToSqr(at) <= r2)) {
			double share = 1.0 - 0.5 * Math.sqrt(living.getBoundingBox().distanceToSqr(at)) / RADIUS;
			living.hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, owner), EmberKindle.seasonal(owner, (float)(DAMAGE * share)));
		}
		if (block != null) {
			EmberKindle.touch(level, block, owner);
		}
		if (Powers.has(owner, SmokePower.INSTANCE)) {
			// any of the thrower's own thick smoke in the burst goes up
			int r = (int)Math.ceil(RADIUS);
			BlockPos center = BlockPos.containing(at);
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
				if (SmokeField.ownedDensity(level, pos.asLong(), owner.getId()) >= EmberKindle.FLASH_LEAST) {
					EmberFlashover.ignite(level, owner, pos.immutable());
					break;
				}
			}
		}
	}
}
