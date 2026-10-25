package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's wild crops, MIT; their art, our own code.) A wild crop growing out in the world (wild cabbages
 * on the coasts): break it for the crop and its seeds.
 */
public class WildCropBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

	public WildCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND);
	}
}
