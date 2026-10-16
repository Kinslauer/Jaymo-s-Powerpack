package com.jaymospowers.power.web;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The PS5-style tendril slam, server side, shared by the symbiote's counters ({@link WebCounter}) and the mobs it rides
 * ({@link WebBehemoth}): a bundle of tendrils wraps something, hauls it through a big arc (a curve through a high point),
 * and slams it into the ground in a burst of black mass and dust. The bundle itself is drawn by the clients
 * ({@code WebTendrilBundle}) from the synced counter or slam.
 */
public final class WebHaul {
	/** Steering a hauled thing: the share of the gap to its spot closed a tick, and its top speed (blocks/tick). */
	static final double STEER = 0.85, MAX_SPEED = 1.7;
	/** Only things no wider and taller than this (blocks) are hauled; bigger ones are slammed where they stand. */
	static final double MAX_WIDTH = 1.6, MAX_HEIGHT = 2.6;
	/** The burst at a slam: puffs of black mass, of the ground's dust, and smoke. */
	static final int BURST_MASS = 40, BURST_DUST = 30, BURST_SMOKE = 10;

	private WebHaul() {
	}

	/** Whether the tendrils can lift it (not a boss, not too big). */
	static boolean haulable(Entity entity) {
		return entity.getBbWidth() <= MAX_WIDTH && entity.getBbHeight() <= MAX_HEIGHT
			&& !(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
			&& !(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss);
	}

	/** The point {@code u} (0-1) along an arc from {@code from} to {@code to} that passes through {@code apex} halfway. */
	static Vec3 arc(Vec3 from, Vec3 apex, Vec3 to, double u) {
		Vec3 control = apex.scale(2.0).subtract(from.add(to).scale(0.5));
		double a = 1.0 - u;
		return from.scale(a * a).add(control.scale(2.0 * a * u)).add(to.scale(u * u));
	}

	/** Steers it toward {@code spot} (its feet), hauled along on the tendrils: no fall damage from the ride itself. */
	static void steer(Entity target, Vec3 spot) {
		Vec3 gap = spot.subtract(target.position()).scale(STEER);
		if (gap.length() > MAX_SPEED) {
			gap = gap.normalize().scale(MAX_SPEED);
		}
		push(target, gap);
		target.resetFallDistance();
	}

	/** Sets its motion (a player's own client has to be told). */
	static void push(Entity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(velocity));
		}
	}

	/** The ground under (or just below) {@code at}: the top of the first solid block within 6 blocks down, else {@code at}. */
	static Vec3 ground(ServerLevel level, Vec3 at, Entity except) {
		Vec3 from = at.add(0.0, 1.0, 0.0);
		BlockHitResult hit = level.clip(new ClipContext(from, from.subtract(0.0, 7.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, except));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
	}

	/**
	 * A slam lands at {@code at} (on the ground): black mass bursting out of the impact, the ground's dust kicked up in a
	 * ring, smoke, and the thud. No blood (Andy).
	 */
	static void burst(ServerLevel level, Vec3 at, float size) {
		level.sendParticles(WebSymbiote.dust(2.0F), at.x, at.y + 0.3, at.z, Math.round(BURST_MASS * size), 0.45 * size, 0.35 * size, 0.45 * size, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.3, at.z, Math.round(16 * size), 0.3 * size, 0.2, 0.3 * size, 0.18);
		BlockState under = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		if (!under.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, under);
			int count = Math.round(BURST_DUST * size);
			for (int k = 0; k < count; k++) {
				double a = k * Math.PI * 2.0 / count;
				// thrown out low along the ground, a ring of dirt and dust
				level.sendParticles(dust, at.x + Math.cos(a) * 0.4, at.y + 0.15, at.z + Math.sin(a) * 0.4, 0, Math.cos(a), 0.35, Math.sin(a), 0.35 * size);
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, under), at.x, at.y + 0.6, at.z, Math.round(10 * size),
				0.8 * size, 0.4, 0.8 * size, 0.0);
		}
		level.sendParticles(ParticleTypes.DUST_PLUME, at.x, at.y + 0.2, at.z, Math.round(8 * size), 0.5 * size, 0.1, 0.5 * size, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, Math.round(BURST_SMOKE * size), 0.5 * size, 0.2, 0.5 * size, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.1F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.45F);
	}
}
