package com.jaymoskitchen.corpse;

import net.minecraft.world.item.ItemStack;

/**
 * Implemented on every living entity (by mixin): while a corpse-leaving mob drops its death loot, the drops are
 * caught here instead of landing in the world, so they can go into its corpse.
 */
public interface CorpseCapture {
	boolean jaymoskitchen$isCapturing();

	void jaymoskitchen$capture(ItemStack stack);
}
