package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.brewing.Brew;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.cooking.Step;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads what has been put on a station and says what it could make: every dish the station cooks whose ingredients are
 * all there, on a prep surface dough or a joint, and on the new stations their own (a brew in the keg, tea in the kettle,
 * rolls on the sushi mat), and on a board a sandwich stacked in order. The fullest recipe comes first; sneak and scroll
 * walks the rest.
 */
final class Recipes {
	/** The rolls the sushi mat makes (the board's chopping makes them too). */
	private static final Dish[] SUSHI = {Dish.SALMON_ROLL, Dish.COD_ROLL, Dish.KELP_ROLL, Dish.RICE_ROLL_MEDLEY};
	/** What a keg of one brew wants: its base, and water to match. */
	private static final int KEG_BASE = com.jaymoskitchen.brewing.CaskEntity.BASE_PER_BATCH, KEG_WATER = com.jaymoskitchen.brewing.CaskEntity.CAPACITY;
	/** What a kettle wants: herbs and water for a batch of cups. */
	private static final int TEA_HERBS = 2, TEA_WATER = com.jaymoskitchen.brewing.CaskEntity.DRINKS_PER_BATCH;
	/** Layers a sandwich needs at least (bread, a filling, bread). */
	private static final int MIN_LAYERS = 3;

	private Recipes() {
	}

	static List<Pick> picks(Table table) {
		List<Pick> out = new ArrayList<>();
		if (table.placed.isEmpty()) {
			return out;
		}
		Station station = table.station();
		if (station != null) {
			for (Dish dish : station.dishes()) {
				if (Table.cover(table.placed, dish.ingredients()) != null) {
					out.add(Pick.of(dish));
				}
			}
		}
		BlockState state = table.level.getBlockState(table.pos);
		if (Prep.isPrepSurface(table.level, table.pos, state, Direction.UP)) {
			for (Prep.Recipe recipe : Prep.Recipe.values()) {
				Pick pick = new Pick(recipe == Prep.Recipe.JOINT ? Pick.Kind.JOINT : Pick.Kind.DOUGH, null, 0);
				if (Table.cover(table.placed, pick.ingredients()) != null) {
					out.add(pick);
				}
			}
		}
		addExtra(table, state, out);
		out.sort(Comparator.comparingInt((Pick pick) -> -pick.ingredients().values().stream().mapToInt(Integer::intValue).sum())
			.thenComparingInt(pick -> pick.kind().ordinal()).thenComparingInt(Pick::dishIndex).thenComparingInt(Pick::extra));
		return out;
	}

	/** The step a brew's base is. */
	static Step kegStep(Brew brew) {
		return switch (brew) {
			case ALE -> Step.WHEAT;
			case CIDER -> Step.APPLE;
			case MEAD -> Step.HONEY_BOTTLE;
			case BERRY_WINE -> Step.SWEET_BERRIES;
			case GRAPE_WINE -> Step.GRAPES;
		};
	}

	static Map<Step, Integer> kegIngredients(int brew) {
		Map<Step, Integer> need = new EnumMap<>(Step.class);
		need.put(kegStep(Brew.values()[Math.floorMod(brew, Brew.values().length)]), KEG_BASE);
		need.put(Step.WATER_BOTTLE, KEG_WATER);
		return need;
	}

	static Map<Step, Integer> teaIngredients() {
		Map<Step, Integer> need = new EnumMap<>(Step.class);
		need.put(Step.TEA_HERB, TEA_HERBS);
		need.put(Step.WATER_BOTTLE, TEA_WATER);
		return need;
	}

	private static boolean board(Table table, BlockState state) {
		return Station.of(state) == Station.BOARD || Prep.isPrepSurface(table.level, table.pos, state, Direction.UP);
	}

	/** Things only some recipes want (a brew's base, herbs, rice and fish for rolls, a sandwich's layers). */
	static boolean extraWanted(Table table, ItemStack stack) {
		BlockState state = table.level.getBlockState(table.pos);
		if (state.is(HandsBlocks.KEG)) {
			for (Brew brew : Brew.values()) {
				if (kegStep(brew).matches(stack)) {
					return true;
				}
			}
			return Step.WATER_BOTTLE.matches(stack);
		}
		if (state.is(HandsBlocks.KETTLE)) {
			return Step.TEA_HERB.matches(stack) || Step.WATER_BOTTLE.matches(stack);
		}
		if (state.is(HandsBlocks.MAT)) {
			for (Dish dish : SUSHI) {
				for (Step step : dish.ingredients().keySet()) {
					if (step.matches(stack)) {
						return true;
					}
				}
			}
			return false;
		}
		return board(table, state) && (Step.BREAD.matches(stack) || stack.has(DataComponents.FOOD));
	}

	private static void addExtra(Table table, BlockState state, List<Pick> out) {
		if (state.is(HandsBlocks.KEG)) {
			for (Brew brew : Brew.values()) {
				if (kegStep(brew).available() && Table.cover(table.placed, kegIngredients(brew.ordinal())) != null) {
					out.add(new Pick(Pick.Kind.KEG, null, brew.ordinal()));
				}
			}
		} else if (state.is(HandsBlocks.KETTLE)) {
			if (Table.cover(table.placed, teaIngredients()) != null) {
				out.add(new Pick(Pick.Kind.TEA, null, 0));
			}
		} else if (state.is(HandsBlocks.MAT)) {
			for (Dish dish : SUSHI) {
				if (dish.available() && Table.cover(table.placed, dish.ingredients()) != null) {
					out.add(new Pick(Pick.Kind.SUSHI, dish, 0));
				}
			}
		} else if (board(table, state) && stacked(table.placed)) {
			out.add(new Pick(Pick.Kind.SANDWICH, null, 0));
		}
	}

	/** Whether the things on the board, in the order put there, are a sandwich: bread, at least one filling, bread. */
	private static boolean stacked(List<ItemStack> placed) {
		if (placed.size() < MIN_LAYERS || !Step.BREAD.matches(placed.get(0)) || !Step.BREAD.matches(placed.get(placed.size() - 1))) {
			return false;
		}
		for (ItemStack layer : placed) {
			if (!Step.BREAD.matches(layer) && !layer.has(DataComponents.FOOD)) {
				return false;
			}
		}
		for (int i = 1; i < placed.size() - 1; i++) {
			if (!Step.BREAD.matches(placed.get(i))) {
				return true;
			}
		}
		return false;
	}
}
