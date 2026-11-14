package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.kiln.Heat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The alloyer: set over a fire, with seared tanks or melters against its sides, it draws the metals of an alloy out of
 * them and mixes them into its own pool (one alloy at a time), which a faucet on a free side pours. It is how metals
 * melted apart (in melters, or a foundry) are alloyed on purpose.
 */
public class AlloyerEntity extends MoltenEntity {
	/** What it holds (two blocks' worth), and how often it mixes one batch (ticks). */
	public static final int CAPACITY = Molten.BLOCK * 2;
	static final int MIX_TICKS = 2;

	private int ticks;

	public AlloyerEntity(BlockPos pos, BlockState state) {
		super(SmelteryExtras.ALLOYER_ENTITY, pos, state, CAPACITY);
	}

	@Override
	protected void work(ServerLevel level, BlockState state) {
		if (++ticks % MIX_TICKS != 0) {
			return;
		}
		boolean mixed = Heat.at(level, worldPosition.below()) > 0 && mix(level);
		if (state.getValue(MelterBlock.LIT) != mixed) {
			level.setBlock(worldPosition, state.setValue(MelterBlock.LIT, mixed), Block.UPDATE_ALL);
		}
	}

	/** Mixes one batch of the first alloy the tanks beside it can make; false when there is none. */
	private boolean mix(ServerLevel level) {
		List<MoltenEntity> sources = new ArrayList<>();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBlockEntity(worldPosition.relative(side)) instanceof MoltenEntity beside
				&& (beside instanceof SearedTankEntity || beside instanceof MelterEntity)) {
				sources.add(beside);
			}
		}
		Molten held = offers();
		for (Melting.Alloy alloy : Melting.ALLOYS) {
			if (held != null && held != alloy.output() || tank.space() < alloy.amount()) {
				continue;
			}
			boolean ready = !sources.isEmpty();
			for (Melting.Input input : alloy.inputs()) {
				int have = 0;
				for (MoltenEntity source : sources) {
					have += source.tank().amount(input.molten());
				}
				ready &= have >= input.amount();
			}
			if (ready) {
				for (Melting.Input input : alloy.inputs()) {
					int need = input.amount();
					for (MoltenEntity source : sources) {
						need -= source.drain(input.molten(), need);
					}
				}
				tank.fill(alloy.output(), alloy.amount(), false);
				changed();
				return true;
			}
		}
		return false;
	}
}
