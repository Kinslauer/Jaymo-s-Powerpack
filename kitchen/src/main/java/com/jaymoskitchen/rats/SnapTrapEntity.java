package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A snap trap's bait, and (on clients) how far its bar has come down. */
public class SnapTrapEntity extends BaitTrapEntity {
	/** The bar's swing, in steps of a tick, and how far it goes (as the Rats mod's: 0 set, 6 shut). */
	public static final float SHUT_STEP = 1.5F, SHUT_FULL = 6.0F;
	public float shutProgress, shutProgressO;

	public SnapTrapEntity(BlockPos pos, BlockState state) {
		super(Rats.SNAP_TRAP_ENTITY, pos, state);
		shutProgress = shutProgressO = state.getValue(SnapTrapBlock.SHUT) ? SHUT_FULL : 0.0F;
	}

	@Override
	public boolean armed() {
		return !getBlockState().getValue(SnapTrapBlock.SHUT) && baited();
	}

	void clientTick(BlockState state) {
		shutProgressO = shutProgress;
		if (state.getValue(SnapTrapBlock.SHUT)) {
			shutProgress = Math.min(SHUT_FULL, shutProgress + SHUT_STEP);
		} else {
			shutProgress = Math.max(0.0F, shutProgress - SHUT_STEP);
		}
	}
}
