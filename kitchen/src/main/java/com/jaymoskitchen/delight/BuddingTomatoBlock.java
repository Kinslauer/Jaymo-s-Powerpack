package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Farmer's Delight's budding tomatoes, MIT; their art, our own code.) What tomato seeds plant: a low bush that grows
 * past its last stage into the tomato vine ({@link TomatoBlock}).
 */
public class BuddingTomatoBlock extends BuddingBushBlock implements BonemealableBlock {
	/** Bone meal pushes the bush on by this many stages (past the last one, the rest grows the vine). */
	private static final int BONEMEAL_MIN = 1, BONEMEAL_MAX = 4;

	public BuddingTomatoBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (state.getValue(AGE) == 4) {
			return DelightFarm.TOMATOES.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	public boolean canGrowPastMaxAge() {
		return true;
	}

	@Override
	public void growPastMaxAge(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.setBlockAndUpdate(pos, DelightFarm.TOMATOES.defaultBlockState());
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		int maxAge = getMaxAge();
		int grown = Math.min(getAge(state) + Mth.nextInt(level.getRandom(), BONEMEAL_MIN, BONEMEAL_MAX), 7);
		if (grown <= maxAge) {
			level.setBlockAndUpdate(pos, state.setValue(AGE, grown));
		} else {
			// past the bush: the rest of the growth goes into the vine
			int remaining = grown - maxAge - 1;
			level.setBlockAndUpdate(pos, DelightFarm.TOMATOES.defaultBlockState().setValue(TomatoBlock.VINE_AGE, remaining));
		}
	}
}
