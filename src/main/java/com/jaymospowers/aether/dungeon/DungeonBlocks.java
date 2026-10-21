package com.jaymospowers.aether.dungeon;

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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Sentry Ruins' blocks (docs/aether.md, "Sentry Ruins and the Slider"): the sentry stone family with its sentry
 * current, the spawner, the trap, the crate, the locked blocks and the two doorways. Ids come from
 * {@code tools/gen_aether_dungeon.py}, which writes every texture, model, blockstate, loot table, recipe, tag and name.
 */
public final class DungeonBlocks {
	// ---- tuning ----
	/** Sentry stone: hardness and blast resistance (the notes' 4/6). */
	public static final float SENTRY_HARDNESS = 4.0F, SENTRY_BLAST = 6.0F;
	/** Light of lit sentry stone, of the lightstone (always on), and of a lit spawner. */
	public static final int SENTRY_LIGHT = 6, LIGHTSTONE_LIGHT = 11, SPAWNER_LIGHT = 8;
	/** A crate survives every explosion (the detonation sentries' and the golem's included); mined by hand it is this hard. */
	public static final float CRATE_HARDNESS = 4.0F, CRATE_BLAST = 3600000.0F;
	/** The locked blocks and the doorways cannot be broken, however hard it is hit. */
	public static final float UNBREAKABLE = -1.0F, UNBREAKABLE_BLAST = 3600000.0F;

	/** Blocks the Slider cannot break and that wake nothing (locked blocks, both doorways). */
	public static final TagKey<Block> SLIDER_IMMUNE = TagKey.create(Registries.BLOCK, JaymosPowers.id("slider_immune"));
	/** Blocks that pass sentry current on (the sentry stone with a LIT state, and the spawner). */
	public static final TagKey<Block> CARRIES_SENTRY_CURRENT = TagKey.create(Registries.BLOCK, JaymosPowers.id("carries_sentry_current"));

	public static Block SENTRY_BRICKS, SENTRY_BASE_BRICKS, SENTRY_CAPSTONE_BRICKS, SENTRY_FLAGSTONES, SENTRY_TILE, SENTRY_LIGHTSTONE, SENTRY_PILLAR;
	public static Block SENTRY_BRICK_STAIRS, SENTRY_BRICK_SLAB, SENTRY_BRICK_WALL, SENTRY_BRICK_BUTTON;
	public static Block SENTRY_SPAWNER, SENTRY_TRAP, SENTRY_CRATE;
	public static Block LOCKED_UNDERSHALE, LOCKED_UNDERSHALE_BRICKS, LOCKED_SENTRY_BRICKS, LOCKED_SENTRY_FLAGSTONES, LOCKED_SENTRY_LIGHTSTONE;
	public static Block BOSS_DOORWAY, TREASURE_DOORWAY;
	public static BlockEntityType<SentrySpawnerEntity> SPAWNER_ENTITY;
	public static BlockEntityType<SentryCrateEntity> CRATE_ENTITY;

	/** Every block id this track registered, by id, in order. */
	public static final Map<String, Block> ALL = new LinkedHashMap<>();
	private static final List<Block> TAB = new ArrayList<>();

	private DungeonBlocks() {
	}

	private static BlockBehaviour.Properties stone(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(SENTRY_HARDNESS, SENTRY_BLAST)
			.requiresCorrectToolForDrops();
	}

	/** Sentry stone that glows when lit. */
	private static BlockBehaviour.Properties lit(int light) {
		return stone(MapColor.COLOR_ORANGE).lightLevel(state -> state.getValue(SentryBlock.LIT) ? light : 0);
	}

	private static BlockBehaviour.Properties locked(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(UNBREAKABLE, UNBREAKABLE_BLAST)
			.noLootTable().pushReaction(PushReaction.IMMOVEABLE).isValidSpawn(Blocks::never);
	}

	static void init() {
		SENTRY_BRICKS = add("sentry_bricks", SentryBlock::new, lit(SENTRY_LIGHT));
		SENTRY_BASE_BRICKS = add("sentry_base_bricks", SentryBlock::new, lit(SENTRY_LIGHT));
		SENTRY_CAPSTONE_BRICKS = add("sentry_capstone_bricks", Block::new, stone(MapColor.COLOR_ORANGE));
		SENTRY_FLAGSTONES = add("sentry_flagstones", SentryBlock::new, lit(SENTRY_LIGHT));
		SENTRY_TILE = add("sentry_tile", SentryBlock::new, lit(SENTRY_LIGHT));
		SENTRY_LIGHTSTONE = add("sentry_lightstone", Block::new, stone(MapColor.COLOR_YELLOW).lightLevel(state -> LIGHTSTONE_LIGHT).sound(SoundType.GLASS));
		SENTRY_PILLAR = add("sentry_pillar", RotatedPillarBlock::new, stone(MapColor.COLOR_ORANGE));
		SENTRY_BRICK_STAIRS = add("sentry_brick_stairs", p -> new StairBlock(SENTRY_BRICKS.defaultBlockState(), p), stone(MapColor.COLOR_ORANGE));
		SENTRY_BRICK_SLAB = add("sentry_brick_slab", SlabBlock::new, stone(MapColor.COLOR_ORANGE));
		SENTRY_BRICK_WALL = add("sentry_brick_wall", WallBlock::new, stone(MapColor.COLOR_ORANGE).forceSolidOn());
		SENTRY_BRICK_BUTTON = add("sentry_brick_button", p -> new ButtonBlock(BlockSetType.STONE, 20, p), BlockBehaviour.Properties.of().noCollision()
			.strength(0.5F).pushReaction(PushReaction.POPPED));

		SENTRY_SPAWNER = add("sentry_spawner", SentrySpawnerBlock::new, stone(MapColor.COLOR_ORANGE).sound(SoundType.METAL)
			.lightLevel(state -> state.getValue(SentryBlock.LIT) ? SPAWNER_LIGHT : 0).noOcclusion());
		SENTRY_TRAP = add("sentry_trap", SentryTrapBlock::new, stone(MapColor.COLOR_ORANGE));
		SENTRY_CRATE = add("sentry_crate", SentryCrateBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.instrument(NoteBlockInstrument.BASS).strength(CRATE_HARDNESS, CRATE_BLAST).sound(SoundType.METAL).requiresCorrectToolForDrops());

		LOCKED_UNDERSHALE = add("locked_undershale", LockedBlock::new, locked(MapColor.COLOR_GRAY));
		LOCKED_UNDERSHALE_BRICKS = add("locked_undershale_bricks", LockedBlock::new, locked(MapColor.COLOR_GRAY));
		LOCKED_SENTRY_BRICKS = add("locked_sentry_bricks", LockedBlock::new, locked(MapColor.COLOR_ORANGE));
		LOCKED_SENTRY_FLAGSTONES = add("locked_sentry_flagstones", LockedBlock::new, locked(MapColor.COLOR_ORANGE));
		LOCKED_SENTRY_LIGHTSTONE = add("locked_sentry_lightstone", LockedBlock::new, locked(MapColor.COLOR_YELLOW).lightLevel(state -> LIGHTSTONE_LIGHT));
		BOSS_DOORWAY = add("boss_doorway", BossDoorwayBlock::new, locked(MapColor.COLOR_ORANGE).noOcclusion()
			.isViewBlocking((state, level, pos, box) -> state.getValue(BossDoorwayBlock.CLOSED))
			.isSuffocating((state, level, pos) -> state.getValue(BossDoorwayBlock.CLOSED))
			.isRedstoneConductor(Blocks::never));
		TREASURE_DOORWAY = add("treasure_doorway", TreasureDoorwayBlock::new, locked(MapColor.GOLD));

		SPAWNER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("sentry_spawner"),
			new BlockEntityType<>(SentrySpawnerEntity::new, Set.of(SENTRY_SPAWNER)));
		CRATE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("sentry_crate"),
			new BlockEntityType<>(SentryCrateEntity::new, Set.of(SENTRY_CRATE)));

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> TAB.forEach(output::accept));
	}

	private static Block add(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALL.put(id, block);
		TAB.add(block);
		return block;
	}
}
