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
 * The cloudbed: a sea of cold aercloud far below the islands, in the bottom 14 blocks of the world (below the line where
 * the Wind power carries a faller home, so it is something to look down on, not something to land on). The cloud's shape
 * is smooth value noise from the world seed, so it is one continuous bank across chunk borders; each chunk draws its own
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

	/** Smooth value noise in 0..1: a hashed value at each lattice corner, blended with a smoothstep. */
	private static double noise(long seed, double x, double z, int salt) {
		int ix = Mth.floor(x);
		int iz = Mth.floor(z);
		double fx = smooth(x - ix);
		double fz = smooth(z - iz);
		double a = corner(seed, ix, iz, salt);
		double b = corner(seed, ix + 1, iz, salt);
		double c = corner(seed, ix, iz + 1, salt);
		double d = corner(seed, ix + 1, iz + 1, salt);
		return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double corner(long seed, int x, int z, int salt) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
