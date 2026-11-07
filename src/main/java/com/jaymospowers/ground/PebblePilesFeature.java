package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * (0.17.175) Andy: "you should be able to rarely find the rocks on grass as well, mostly in pebble piles. changing
 * dependent on biome". Now and then a pebble pile lies on a grass meadow: a tight cluster of small rock piles, most of
 * them the biome's rock (granite on savannas, diorite in birch woods, cherry groves and meadows, andesite in taigas and
 * the hills, mossy in jungles, swamps and dark woods, red sandstone in the badlands, sandstone on beaches), the rest
 * plain stone. A telling stone just under the turf still wins, as it does for the other rocks.
 * (0.19.22) Andy: "except for grass, then it always does cobblestone rock pebbles": pebbles on grass are always plain
 * stone (the cobblestone rock) now, whatever the biome or the stone under the turf.
 * Placement: worldgen/placed_feature/pebble_piles.json.
 */
public record PebblePilesFeature() implements Feature {
	public static final MapCodec<PebblePilesFeature> CODEC = MapCodec.unit(PebblePilesFeature::new);

	/** Piles in a cluster (MIN_PILES to MAX_PILES), how far they lie from its middle, how far each looks up and down for grass. */
	public static final int MIN_PILES = 3;
	public static final int MAX_PILES = 7;
	public static final int SPREAD = 2;
	public static final int SEEK = 2;
	/** Pile sizes, out of 100: 1-4 rocks, mostly small pebbles. */
	private static final int[] SIZES = {50, 32, 14, 4};

	@Override
	public MapCodec<PebblePilesFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		BlockPos middle = grass(level, origin);
		if (middle == null) {
			return false;
		}
		boolean placed = false;
		int piles = MIN_PILES + random.nextInt(MAX_PILES - MIN_PILES + 1);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = i == 0 ? middle
				: grass(level, middle.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD));
			if (spot == null) {
				continue;
			}
			level.setBlock(spot, GroundRocks.ROCKS.get(GroundRocks.Variant.STONE).defaultBlockState()
				.setValue(LooseStonesBlock.ROCKS, GroundRocksFeature.size(random, SIZES)), Block.UPDATE_CLIENTS);
			placed = true;
		}
		return placed;
	}

	/** The free spot on a grass block nearest {@code near}'s height, if there's one (short grass and ferns give way). */
	private static BlockPos grass(WorldGenLevel level, BlockPos near) {
		for (int dy = SEEK; dy >= -SEEK; dy--) {
			BlockPos pos = near.above(dy);
			BlockState here = level.getBlockState(pos);
			boolean free = here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN) || here.is(Blocks.SHORT_DRY_GRASS);
			if (free && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && Block.canSupportCenter(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}
}
