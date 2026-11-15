package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.kiln.Heat;
import com.jaymospowers.tinkering.smithing.RealmSteels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The alloyer: set over a fire, with seared tanks or melters against its sides, it draws the metals of an alloy out of
 * them and mixes them into its own pool (one alloy at a time), which a faucet on a free side pours. It is how metals
 * melted apart (in melters, or a foundry) are alloyed on purpose.
 * <p>
 * (medieval picks) A realm steel takes knowing ({@link RealmSteels#knows}). The alloyer's keeper is whoever set it down
 * (or first tended one nobody keeps); it works with what its keeper knows, looked at again whenever the keeper is
 * online ({@link #KEEPER_CHECK_TICKS}) or tends it. A realm steel its keeper doesn't know spoils: only its base metal
 * comes out, as much as went in, and the metal added to it is lost as slag ({@link RealmSteels#spoiled}).
 */
public class AlloyerEntity extends MoltenEntity {
	/** What it holds (two blocks' worth), and how often it mixes one batch (ticks). */
	public static final int CAPACITY = Molten.BLOCK * 2;
	static final int MIX_TICKS = 2;
	/** How often it looks again at what its keeper knows, while they're online (ticks). */
	static final int KEEPER_CHECK_TICKS = 100;

	private int ticks;
	/** Its keeper, or null when nobody keeps it. */
	private @Nullable UUID keeper;
	/** The realm steels its keeper knows the working of (their {@link Molten} ids), as last seen. */
	private final Set<String> known = new HashSet<>();

	public AlloyerEntity(BlockPos pos, BlockState state) {
		super(SmelteryExtras.ALLOYER_ENTITY, pos, state, CAPACITY);
	}

	@Override
	protected void work(ServerLevel level, BlockState state) {
		if (keeper != null && ticks % KEEPER_CHECK_TICKS == 0) {
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(keeper);
			if (online != null) {
				learn(online);
			}
		}
		if (++ticks % MIX_TICKS != 0) {
			return;
		}
		boolean mixed = Heat.at(level, worldPosition.below()) > 0 && mix(level);
		if (state.getValue(MelterBlock.LIT) != mixed) {
			level.setBlock(worldPosition, state.setValue(MelterBlock.LIT, mixed), Block.UPDATE_ALL);
		}
	}

	/** {@code player} sets it down or tends it: one that nobody keeps becomes theirs, and its keeper's knowing is looked at again. A stranger changes nothing. */
	void keep(ServerPlayer player) {
		if (keeper == null) {
			keeper = player.getUUID();
			setChanged();
		}
		if (keeper.equals(player.getUUID())) {
			learn(player);
		}
	}

	/** What its keeper knows now. */
	private void learn(ServerPlayer player) {
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

	/** Whether this alloy spoils here: a realm steel its keeper doesn't know. */
	private boolean spoils(Melting.Alloy alloy) {
		return RealmSteels.realm(alloy.output()) && !known.contains(alloy.output().id);
	}

	/** How much of what comes out: a spoiled melt gives back only its base metal, as much of it as went in. */
	private static int amountOut(Melting.Alloy alloy, Molten out) {
		if (out == alloy.output()) {
			return alloy.amount();
		}
		int base = 0;
		for (Melting.Input input : alloy.inputs()) {
			if (input.molten() == out) {
				base += input.amount();
			}
		}
		return Math.min(base, alloy.amount());
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
			Molten out = spoils(alloy) ? RealmSteels.spoiled(alloy.output()) : alloy.output();
			int amount = amountOut(alloy, out);
			if (held != null && held != out || amount <= 0 || tank.space() < amount) {
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
				tank.fill(out, amount, false);
				changed();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		String id = input.getStringOr("keeper", "");
		try {
			keeper = id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			keeper = null;
		}
		known.clear();
		for (String steel : input.getStringOr("known_steels", "").split(",")) {
			if (!steel.isEmpty()) {
				known.add(steel);
			}
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (keeper != null) {
			output.putString("keeper", keeper.toString());
		}
		output.putString("known_steels", String.join(",", known));
	}
}
