package com.jaymospowers.power.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wind blows fire out: every Wind ability snuffs the fire blocks and burning things it sweeps through (the caster too).
 * A Stormbound wind's own lightning is the exception: it's cast after the wind, so what it lights keeps burning.
 */
final class WindDouse {
	/** The widest ball one call snuffs (blocks), so a huge funnel can't scan half a chunk at once. */
	static final double MAX_RADIUS = 8.0;

	private WindDouse() {
	}

	/** Snuffs fire in a ball of {@code radius} around {@code centre}: fire blocks, burning things and {@code caster}. */
	static void douse(ServerLevel level, Vec3 centre, double radius, @Nullable Entity caster) {
		douse(level, centre, radius, caster, true);
	}

	/** As {@link #douse(ServerLevel, Vec3, double, Entity)}; {@code entities} false leaves burning things (but the caster) alight. */
	static void douse(ServerLevel level, Vec3 centre, double radius, @Nullable Entity caster, boolean entities) {
		double r = Math.min(radius, MAX_RADIUS);
		double r2 = r * r;
		AABB box = new AABB(centre, centre).inflate(r);
		BlockPos sound = null;
		for (BlockPos pos : BlockPos.betweenClosed(box)) {
			if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock && Vec3.atCenterOf(pos).distanceToSqr(centre) <= r2) {
				level.removeBlock(pos, false);
				level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.02);
				if (sound == null) {
					sound = pos.immutable();
				}
			}
		}
		if (sound != null) {
			level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, sound, 0);
		}
		if (caster != null && caster.isOnFire()) {
			snuff(level, caster);
		}
		if (!entities) {
			return;
		}
		for (Entity entity : level.getEntities(caster, box, e -> e.isOnFire() && !e.fireImmune() && e.getBoundingBox().distanceToSqr(centre) <= r2)) {
			snuff(level, entity);
		}
	}

	/** Snuffs fire all along a line from {@code from} to {@code to}, {@code radius} either side. */
	static void line(ServerLevel level, Vec3 from, Vec3 to, double radius, @Nullable Entity caster) {
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int)Math.ceil(length / Math.max(0.5, radius)));
		for (int i = 0; i <= steps; i++) {
			douse(level, from.lerp(to, i / (double)steps), radius, caster);
		}
	}

	private static void snuff(ServerLevel level, Entity entity) {
		entity.clearFire();
		level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 6,
			entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.02);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.5F, 1.4F);
	}
}
