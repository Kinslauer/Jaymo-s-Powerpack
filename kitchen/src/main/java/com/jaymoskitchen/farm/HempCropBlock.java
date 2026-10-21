package com.jaymoskitchen.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lower half of hemp: a crop on farmland like wheat. Once it's fully grown it puts up a top half above it
 * ({@link HempTopBlock}), now and then on a random tick or straight away with bone meal; most of the fibre is up there.
 */
public class HempCropBlock extends CropBlock {
	public HempCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return Hemp.SEEDS;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		// still ticks once grown, to put up its top
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!isMaxAge(state)) {
			super.randomTick(state, level, pos, random);
		} else if (hasSufficientLight(level, pos) && random.nextFloat() < Hemp.TOP_CHANCE) {
			growTop(level, pos);
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return !isMaxAge(state) || level.isEmptyBlock(pos.above());
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		if (isMaxAge(state)) {
			growTop(level, pos);
		} else {
			super.performBonemeal(level, random, pos, state, source);
		}
	}

	private static void growTop(Level level, BlockPos pos) {
		if (level.isEmptyBlock(pos.above())) {
			level.setBlock(pos.above(), Hemp.TOP.defaultBlockState(), Block.UPDATE_ALL);
		}
	}
}
