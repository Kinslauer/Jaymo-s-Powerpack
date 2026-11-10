package com.jaymospowers.ground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.material.Fluids;

/**
 * A few piles of loose sticks around {@code origin}, on natural soil only (so not on stone, sand, snow or in water).
 * A pile always lies where there are leaves within 12 blocks overhead, but in the open only 1 time in 10.
 * That works out to up to 3 piles per chunk in woods and about one pile every three chunks on open grassland.
 * Pile sizes: 45% one stick, 30% two, 17% three, 8% four.
 * Placement: worldgen/placed_feature/loose_sticks.json.
 */
public record LooseSticksFeature() implements Feature {
	public static final MapCodec<LooseSticksFeature> CODEC = MapCodec.unit(LooseSticksFeature::new);

	/** Piles per placement (1 to this), how far they spread from it, and how far each looks up and down for ground. */
	public static final int MAX_PILES = 3;
	public static final int SPREAD = 3;
	public static final int SEEK = 3;
	/** How far up to check for leaves overhead (canopy). */
	public static final int CANOPY_REACH = 12;
	/** Pile sizes, out of 100. */
	private static final int[] SIZES = {45, 30, 17, 8};
	/** In the open (no canopy), a pile places only this often (in 100). */
	public static final int OPEN_CHANCE = 10;

	@Override
	public MapCodec<LooseSticksFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		boolean placed = false;
		int piles = 1 + random.nextInt(MAX_PILES);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = this.ground(level, i == 0 ? origin
				: origin.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD));
			if (spot == null) {
				continue;
			}
			// Must have a canopy (leaves within CANOPY_REACH blocks overhead) unless we hit the open chance
			boolean canopy = this.hasCanopy(level, spot);
			if (!canopy && random.nextInt(100) >= OPEN_CHANCE) {
				continue;
			}
			BlockState state = GroundSticks.LOOSE_STICKS.defaultBlockState().setValue(LooseSticksBlock.STICKS, size(random, SIZES));
			boolean water = level.getFluidState(spot).is(Fluids.WATER);
			level.setBlock(spot, state.setValue(LooseSticksBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS);
			placed = true;
		}
		return placed;
	}

	/** The free spot (air, or short plants) on natural soil nearest {@code near}'s height, if there's one. */
	private BlockPos ground(WorldGenLevel level, BlockPos near) {
		for (int dy = SEEK; dy >= -SEEK; dy--) {
			BlockPos pos = near.above(dy);
			BlockState here = level.getBlockState(pos);
			// short grass, ferns, leaf litter and dry grass give way (they're grown by the time this runs); tall plants don't, they're two blocks
			boolean free = here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN)
				|| here.is(Blocks.SHORT_DRY_GRASS) || here.is(Blocks.LEAF_LITTER);
			if (free && naturalSoil(level.getBlockState(pos.below())) && Block.canSupportCenter(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}

	/** True if the block is natural soil: grass, podzol, dirt, coarse/rooted dirt, mud, moss, mycelium (vanilla #substrate_overworld tag). */
	private static boolean naturalSoil(BlockState ground) {
		return ground.is(BlockTags.SUBSTRATE_OVERWORLD);
	}

	/** True if there are leaves within CANOPY_REACH blocks above the position. */
	private boolean hasCanopy(WorldGenLevel level, BlockPos pos) {
		for (int dy = 1; dy <= CANOPY_REACH; dy++) {
			if (level.getBlockState(pos.above(dy)).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}

	static int size(RandomSource random, int[] weights) {
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