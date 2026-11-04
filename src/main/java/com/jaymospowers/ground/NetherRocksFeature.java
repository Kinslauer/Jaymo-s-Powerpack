package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
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
 * (0.20.2) Andy: "add rare pebbles and flint to the nether so those starts are possible for emberborn [...] after trying
 * that start you can only advance by stealing axes from pigmen". Now and then a few loose rocks lie on one of the Nether's
 * floors (it has many, under its roof, so the surface heightmap the overworld's rocks use won't do): netherrack rocks on
 * netherrack and nylium, blackstone on blackstone, basalt on basalt and in the soul sand valleys; and some of the piles
 * are loose flint, most of them on the Nether's gravel. With them the first-tools road (knap flint on blackstone, basalt
 * or netherrack, a shard on a stick, fibre cut from the crimson and warped roots) works for a start in the Nether.
 * Placement: worldgen/placed_feature/nether_rocks.json (a rarity filter: a chunk in so many has a cluster).
 */
public record NetherRocksFeature() implements Feature {
	public static final MapCodec<NetherRocksFeature> CODEC = MapCodec.unit(NetherRocksFeature::new);

	/** The floors looked at: above the lava sea, under the roof. */
	public static final int MIN_Y = 32, MAX_Y = 120;
	/** Piles in a cluster (1 to this), how far they lie from its first, how far each looks up and down for floor. */
	public static final int MAX_PILES = 3;
	public static final int SPREAD = 3;
	public static final int SEEK = 3;
	/** A pile is loose flint this often (in 100), on gravel this often. */
	public static final int FLINT_CHANCE = 30;
	public static final int FLINT_ON_GRAVEL = 75;
	/** Pile sizes, out of 100: rocks 1-4, flint 1-3 (as GroundRocksFeature's). */
	private static final int[] ROCK_SIZES = {45, 30, 17, 8};
	private static final int[] FLINT_SIZES = {60, 30, 10};

	@Override
	public MapCodec<NetherRocksFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		// every floor in this column, then one of them at random
		List<BlockPos> floors = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(origin.getX(), MIN_Y, origin.getZ());
		for (int y = MIN_Y; y <= MAX_Y; y++) {
			at.setY(y);
			if (floor(level, at)) {
				floors.add(at.immutable());
			}
		}
		if (floors.isEmpty()) {
			return false;
		}
		BlockPos first = floors.get(random.nextInt(floors.size()));
		boolean placed = false;
		int piles = 1 + random.nextInt(MAX_PILES);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = i == 0 ? first
				: near(level, first.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD));
			if (spot == null) {
				continue;
			}
			BlockState ground = level.getBlockState(spot.below());
			boolean flint = random.nextInt(100) < (ground.is(Blocks.GRAVEL) ? FLINT_ON_GRAVEL : FLINT_CHANCE);
			BlockState state = flint
				? GroundRocks.LOOSE_FLINT.defaultBlockState().setValue(LooseStonesBlock.FLINTS, GroundRocksFeature.size(random, FLINT_SIZES))
				: GroundRocks.ROCKS.get(rockOf(ground)).defaultBlockState().setValue(LooseStonesBlock.ROCKS, GroundRocksFeature.size(random, ROCK_SIZES));
			level.setBlock(spot, state, Block.UPDATE_CLIENTS);
			placed = true;
		}
		return placed;
	}

	/** The floor spot nearest {@code near}'s height, if there's one. */
	private static BlockPos near(WorldGenLevel level, BlockPos near) {
		for (int dy = SEEK; dy >= -SEEK; dy--) {
			BlockPos pos = near.above(dy);
			if (floor(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	/** Free (air, or roots and sprouts that give way) over natural Nether ground. */
	private static boolean floor(WorldGenLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		boolean free = here.isAir() || here.is(Blocks.CRIMSON_ROOTS) || here.is(Blocks.WARPED_ROOTS) || here.is(Blocks.NETHER_SPROUTS);
		if (!free || !level.getFluidState(pos).isEmpty()) {
			return false;
		}
		BlockState ground = level.getBlockState(pos.below());
		return natural(ground) && Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	private static boolean natural(BlockState ground) {
		return ground.is(BlockTags.BASE_STONE_NETHER) || ground.is(BlockTags.NYLIUM) || ground.is(Blocks.SOUL_SOIL) || ground.is(Blocks.SOUL_SAND)
			|| ground.is(Blocks.GRAVEL);
	}

	/** The rock the ground under it gives. */
	static GroundRocks.Variant rockOf(BlockState ground) {
		if (ground.is(Blocks.BLACKSTONE)) {
			return GroundRocks.Variant.BLACKSTONE;
		} else if (ground.is(Blocks.BASALT) || ground.is(Blocks.SOUL_SOIL) || ground.is(Blocks.SOUL_SAND)) {
			return GroundRocks.Variant.BASALT;
		}
		return GroundRocks.Variant.NETHERRACK;
	}
}
