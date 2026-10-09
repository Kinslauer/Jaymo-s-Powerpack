package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A big puff of debris the colour of the block it came from: chunks flung out, block-coloured dust billowing up (the
 * mace's dust pillars) and a crumble. Every power that smashes the ground uses it, so destruction looks the same.
 */
public final class Debris {
	private Debris() {
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
