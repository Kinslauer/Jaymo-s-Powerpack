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
 * make, and its packet. Our own take on the idea (block ships that sail), no one's code. (0.7.18) Stage 2: the wind in
 * her sails ({@link Winds}), {@link OarBlock oars}, {@link ShipCannonBlock guns} and their {@link ShipShot shot}, and
 * {@link ShipKits build kits}, after AlekiShips' ideas (MIT; no code or art of it is used).
 */
public final class Sailing {
	public static Block HELM, SAIL, OAR, CANNON, FRAME;
	public static Item HELM_ITEM, SAIL_ITEM, OAR_ITEM, CANNON_ITEM, ROUND_SHOT, ROWBOAT_KIT, SLOOP_KIT, BRIG_KIT, SHIP_PLANS;

	private Sailing() {
	}

	public static void register() {
		Ship.register();
		HELM = block("ship_helm", HelmBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
			.strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		SAIL = block("sail", SailBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).instrument(NoteBlockInstrument.GUITAR)
			.strength(0.8F).sound(SoundType.WOOL).noOcclusion().ignitedByLava());
		// (0.7.18) oars, guns and the frames of build kits
		OAR = block("oar", OarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
			.strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		CANNON = block("ship_cannon", ShipCannonBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
			.strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
		FRAME = block("ship_frame", ShipFrameBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(0.3F).sound(SoundType.SCAFFOLDING).noOcclusion().noLootTable().ignitedByLava());
		HELM_ITEM = blockItem("ship_helm", HELM);
		SAIL_ITEM = blockItem("sail", SAIL);
		OAR_ITEM = blockItem("oar", OAR);
		CANNON_ITEM = blockItem("ship_cannon", CANNON);
		ROUND_SHOT = item("round_shot", Item::new, new Item.Properties().stacksTo(16));
		ROWBOAT_KIT = item(ShipKits.Kind.ROWBOAT.id(), p -> new ShipKits.KitItem(ShipKits.Kind.ROWBOAT, p), new Item.Properties().stacksTo(1));
		SLOOP_KIT = item(ShipKits.Kind.SLOOP.id(), p -> new ShipKits.KitItem(ShipKits.Kind.SLOOP, p), new Item.Properties().stacksTo(1));
		BRIG_KIT = item(ShipKits.Kind.BRIG.id(), p -> new ShipKits.KitItem(ShipKits.Kind.BRIG, p), new Item.Properties().stacksTo(1));
		// (0.7.28) kits of your own: plans drawn from a ship you've built
		SHIP_PLANS = item("ship_plans", ShipPlans.PlansItem::new, new Item.Properties().stacksTo(1));
		ShipShot.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.BAMBOO_CHEST_RAFT, HELM_ITEM);
			output.insertAfter(HELM_ITEM, SAIL_ITEM);
			output.insertAfter(SAIL_ITEM, OAR_ITEM);
			output.insertAfter(OAR_ITEM, CANNON_ITEM);
			output.insertAfter(CANNON_ITEM, ROUND_SHOT);
			output.insertAfter(ROUND_SHOT, ROWBOAT_KIT);
			output.insertAfter(ROWBOAT_KIT, SLOOP_KIT);
			output.insertAfter(SLOOP_KIT, BRIG_KIT);
			output.insertAfter(BRIG_KIT, SHIP_PLANS);
		});
		ShipNet.register();
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}
}
