package com.jaymoskitchen.farmwater;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A {@link FishTrapBlock}'s bait: the game time it was set (the trap fills from then, lazily, when it is emptied). */
public class FishTrapEntity extends BlockEntity {
	private long baitedAt = -1L;

	public FishTrapEntity(BlockPos pos, BlockState state) {
		super(FarmWater.FISH_TRAP_ENTITY, pos, state);
	}

	public long baitedAt() {
		return baitedAt;
	}

	public void setBaitedAt(long time) {
		baitedAt = time;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		baitedAt = input.getLongOr("baited_at", -1L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("baited_at", baitedAt);
	}
}
