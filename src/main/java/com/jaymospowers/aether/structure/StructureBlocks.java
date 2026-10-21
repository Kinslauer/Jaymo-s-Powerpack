package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The blocks the Aether II structures bring (docs/aether.md, "Aether II structures"): the three vases, the animal stash, the outpost
 * and ambrosium campfires, the prayer candle, guardian wood and the faded holystone pieces. Art, data and names come from
 * {@code tools/gen_aether_structures.py}.
 */
public final class StructureBlocks {
	// ---- tuning ----
	/** Vases shatter at a touch. */
	public static final float VASE_HARDNESS = 0.3F;
	/** Guardian wood: hardness of logs and planks, of the roots, and the light of the lamp and of lucent roots. */
	public static final float GUARDIAN_HARDNESS = 2.0F, ROOTS_HARDNESS = 1.5F;
	public static final int LAMP_LIGHT = 15, LUCENT_LIGHT = 9;
	/** Faded holystone's hardness and blast (as the bricks). */
	public static final float FADED_HARDNESS = 1.5F, FADED_BLAST = 6.0F;
	/** The animal stash: hardness. */
	public static final float STASH_HARDNESS = 0.8F;

	public static Block HOLYSTONE_VASE, VERADEXIAN_VASE, BREXALLEN_VASE, ANIMAL_STASH, OUTPOST_CAMPFIRE, AMBROSIUM_CAMPFIRE, PRAYER_CANDLE;
	public static Block GUARDIAN_LOG, GUARDIAN_WOOD, STRIPPED_GUARDIAN_LOG, STRIPPED_GUARDIAN_WOOD, GUARDIAN_PLANKS, GUARDIAN_ROOTS,
		LUCENT_GUARDIAN_ROOTS, GUARDIAN_LAMP, INFECTED_GUARDIAN_LOG, INFECTED_GUARDIAN_WOOD, GUARDIAN_STAIRS, GUARDIAN_SLAB;
	public static Block FADED_HOLYSTONE_PILLAR, FADED_HOLYSTONE_KEYSTONE;
	public static BlockEntityType<VaseEntity> VASE_ENTITY;
	public static BlockEntityType<StashEntity> STASH_ENTITY;

	/** Every block id registered here, by id, in order. */
	public static final Map<String, Block> ALL = new LinkedHashMap<>();
	private static final List<Block> TAB = new ArrayList<>();

	private StructureBlocks() {
	}

	private static BlockBehaviour.Properties wood(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(GUARDIAN_HARDNESS).sound(SoundType.WOOD);
	}

	private static BlockBehaviour.Properties stone(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(FADED_HARDNESS, FADED_BLAST).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties vase() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(VASE_HARDNESS).sound(SoundType.DECORATED_POT).noOcclusion()
			.pushReaction(PushReaction.POPPED);
	}

	static void init() {
		HOLYSTONE_VASE = add("holystone_vase", VaseBlock::new, vase());
		VERADEXIAN_VASE = add("veradexian_vase", VaseBlock::new, vase());
		BREXALLEN_VASE = add("brexallen_vase", VaseBlock::new, vase());
		ANIMAL_STASH = add("animal_stash", AnimalStashBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(STASH_HARDNESS)
			.sound(SoundType.WOOD).noOcclusion());
		OUTPOST_CAMPFIRE = add("outpost_campfire", OutpostCampfireBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(2.0F)
			.sound(SoundType.WOOD).noOcclusion().lightLevel(s -> s.getValue(OutpostCampfireBlock.LIT) ? OutpostCampfireBlock.LIGHT : 0).noLootTable());
		AMBROSIUM_CAMPFIRE = add("ambrosium_campfire", AmbrosiumCampfireBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(2.0F)
			.sound(SoundType.WOOD).noOcclusion().lightLevel(s -> s.getValue(AmbrosiumCampfireBlock.LIT) ? AmbrosiumCampfireBlock.LIGHT : 0));
		PRAYER_CANDLE = add("prayer_candle", PrayerCandleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).instabreak()
			.sound(SoundType.CANDLE).noOcclusion().noCollision().pushReaction(PushReaction.POPPED)
			.lightLevel(s -> s.getValue(PrayerCandleBlock.LIT) ? PrayerCandleBlock.LIGHT : 0));

		GUARDIAN_LOG = add("guardian_log", RotatedPillarBlock::new, wood(MapColor.COLOR_BROWN));
		GUARDIAN_WOOD = add("guardian_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_BROWN));
		STRIPPED_GUARDIAN_LOG = add("stripped_guardian_log", RotatedPillarBlock::new, wood(MapColor.COLOR_ORANGE));
		STRIPPED_GUARDIAN_WOOD = add("stripped_guardian_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_ORANGE));
		GUARDIAN_PLANKS = add("guardian_planks", Block::new, wood(MapColor.COLOR_ORANGE));
		GUARDIAN_STAIRS = add("guardian_stairs", p -> new StairBlock(GUARDIAN_PLANKS.defaultBlockState(), p), wood(MapColor.COLOR_ORANGE));
		GUARDIAN_SLAB = add("guardian_slab", SlabBlock::new, wood(MapColor.COLOR_ORANGE));
		INFECTED_GUARDIAN_LOG = add("infected_guardian_log", RotatedPillarBlock::new, wood(MapColor.COLOR_GREEN));
		INFECTED_GUARDIAN_WOOD = add("infected_guardian_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_GREEN));
		GUARDIAN_ROOTS = add("guardian_roots", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(ROOTS_HARDNESS)
			.sound(SoundType.MANGROVE_ROOTS));
		LUCENT_GUARDIAN_ROOTS = add("lucent_guardian_roots", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(ROOTS_HARDNESS)
			.sound(SoundType.MANGROVE_ROOTS).lightLevel(s -> LUCENT_LIGHT));
		GUARDIAN_LAMP = add("guardian_lamp", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.GLASS)
			.lightLevel(s -> LAMP_LIGHT));

		FADED_HOLYSTONE_PILLAR = add("faded_holystone_pillar", RotatedPillarBlock::new, stone(MapColor.WOOL));
		FADED_HOLYSTONE_KEYSTONE = add("faded_holystone_keystone", Block::new, stone(MapColor.WOOL));


		VASE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("aether_vase"),
			new BlockEntityType<>(VaseEntity::new, Set.of(HOLYSTONE_VASE, VERADEXIAN_VASE, BREXALLEN_VASE)));
		STASH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("animal_stash"),
			new BlockEntityType<>(StashEntity::new, Set.of(ANIMAL_STASH)));

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> TAB.forEach(output::accept));
	}

	private static Block add(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALL.put(id, block);
		TAB.add(block);
		return block;
	}
}
