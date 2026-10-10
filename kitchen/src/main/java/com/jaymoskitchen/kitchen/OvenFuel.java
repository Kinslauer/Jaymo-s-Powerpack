package com.jaymoskitchen.kitchen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cooking for Blockheads' oven's own fuel lookup, opened up by {@code OvenBlockEntityMixin}. */
public interface OvenFuel {
	int jaymoskitchen$burnTime(Level level, ItemStack stack);
}
