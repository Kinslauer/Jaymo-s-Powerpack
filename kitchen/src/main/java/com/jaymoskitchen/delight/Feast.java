package com.jaymoskitchen.delight;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's feasts, MIT; their models and art, our own code.) A big dish cooked like any other (its minigame
 * at the campfire, oven or smoker), that comes out as a block to set on a table: everyone takes a serving from it
 * ({@link FeastBlock}), the bowl-served ones with a bowl in hand. Each serving has the feast's quality.
 */
public enum Feast {
	ROAST_CHICKEN(Dish.ROAST_CHICKEN_FEAST, Dish.ROAST_CHICKEN, null, true),
	SHEPHERDS_PIE(Dish.SHEPHERDS_PIE_FEAST, Dish.SHEPHERDS_PIE, null, true),
	HONEY_GLAZED_HAM(Dish.HONEY_GLAZED_HAM_FEAST, null, () -> Delight.HONEY_GLAZED_HAM, true),
	STUFFED_PUMPKIN(Dish.STUFFED_PUMPKIN_FEAST, null, () -> Delight.STUFFED_PUMPKIN, false),
	// (0.5.33) cut on the board; it glows (see FeastBlock). The rice roll medley has eight servings and is its own block (MedleyBlock)
	GLEAMING_SALAD(Dish.GLEAMING_SALAD_FEAST, null, () -> DelightDishes.GLEAMING_SALAD, true);

	/** Servings in a whole feast. */
	public static final int SERVINGS = 4;

	private final Dish dish;
	private final @Nullable Dish servingDish;
	private final @Nullable Supplier<Item> servingItem;
	/** Whether something's left on the tray once it's all served (bones, crumbs) to clear away. */
	private final boolean leftovers;

	Feast(Dish dish, @Nullable Dish servingDish, @Nullable Supplier<Item> servingItem, boolean leftovers) {
		this.dish = dish;
		this.servingDish = servingDish;
		this.servingItem = servingItem;
		this.leftovers = leftovers;
	}

	public Dish dish() {
		return dish;
	}

	public boolean leftovers() {
		return leftovers;
	}

	public Item servingItem() {
		return servingDish != null ? ModItems.dish(servingDish) : servingItem.get();
	}

	/** Whether a serving is eaten from a bowl (and so needs one in hand to take it). */
	public boolean needsBowl() {
		ItemStack serving = new ItemStack(servingItem());
		var remainder = serving.get(DataComponents.USE_REMAINDER);
		return remainder != null && remainder.convertInto().item().value() == Items.BOWL;
	}

	/** A serving at the feast's quality. */
	public ItemStack serving(Quality quality) {
		ItemStack stack = new ItemStack(servingItem());
		if (servingDish != null) {
			quality.apply(stack, servingDish);
		} else if (quality != Quality.GOOD) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			int nutrition = food == null ? 0 : food.nutrition();
			float saturation = food == null ? 0.0F : food.saturation() / Math.max(1, 2 * nutrition);
			quality.apply(stack, nutrition, saturation, dish.perfectBuffs());
		}
		return stack;
	}

	public static @Nullable Feast of(Dish dish) {
		for (Feast feast : values()) {
			if (feast.dish == dish) {
				return feast;
			}
		}
		return null;
	}
}
