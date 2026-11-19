package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Andy (2026-11-19): "crushing should leave white debris and smaller offal like pebbles called brain matter". What a
 * skull crushed by a mace or a hammer leaves on the ground ({@code Gibs#skullCrushed}): 1-{@link #MAX} little pale lumps
 * with chips of white bone among them, set down as a gut pile is, and like one taken by hand, smelled by scavengers and
 * rotting away a piece at a time. Only lower and smaller than a gut pile.
 */
public class BrainMatterBlock extends OffalPileBlock {
	private static final VoxelShape[] SHAPES = {
		Block.box(6.0, 0.0, 6.0, 10.0, 1.0, 10.0),
		Block.box(5.0, 0.0, 5.0, 11.0, 1.0, 11.0),
		Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0),
		Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
	};

	public BrainMatterBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(PIECES) - 1];
	}
}
