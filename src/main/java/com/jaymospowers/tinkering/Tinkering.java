package com.jaymospowers.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smeltery.CastItem;
import com.jaymospowers.tinkering.smeltery.CastingBlock;
import com.jaymospowers.tinkering.smeltery.CastingEntity;
import com.jaymospowers.tinkering.smeltery.DrainBlock;
import com.jaymospowers.tinkering.smeltery.DrainEntity;
import com.jaymospowers.tinkering.smeltery.FaucetBlock;
import com.jaymospowers.tinkering.smeltery.FaucetEntity;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smeltery.SmelteryBlock;
import com.jaymospowers.tinkering.smeltery.SmelteryEntity;
import com.jaymospowers.tinkering.smeltery.SmelteryMenu;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Jaymo's Tinkering: tools built from parts, after Tinkers' Construct (SlimeKnights, MIT; its textures are reused, see
 * textures/item/tinkering/license_tinkers_construct.txt). Shape parts from patterns and materials at the Part Builder,
 * put them together at the Tool Station; each material brings its own stats and trait. Tools break instead of vanishing
 * and are repaired with their head's material. Assets, recipes and lang come from tools/tinkering_assets.py.
 * <p>
 * Stage 2 of 3: the smeltery melts metal (ores doubled), alloys it, and faucets pour it into casts (package
 * {@code smeltery}); metal parts are cast, not shaped. Modifiers and tool levels come with it. The pack tie-ins come next.
 */
public final class Tinkering {
	/** A tool's materials, in its part order. */
	public static DataComponentType<List<String>> TOOL_MATERIALS;
	/** A part's material. */
	public static DataComponentType<String> PART_MATERIAL;
	/** A tool's modifiers (id to level), and the xp it has earned toward its next level. */
	public static DataComponentType<Map<String, Integer>> TOOL_MODIFIERS;
	public static DataComponentType<Integer> TOOL_XP;

	public static Item PATTERN;
	public static Block PART_BUILDER, TOOL_STATION;
	public static BlockEntityType<StationEntity> STATION_ENTITY;
	public static final Map<PartType, Item> PARTS = new EnumMap<>(PartType.class);
	public static final Map<ToolType, Item> TOOLS = new EnumMap<>(ToolType.class);
	public static MenuType<PartBuilderMenu> PART_BUILDER_MENU;
	public static MenuType<ToolStationMenu> TOOL_STATION_MENU;
	/** The smeltery: seared bricks and glass, the controller, drains, faucets and casting. */
	public static Block GROUT, SEARED_BRICKS, SEARED_GLASS, SMELTERY, DRAIN, FAUCET, CASTING_TABLE, CASTING_BASIN;
	public static Item SEARED_BRICK;
	public static BlockEntityType<SmelteryEntity> SMELTERY_ENTITY;
	public static BlockEntityType<DrainEntity> DRAIN_ENTITY;
	public static BlockEntityType<FaucetEntity> FAUCET_ENTITY;
	public static BlockEntityType<CastingEntity> CASTING_ENTITY;
	public static ExtendedMenuType<SmelteryMenu, BlockPos> SMELTERY_MENU;
	public static final Map<CastItem.Shape, Item> CASTS = new EnumMap<>(CastItem.Shape.class);
	/** Ingots of the alloys (and the metals vanilla has no ingot for). */
	public static final Map<Molten, Item> ALLOY_INGOTS = new EnumMap<>(Molten.class);
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
		TOOL_MODIFIERS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("tool_modifiers"),
			DataComponentType.<Map<String, Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
				.networkSynchronized(ByteBufCodecs.map(java.util.LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)).build());
		TOOL_XP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("tool_xp"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

		PATTERN = item("pattern", Item::new, p -> p.component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("tooltip.jaymospowers.tinkering.pattern").withStyle(ChatFormatting.GRAY)))));
		for (PartType part : PartType.values()) {
			PARTS.put(part, item(part.id, p -> new ToolPartItem(p, part), Function.identity()));
		}
		for (ToolType type : ToolType.values()) {
			TOOLS.put(type, item(type.itemId(), p -> new TinkerToolItem(p, type), p -> {
				p = p.durability(100);
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
		STATION_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("tinker_station"),
			new BlockEntityType<>(StationEntity::new, Set.of(PART_BUILDER, TOOL_STATION)));
		PART_BUILDER_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("part_builder"),
			new MenuType<>(PartBuilderMenu::new, FeatureFlags.VANILLA_SET));
		TOOL_STATION_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("tool_station"),
			new MenuType<>(ToolStationMenu::new, FeatureFlags.VANILLA_SET));

		registerSmeltery();

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("tinkering"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.tinkering"))
			.icon(() -> ToolStats.build(ToolType.PICKAXE, List.of(TinkerMaterial.IRON, TinkerMaterial.WOOD, TinkerMaterial.STRING)))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			output.accept(PART_BUILDER);
			output.accept(TOOL_STATION);
			output.accept(PATTERN);
			for (Block block : List.of(GROUT, SEARED_BRICKS, SEARED_GLASS, SMELTERY, DRAIN, FAUCET, CASTING_TABLE, CASTING_BASIN)) {
				output.accept(block);
			}
			output.accept(SEARED_BRICK);
			ALLOY_INGOTS.values().forEach(output::accept);
			CASTS.values().forEach(output::accept);
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

	private static void registerSmeltery() {
		// a fresh one for each block (properties are changed in place)
		Supplier<BlockBehaviour.Properties> seared = () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY)
			.strength(3.0F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
		GROUT = block("grout", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(0.6F).sound(SoundType.GRAVEL));
		SEARED_BRICKS = block("seared_bricks", Block::new, seared.get());
		SEARED_GLASS = block("seared_glass", TransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY)
			.strength(3.0F, 9.0F).sound(SoundType.GLASS).noOcclusion().requiresCorrectToolForDrops()
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
		SMELTERY = block("smeltery_controller", SmelteryBlock::new, seared.get().lightLevel(state -> state.getValue(SmelteryBlock.LIT) ? 13 : 0));
		DRAIN = block("seared_drain", DrainBlock::new, seared.get());
		FAUCET = block("seared_faucet", FaucetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).strength(1.5F)
			.sound(SoundType.METAL).noOcclusion());
		CASTING_TABLE = block("casting_table", p -> new CastingBlock(p, false), seared.get().noOcclusion());
		CASTING_BASIN = block("casting_basin", p -> new CastingBlock(p, true), seared.get().noOcclusion());
		SEARED_BRICK = item("seared_brick", Item::new, Function.identity());
		TinkerMaterial.own(TinkerMaterial.SEARED_STONE, 1, SEARED_BRICK);
		TinkerMaterial.own(TinkerMaterial.SEARED_STONE, 4, SEARED_BRICKS.asItem());
		for (Molten alloy : List.of(Molten.ROSE_GOLD, Molten.AMETHYST_BRONZE, Molten.PIG_IRON, Molten.SLIMESTEEL)) {
			Item ingot = item(alloy.id + "_ingot", Item::new, Function.identity());
			ALLOY_INGOTS.put(alloy, ingot);
			TinkerMaterial.own(alloy.material, 1, ingot);
		}
		for (CastItem.Shape shape : CastItem.Shape.values()) {
			CASTS.put(shape, item(shape.id + "_cast", p -> new CastItem(p, shape), Function.identity()));
		}
		SMELTERY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("smeltery"),
			new BlockEntityType<>(SmelteryEntity::new, Set.of(SMELTERY)));
		DRAIN_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("seared_drain"),
			new BlockEntityType<>(DrainEntity::new, Set.of(DRAIN)));
		FAUCET_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("seared_faucet"),
			new BlockEntityType<>(FaucetEntity::new, Set.of(FAUCET)));
		CASTING_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("casting"),
			new BlockEntityType<>(CastingEntity::new, Set.of(CASTING_TABLE, CASTING_BASIN)));
		SMELTERY_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("smeltery"),
			new ExtendedMenuType<>(SmelteryMenu::new, BlockPos.STREAM_CODEC));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
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
