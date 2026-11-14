package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stretch of channel: a little metal at a time, one kind. Each tick what it holds pours into whatever takes metal under
 * it (a casting table or basin, a tank, a channel lower down); what's left over runs on into the channels joined to it that
 * hold less. So metal poured in at one end finds every cast under the run.
 */
public class ChannelEntity extends MoltenEntity {
	/** What a stretch holds, and what moves a tick (down, and along). */
	public static final int CAPACITY = 48;
	static final int FLOW = 6;
	/** Dregs of less than a nugget that have had nowhere to go for this long cool away, so the run is free for another metal. */
	static final int DREGS_TICKS = 200;

	private int idle;

	public ChannelEntity(BlockPos pos, BlockState state) {
		super(SmelteryExtras.CHANNEL_ENTITY, pos, state, CAPACITY);
	}

	@Override
	public int wants(Molten molten) {
		Molten held = offers();
		return held == null || held == molten ? tank.space() : 0;
	}

	@Override
	public void fill(Molten molten, int amount) {
		if (tank.fill(molten, amount, false) > 0) {
			idle = 0;
			changed();
		}
	}

	@Override
	protected void work(ServerLevel level, BlockState state) {
		Molten molten = offers();
		if (molten == null) {
			return;
		}
		int before = tank.amount(molten);
		flow(level, state, molten);
		if (tank.amount(molten) != before || before >= Molten.NUGGET) {
			idle = 0;
		} else if (++idle >= DREGS_TICKS) {
			idle = 0;
			drain(molten, before);
		}
	}

	private void flow(ServerLevel level, BlockState state, Molten molten) {
		if (level.getBlockEntity(worldPosition.below()) instanceof MoltenHolder below) {
			int poured = Math.min(FLOW, Math.min(tank.amount(molten), below.wants(molten)));
			if (poured > 0) {
				below.fill(molten, drain(molten, poured));
				return;
			}
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int have = tank.amount(molten);
			if (have > 1 && state.getValue(ChannelBlock.side(side))
				&& level.getBlockEntity(worldPosition.relative(side)) instanceof ChannelEntity next) {
				int moved = Math.min(FLOW, Math.min((have - next.tank.amount(molten)) / 2, next.wants(molten)));
				if (moved > 0) {
					next.fill(molten, drain(molten, moved));
				}
			}
		}
	}
}
