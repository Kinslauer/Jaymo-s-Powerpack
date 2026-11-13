package com.jaymoskingdoms;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The wondrous gadgets, our own set (the boots of long strides and the cloak of the depths are baubles in Jaymo's Powers):
 * the immovable rod, the climbing rope, the endless jug, the floating light, the stone hound, the horn of breaking and the
 * bottle of endless smoke. Each is its own class; this one registers them and holds their shared helpers.
 */
public final class Gadgets {
	private Gadgets() {
	}

	static void register() {
		ImmovableRod.register();
		ClimbingRope.register();
		EndlessJug.register();
		FloatingLight.register();
		StoneHound.register();
		BreakingHorn.register();
		SmokeBottle.register();
		ChargedWand.register();
		DeckOfFates.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(ImmovableRod.ITEM);
			output.accept(ClimbingRope.ITEM);
			output.accept(EndlessJug.ITEM);
			output.accept(FloatingLight.ITEM);
			output.accept(StoneHound.ITEM);
			output.accept(BreakingHorn.ITEM);
			output.accept(SmokeBottle.ITEM);
			output.accept(DeckOfFates.ITEM);
			output.accept(DeckOfFates.DEED);
		});
		GadgetLoot.register();
	}

	/** Registers a plain item that stacks to one. */
	static Item item(String id, Rarity rarity, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key).stacksTo(1).rarity(rarity)));
	}

	/** Registers a block. */
	static Block block(String id, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	/** Registers the item of a block (it never shows in a hand: the gadget item is what is carried). */
	static Item blockItem(String id, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}
}
