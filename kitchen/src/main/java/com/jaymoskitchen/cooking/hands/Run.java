package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One dish being made at a station, shared by everyone who steps in: the simulation runs here on the server (the same
 * numbers and the same judging as the screen games it replaces, see {@link com.jaymoskitchen.cooking.Minigames},
 * {@link com.jaymoskitchen.cooking.Grill}, {@link com.jaymoskitchen.cooking.Sear}, {@link com.jaymoskitchen.cooking.Smoke},
 * {@link com.jaymoskitchen.cooking.Prep}), whatever any cook does goes into it, and it is judged once. It ends when it is
 * done, or as left once the last cook has gone ({@link #leave()}).
 */
public abstract class Run {
	protected final Table table;
	protected final Pick pick;
	protected final @Nullable Dish dish;
	final com.jaymoskitchen.cooking.@Nullable Station station;
	protected final int startTick;
	protected final RandomSource random;
	/** Who has had a hand in it, and the tick they last did. */
	final Map<UUID, Integer> cooks = new LinkedHashMap<>();
	@Nullable UUID last;
	boolean over;

	protected Run(Table table, Pick pick, int now) {
		this.table = table;
		this.pick = pick;
		this.dish = pick.dish();
		this.station = table.station();
		this.startTick = now;
		this.random = table.level.getRandom();
	}

	public Pick.Kind kind() {
		return pick.kind();
	}

	/** Notes that this cook has just done something. */
	final void touch(ServerPlayer player, int now) {
		cooks.put(player.getUUID(), now);
		last = player.getUUID();
	}

	/** Drops cooks who've logged out or gone more than eight blocks off; true if that was the last of them. */
	final boolean lostCooks() {
		if (cooks.isEmpty()) {
			return false;
		}
		Iterator<UUID> it = cooks.keySet().iterator();
		while (it.hasNext()) {
			ServerPlayer cook = table.level.getServer().getPlayerList().getPlayer(it.next());
			if (cook == null || cook.level() != table.level || !table.inReach(cook)) {
				it.remove();
			}
		}
		return cooks.isEmpty();
	}

	/** One tick of the simulation. */
	abstract void tick(int now);

	/** A right-click on the station by a cook holding {@code held}. True if it was used (so nothing else happens). */
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		return false;
	}

	/** A left-click (the attack swing) at the station. True if it was used. */
	boolean attack(ServerPlayer player, ItemStack held, int now) {
		return false;
	}

	/** A continuous input from the cook's client (an angle round the pot, whether use is held...); {@code what} is the run's own code. */
	void input(ServerPlayer player, int what, float value, int now) {
	}

	/** Every cook has gone: judge it as left. */
	abstract void leave();

	/** The readout the clients draw (see HandsHud). */
	abstract void fill(List<Integer> ints, List<Float> floats);

	/** Ends it, judged at {@code quality}: the dish goes to the cook. */
	protected final void end(Quality quality) {
		if (!over) {
			over = true;
			table.award(this, quality);
		}
	}

	/** Ends it with something other than a dish made (dough, a joint...): {@code what} hands the cook the product. */
	protected final void endWith(int result, Runnable what) {
		if (!over) {
			over = true;
			what.run();
			table.concluded(this, result);
		}
	}
}
