package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers the season dial ({@link SeasonDialBlock}) and puts it in the redstone tab. */
public final class SeasonDial {
	public static SeasonDialBlock BLOCK;
	public static Item ITEM;

	private SeasonDial() {
	}

	public static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("season_dial"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new SeasonDialBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
			.strength(3.0F, 6.0F)
			.sound(SoundType.COPPER).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("season_dial"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> output.accept(new ItemStack(ITEM)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(new ItemStack(ITEM)));
	}
}
