package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.delight.Delight;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The tools of hands-on cooking. The knife is the Kitchen's own (the {@link Delight#KNIVES} tag); the spoon, tongs, fan
 * and peel are new: nothing of the kind was in the Kitchen. Original art (kitchen/tools/gen_cooking_tools.py).
 * Each way of cooking is done with one of them (see {@link Tool}).
 */
public final class CookTools {
	public static Item SPOON, TONGS, FAN, PEEL;

	private CookTools() {
	}

	public static void register() {
		SPOON = tool("wooden_spoon");
		TONGS = tool("kitchen_tongs");
		FAN = tool("smokers_fan");
		PEEL = tool("bakers_peel");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(new ItemStack(SPOON));
			output.accept(new ItemStack(TONGS));
			output.accept(new ItemStack(FAN));
			output.accept(new ItemStack(PEEL));
		});
	}

	private static Item tool(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).setId(key)));
	}

	/** What a cook holds to do one thing at a station. */
	public enum Tool {
		/** The empty hand (kneading dough, sprinkling, turning the spit). */
		HAND,
		KNIFE,
		SPOON,
		TONGS,
		PEEL,
		FAN;

		/** Whether {@code held} is this tool (HAND: nothing in the hand). */
		public boolean holds(ItemStack held) {
			return switch (this) {
				case HAND -> held.isEmpty();
				case KNIFE -> held.is(Delight.KNIVES);
				case SPOON -> held.is(CookTools.SPOON);
				case TONGS -> held.is(CookTools.TONGS);
				case PEEL -> held.is(CookTools.PEEL);
				case FAN -> held.is(CookTools.FAN);
			};
		}

		public String translationKey() {
			return "tool.jaymoskitchen." + name().toLowerCase();
		}
	}

	/** Whether the stack is any of the cooking tools (a knife, spoon, tongs, fan or peel). */
	public static boolean isTool(ItemStack stack) {
		return stack.is(Delight.KNIVES) || stack.is(SPOON) || stack.is(TONGS) || stack.is(FAN) || stack.is(PEEL);
	}
}
