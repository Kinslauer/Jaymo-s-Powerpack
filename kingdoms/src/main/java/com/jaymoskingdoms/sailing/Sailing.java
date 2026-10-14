package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.7.7) Sailing, stage 1: ships built from blocks. The {@link HelmBlock} and {@link SailBlock}, the {@link Ship} they
 * make, and its packet. Our own take on the idea (block ships that sail), no one's code.
 */
public final class Sailing {
	public static Block HELM, SAIL;
	public static Item HELM_ITEM, SAIL_ITEM;

	private Sailing() {
	}

	public static void register() {
		Ship.register();
		HELM = block("ship_helm", HelmBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
			.strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		SAIL = block("sail", SailBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).instrument(NoteBlockInstrument.GUITAR)
			.strength(0.8F).sound(SoundType.WOOL).noOcclusion().ignitedByLava());
		HELM_ITEM = blockItem("ship_helm", HELM);
		SAIL_ITEM = blockItem("sail", SAIL);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.BAMBOO_CHEST_RAFT, HELM_ITEM);
			output.insertAfter(HELM_ITEM, SAIL_ITEM);
		});
		ShipNet.register();
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}
}
