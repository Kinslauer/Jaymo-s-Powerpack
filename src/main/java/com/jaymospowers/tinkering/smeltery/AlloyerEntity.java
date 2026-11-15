package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.kiln.Heat;
import com.jaymospowers.tinkering.smithing.RealmSteels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The alloyer: set over a fire, with seared tanks or melters against its sides, it draws the metals of an alloy out of
 * them and mixes them into its own pool (one alloy at a time), which a faucet on a free side pours. It is how metals
 * melted apart (in melters, or a foundry) are alloyed on purpose.
 * <p>
 * (medieval picks) A realm steel takes knowing ({@link RealmSteels#knows}): the alloyer keeps which ones its keeper knows
 * (whoever last set it down or tended it), and in the hands of one who doesn't know a steel's working the melt spoils to
 * the plain metal it was made from ({@link RealmSteels#spoiled}).
 */
public class AlloyerEntity extends MoltenEntity {
	/** What it holds (two blocks' worth), and how often it mixes one batch (ticks). */
	public static final int CAPACITY = Molten.BLOCK * 2;
	static final int MIX_TICKS = 2;

	private int ticks;
	/** The realm steels its keeper knows the working of (their {@link Molten} ids). */
	private final Set<String> known = new HashSet<>();

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

	/** {@code player} sets it down or tends it: theirs is the knowing it works with now. */
	void keep(ServerPlayer player) {
		Set<String> now = new HashSet<>();
		for (Melting.Alloy alloy : Melting.ALLOYS) {
			if (RealmSteels.realm(alloy.output()) && RealmSteels.knowing(player, alloy.output())) {
				now.add(alloy.output().id);
			}
		}
		if (!now.equals(known)) {
			known.clear();
			known.addAll(now);
			setChanged();
		}
	}

	/** What this alloy comes out as here: a realm steel its keeper doesn't know spoils. */
	private Molten made(Molten output) {
		return RealmSteels.realm(output) && !known.contains(output.id) ? RealmSteels.spoiled(output) : output;
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
			Molten out = made(alloy.output());
			if (held != null && held != out || tank.space() < alloy.amount()) {
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
				tank.fill(out, alloy.amount(), false);
				changed();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		known.clear();
		for (String id : input.getStringOr("known_steels", "").split(",")) {
			if (!id.isEmpty()) {
				known.add(id);
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("known_steels", String.join(",", known));
	}
}
