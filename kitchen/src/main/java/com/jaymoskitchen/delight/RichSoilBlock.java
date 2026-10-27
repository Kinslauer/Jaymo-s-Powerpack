package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Farmer's Delight's rich soil, MIT; their art, our own code.) What organic compost rots down to: soil that gives
 * the plant growing on it a bone meal's push now and then, turns a mushroom growing on it into a colony, and tills
 * to {@link RichSoilFarmlandBlock} (a hoe, registered in {@link DelightFarm}).
 */
public class RichSoilBlock extends Block {
	/** The chance a random tick gives the plant above (or a cave vine below) a bone meal's growth. */
	public static final float BOOST_CHANCE = 0.2F;
	/** The game event for bone meal's green sparkles, shown at the plant that grew. */
	private static final int BONEMEAL_PARTICLES = 1505, PARTICLE_COUNT = 15;

	public RichSoilBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos above = pos.above();
		if (convertMushroomToColony(level.getBlockState(above), above, level)) {
			return;
		}
		tryBoostingPlantsAboveAndBelow(level, pos, random);
	}

	public static void tryBoostingPlantsAboveAndBelow(ServerLevel level, BlockPos pos, RandomSource random) {
		if (BOOST_CHANCE == 0.0F || random.nextFloat() > BOOST_CHANCE) {
			return;
		}
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		if (!aboveState.is(DelightFarm.PLANTED_FROM_BELOW) && boostPlant(aboveState, above, level, BonemealSource.INTERACTION)) {
			return;
		}
		BlockPos below = pos.below();
		BlockState belowState = level.getBlockState(below);
		if (belowState.is(DelightFarm.PLANTED_FROM_BELOW)) {
			boostPlant(belowState, below, level, BonemealSource.INTERACTION);
		}
	}

	/** Bone-meals a plant if it can take it (and isn't one rich soil leaves alone). */
	public static boolean boostPlant(BlockState plantState, BlockPos plantPos, ServerLevel level, BonemealSource source) {
		if (plantState.is(DelightFarm.UNAFFECTED_BY_RICH_SOIL)) {
			return false;
		}
		if (plantState.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, plantPos, plantState, source)) {
			growable.performBonemeal(level, level.getRandom(), plantPos, plantState, source);
			level.levelEvent(BONEMEAL_PARTICLES, plantPos, PARTICLE_COUNT);
			return true;
		}
		return false;
	}

	private static boolean convertMushroomToColony(BlockState target, BlockPos targetPos, ServerLevel level) {
		if (target.is(Blocks.BROWN_MUSHROOM)) {
			level.setBlockAndUpdate(targetPos, DelightFarm.BROWN_MUSHROOM_COLONY.defaultBlockState());
			return true;
		}
		if (target.is(Blocks.RED_MUSHROOM)) {
			level.setBlockAndUpdate(targetPos, DelightFarm.RED_MUSHROOM_COLONY.defaultBlockState());
			return true;
		}
		return false;
	}
}
