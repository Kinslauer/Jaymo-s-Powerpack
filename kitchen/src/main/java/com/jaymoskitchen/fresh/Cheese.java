package com.jaymoskitchen.fresh;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Cheese (Andy's TFC Hardrock picks): a wheel made from three buckets of milk and salt, aged in a cold cellar
 * ({@link CheeseWheelBlock}), cut into young or aged slices. Dairy for the food groups.
 */
public final class Cheese {
	public static Block CHEESE_WHEEL;
	public static Item CHEESE, AGED_CHEESE;

	private Cheese() {
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("cheese_wheel"));
		CHEESE_WHEEL = Registry.register(BuiltInRegistries.BLOCK, key, new CheeseWheelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(0.5F).sound(SoundType.WOOL).randomTicks().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("cheese_wheel"));
		Item wheel = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(CHEESE_WHEEL, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
		CHEESE = food("cheese", 3, 0.5F);
		AGED_CHEESE = food("aged_cheese", 6, 0.8F);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(wheel);
			output.accept(CHEESE);
			output.accept(AGED_CHEESE);
		});
	}

	private static Item food(String id, int nutrition, float saturation) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()).setId(key)));
	}
}
