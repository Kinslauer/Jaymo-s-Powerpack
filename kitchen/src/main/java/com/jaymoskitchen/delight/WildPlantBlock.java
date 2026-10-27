package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's wild crops, MIT; their art, our own code.) A wild vegetable growing out in the world (wild onions,
 * carrots, potatoes, tomatoes, sea beets): a small flower (suspicious stew takes an effect from it) that gives the crop
 * and its seeds when broken, and that bone meal spreads to a few neighbours.
 */
public class WildPlantBlock extends FlowerBlock implements BonemealableBlock {
	private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);
	/** Bone meal fails to spread when this many of its kind already grow nearby (within 4 blocks sideways, 1 up and down). */
	private static final int NEARBY_LIMIT = 10;
	private static final float BONEMEAL_SUCCESS = 0.8F;
	private static final int SPREAD_TRIES = 4;

	public WildPlantBlock(Holder<MobEffect> stewEffect, float stewSeconds, Properties properties) {
		super(stewEffect, stewSeconds, properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(DelightFarm.GROWS_WILD_CROPS);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return false;
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return random.nextFloat() < BONEMEAL_SUCCESS;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		int limit = NEARBY_LIMIT;
		for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
			if (level.getBlockState(nearby).is(this) && --limit <= 0) {
				return;
			}
		}
		BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
		for (int i = 0; i < SPREAD_TRIES; ++i) {
			if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
				pos = target;
			}
			target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
		}
		if (level.isEmptyBlock(target) && state.canSurvive(level, target)) {
			level.setBlock(target, state, Block.UPDATE_CLIENTS);
		}
	}
}
