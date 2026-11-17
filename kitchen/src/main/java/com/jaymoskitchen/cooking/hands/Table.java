package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.cooking.Step;
import com.jaymoskitchen.net.Payloads.HandsSync;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * One station's worktop: what has been put on it (sneak and right-click with the thing in hand), the dishes that makes
 * ({@link #picks}, chosen between with sneak and scroll), and the {@link Run} once a cook starts one with its tool. There
 * is one dish at a station, shared by whoever steps in.
 */
public final class Table {
	/** Most things that can lie on a worktop at once. */
	static final int MAX_PLACED = 16;
	/** Players this near (blocks) are sent what is on the station. */
	static final double WATCH_RANGE_SQ = 32.0 * 32.0;
	/** How long a finished dish's verdict stays on the readout (ticks). */
	static final int VERDICT_TICKS = 40;

	final ServerLevel level;
	final BlockPos pos;
	final List<ItemStack> placed = new ArrayList<>();
	List<Pick> picks = List.of();
	int pickIndex;
	@Nullable Run run;
	/** The last tick someone stood near it. */
	int lastNear;
	/** Tick the verdict clears (judged and waiting), or 0. */
	int verdictUntil;
	int verdictKind;
	int verdictDish = -1;
	int verdictQuality = -1;
	int lastSync;

	Table(ServerLevel level, BlockPos pos, int now) {
		this.level = level;
		this.pos = pos;
		this.lastNear = now;
	}

	boolean inReach(ServerPlayer player) {
		return Kitchen.inReach(player, pos);
	}

	@Nullable Station station() {
		return Station.of(level, pos);
	}

	// --- what's been put on it ---

	/** Whether {@code stack} is something one of the dishes here wants. */
	boolean wanted(ItemStack stack) {
		Station station = station();
		if (station != null) {
			for (Dish dish : station.dishes()) {
				for (Step step : dish.ingredients().keySet()) {
					if (step.matches(stack)) {
						return true;
					}
				}
			}
		}
		if (Prep.isPrepSurface(level, pos, level.getBlockState(pos), Direction.UP)) {
			for (Prep.Recipe recipe : Prep.Recipe.values()) {
				for (Step step : Prep.ingredients(recipe).keySet()) {
					if (step.matches(stack)) {
						return true;
					}
				}
			}
		}
		return Recipes.extraWanted(this, stack);
	}

	/** What a sandwich was stacked from (in order), for its run. */
	List<ItemStack> taken = List.of();

	boolean put(ServerPlayer player, ItemStack held) {
		if (placed.size() >= MAX_PLACED || !wanted(held)) {
			return false;
		}
		placed.add(held.copyWithCount(1));
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		changed();
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.2F);
		return true;
	}

	/** Takes the last thing put there back into the cook's pockets. */
	boolean takeBack(ServerPlayer player) {
		if (placed.isEmpty()) {
			return false;
		}
		Kitchen.give(player, placed.remove(placed.size() - 1));
		changed();
		return true;
	}

	/** Everything on it goes back to the cook (or the floor). */
	void clear(@Nullable ServerPlayer player) {
		for (ItemStack stack : placed) {
			if (player != null) {
				Kitchen.give(player, stack);
			} else {
				Block.popResource(level, pos.above(), stack);
			}
		}
		placed.clear();
		changed();
	}

	/** Recomputes what the placed things make, keeping the chosen dish if it still can be. */
	void changed() {
		Pick before = picks.isEmpty() ? null : picks.get(Math.min(pickIndex, picks.size() - 1));
		picks = Recipes.picks(this);
		pickIndex = Math.max(0, before == null ? 0 : picks.indexOf(before));
		sync(level.getServer().getTickCount(), true);
	}

	void choose(int delta) {
		if (picks.size() > 1) {
			pickIndex = Math.floorMod(pickIndex + delta, picks.size());
			sync(level.getServer().getTickCount(), true);
		}
	}

	@Nullable Pick chosen() {
		return picks.isEmpty() ? null : picks.get(Math.min(pickIndex, picks.size() - 1));
	}

	// --- ending ---

	/** The dish at {@code run} is judged: it goes to the last cook who's still about, else it's left on the station. */
	void award(Run run, Quality quality) {
		Dish dish = run.dish;
		this.run = null;
		ServerPlayer cook = recipient(run);
		if (dish != null) {
			if (cook != null) {
				Kitchen.award(cook, dish, pos, quality);
			} else {
				ItemStack stack = new ItemStack(ModItems.dish(dish), dish.batch());
				quality.apply(stack, dish);
				Block.popResource(level, pos.above(), stack);
			}
		}
		verdict(run, quality.ordinal());
	}

	/** Something made by hand (dough, a joint...) is done: {@code result} is shown. */
	void concluded(Run run, int result) {
		this.run = null;
		verdict(run, result);
	}

	private void verdict(Run run, int quality) {
		verdictUntil = level.getServer().getTickCount() + VERDICT_TICKS;
		verdictKind = run.kind().ordinal();
		verdictDish = run.pick.dishIndex();
		verdictQuality = quality;
		changed();
		sync(level.getServer().getTickCount(), true);
	}

	@Nullable ServerPlayer recipient(Run run) {
		if (run.last == null) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(run.last);
		return player != null && player.level() == level ? player : null;
	}

	/** Something made by hand goes to the last cook still about, else it's left on the station. */
	void handOver(Run run, ItemStack stack) {
		ServerPlayer cook = recipient(run);
		if (cook != null) {
			Kitchen.give(cook, stack);
		} else {
			Block.popResource(level, pos.above(), stack);
		}
	}

	boolean empty() {
		return placed.isEmpty() && run == null && verdictUntil == 0;
	}

	// --- telling the clients ---

	void sync(int now, boolean force) {
		if (!force && now - lastSync < 4) {
			return;
		}
		lastSync = now;
		List<Integer> ints = new ArrayList<>();
		List<Float> floats = new ArrayList<>();
		int phase = HandsSync.PHASE_STAGING;
		int kind = -1;
		int dish = -1;
		int quality = -1;
		int cooks = 0;
		Pick chosen = chosen();
		if (chosen != null) {
			kind = chosen.kind().ordinal();
			dish = chosen.dishIndex();
		}
		if (run != null) {
			phase = HandsSync.PHASE_RUNNING;
			kind = run.kind().ordinal();
			dish = run.pick.dishIndex();
			cooks = run.cooks.size();
			run.fill(ints, floats);
		} else if (verdictUntil > 0) {
			phase = HandsSync.PHASE_JUDGED;
			kind = verdictKind;
			dish = verdictDish;
			quality = verdictQuality;
		} else if (placed.isEmpty()) {
			phase = HandsSync.PHASE_GONE;
		}
		HandsSync packet = new HandsSync(pos, phase, kind, dish, picks.isEmpty() ? 0 : Math.min(pickIndex, picks.size() - 1), picks.size(),
			quality, cooks, List.copyOf(placed), ints.stream().mapToInt(Integer::intValue).toArray(), toArray(floats));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= WATCH_RANGE_SQ) {
				ServerPlayNetworking.send(player, packet);
			}
		}
	}

	private static float[] toArray(List<Float> floats) {
		float[] out = new float[floats.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = floats.get(i);
		}
		return out;
	}

	// --- matching what's placed against a recipe ---

	/**
	 * Which placed things a recipe's ingredients use (pickiest ingredient first, so a thing any fruit will do for never
	 * takes the one peach the Peach ingredient needs), or null if they aren't all there.
	 */
	static boolean @Nullable [] cover(List<ItemStack> placed, Map<Step, Integer> need) {
		boolean[] used = new boolean[placed.size()];
		List<Map.Entry<Step, Integer>> order = new ArrayList<>(need.entrySet());
		order.sort(Comparator.comparingInt(entry -> entry.getKey().items().size()));
		for (Map.Entry<Step, Integer> entry : order) {
			Step step = entry.getKey();
			int left = entry.getValue();
			for (Item item : step.items()) {
				for (int i = 0; i < used.length && left > 0; i++) {
					if (!used[i] && placed.get(i).is(item) && step.matches(placed.get(i))) {
						used[i] = true;
						left--;
					}
				}
			}
			if (left > 0) {
				return null;
			}
		}
		return used;
	}

	/** Uses up what {@code pick} needs from the worktop and gives what's left over back to {@code starter}. */
	void consume(Pick pick, ServerPlayer starter) {
		if (pick.kind() == Pick.Kind.SANDWICH) {
			// the whole stack is the sandwich
			taken = new ArrayList<>(placed);
			placed.clear();
			changed();
			return;
		}
		boolean[] used = cover(placed, pick.ingredients());
		List<ItemStack> keep = new ArrayList<>();
		for (int i = 0; i < placed.size(); i++) {
			if (used == null || !used[i]) {
				keep.add(placed.get(i));
			} else {
				Step remainderStep = null;
				for (Step step : pick.ingredients().keySet()) {
					if (step.matches(placed.get(i))) {
						remainderStep = step;
						break;
					}
				}
				if (remainderStep != null && remainderStep.remainder() != null) {
					Kitchen.give(starter, new ItemStack(remainderStep.remainder()));
				}
			}
		}
		placed.clear();
		for (ItemStack stack : keep) {
			Kitchen.give(starter, stack);
		}
		changed();
	}

	void playBlock(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
	}
}
