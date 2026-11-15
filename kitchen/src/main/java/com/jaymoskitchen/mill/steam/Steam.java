package com.jaymoskitchen.mill.steam;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
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
 * Steam power for the mill machines (GT New Horizons picks, patch 8: the small steam boiler). A {@link FireboxBlock
 * firebox} burns fuel under a copper or bronze {@link BoilerBlock boiler}, and a {@link SteamEngineBlock steam engine}
 * beside or on the boiler turns a shaft for the horse mill, chopper, press, potter's wheel and gearbox, just as a
 * windmill would. The engine's cylinder and the boiler's safety valve are parts of their own, made by any tinker and
 * sold by the ones who build steam (items a trader can carry).
 */
public final class Steam {
	/** What fills a boiler a bucket at a time: any water bucket, and Jaymo's Powers' clay one. */
	public static final TagKey<Item> BOILER_WATER = TagKey.create(Registries.ITEM, JaymosKitchen.id("boiler_water"));

	public static FireboxBlock FIREBOX;
	public static BoilerBlock COPPER_BOILER;
	public static BoilerBlock BRONZE_BOILER;
	public static SteamEngineBlock ENGINE;
	public static BlockEntityType<FireboxEntity> FIREBOX_ENTITY;
	public static BlockEntityType<BoilerEntity> BOILER_ENTITY;
	public static BlockEntityType<SteamEngineEntity> ENGINE_ENTITY;
	public static Item FIREBOX_ITEM;
	public static Item COPPER_BOILER_ITEM;
	public static Item BRONZE_BOILER_ITEM;
	public static Item ENGINE_ITEM;
	/** The parts: an engine's cylinder (piston and valve gear) and a boiler's safety valve. */
	public static Item STEAM_CYLINDER;
	public static Item SAFETY_VALVE;

	private Steam() {
	}

	public static void register() {
		FIREBOX = block("firebox", FireboxBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE)
			.lightLevel(state -> state.getValue(FireboxBlock.LIT) ? 13 : 0));
		COPPER_BOILER = block("copper_boiler", props -> new BoilerBlock(BoilerMetal.COPPER, props), metal(MapColor.COLOR_ORANGE));
		BRONZE_BOILER = block("bronze_boiler", props -> new BoilerBlock(BoilerMetal.BRONZE, props), metal(MapColor.TERRACOTTA_ORANGE));
		ENGINE = block("steam_engine", SteamEngineBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
		FIREBOX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("firebox"),
			new BlockEntityType<>(FireboxEntity::new, Set.of(FIREBOX)));
		BOILER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("boiler"),
			new BlockEntityType<>(BoilerEntity::new, Set.of(COPPER_BOILER, BRONZE_BOILER)));
		ENGINE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("steam_engine"),
			new BlockEntityType<>(SteamEngineEntity::new, Set.of(ENGINE)));
		// a pipe that speaks Fabric's fluid transfer can fill a boiler (never empty it)
		FluidStorage.SIDED.registerForBlockEntity((boiler, side) -> boiler.water, BOILER_ENTITY);

		FIREBOX_ITEM = blockItem("firebox", FIREBOX);
		COPPER_BOILER_ITEM = blockItem("copper_boiler", COPPER_BOILER);
		BRONZE_BOILER_ITEM = blockItem("bronze_boiler", BRONZE_BOILER);
		ENGINE_ITEM = blockItem("steam_engine", ENGINE);
		STEAM_CYLINDER = item("steam_cylinder");
		SAFETY_VALVE = item("safety_valve");

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : List.of(FIREBOX_ITEM, COPPER_BOILER_ITEM, BRONZE_BOILER_ITEM, ENGINE_ITEM)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(new ItemStack(STEAM_CYLINDER));
			output.accept(new ItemStack(SAFETY_VALVE));
		});
	}

	/** A riveted drum: mined with a pickaxe; red-hot, it glows. */
	private static BlockBehaviour.Properties metal(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.COPPER)
			.lightLevel(state -> state.getValue(BoilerBlock.HEAT) == 2 ? 9 : 0);
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}
}
