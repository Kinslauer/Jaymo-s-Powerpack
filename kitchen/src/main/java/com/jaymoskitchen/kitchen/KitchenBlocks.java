package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mixin.ItemAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

/**
 * The cookware (cooking pot, frying pan, saucepan, baking tray, rolling pin), which stays in the crafting grid when
 * you cook with it, and the foods it makes. The kitchen itself (oven, fridges, counters...) is Cooking for
 * Blockheads'. Original art (the pot and pan's are Farmer's Delight's, MIT); the idea of cookware as crafting tools comes from Pam's HarvestCraft.
 */
public final class KitchenBlocks {
	public static Item COOKING_POT;
	public static Item FRYING_PAN;
	public static Item SAUCEPAN;
	public static Item BAKING_TRAY;
	public static Item ROLLING_PIN;
	/** Food made with the cookware, by id. */
	public static final Map<String, Item> FOODS = new LinkedHashMap<>();

	private KitchenBlocks() {
	}

	public static void register() {
		// (0.5.28) the pot and pan also set down over a fire to cook in (delight/CookwareBlock)
		com.jaymoskitchen.delight.Delight.registerCookware();
		COOKING_POT = tool("cooking_pot", com.jaymoskitchen.delight.Delight.COOKING_POT_BLOCK);
		FRYING_PAN = tool("frying_pan", com.jaymoskitchen.delight.Delight.FRYING_PAN_BLOCK);
		SAUCEPAN = tool("saucepan");
		BAKING_TRAY = tool("baking_tray");
		ROLLING_PIN = tool("rolling_pin");

		food("fried_egg", 4, 0.6F, null, false);
		food("bacon", 3, 0.6F, null, false);
		food("fries", 6, 0.6F, null, false);
		food("vegetable_soup", 8, 0.7F, Items.BOWL, false);
		food("berry_jam", 4, 0.3F, Items.GLASS_BOTTLE, true);
		food("hot_chocolate", 5, 0.5F, Items.GLASS_BOTTLE, true);
		food("flatbread", 5, 0.6F, null, false);
		food("mushroom_pasta", 10, 0.8F, Items.BOWL, false);
		food("brownie", 4, 0.4F, null, false);
		// Pantry for Blockheads' lemons, squeezed into water with sugar (the recipe only loads with Pantry)
		food("lemonade", 2, 0.3F, Items.GLASS_BOTTLE, true);
		// an ingredient, not a meal
		FOODS.put("pasta", register("pasta", Item::new, new Item.Properties()));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (Item item : List.of(COOKING_POT, FRYING_PAN, SAUCEPAN, BAKING_TRAY, ROLLING_PIN)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : FOODS.values()) {
				output.accept(new ItemStack(item));
			}
		});
	}

	/** Cookware: one to a stack, and it stays in the crafting grid after use. */
	private static Item tool(String id) {
		return tool(id, null);
	}

	/** Cookware that can also be set down as a block. */
	private static Item tool(String id, net.minecraft.world.level.block.Block block) {
		Item item = block == null ? register(id, Item::new, new Item.Properties().stacksTo(1))
			: register(id, p -> new net.minecraft.world.item.BlockItem(block, p), new Item.Properties().stacksTo(1));
		((ItemAccessor) item).jaymoskitchen$setCraftingRemainder(new ItemStackTemplate(item));
		return item;
	}

	private static void food(String id, int nutrition, float saturation, Item leaves, boolean drink) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		Item.Properties properties = new Item.Properties();
		if (drink) {
			properties.food(food, Consumables.DEFAULT_DRINK);
		} else {
			properties.food(food);
		}
		if (leaves != null) {
			properties.usingConvertsTo(leaves).stacksTo(16);
		}
		FOODS.put(id, register(id, Item::new, properties));
	}

	private static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
