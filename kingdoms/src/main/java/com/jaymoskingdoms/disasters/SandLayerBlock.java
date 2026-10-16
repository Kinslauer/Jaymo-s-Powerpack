package com.jaymoskingdoms.disasters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.31) A layer of sand (or red sand) a sandstorm leaves drifted over the ground, one to eight deep like snow. It
 * doesn't melt; dig it away with a shovel.
 */
public final class SandLayerBlock extends SnowLayerBlock {
	public SandLayerBlock(Properties properties) {
		super(properties);
	}

	/**
	 * (0.7.86) Only a single layer is replaced (as snow is by anything but snow). Snow's own rule asks whether the
	 * placer holds the layer's item; a sand layer has none (air), so an empty hand (a falling block landing) counted as
	 * holding it, and the check called itself until the server crashed.
	 */
	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return state.getValue(LAYERS) == 1;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// (sand doesn't melt)
	}
}
