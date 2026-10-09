package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A big puff of debris the colour of the block it came from: chunks flung out, block-coloured dust billowing up (the
 * mace's dust pillars) and a crumble. Every power that smashes the ground uses it, so destruction looks the same.
 */
public final class Debris {
	/** The smash's reach (blocks): a base, plus this per block/tick of speed over {@code SMASH_FROM}, up to a cap. */
	static final double SMASH_FROM = 1.1, SMASH_RADIUS_BASE = 1.2, SMASH_RADIUS_PER_SPEED = 1.6, SMASH_RADIUS_MAX = 3.5;
	/** How hard a block the smash can break per block/tick of speed (dirt 0.5, stone 1.5, deepslate 3). */
	static final float SMASH_POWER_PER_SPEED = 1.6F;
	/** Chance a broken block drops itself (like an explosion's), and the debris puff from each. */
	static final float SMASH_DROP_CHANCE = 0.3F, SMASH_DEBRIS = 1.6F;

	private Debris() {
	}

	/**
	 * A hard impact at {@code at}, at {@code speed} blocks/tick: like a small explosion, the blocks around it break,
	 * the softer ones more readily and further out. The faster, the wider and the harder the blocks it breaks.
	 */
	public static void smash(ServerLevel level, Vec3 at, double speed, @Nullable Entity cause) {
		double radius = Math.min(SMASH_RADIUS_MAX, SMASH_RADIUS_BASE + Math.max(0.0, speed - SMASH_FROM) * SMASH_RADIUS_PER_SPEED);
		float power = (float)(speed * SMASH_POWER_PER_SPEED);
		var random = level.getRandom();
		int r = Mth.ceil(radius);
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			double distance = Math.sqrt(pos.distToCenterSqr(at));
			if (distance > radius) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F) {
				continue;
			}
			// the blast weakens outward; a soft block goes where a hard one holds
			float strength = power * (float)(1.0 - distance / (radius + 0.5));
			float chance = Mth.clamp(1.0F - hardness / Math.max(0.01F, strength), 0.0F, 1.0F);
			if (random.nextFloat() < chance) {
				level.destroyBlock(pos, random.nextFloat() < SMASH_DROP_CHANCE, cause);
				puff(level, pos, state, SMASH_DEBRIS);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (float)Math.min(1.0, 0.3 + speed * 0.25), 1.3F);
	}


	/** Debris from one broken (or struck) block at {@code pos}; {@code size} 1 is a normal puff, more is bigger. */
	public static void puff(ServerLevel level, BlockPos pos, BlockState state, float size) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
		double spread = 0.35 * size;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, (int)(16 * size), spread, spread, spread, 0.25);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), x, y, z, (int)(10 * size), spread * 1.4, spread * 0.6,
			spread * 1.4, 0.15 * size);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, state), x, y, z, (int)(6 * size), spread, spread * 0.5, spread, 0.0);
	}

	/** A ring of ground around {@code center} throwing up its debris (each block its own colour). */
	public static void ring(ServerLevel level, BlockPos center, int radius, float size) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + 1) {
					continue;
				}
				// the surface here: the top solid block within a step of the centre's level
				for (int dy = 1; dy >= -1; dy--) {
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || !level.getBlockState(pos.above()).isAir()) {
						continue;
					}
					double distance = Math.sqrt(dx * dx + dz * dz);
					puff(level, pos.above(), state, size * (float)(1.0 - distance / (radius + 1.5)));
					break;
				}
			}
		}
	}
}
