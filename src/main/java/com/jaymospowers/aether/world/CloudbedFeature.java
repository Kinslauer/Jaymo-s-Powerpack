package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.jaymospowers.noise.FastNoiseLite;
import com.jaymospowers.noise.Noises;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The cloudbed: a sea of cold aercloud far below the islands, in the bottom 14 blocks of the world (below the line where
 * the Wind power carries a faller home, so it is something to look down on, not something to land on). The cloud's shape
 * is FastNoiseLite noise from the world seed ({@link #noise}), so it is one continuous bank across chunk borders; each chunk draws its own
 * 16 by 16 columns, once, when its decoration runs.
 */
public record CloudbedFeature() implements Feature {
	public static final MapCodec<CloudbedFeature> CODEC = MapCodec.unit(CloudbedFeature::new);

	/** The bank's floor wanders between these heights; the cloud stands up to {@link #MAX_THICKNESS} blocks above it. */
	public static final int FLOOR_MIN = -30, FLOOR_MAX = -24, MAX_THICKNESS = 5;
	/** Length of the large and the small waves of the noise, in blocks, and how much the small ones count. */
	public static final double LARGE_SCALE = 96.0, SMALL_SCALE = 24.0, SMALL_SHARE = 0.35;
	/** Noise (0 to 1) above this is cloud. */
	public static final double THRESHOLD = 0.38;

	@Override
	public MapCodec<CloudbedFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		long seed = level.getSeed();
		BlockState cloud = AetherBlocks.COLD_AERCLOUD.defaultBlockState();
		int minY = Math.max(level.getMinY(), FLOOR_MIN);
		boolean placed = false;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double n = (noise(seed, x / LARGE_SCALE, z / LARGE_SCALE, 0) + SMALL_SHARE * noise(seed, x / SMALL_SCALE, z / SMALL_SCALE, 1))
					/ (1.0 + SMALL_SHARE);
				if (n <= THRESHOLD) {
					continue;
				}
				double over = Mth.clamp((n - THRESHOLD) / (1.0 - THRESHOLD) * 2.0, 0.0, 1.0);
				int thickness = 1 + (int)Math.floor(over * (MAX_THICKNESS - 1));
				int floor = FLOOR_MIN + (int)Math.floor(noise(seed, x / 64.0, z / 64.0, 2) * (FLOOR_MAX - FLOOR_MIN + 1));
				for (int y = Math.max(minY, floor); y < floor + thickness; y++) {
					at.set(x, y, z);
					if (level.getBlockState(at).isAir()) {
						placed |= AetherFeatures.put(level, at, cloud);
					}
				}
			}
		}
		return placed;
	}

	/**
	 * Cloud noise in 0..1 at (x, z), already divided by the wave length: FastNoiseLite's OpenSimplex2, its coordinates
	 * pushed about by a domain warp so the bank's edges curl and finger out (the old value noise sat on a square grid,
	 * which showed as blocky lumps in the cloud's depth). {@link #BIAS} keeps about as much sky covered as before.
	 */
	static double noise(long seed, double x, double z, int salt) {
		FastNoiseLite noise = CLOUD.at(seed, salt);
		FastNoiseLite.Vector2 at = new FastNoiseLite.Vector2(x, z);
		noise.DomainWarp(at);
		return Mth.clamp(0.5 + BIAS + 0.5 * noise.GetNoise(at.x, at.y), 0.0, 1.0);
	}

	/** Lifts the noise a little so the cloud covers about as much as the value noise did (73% of the sky). */
	static final double BIAS = 0.03;
	/** Half a wave per unit, and a warp of 0.6 of a unit: wide enough to bend the bank's edges, not to tear it apart. */
	private static final Noises.Seeded CLOUD = new Noises.Seeded(0xC10DL, noise -> {
		noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
		noise.SetFrequency(0.5F);
		noise.SetDomainWarpType(FastNoiseLite.DomainWarpType.OpenSimplex2);
		noise.SetDomainWarpAmp(0.6F);
	});
}
