package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A seared tank: one molten metal at a time, {@link #CAPACITY} of it. A faucet above fills it; a faucet on its side empties it. */
public class SearedTankEntity extends MoltenEntity {
	/** What it holds (four blocks' worth). */
	public static final int CAPACITY = Molten.BLOCK * 4;

	public SearedTankEntity(BlockPos pos, BlockState state) {
		super(SmelteryExtras.TANK_ENTITY, pos, state, CAPACITY);
	}

	@Override
	public int wants(Molten molten) {
		Molten held = offers();
		return held == null || held == molten ? tank.space() : 0;
	}

	@Override
	public void fill(Molten molten, int amount) {
		if (tank.fill(molten, amount, false) > 0) {
			changed();
		}
	}
}
