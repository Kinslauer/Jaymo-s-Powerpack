package com.jaymoskitchen.fresh;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;
import com.jaymoskitchen.stomach.RawFood;

/**
 * The ways to keep food (Andy's TFC Hardrock picks): salt in hand and the smoker's minigame were all there was. Now also
 * <b>brine</b> and <b>vinegar</b>, kept in crocks ({@link CrockBlock}): a crock holds up to {@link #SLOTS} stacks and a few
 * bottles of what they steep in; food left in it for {@link #CURE_TICKS} comes out cured. And the <b>drying rack</b>
 * ({@code tanning.DryingRackBlock}, which hung hides) now dries raw meat, fish and fruit into jerky and dried fruit. Each way
 * stamps the food {@link #CURED} with its name and keeps it {@link #multiplier longer}: salt and smoke four times, brine five,
 * drying five, vinegar six. Brine is for meat, fish and vegetables; vinegar for vegetables, fruit and fish; drying for raw
 * meat, fish and fruit.
 */
public final class Curing {
	/** How a stack was kept: salt, smoke, brine, vinegar or dried. Preserved food without one was kept before this and counts as salted. */
	public static DataComponentType<String> CURED;
	public static Item BRINE, VINEGAR;
	public static CrockBlock BRINE_CROCK, VINEGAR_CROCK;
	public static BlockEntityType<CrockEntity> CROCK_ENTITY;

	/** Stacks one crock holds, bottles' worth of steeping it takes at the most, charges a bottle gives, ticks a batch steeps. */
	static final int SLOTS = 8, MOST_CHARGES = 8, PER_BOTTLE = 2, CURE_TICKS = 6000;

	private Curing() {
	}

	public static void register() {
		CURED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("cured"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		BRINE = item("brine");
		VINEGAR = item("vinegar");
		BRINE_CROCK = crock("brine_crock", "brine");
		VINEGAR_CROCK = crock("vinegar_crock", "vinegar");
		CROCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("crock"),
			new BlockEntityType<>(CrockEntity::new, Set.of(BRINE_CROCK, VINEGAR_CROCK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(BRINE);
			output.accept(VINEGAR);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(BRINE_CROCK);
			output.accept(VINEGAR_CROCK);
		});
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(16).setId(key)));
	}

	private static CrockBlock crock(String id, String method) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(id));
		CrockBlock block = Registry.register(BuiltInRegistries.BLOCK, key, new CrockBlock(method, BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.2F).sound(SoundType.DECORATED_POT).noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** Whether {@code method} (brine, vinegar or dried) takes {@code food}. */
	public static boolean takes(String method, ItemStack food) {
		if (!Freshness.perishable(food) || food.has(Freshness.PRESERVED)) {
			return false;
		}
		boolean meat = food.is(RawFood.RAW_MEATS) || food.is(ConventionalItemTags.RAW_MEAT_FOODS);
		boolean fish = food.is(ConventionalItemTags.RAW_FISH_FOODS);
		boolean vegetable = food.is(ConventionalItemTags.VEGETABLE_FOODS);
		boolean fruit = food.is(ConventionalItemTags.FRUIT_FOODS) || food.is(ConventionalItemTags.BERRY_FOODS);
		return switch (method) {
			case "brine" -> meat || fish || vegetable;
			case "vinegar" -> vegetable || fruit || fish;
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
			case "brine", "dried" -> 5;
			case "vinegar" -> 6;
			default -> Freshness.PRESERVED_KEEPS;
		};
	}
}
