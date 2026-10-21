package com.jaymoskitchen.mill.power;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Wind and water power for the mill machines (after Better With Mods' axles, windmills and waterwheels; MIT, our own
 * code and art): a wooden shaft carries the turn of a windmill or a waterwheel to a horse mill, horse chopper or horse
 * press at its other end, which then works as if a horse were walking it (see {@link ShaftDriven}). No gearboxes, no
 * networks: one source, up to {@link Shafts#SHAFT_MAX} shafts and at most one corner, one machine.
 */
public final class Power {
	/** What a windmill's sails can be sewn from: any wool, or hemp cloth. */
	public static final TagKey<Item> SAIL_CLOTH = TagKey.create(Registries.ITEM, JaymosKitchen.id("sail_cloth"));

	public static ShaftBlock SHAFT;
	public static ShaftCornerBlock CORNER;
	public static RotorBlock WINDMILL;
	public static RotorBlock WATERWHEEL;
	public static PowerPartBlock PART;
	public static BlockEntityType<RotorEntity> ROTOR_ENTITY;
	public static Item SHAFT_ITEM;
	public static Item CORNER_ITEM;
	public static Item WINDMILL_ITEM;
	public static Item WATERWHEEL_ITEM;

	private Power() {
	}

	public static void register() {
		SHAFT = block("shaft", ShaftBlock::new, wood());
		CORNER = block("shaft_corner", ShaftCornerBlock::new, wood());
		WINDMILL = block("windmill", props -> new RotorBlock(Rotor.WINDMILL, props), wood().noOcclusion());
		WATERWHEEL = block("waterwheel", props -> new RotorBlock(Rotor.WATERWHEEL, props), wood().noOcclusion());
		PART = block("power_part", PowerPartBlock::new, BlockBehaviour.Properties.of().noCollision().noOcclusion().noLootTable());
		ROTOR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("rotor"),
			new BlockEntityType<>(RotorEntity::new, java.util.Set.of(WINDMILL, WATERWHEEL)));

		SHAFT_ITEM = blockItem("shaft", SHAFT);
		CORNER_ITEM = blockItem("shaft_corner", CORNER);
		WINDMILL_ITEM = item("windmill", props -> new RotorItem(Rotor.WINDMILL, props), new Item.Properties().stacksTo(1));
		WATERWHEEL_ITEM = item("waterwheel", props -> new RotorItem(Rotor.WATERWHEEL, props), new Item.Properties().stacksTo(1));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : List.of(SHAFT_ITEM, CORNER_ITEM, WINDMILL_ITEM, WATERWHEEL_ITEM)) {
				output.accept(new ItemStack(item));
			}
		});
	}

	/** Like a log: chopped with an axe, burns. */
	private static BlockBehaviour.Properties wood() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F)
			.sound(SoundType.WOOD).ignitedByLava();
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
