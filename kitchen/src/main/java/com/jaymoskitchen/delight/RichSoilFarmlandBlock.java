package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Farmer's Delight's rich soil farmland, MIT; their art, our own code.) Rich soil tilled: farmland that goes back to
 * rich soil, not dirt, when it's built over, can't be trampled, and once wet gives its crop a bone meal's growth now
 * and then. Separate from the Kitchen's fertile farmland, which is plain farmland worked with fertilizer.
 */
public class RichSoilFarmlandBlock extends FarmlandBlock {
	/** How far (sideways, and one up) water has to be to wet it, as for farmland. */
	private static final int WATER_REACH = 4;
	/** Bone meal's green sparkles, at the plant that grew. */
	private static final int BONEMEAL_PARTICLES = 1505, PARTICLE_COUNT = 15;
	/** A fall onto it hurts as on any block (the farmland isn't trampled). */
	private static final float FALL_DAMAGE_SCALE = 1.0F;

	public RichSoilFarmlandBlock(Properties properties) {
		super(DelightFarm.RICH_SOIL, properties);
	}

	private static boolean isNearWater(LevelReader level, BlockPos pos) {
		for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-WATER_REACH, 0, -WATER_REACH), pos.offset(WATER_REACH, 1, WATER_REACH))) {
			if (level.getFluidState(nearby).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Block above = level.getBlockState(pos.above()).getBlock();
		return super.canSurvive(state, level, pos) || above == Blocks.MELON || above == Blocks.PUMPKIN;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int moisture = state.getValue(MOISTURE);
		if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
			if (moisture > 0) {
				level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), Block.UPDATE_CLIENTS);
			}
		} else if (moisture < MAX_MOISTURE) {
			level.setBlock(pos, state.setValue(MOISTURE, MAX_MOISTURE), Block.UPDATE_CLIENTS);
		} else if (moisture == MAX_MOISTURE && RichSoilBlock.BOOST_CHANCE > 0.0F) {
			BlockPos above = pos.above();
			BlockState aboveState = level.getBlockState(above);
			if (aboveState.is(DelightFarm.UNAFFECTED_BY_RICH_SOIL) || aboveState.getBlock() instanceof TallFlowerBlock) {
				return;
			}
			if (aboveState.getBlock() instanceof BonemealableBlock growable && random.nextFloat() <= RichSoilBlock.BOOST_CHANCE
				&& growable.isValidBonemealTarget(level, above, aboveState, BonemealSource.INTERACTION)) {
				growable.performBonemeal(level, random, above, aboveState, BonemealSource.INTERACTION);
				level.levelEvent(BONEMEAL_PARTICLES, above, PARTICLE_COUNT);
			}
		}
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		entity.causeFallDamage(fallDistance, FALL_DAMAGE_SCALE, entity.damageSources().fall());
	}
}
