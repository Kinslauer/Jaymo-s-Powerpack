package com.jaymoskitchen.butchery;

import com.jaymoskitchen.corpse.Scavengers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andy: "Can we do something similar to attract game animals?" A salt lick: 1-{@link #MAX} pieces of salt set down on
 * the ground as a gut pile is. Deer, boar, cattle and horses come to it from a way off and lick it
 * ({@link Scavengers#SALT_LICKERS}), as the real ones seek out salt. It doesn't rot; rain washes it away a piece at a time.
 */
public class SaltLickBlock extends OffalPileBlock {
	/** A random tick in the rain in this many washes a piece away. */
	static final int WASH_ONE_IN = 3;

	public SaltLickBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.isRainingAt(pos.above()) && random.nextInt(WASH_ONE_IN) == 0) {
			takeOne(level, pos, state);
		} else {
			Scavengers.smellPile(level, pos);
		}
	}
}
