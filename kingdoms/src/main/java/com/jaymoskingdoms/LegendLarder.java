package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * What the age of songs' great beasts leave for a cook (Jaymo's Kitchen's Legend feasts, which read them by id): a wind
 * dragon's meat, from the five winds, and a Giant's marrow, from the Aether's Giants. Both come from their loot tables;
 * neither can be eaten as it is.
 */
public final class LegendLarder {
	public static Item WIND_DRAGON_MEAT, GIANTS_MARROW;

	private LegendLarder() {
	}

	static void register() {
		WIND_DRAGON_MEAT = plain("wind_dragon_meat");
		GIANTS_MARROW = plain("giants_marrow");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(WIND_DRAGON_MEAT);
			output.accept(GIANTS_MARROW);
		});
	}

	private static Item plain(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.RARE)));
	}
}
