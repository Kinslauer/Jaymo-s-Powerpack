package com.jaymospowers.alchemy.flux;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blight Growth: what a flower, fern, sapling or crop withers into once flux gets to it, a grey-purple plant that is
 * worth nothing. Like any plant it stands on dirt, and also on Blighted Soil.
 */
public class BlightGrowthBlock extends BushBlock {
	public BlightGrowthBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT) || state.is(FluxEffects.BLIGHTED_SOIL) || state.is(Blocks.FARMLAND);
	}
}
