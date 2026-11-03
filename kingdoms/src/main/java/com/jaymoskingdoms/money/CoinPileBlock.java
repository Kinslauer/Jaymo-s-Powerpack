package com.jaymoskingdoms.money;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.486) A pile of coin stacks: one to eight layers, laid one coin stack at a time like snow (use a stack on a pile
 * to add a layer; breaking it gives them all back). Hoards and big withdrawals sit in these. It never melts, which is the
 * one thing a snow layer does that a coin pile mustn't.
 */
public class CoinPileBlock extends SnowLayerBlock {
	public CoinPileBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
	}
}
