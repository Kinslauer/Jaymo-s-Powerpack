package com.jaymoskitchen.delight;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a knife makes of things on the cutting board (Farmer's Delight's cuts): meat into two smaller cuts that cook
 * quicker into finger food, a cabbage into leaves, a rice panicle into rice and straw; (0.5.33) a cake, a pie, a pumpkin,
 * a melon or a kelp roll into slices, and a ham into chops.
 */
public final class Chopping {
	/** Cuts from one piece of meat or fish. */
	static final int MEAT_CUTS = 2;
	static final int CABBAGE_LEAVES = 2;
	static final int PANICLE_RICE = 2;
	/** Slices from a cake, a pumpkin pie (a pie dish gives PieBlock.SLICES), a pumpkin, a melon and a kelp roll. */
	static final int CAKE_SLICES = 7;
	static final int PIE_SLICES = 4;
	static final int PUMPKIN_SLICES = 4;
	static final int MELON_SLICES = 9;
	static final int KELP_ROLL_SLICES = 3;

	private Chopping() {
	}

	/** The pieces this cuts into (fresh stacks), or empty if a knife can't do anything with it. */
	public static List<ItemStack> cut(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}
		Item item = stack.getItem();
		if (item == Items.BEEF) {
			return List.of(new ItemStack(Delight.MINCED_BEEF, MEAT_CUTS));
		}
		if (item == Items.PORKCHOP) {
			return List.of(new ItemStack(Delight.RAW_BACON, MEAT_CUTS));
		}
		if (item == Items.CHICKEN) {
			return List.of(new ItemStack(Delight.CHICKEN_CUTS, MEAT_CUTS), new ItemStack(Items.BONE_MEAL));
		}
		if (item == Items.MUTTON) {
			return List.of(new ItemStack(Delight.MUTTON_CHOPS, MEAT_CUTS));
		}
		if (item == Items.COD) {
			return List.of(new ItemStack(Delight.COD_SLICE, MEAT_CUTS), new ItemStack(Items.BONE_MEAL));
		}
		if (item == Items.SALMON) {
			return List.of(new ItemStack(Delight.SALMON_SLICE, MEAT_CUTS), new ItemStack(Items.BONE_MEAL));
		}
		if (item == Delight.CABBAGE) {
			return List.of(new ItemStack(Delight.CABBAGE_LEAF, CABBAGE_LEAVES));
		}
		if (item == Delight.RICE_PANICLE) {
			return List.of(new ItemStack(Delight.RICE, PANICLE_RICE), new ItemStack(Delight.STRAW));
		}
		if (item == Items.WHEAT) {
			return List.of(new ItemStack(Delight.STRAW), new ItemStack(Items.WHEAT_SEEDS));
		}
		// (0.5.33) the rest of Farmer's Delight's cuts (see DelightDishes)
		if (item == Items.CAKE) {
			return List.of(new ItemStack(DelightDishes.CAKE_SLICE, CAKE_SLICES));
		}
		if (item == Items.PUMPKIN_PIE) {
			return List.of(new ItemStack(DelightDishes.PUMPKIN_PIE_SLICE, PIE_SLICES));
		}
		if (item == Items.PUMPKIN) {
			return List.of(new ItemStack(DelightDishes.PUMPKIN_SLICE, PUMPKIN_SLICES));
		}
		if (item == Items.MELON) {
			return List.of(new ItemStack(Items.MELON_SLICE, MELON_SLICES));
		}
		if (item == DelightDishes.HAM) {
			return List.of(new ItemStack(Items.PORKCHOP, MEAT_CUTS), new ItemStack(Items.BONE));
		}
		Dish dish = ModItems.dishOf(item);
		if (dish == Dish.SMOKED_HAM) {
			return List.of(new ItemStack(Items.COOKED_PORKCHOP, MEAT_CUTS), new ItemStack(Items.BONE));
		}
		if (dish == Dish.KELP_ROLL) {
			return List.of(new ItemStack(DelightDishes.KELP_ROLL_SLICE, KELP_ROLL_SLICES));
		}
		ItemStack slices = DelightDishes.pieSlices(stack);
		if (slices != null) {
			return List.of(slices);
		}
		return List.of();
	}
}
