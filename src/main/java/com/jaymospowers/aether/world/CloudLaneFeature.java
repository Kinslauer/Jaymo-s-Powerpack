package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The cloud lanes: thin seas of cloudbank hanging in the open air between the isles, high in the sky, so a pod climbing out of
 * the Aether goes up through cloud and the gaps between isles are not empty. Cloudbank is see-through, and a pod passes through
 * it. Only open air is written, so no isle is touched. The shape is the cloudbed's FastNoiseLite noise from the world seed (one lane across chunk
 * borders); each chunk draws its own columns once, when its decoration runs, so a world already generated keeps its sky (the
 * lanes appear in new chunks only).
 */
public record CloudLaneFeature() implements Feature {
	public static final MapCodec<CloudLaneFeature> CODEC = MapCodec.unit(CloudLaneFeature::new);

	/** The lane's deck wanders between these heights and is {@link #MAX_THICKNESS} blocks thick at most. */
	public static final int DECK_MIN = 120, DECK_MAX = 190, MAX_THICKNESS = 3;
	/** Length of the large and the small waves of the noise (blocks), and how much the small ones count. */
	public static final double LARGE_SCALE = 140.0, SMALL_SCALE = 36.0, SMALL_SHARE = 0.3;
	/** Noise (0 to 1) above this is lane: most of the sky stays clear (about a third is lane, as with the old value noise). */
	public static final double THRESHOLD = 0.66;

	@Override
	public MapCodec<CloudLaneFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		long seed = level.getSeed() ^ 0x5DEECE66DL;
		BlockState cloud = AetherBlocks.CLOUDBANK.defaultBlockState();
		boolean placed = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double n = (CloudbedFeature.noise(seed, x / LARGE_SCALE, z / LARGE_SCALE, 3) + SMALL_SHARE * CloudbedFeature.noise(seed, x / SMALL_SCALE, z / SMALL_SCALE, 4))
					/ (1.0 + SMALL_SHARE);
				if (n <= THRESHOLD) {
					continue;
				}
				double over = Mth.clamp((n - THRESHOLD) / (1.0 - THRESHOLD) * 2.0, 0.0, 1.0);
				int thickness = 1 + (int)Math.floor(over * (MAX_THICKNESS - 1));
				int deck = DECK_MIN + (int)Math.floor(CloudbedFeature.noise(seed, x / 90.0, z / 90.0, 5) * (DECK_MAX - DECK_MIN + 1));
				for (int y = deck; y < deck + thickness; y++) {
					at.set(x, y, z);
					if (AetherFeatures.isAir(level, at)) {
						placed |= AetherFeatures.put(level, at, cloud);
					}
				}
			}
		}
		return placed;
	}
}
