package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
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
 * Placement: worldgen/placed_feature/pebble_piles.json.
 */
public record PebblePilesFeature() implements Feature {
	public static final MapCodec<PebblePilesFeature> CODEC = MapCodec.unit(PebblePilesFeature::new);

	/** Piles in a cluster (MIN_PILES to MAX_PILES), how far they lie from its middle, how far each looks up and down for grass. */
	public static final int MIN_PILES = 3;
	public static final int MAX_PILES = 7;
	public static final int SPREAD = 2;
	public static final int SEEK = 2;
	/** How deep under the turf a telling stone (granite, tuff...) decides the rock instead of the biome. */
	public static final int TURF_DEPTH = 4;
	/** Out of 100, how many piles in a cluster are the biome's rock; the rest are plain stone (andesite where it's stone). */
	public static final int BIOME_ROCK = 75;
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
		GroundRocks.Variant main = rockOf(level, middle);
		boolean placed = false;
		int piles = MIN_PILES + random.nextInt(MAX_PILES - MIN_PILES + 1);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = i == 0 ? middle
				: grass(level, middle.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD));
			if (spot == null) {
				continue;
			}
			GroundRocks.Variant variant = random.nextInt(100) < BIOME_ROCK ? main
				: main == GroundRocks.Variant.STONE ? GroundRocks.Variant.ANDESITE : GroundRocks.Variant.STONE;
			level.setBlock(spot, GroundRocks.ROCKS.get(variant).defaultBlockState()
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

	/** The cluster's rock: a telling stone just under the turf, else the biome's. */
	static GroundRocks.Variant rockOf(WorldGenLevel level, BlockPos spot) {
		for (int dy = 2; dy <= TURF_DEPTH + 1; dy++) {
			GroundRocks.Variant found = GroundRocksFeature.stoneOf(level.getBlockState(spot.below(dy)));
			if (found != null && found != GroundRocks.Variant.SANDSTONE) {
				return found;
			}
		}
		Holder<Biome> biome = level.getBiome(spot);
		if (biome.is(BiomeTags.IS_BADLANDS)) {
			return GroundRocks.Variant.RED_SANDSTONE;
		} else if (biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.DESERT)) {
			return GroundRocks.Variant.SANDSTONE;
		} else if (biome.is(BiomeTags.IS_SAVANNA)) {
			return GroundRocks.Variant.GRANITE;
		} else if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.DARK_FOREST)
			|| biome.is(Biomes.PALE_GARDEN) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
			return GroundRocks.Variant.MOSSY;
		} else if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST) || biome.is(Biomes.CHERRY_GROVE)
			|| biome.is(Biomes.MEADOW) || biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.SUNFLOWER_PLAINS)) {
			return GroundRocks.Variant.DIORITE;
		} else if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)
			|| biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_FOREST)
			|| biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
			return GroundRocks.Variant.ANDESITE;
		}
		return GroundRocks.Variant.STONE;
	}
}
