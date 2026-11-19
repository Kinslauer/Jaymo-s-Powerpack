package com.jaymoskitchen.vintner;

import com.jaymoskitchen.fresh.Curing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The wood chips the smoker's minigame takes: one chip from the cook's pockets (the off hand first) goes on the coals
 * when the meat or fish comes out well smoked. Each wood keeps the food a different number of times longer than plain
 * smoke ({@link #keeps}; plain smoke is four) and fills a different amount more ({@link #fill}, in food points).
 */
public enum Chip {
	OAK("oak", 5, 0),
	BIRCH("birch", 4, 1),
	CHERRY("cherry", 5, 1),
	APPLE("apple", 4, 2),
	DARK_OAK("dark_oak", 7, 0);

	/** Prefix of the {@link Curing#CURED} method a chip leaves ({@code smoke_oak}). */
	public static final String CURED_PREFIX = "smoke_";

	public final String id;
	public final int keeps;
	public final int fill;
	private Item item;

	Chip(String id, int keeps, int fill) {
		this.id = id;
		this.keeps = keeps;
		this.fill = fill;
	}

	public Item item() {
		return item;
	}

	void item(Item item) {
		this.item = item;
	}

	public static @Nullable Chip of(ItemStack stack) {
		for (Chip chip : values()) {
			if (!stack.isEmpty() && stack.is(chip.item)) {
				return chip;
			}
		}
		return null;
	}

	/** The chip a cured method names ({@code smoke_oak}), or null. */
	public static @Nullable Chip ofCured(String method) {
		for (Chip chip : values()) {
			if (method.equals(CURED_PREFIX + chip.id)) {
				return chip;
			}
		}
		return null;
	}

	/** Smoked food comes out of the smoker: a chip from the cook's pockets goes on the coals and leaves its mark on it. */
	public static void smoke(ServerPlayer cook, ItemStack food) {
		ItemStack found = cook.getOffhandItem();
		Chip chip = of(found);
		if (chip == null) {
			for (int i = 0; i < cook.getInventory().getContainerSize() && chip == null; i++) {
				found = cook.getInventory().getItem(i);
				chip = of(found);
			}
		}
		if (chip == null) {
			return;
		}
		if (!cook.getAbilities().instabuild) {
			found.shrink(1);
		}
		food.set(Curing.CURED, CURED_PREFIX + chip.id);
		FoodProperties properties = food.get(DataComponents.FOOD);
		if (properties != null && chip.fill > 0) {
			food.set(DataComponents.FOOD, new FoodProperties(properties.nutrition() + chip.fill, properties.saturation(), properties.canAlwaysEat()));
		}
	}
}
