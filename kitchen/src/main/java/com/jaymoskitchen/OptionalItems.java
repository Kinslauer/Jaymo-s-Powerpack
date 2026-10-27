package com.jaymoskitchen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Food from optional mods that goes into our dishes: Pantry for Blockheads' fruit and vegetables, Naturalist's game and
 * fish, and Jaymo's Powers' Aether fruit and meat. Without the mod those items are air, the dishes that need them stay
 * off the stations, and their recipes don't load (each names the items it needs under fabric:load_conditions).
 */
public final class OptionalItems {
	public static final String PANTRY = "pantryforblockheads";
	public static final String NATURALIST = "naturalist";
	public static final String POWERS = "jaymospowers";
	/** This mod itself, for items another part of it adds (looked up by id, with no link to the class that registers them). */
	public static final String KITCHEN = "jaymoskitchen";

	private OptionalItems() {
	}

	/** The mod's item, or air when the mod isn't there. */
	public static Item item(String mod, String path) {
		if (!FabricLoader.getInstance().isModLoaded(mod)) {
			return Items.AIR;
		}
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(mod, path));
	}
}
