package com.jaymoskingdoms.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Where the cave moulds may take hold: the bare rock and earth of a cave's walls, floor and ceiling, out of the sky. */
final class Surfaces {
	private Surfaces() {
	}

	/** Rock or earth a mould can grow in place of. */
	static boolean rock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.TUFF)
			|| state.is(Blocks.CALCITE) || state.is(Blocks.CLAY) || state.is(Blocks.MUD);
	}

	/** Bare rock at {@code pos} that has air against it and no sky over it: the inside of a cave. */
	static boolean inCave(ServerLevel level, BlockPos pos) {
		if (!rock(level.getBlockState(pos)) || level.canSeeSky(pos.above())) {
			return false;
		}
		for (Direction d : Direction.values()) {
			if (level.getBlockState(pos.relative(d)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Rock with open air directly below it: a ceiling. */
	static boolean ceiling(ServerLevel level, BlockPos pos) {
		return rock(level.getBlockState(pos)) && level.getBlockState(pos.below()).isAir() && !level.canSeeSky(pos.above());
	}

	/** A cave surface next to {@code from}, the one nearest {@code toward} if given (else a random one), or null. */
	static @Nullable BlockPos neighbour(ServerLevel level, BlockPos from, @Nullable BlockPos toward, RandomSource random, boolean ceilings) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Direction d : Direction.values()) {
			BlockPos p = from.relative(d);
			if (ceilings ? !ceiling(level, p) : !inCave(level, p)) {
				continue;
			}
			double score = toward == null ? random.nextDouble() : p.distSqr(toward) + random.nextDouble() * 0.5;
			if (score < bestD) {
				bestD = score;
				best = p;
			}
		}
		return best;
	}

	/** Whether {@code state} is a flame or a heat that a cold mould can drink. */
	static boolean heat(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && state.getValueOrElse(net.minecraft.world.level.block.CampfireBlock.LIT, false)
			|| state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.LAVA)
			|| state.is(Blocks.FURNACE) && state.getValueOrElse(net.minecraft.world.level.block.FurnaceBlock.LIT, false)
			|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
	}
}
