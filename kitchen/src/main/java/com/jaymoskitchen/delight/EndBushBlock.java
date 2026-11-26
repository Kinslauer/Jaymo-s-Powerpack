package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Ender's Delight's End plants, MIT, by Ax3dGaming; their art, our own code.) A plant of the End's bare stone (ethereal
 * saffron, the amberveil mushroom): it stands on end stone ({@link EnderDelight#END_PLANT_SOIL}) where it grows wild, or
 * on farmland where a cook plants it.
 */
public class EndBushBlock extends VegetationBlock {
	private final VoxelShape shape;

	public EndBushBlock(VoxelShape shape, Properties properties) {
		super(properties);
		this.shape = shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(EnderDelight.END_PLANT_SOIL) || state.is(BlockTags.SUPPORTS_CROPS);
	}
}
