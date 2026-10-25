package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;

/**
 * A few piles of loose rocks (or flint) around {@code origin}, on natural ground only (so not on paths, floors or roofs).
 * A rock is the stone of the ground it lies on: the first telling stone in the column under it (granite, diorite,
 * andesite, tuff, deepslate, sandstone...), else the biome's (mossy in jungles and swamps, sandstone in deserts and on
 * beaches, red sandstone in the badlands), else plain stone. Placement: worldgen/placed_feature/loose_*.json.
 */
public record GroundRocksFeature(boolean flint) implements Feature {
	public static final MapCodec<GroundRocksFeature> ROCKS_CODEC = MapCodec.unit(() -> new GroundRocksFeature(false));
	public static final MapCodec<GroundRocksFeature> FLINT_CODEC = MapCodec.unit(() -> new GroundRocksFeature(true));

	/** Piles per placement (1 to this), how far they spread from it, and how far each looks up and down for ground. */
	public static final int MAX_PILES = 3;
	public static final int SPREAD = 3;
	public static final int SEEK = 3;
	/** How far down a rock looks for the stone it's made of. */
	public static final int STRATA_DEPTH = 16;
	/** Pile sizes, out of 100: rocks 1-4, flint 1-3. */
	private static final int[] ROCK_SIZES = {45, 30, 17, 8};
	private static final int[] FLINT_SIZES = {60, 30, 10};
	/** Flint lies on gravel always; by water or on beaches this often (in 100); elsewhere this often. */
	public static final int FLINT_BY_WATER = 50;
	public static final int FLINT_ELSEWHERE = 15;
	/** In jungles, swamps and lush caves, plain stone rocks are mossy this often (in 100). */
	public static final int MOSSY_CHANCE = 60;

	@Override
	public MapCodec<GroundRocksFeature> codec() {
		return this.flint ? FLINT_CODEC : ROCKS_CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		boolean placed = false;
		int piles = this.flint ? 1 : 1 + random.nextInt(MAX_PILES);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = this.ground(level, i == 0 ? origin
				: origin.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD));
			if (spot == null) {
				continue;
			}
			BlockState state;
			if (this.flint) {
				if (!this.flintLies(level, spot, random)) {
					continue;
				}
				state = GroundRocks.LOOSE_FLINT.defaultBlockState().setValue(LooseStonesBlock.FLINTS, size(random, FLINT_SIZES));
			} else {
				state = GroundRocks.ROCKS.get(variant(level, spot, random)).defaultBlockState().setValue(LooseStonesBlock.ROCKS, size(random, ROCK_SIZES));
			}
			boolean water = level.getFluidState(spot).is(Fluids.WATER);
			level.setBlock(spot, state.setValue(LooseStonesBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS);
			placed = true;
		}
		return placed;
	}

	/** The free spot (air, or still water) on natural ground nearest {@code near}'s height, if there's one. */
	private BlockPos ground(WorldGenLevel level, BlockPos near) {
		for (int dy = SEEK; dy >= -SEEK; dy--) {
			BlockPos pos = near.above(dy);
			BlockState here = level.getBlockState(pos);
			// short grass gives way (it's grown by the time this runs); tall plants don't, they're two blocks
			boolean free = here.isAir() || here.is(Blocks.WATER) && level.getFluidState(pos).isSource()
				|| here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN) || here.is(Blocks.SHORT_DRY_GRASS);
			if (free && natural(level.getBlockState(pos.below())) && Block.canSupportCenter(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean natural(BlockState ground) {
		return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD)
			|| ground.is(BlockTags.TERRACOTTA) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.SANDSTONE) || ground.is(Blocks.RED_SANDSTONE)
			|| ground.is(Blocks.CALCITE) || ground.is(Blocks.CLAY);
	}

	private boolean flintLies(WorldGenLevel level, BlockPos spot, RandomSource random) {
		if (level.getBlockState(spot.below()).is(Blocks.GRAVEL)) {
			return true;
		}
		Holder<Biome> biome = level.getBiome(spot);
		boolean wet = level.getFluidState(spot).is(Fluids.WATER) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH);
		return random.nextInt(100) < (wet ? FLINT_BY_WATER : FLINT_ELSEWHERE);
	}

	static GroundRocks.Variant variant(WorldGenLevel level, BlockPos spot, RandomSource random) {
		for (int dy = 1; dy <= STRATA_DEPTH; dy++) {
			GroundRocks.Variant found = stoneOf(level.getBlockState(spot.below(dy)));
			if (found != null) {
				return found;
			}
		}
		if (spot.getY() < 0) {
			return GroundRocks.Variant.DEEPSLATE;
		}
		Holder<Biome> biome = level.getBiome(spot);
		if (biome.is(BiomeTags.IS_BADLANDS)) {
			return GroundRocks.Variant.RED_SANDSTONE;
		}
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BEACH)) {
			return GroundRocks.Variant.SANDSTONE;
		}
		boolean lush = biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.LUSH_CAVES)
			|| biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
		return lush && random.nextInt(100) < MOSSY_CHANCE ? GroundRocks.Variant.MOSSY : GroundRocks.Variant.STONE;
	}

	/** The rock a telling block makes, or null for plain stone, dirt and the rest (keep looking). */
	private static GroundRocks.Variant stoneOf(BlockState state) {
		if (state.is(Blocks.GRANITE)) {
			return GroundRocks.Variant.GRANITE;
		} else if (state.is(Blocks.DIORITE)) {
			return GroundRocks.Variant.DIORITE;
		} else if (state.is(Blocks.ANDESITE)) {
			return GroundRocks.Variant.ANDESITE;
		} else if (state.is(Blocks.TUFF)) {
			return GroundRocks.Variant.TUFF;
		} else if (state.is(Blocks.DEEPSLATE)) {
			return GroundRocks.Variant.DEEPSLATE;
		} else if (state.is(Blocks.SANDSTONE) || state.is(Blocks.SAND)) {
			return GroundRocks.Variant.SANDSTONE;
		} else if (state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.RED_SAND) || state.is(BlockTags.TERRACOTTA)) {
			return GroundRocks.Variant.RED_SANDSTONE;
		} else if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSSY_COBBLESTONE)) {
			return GroundRocks.Variant.MOSSY;
		}
		return null;
	}

	private static int size(RandomSource random, int[] weights) {
		int roll = random.nextInt(100);
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return i + 1;
			}
		}
		return 1;
	}
}
