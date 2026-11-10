package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A support beam: a squared timber, set upright as a post or on its side as a cross beam, that holds the roof round it
 * up ({@link CaveIns#HELD}). Placed like a log.
 */
public class SupportBeamBlock extends RotatedPillarBlock {
	private static final VoxelShape Y = Block.box(4, 0, 4, 12, 16, 12), X = Block.box(0, 4, 4, 16, 12, 12), Z = Block.box(4, 4, 0, 12, 12, 16);

	public SupportBeamBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(AXIS)) {
			case X -> X;
			case Z -> Z;
			default -> Y;
		};
	}
}
