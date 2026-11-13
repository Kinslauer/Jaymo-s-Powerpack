package com.jaymoskitchen.fresh;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import com.jaymoskitchen.stomach.RawFood;

/**
 * The ways to keep food (Andy's TFC Hardrock picks): salt in hand and the smoker's minigame were all there was. Now also
 * the <b>drying rack</b> ({@code tanning.DryingRackBlock}, which hung hides), which dries raw meat, fish and fruit into
 * jerky and dried fruit. Each way stamps the food {@link #CURED} with its name and keeps it {@link #multiplier longer}:
 * salt and smoke four times, drying five. (Brine and vinegar crocks were built and taken out again: Andy, 2026-11-12,
 * "maybe no brine or vinegar because salt can handle that".)
 */
public final class Curing {
	/** How a stack was kept: salt, smoke or dried. Preserved food without one was kept before this and counts as salted. */
	public static DataComponentType<String> CURED;

	private Curing() {
	}

	public static void register() {
		CURED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("cured"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
	}

	/** Whether {@code method} (dried) takes {@code food}. */
	public static boolean takes(String method, ItemStack food) {
		if (!Freshness.perishable(food) || food.has(Freshness.PRESERVED)) {
			return false;
		}
		boolean meat = food.is(RawFood.RAW_MEATS) || food.is(ConventionalItemTags.RAW_MEAT_FOODS);
		boolean fish = food.is(ConventionalItemTags.RAW_FISH_FOODS);
		boolean fruit = food.is(ConventionalItemTags.FRUIT_FOODS) || food.is(ConventionalItemTags.BERRY_FOODS);
		return switch (method) {
			case "dried" -> meat || fish || fruit;
			default -> false;
		};
	}

	/** Cures {@code food} by {@code method}: preserved, and stamped with how. */
	public static void cure(ItemStack food, String method) {
		Freshness.preserve(food);
		food.set(CURED, method);
	}

	/** How many times longer food kept this way keeps. */
	public static int multiplier(ItemStack food) {
		String method = food.get(CURED);
		if (method == null) {
			return Freshness.PRESERVED_KEEPS;
		}
		return switch (method) {
			case "dried" -> 5;
			default -> Freshness.PRESERVED_KEEPS;
		};
	}
}
