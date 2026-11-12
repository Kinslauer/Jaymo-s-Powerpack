package com.jaymospowers.tundra;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Sprouted glow lichen: glow lichen that has been fed bone meal and come alive. It clings to faces like its vanilla
 * sister and, in daylight, eats the rock it sits on and seeds plain lichen round about ({@link LichenLife}), until
 * shears snip it ({@link #SNIPPED}): a snipped one only glows.
 */
public class SproutedLichenBlock extends MultifaceBlock {
	public static final BooleanProperty SNIPPED = BooleanProperty.create("snipped");

	public SproutedLichenBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(SNIPPED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SNIPPED);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(SNIPPED);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		LichenLife.tick(level, pos, state, random);
	}
}
