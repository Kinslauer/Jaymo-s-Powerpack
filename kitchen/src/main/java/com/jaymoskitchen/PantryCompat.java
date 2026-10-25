package com.jaymoskitchen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Pantry for Blockheads (optional): its fruit and vegetables go into some of our dishes. Without it those dishes stay
 * off the stations and their recipes don't load (each names the items it needs under fabric:load_conditions).
 */
public final class PantryCompat {
	public static final String MOD_ID = "pantryforblockheads";

	private PantryCompat() {
	}

	public static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/** One of Pantry's items, or air when the mod isn't there. */
	public static Item item(String path) {
		if (!loaded()) {
			return Items.AIR;
		}
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, path));
	}
}
