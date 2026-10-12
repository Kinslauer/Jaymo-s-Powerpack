package com.jaymospowers.tinkering;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Jaymo's Tinkering: tools built from parts, after Tinkers' Construct (SlimeKnights, MIT; its textures are reused, see
 * textures/item/tinkering/license_tinkers_construct.txt). Shape parts from patterns and materials at the Part Builder,
 * put them together at the Tool Station; each material brings its own stats and trait. Tools break instead of vanishing
 * and are repaired with their head's material. Assets, recipes and lang come from tools/tinkering_assets.py.
 * <p>
 * Stage 1 of 3: the smeltery, casting, alloys and modifiers come next, then the pack tie-ins.
 */
public final class Tinkering {
	/** A tool's materials, in its part order. */
	public static DataComponentType<List<String>> TOOL_MATERIALS;
	/** A part's material. */
	public static DataComponentType<String> PART_MATERIAL;

	public static Item PATTERN;
	public static Block PART_BUILDER, TOOL_STATION;
	public static final Map<PartType, Item> PARTS = new EnumMap<>(PartType.class);
	public static final Map<ToolType, Item> TOOLS = new EnumMap<>(ToolType.class);
	public static MenuType<PartBuilderMenu> PART_BUILDER_MENU;
	public static MenuType<ToolStationMenu> TOOL_STATION_MENU;
	/** Whether tooltips spell out what each trait does (the client makes this "Shift is held"). */
	public static BooleanSupplier traitDetails = () -> true;

	private Tinkering() {
	}

	public static void init() {
		TOOL_MATERIALS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("tool_materials"),
			DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
				.networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())).build());
		PART_MATERIAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("part_material"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

		PATTERN = item("pattern", Item::new, p -> p.component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("tooltip.jaymospowers.tinkering.pattern").withStyle(ChatFormatting.GRAY)))));
		for (PartType part : PartType.values()) {
			PARTS.put(part, item(part.id, p -> new ToolPartItem(p, part), Function.identity()));
		}
		for (ToolType type : ToolType.values()) {
			TOOLS.put(type, item(type.itemId(), p -> new TinkerToolItem(p, type), p -> {
				p = p.durability(100).enchantable(14);
				if (type == ToolType.HAND_AXE) {
					p = p.delayedComponent(DataComponents.BLOCK_TRANSFORMER, context -> context.getOrThrow(BlockTransformers.AXE));
				} else if (type == ToolType.MATTOCK) {
					p = p.delayedComponent(DataComponents.BLOCK_TRANSFORMER, context -> context.getOrThrow(BlockTransformers.HOE));
				}
				return p;
			}));
		}
		PART_BUILDER = station("part_builder", StationBlock.Kind.PART_BUILDER);
		TOOL_STATION = station("tool_station", StationBlock.Kind.TOOL_STATION);
		PART_BUILDER_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("part_builder"),
			new MenuType<>(PartBuilderMenu::new, FeatureFlags.VANILLA_SET));
		TOOL_STATION_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("tool_station"),
			new MenuType<>(ToolStationMenu::new, FeatureFlags.VANILLA_SET));

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("tinkering"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.tinkering"))
			.icon(() -> ToolStats.build(ToolType.PICKAXE, List.of(TinkerMaterial.IRON, TinkerMaterial.WOOD, TinkerMaterial.STRING)))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			output.accept(PART_BUILDER);
			output.accept(TOOL_STATION);
			output.accept(PATTERN);
			for (ToolType type : ToolType.values()) {
				for (TinkerMaterial head : TinkerMaterial.heads()) {
					if (head.id.contains("_") && !head.id.startsWith("rock_stone")) {
						continue;
					}
					List<TinkerMaterial> materials = new ArrayList<>();
					for (ToolType.Slot slot : type.parts) {
						materials.add(slot.part().kind == PartType.Kind.HEAD ? head
							: slot.part().kind == PartType.Kind.HANDLE ? TinkerMaterial.WOOD : TinkerMaterial.STRING);
					}
					output.accept(ToolStats.build(type, materials));
				}
			}
			for (PartType part : PartType.values()) {
				for (TinkerMaterial material : TinkerMaterial.values()) {
					if (material.makes(part.kind)) {
						output.accept(ToolPartItem.of(part, material, 1));
					}
				}
			}
		});
		TraitEffects.init();
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
	}

	private static Block station(String id, StationBlock.Kind kind) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new StationBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion().setId(key), kind));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
