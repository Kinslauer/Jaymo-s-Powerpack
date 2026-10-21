package com.jaymospowers.aether.world;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * One pointed holystone spike on the nearest floor: from the origin, the air block that has solid ground under it,
 * looking down (if it is in the open) or up (if it is inside rock) a few blocks. Used on the surface and in caves.
 */
public record PointedHolystoneFeature() implements Feature {
	public static final MapCodec<PointedHolystoneFeature> CODEC = MapCodec.unit(PointedHolystoneFeature::new);

	/** How far the origin may be from the floor it settles on. */
	public static final int SEARCH = 10;

	@Override
	public MapCodec<PointedHolystoneFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		BlockPos.MutableBlockPos at = origin.mutable();
		if (level.getBlockState(at).isAir()) {
			for (int i = 0; i < SEARCH && level.getBlockState(at.below()).isAir(); i++) {
				at.move(Direction.DOWN);
			}
		} else {
			int i = 0;
			while (i++ < SEARCH && !level.getBlockState(at).isAir()) {
				at.move(Direction.UP);
			}
		}
		BlockPos floor = at.below();
		if (!level.getBlockState(at).isAir() || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return false;
		}
		BlockState spike = AetherBlocks.POINTED_HOLYSTONE.defaultBlockState();
		return AetherFeatures.put(level, at.immutable(), spike);
	}
}
