package com.jaymospowers.armory;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Andy: "the dagger doesnt harvest the same way the other dagger does". The Tool Station dagger (a flint one is named
 * "Flint Dagger") cuts what a hoe does (leaves, hay, moss, sponge, sculk...) at its head's speed, on top of a sword's
 * cuts ({@code ToolStats}). Rogues &amp; Warriors' daggers (Flint Dagger and the rest) only had the sword's. Now they cut the
 * same, at their own material's speed scaled by the Tool Station dagger's {@link #SPEED_SHARE}.
 */
public final class DaggerHarvest {
	/** The Tool Station dagger's mining multiplier: a dagger cuts at this share of its material's speed. */
	static final float SPEED_SHARE = 0.75F;
	/** A material's own mining speed (vanilla's tool materials; flint as the Tool Station's), by the id's first word. */
	static final Map<String, Float> MATERIAL_SPEED = Map.of("flint", 3.5F, "wooden", 2.0F, "stone", 4.0F, "iron", 6.0F,
		"golden", 12.0F, "diamond", 8.0F, "netherite", 9.0F);
	/** Any other dagger of theirs (from an add-on's metal): an iron one's speed. */
	static final float DEFAULT_SPEED = 6.0F;
	/** A sword's speed through cobwebs, as vanilla's. */
	static final float COBWEB_SPEED = 15.0F;

	private DaggerHarvest() {
	}

	static void init() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(DaggerHarvest::rogueDagger, (builder, lookup, item) -> {
			HolderGetter<Block> blocks = lookup.lookupOrThrow(Registries.BLOCK);
			builder.set(DataComponents.TOOL, new Tool(List.of(
				Tool.Rule.minesAndDrops(HolderSet.direct(new Holder[] {Blocks.COBWEB.builtInRegistryHolder()}), COBWEB_SPEED),
				Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
				Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5F),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), speed(item))), 1.0F, 2, false));
		}));
	}

	/** One of Rogues &amp; Warriors' daggers ({@code rogues:flint_dagger}, {@code rogues:iron_dagger}...). */
	static boolean rogueDagger(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("rogues") && id.getPath().endsWith("_dagger");
	}

	private static float speed(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		String material = path.substring(0, path.indexOf('_'));
		return MATERIAL_SPEED.getOrDefault(material, DEFAULT_SPEED) * SPEED_SHARE;
	}
}
