package com.jaymospowers.tundra;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tundra shrub: a small bush on soil, moss or gravel ({@link Tundra#SHRUB_GROUND}), set a little off the block's
 * middle like grass.
 */
public class ShrubBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);

	public ShrubBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(Tundra.SHRUB_GROUND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Vec3 offset = state.getOffset(pos);
		return SHAPE.move(offset.x, offset.y, offset.z);
	}
}
