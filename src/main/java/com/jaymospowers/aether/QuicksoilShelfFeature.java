package com.jaymospowers.aether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Quicksoil shelves: golden quicksoil plastered along the undersides of islands in the low part of the sky. Each patch
 * is a flat disc about {@link #RADIUS} blocks across laid on the first underside found in a random column of the chunk
 * (the first air with island right above it, looking up from {@code min_y} (default the bottom of the world) to {@code max_y} (default {@link #MAX_Y})); only air with a solid
 * block over it gets quicksoil, so the discs hug the islands and overlapping ones join into a shelf.
 */
public record QuicksoilShelfFeature(int minY, int maxY) implements Feature {
	/** Patches tried in a chunk that has a shelf, how far up they look, and the disc's radius. */
	public static final int PATCHES = 8, MAX_Y = 48;
	public static final double RADIUS = 3.46;

	public static final MapCodec<QuicksoilShelfFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("min_y", Integer.MIN_VALUE).forGetter(QuicksoilShelfFeature::minY),
			Codec.INT.optionalFieldOf("max_y", MAX_Y).forGetter(QuicksoilShelfFeature::maxY))
		.apply(i, QuicksoilShelfFeature::new));

	@Override
	public MapCodec<QuicksoilShelfFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		int chunkX = origin.getX() & ~15;
		int chunkZ = origin.getZ() & ~15;
		BlockState quicksoil = AetherBlocks.QUICKSOIL.defaultBlockState();
		boolean placed = false;
		for (int i = 0; i < PATCHES; i++) {
			int x = chunkX + random.nextInt(16);
			int z = chunkZ + random.nextInt(16);
			int y = underside(level, x, z, this.minY, this.maxY);
			if (y < 0) {
				continue;
			}
			int reach = (int)Math.ceil(RADIUS);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz > RADIUS * RADIUS) {
						continue;
					}
					BlockPos at = new BlockPos(x + dx, y, z + dz);
					if (AetherFeatures.isAir(level, at) && isIsland(level.getBlockState(at.above()))) {
						placed |= AetherFeatures.put(level, at, quicksoil);
					}
				}
			}
		}
		return placed;
	}

	/** The height of the first underside in a column (air with island above it), or -1. */
	private static int underside(WorldGenLevel level, int x, int z, int minY, int maxY) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY() + 1, minY); y < maxY; y++) {
			at.set(x, y, z);
			if (AetherFeatures.isAir(level, at) && isIsland(level.getBlockState(at.above()))) {
				return y;
			}
		}
		return -1;
	}

	private static boolean isIsland(BlockState state) {
		return state.is(AetherBlocks.HOLYSTONE) || state.is(AetherBlocks.AETHER_DIRT);
	}
}
