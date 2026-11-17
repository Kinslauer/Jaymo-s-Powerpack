package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Station;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Reads what has been put on a station and says what it could make: every dish the station cooks whose ingredients are
 * all there, and (on a prep surface) dough or a joint. The fullest recipe comes first; sneak and scroll walks the rest.
 */
final class Recipes {
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
		if (Prep.isPrepSurface(table.level, table.pos, table.level.getBlockState(table.pos), Direction.UP)) {
			for (Prep.Recipe recipe : Prep.Recipe.values()) {
				Pick pick = new Pick(recipe == Prep.Recipe.JOINT ? Pick.Kind.JOINT : Pick.Kind.DOUGH, null, 0);
				if (Table.cover(table.placed, pick.ingredients()) != null) {
					out.add(pick);
				}
			}
		}
		addExtra(table, out);
		out.sort(Comparator.comparingInt((Pick pick) -> -pick.ingredients().values().stream().mapToInt(Integer::intValue).sum())
			.thenComparingInt(pick -> pick.kind().ordinal()).thenComparingInt(Pick::dishIndex));
		return out;
	}

	/** Things only some patches' recipes want (sandwiches, brews, tea, sushi). */
	static boolean extraWanted(Table table, ItemStack stack) {
		return false;
	}

	private static void addExtra(Table table, List<Pick> out) {
	}
}
