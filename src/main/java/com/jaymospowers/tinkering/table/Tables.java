package com.jaymospowers.tinkering.table;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.StationBlock;
import com.jaymospowers.tinkering.ToolStationMenu;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The tables Tinkers' Construct has besides the Part Builder and the Tool Station: the Tool Forge (the big Tool Station,
 * for tools of more than 3 parts), the Crafting Station, and the Pattern and Part Chests. Assets and data come from
 * tools/tinkering_tables_assets.py; the side panel and tabs every table screen shares are {@link TableExtras}.
 */
public final class Tables {
	/** A tool of more parts than this can only be put together at the Tool Forge. */
	public static final int STATION_MAX_PARTS = 3;

	public static Block TOOL_FORGE, CRAFTING_STATION, PATTERN_CHEST, PART_CHEST;
	public static BlockEntityType<TinkerChestEntity> CHEST_ENTITY;
	/** The forge's menu is the Tool Station's, with 5 slots ({@link ToolStationMenu#forge}). */
	public static MenuType<ToolStationMenu> TOOL_FORGE_MENU;
	public static MenuType<CraftingStationMenu> CRAFTING_STATION_MENU;

	private Tables() {
	}

	/** Called from {@code Tinkering.init} before the station block entity is registered (it takes these blocks too). */
	public static void register() {
		TOOL_FORGE = block("tool_forge", p -> new StationBlock(p, StationBlock.Kind.TOOL_FORGE), BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(5.0F, 1200.0F).sound(SoundType.ANVIL).requiresCorrectToolForDrops().noOcclusion());
		CRAFTING_STATION = block("crafting_station", p -> new StationBlock(p, StationBlock.Kind.CRAFTING_STATION), BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
		PATTERN_CHEST = block("pattern_chest", TinkerChestBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
		PART_CHEST = block("part_chest", TinkerChestBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
		CHEST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("tinker_chest"),
			new BlockEntityType<>(TinkerChestEntity::new, Set.of(PATTERN_CHEST, PART_CHEST)));
		TOOL_FORGE_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("tool_forge"),
			new MenuType<ToolStationMenu>(ToolStationMenu::forge, FeatureFlags.VANILLA_SET));
		CRAFTING_STATION_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("crafting_station"),
			new MenuType<>(CraftingStationMenu::new, FeatureFlags.VANILLA_SET));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
