package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corrobonite ore: every random tick it grows a corrobonite cluster on one free side (a random one of the six; that side
 * must be air), so at most one cluster per face.
 */
public class CorroboniteOreBlock extends AetherOreBlock {
	// ---- tuning ----
	/** One growth attempt per random tick takes a 1 in this chance. */
	public static final int GROW_ODDS = 1;

	public CorroboniteOreBlock(BlockBehaviour.Properties properties) {
		super(0, 0, properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(GROW_ODDS) != 0) {
			return;
		}
		Direction face = Direction.getRandom(random);
		BlockPos at = pos.relative(face);
		if (level.getBlockState(at).isAir()) {
			level.setBlockAndUpdate(at, AetherBlocks.CORROBONITE_CLUSTER.defaultBlockState().setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, face));
		}
	}
}
