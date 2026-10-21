package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain ground plant (the Aether II grasses, ferns, flowers, shoots, small mushrooms, the twig and the shrub): one
 * block with its own outline. Most stand on anything vanilla plants do (dirt, grass, moss); mushrooms and twigs also
 * stand on any solid top.
 */
public class FloraPlantBlock extends VegetationBlock {
	private final VoxelShape shape;
	private final boolean solidGround;

	public FloraPlantBlock(VoxelShape shape, boolean solidGround, BlockBehaviour.Properties properties) {
		super(properties);
		this.shape = shape;
		this.solidGround = solidGround;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shape;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return this.solidGround ? state.isFaceSturdy(level, pos, Direction.UP) : super.mayPlaceOn(state, level, pos);
	}
}
