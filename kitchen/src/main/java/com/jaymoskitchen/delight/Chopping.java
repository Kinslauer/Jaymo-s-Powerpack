package com.jaymoskitchen.delight;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a knife makes of things on the cutting board (Farmer's Delight's cuts): meat into two smaller cuts that cook
 * quicker into finger food, a cabbage into leaves, a rice panicle into rice and straw.
 */
public final class Chopping {
	/** Cuts from one piece of meat or fish. */
	static final int MEAT_CUTS = 2;
	static final int CABBAGE_LEAVES = 2;
	static final int PANICLE_RICE = 2;

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
		return List.of();
	}
}
