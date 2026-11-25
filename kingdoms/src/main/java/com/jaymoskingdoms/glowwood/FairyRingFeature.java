package com.jaymoskingdoms.glowwood;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.ArrayList;
import java.util.List;

/**
 * A fairy ring: one species of mushroom in a near-perfect circle on open, fairly level ground, with the odd gap.
 * Skips slopes (more than two blocks of height difference around the ring) so rings always read as circles.
 * Ported from Spore & Stem (Apache-2.0).
 */
public class FairyRingFeature implements Feature {
	/** The most the ground may differ round the ring (blocks), the chance each spot is left bare, and how thick the ring is. */
	private static final int MAX_SLOPE = 2;
	private static final float GAP_CHANCE = 0.12F;
	private static final double RING_WIDTH = 0.5;

	// 26.3: a feature is its own configured instance.
	public static final MapCodec<FairyRingFeature> CODEC = FairyRingConfiguration.CODEC.xmap(FairyRingFeature::new, FairyRingFeature::config);

	private final FairyRingConfiguration config;

	public FairyRingFeature(FairyRingConfiguration config) {
		this.config = config;
	}

	public FairyRingConfiguration config() {
		return config;
	}

	@Override
	public MapCodec<FairyRingFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int radius = config.radius().sample(random);
		BlockState fungus = config.fungus().getState(level, random, origin);
		List<BlockPos> ring = new ArrayList<>();
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (Math.abs(distance - radius) > RING_WIDTH) {
					continue;
				}
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				ring.add(new BlockPos(x, y, z));
			}
		}
		if (ring.isEmpty() || maxY - minY > MAX_SLOPE) {
			return false;
		}
		int placed = 0;
		for (BlockPos pos : ring) {
			if (random.nextFloat() < GAP_CHANCE) {
				continue;
			}
			BlockState here = level.getBlockState(pos);
			boolean free = here.isAir() || (here.is(BlockTags.REPLACEABLE_BY_TREES) && here.getFluidState().isEmpty()
					&& !here.is(BlockTags.LEAVES));
			if (free && fungus.canSurvive(level, pos)) {
				level.setBlock(pos, fungus, 2);
				placed++;
			}
		}
		return placed > 0;
	}
}
