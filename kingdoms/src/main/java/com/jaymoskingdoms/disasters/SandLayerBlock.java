package com.jaymoskingdoms.disasters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// (sand doesn't melt)
	}
}
