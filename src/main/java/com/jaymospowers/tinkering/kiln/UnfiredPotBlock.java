package com.jaymospowers.tinkering.kiln;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A pot shaped from four clay balls, still soft: fired in a brick kiln it becomes a decorated pot. */
public class UnfiredPotBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 11, 14), Block.box(5, 11, 5, 11, 13, 11),
		Block.box(4, 13, 4, 12, 16, 12));

	public UnfiredPotBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
