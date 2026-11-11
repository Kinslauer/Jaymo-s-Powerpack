package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An upright stem thinner than a block (a young balloon mushroom's): a post you can walk round. */
public class ThinStemBlock extends Block {
	private final VoxelShape shape;

	/** @param inset how far in from the block's sides the stem stands, in sixteenths */
	public ThinStemBlock(int inset, BlockBehaviour.Properties properties) {
		super(properties);
		this.shape = Block.box(inset, 0, inset, 16 - inset, 16, 16 - inset);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shape;
	}
}
