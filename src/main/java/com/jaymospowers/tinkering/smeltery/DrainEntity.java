package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A drain remembers the smeltery whose wall it's in, so a faucet on it knows where to pour from. */
public class DrainEntity extends BlockEntity {
	private @Nullable BlockPos controller;

	public DrainEntity(BlockPos pos, BlockState state) {
		super(Tinkering.DRAIN_ENTITY, pos, state);
	}

	void setController(BlockPos pos) {
		if (!pos.equals(controller)) {
			controller = pos;
			setChanged();
		}
	}

	/** Its smeltery, if it's still standing and whole. */
	public @Nullable SmelteryEntity smeltery() {
		if (controller == null || level == null || !(level.getBlockEntity(controller) instanceof SmelteryEntity smeltery)) {
			return null;
		}
		return smeltery.formed() ? smeltery : null;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		long at = input.getLongOr("controller", Long.MIN_VALUE);
		controller = at == Long.MIN_VALUE ? null : BlockPos.of(at);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (controller != null) {
			output.putLong("controller", controller.asLong());
		}
	}
}
