package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A pile of loose sticks (GroundSticks) on soil: grass, podzol, dirt, coarse or rooted dirt, mud, moss and mycelium
 * (the #substrate_overworld tag), never on stone, sand, gravel, snow or in water. Under trees (leaves overhead) a pile
 * always lies; in the open only {@link #OPEN_CHANCE} in 100 do. Short grass, ferns and leaf litter give way to it.
 * Placement: worldgen/placed_feature/loose_sticks.json (tries per chunk).
 */
public record LooseSticksFeature() implements Feature {
	public static final MapCodec<LooseSticksFeature> CODEC = MapCodec.unit(LooseSticksFeature::new);

	/** How far up and down a pile looks for soil. */
	public static final int SEEK = 3;
	/** How high over the spot leaves count as a canopy. */
	public static final int CANOPY_REACH = 12;
	/** A pile with no canopy over it lies this often (in 100). */
	public static final int OPEN_CHANCE = 10;
	/** Pile sizes, out of 100: 1 to LooseSticksBlock.MAX_STICKS sticks, mostly one or two. */
	private static final int[] SIZES = {45, 30, 17, 8};

	@Override
	public MapCodec<LooseSticksFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		BlockPos spot = soil(level, origin);
		if (spot == null || !canopied(level, spot) && random.nextInt(100) >= OPEN_CHANCE) {
			return false;
		}
		level.setBlock(spot, GroundSticks.LOOSE_STICKS.defaultBlockState()
			.setValue(LooseSticksBlock.STICKS, GroundRocksFeature.size(random, SIZES)), Block.UPDATE_CLIENTS);
		return true;
	}

	/** The free spot on soil nearest {@code near}'s height, if there's one. */
	private static BlockPos soil(WorldGenLevel level, BlockPos near) {
		for (int dy = SEEK; dy >= -SEEK; dy--) {
			BlockPos pos = near.above(dy);
			BlockState here = level.getBlockState(pos);
			boolean free = here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN) || here.is(Blocks.SHORT_DRY_GRASS)
				|| here.is(Blocks.LEAF_LITTER);
			BlockState ground = level.getBlockState(pos.below());
			if (free && ground.is(BlockTags.SUBSTRATE_OVERWORLD) && Block.canSupportCenter(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean canopied(WorldGenLevel level, BlockPos spot) {
		for (int dy = 1; dy <= CANOPY_REACH; dy++) {
			if (level.getBlockState(spot.above(dy)).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}
}
