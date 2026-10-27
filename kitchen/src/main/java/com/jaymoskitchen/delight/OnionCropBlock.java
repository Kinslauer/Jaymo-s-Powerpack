package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's onions, MIT; their art, our own code.) Onions: a low crop on farmland like wheat. The onion
 * itself is what you plant.
 */
public class OnionCropBlock extends CropBlock {
	/** How tall the crop stands at each growth stage. */
	private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[] {
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 5.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
		Block.box(0.0D, 0.0D, 0.0D, 16.0D, 9.0D, 16.0D)
	};

	public OnionCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return DelightFarm.ONION;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_AGE[getAge(state)];
	}
}
