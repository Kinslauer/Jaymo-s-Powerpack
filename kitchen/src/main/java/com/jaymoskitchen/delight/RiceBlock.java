package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's rice, MIT; its art, our own code.) Rice: planted in shallow water (one block deep, on dirt or
 * mud), it always stands in water. Grown, it puts up its panicles above the water ({@link RicePaniclesBlock}), which
 * ripen into rice panicles to cut on the board. The paddy's water feeds it, so it grows steadily wherever it's planted.
 */
public class RiceBlock extends CropBlock implements LiquidBlockContainer {
	/** One in this many random ticks it grows a stage (a moist farmland wheat is about one in seven). */
	static final int GROWTH = 6;
	/** The chance a grown plant puts up its panicles on a random tick. */
	static final float PANICLE_CHANCE = 0.3F;

	public RiceBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return Delight.RICE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		FluidState water = level.getFluidState(pos);
		// a paddy: standing water, open above it
		if (!water.is(FluidTags.WATER) || !water.isSource() || level.getFluidState(pos.above()).is(FluidTags.WATER)) {
			return null;
		}
		return super.getStateForPlacement(context);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		BlockState result = super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		if (!result.isAir()) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return result;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		// still ticks once grown, to put up its panicles
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// (CropSeasonMixin's check, as this doesn't go through CropBlock's tick)
		if (com.jaymoskitchen.season.SeasonalCrops.waits(state, level, pos, random) || !hasSufficientLight(level, pos)) {
			return;
		}
		if (!isMaxAge(state)) {
			if (random.nextInt(GROWTH) == 0) {
				level.setBlock(pos, getStateForAge(getAge(state) + 1), Block.UPDATE_CLIENTS);
			}
		} else if (random.nextFloat() < PANICLE_CHANCE) {
			growPanicles(level, pos);
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return !isMaxAge(state) || level.isEmptyBlock(pos.above());
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		if (isMaxAge(state)) {
			growPanicles(level, pos);
		} else {
			super.performBonemeal(level, random, pos, state, source);
		}
	}

	static void growPanicles(Level level, BlockPos pos) {
		if (level.isEmptyBlock(pos.above())) {
			level.setBlock(pos.above(), Delight.RICE_PANICLES.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return Fluids.WATER.getSource(false);
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
		return false;
	}

	@Override
	public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
		return false;
	}
}
