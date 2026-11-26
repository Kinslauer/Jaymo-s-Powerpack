package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The climate patch's house blocks: the rain barrel ({@link RainBarrelBlock}) and the wall air gauge ({@link GaugeBlock}).
 * (The climate stove, {@link RegulatorBlock}, is registered with the hearth it shares its block entity with: {@link Warmth}.)
 */
public final class HouseBlocks {
	public static RainBarrelBlock RAIN_BARREL;
	public static GaugeBlock GAUGE;
	public static Item RAIN_BARREL_ITEM, GAUGE_ITEM;

	private HouseBlocks() {
	}

	static void register() {
		// wood, so a builder crew pays for one as for any plank (Kingdoms' Materials reads the note-block instrument)
		ResourceKey<Block> barrelKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("rain_barrel"));
		RAIN_BARREL = Registry.register(BuiltInRegistries.BLOCK, barrelKey, new RainBarrelBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion().setId(barrelKey)));
		ResourceKey<Item> barrelItem = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("rain_barrel"));
		RAIN_BARREL_ITEM = Registry.register(BuiltInRegistries.ITEM, barrelItem, new BlockItem(RAIN_BARREL,
			new Item.Properties().useBlockDescriptionPrefix().setId(barrelItem)));
		ResourceKey<Block> gaugeKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("air_gauge"));
		GAUGE = Registry.register(BuiltInRegistries.BLOCK, gaugeKey, new GaugeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(1.0F).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.POPPED).setId(gaugeKey)));
		ResourceKey<Item> gaugeItem = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("air_gauge"));
		GAUGE_ITEM = Registry.register(BuiltInRegistries.ITEM, gaugeItem, new BlockItem(GAUGE,
			new Item.Properties().useBlockDescriptionPrefix().setId(gaugeItem)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(RAIN_BARREL_ITEM);
			output.accept(GAUGE_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> output.accept(GAUGE_ITEM));
	}
}
