package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A cage trap's bait. */
public class CageTrapEntity extends BaitTrapEntity {
	public CageTrapEntity(BlockPos pos, BlockState state) {
		super(Rats.CAGE_TRAP_ENTITY, pos, state);
	}

	@Override
	public boolean armed() {
		return !getBlockState().getValue(CageTrapBlock.CAUGHT) && baited();
	}
}
