package com.jaymospowers.tinkering.coke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.ClayBucketItem;
import com.jaymospowers.ground.StoneAge;
import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

/**
 * Andy's GT New Horizons picks, patch 4 (docs/GTNH-Plan.md): coke and steel.
 * <ul>
 * <li>The <b>coke oven</b> ({@link CokeOvenEntity}): coke oven bricks (clay and sand pressed in a brick form, then fired)
 * built as a hollow 3x3x3 with its mouth in one wall. It bakes logs to charcoal and coal to coal coke with no fuel,
 * sweating creosote into its tank as it goes; a full tank stops it.</li>
 * <li><b>Creosote</b> ({@link CreosoteFluid}): drained into a bucket or clay bucket; it burns in a furnace, soaks rags for
 * torches and planks into treated planks, which never weather (block tag {@code jaymospowers:weather_proof}, which Jaymo's
 * Kingdoms' Weathering skips).</li>
 * <li>The <b>charcoal pit</b> ({@link CharcoalPit}): a heap of logs buried in earth and lit through one gap smoulders for
 * days into charred logs, which break into charcoal.</li>
 * <li>The <b>brick blast furnace</b> ({@link BlastFurnaceEntity}): firebricks (clay and flint dust, formed and fired) in a
 * hollow 3x4x3 tower; iron and coal coke slowly become steel, and ash.</li>
 * </ul>
 * The coke oven, the pit and the blast furnace are lit block entities, so Jaymo's Kitchen's soot counts them; the pit and
 * the blast furnace are heavy (block tag {@code jaymoskitchen:heavy_soot}). Assets, data and lang: tools/gen_coke_and_steel.py.
 */
public final class CokeAndSteel {
	/** Blocks that never weather or rot (Jaymo's Kingdoms' Weathering reads this tag by its id). */
	public static final TagKey<Block> WEATHER_PROOF = TagKey.create(Registries.BLOCK, JaymosPowers.id("weather_proof"));

	public static Item UNFIRED_COKE_OVEN_BRICK, COKE_OVEN_BRICK, UNFIRED_FIREBRICK, FIREBRICK, COAL_COKE, CREOSOTE_BUCKET,
		CLAY_CREOSOTE_BUCKET, CREOSOTE_RAG, ASH;
	public static Block COKE_OVEN_BRICKS, COKE_OVEN, FIREBRICKS, BLAST_FURNACE, COAL_COKE_BLOCK, CREOSOTE_BLOCK, TREATED_PLANKS,
		SMOULDERING_LOG, CHARRED_LOG;
	public static FlowingFluid CREOSOTE, FLOWING_CREOSOTE;
	public static BlockEntityType<CokeOvenEntity> COKE_OVEN_ENTITY;
	public static BlockEntityType<BlastFurnaceEntity> BLAST_FURNACE_ENTITY;
	public static BlockEntityType<SmoulderingLogEntity> SMOULDERING_LOG_ENTITY;

	private CokeAndSteel() {
	}

	/** After {@link StoneAge#init} (the clay bucket) and {@link Kiln#register}. */
	public static void init() {
		CREOSOTE = Registry.register(BuiltInRegistries.FLUID, JaymosPowers.id("creosote"), new CreosoteFluid.Source());
		FLOWING_CREOSOTE = Registry.register(BuiltInRegistries.FLUID, JaymosPowers.id("flowing_creosote"), new CreosoteFluid.Flowing());
		CREOSOTE_BLOCK = blockOnly("creosote", p -> new LiquidBlock(CREOSOTE, p), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.POPPED).noLootTable().liquid().sound(SoundType.EMPTY));
		CREOSOTE_BUCKET = item("creosote_bucket", p -> new BucketItem(CREOSOTE, p),
			p -> p.craftRemainder(Items.BUCKET).stacksTo(1).cookingFuel(fuel("time_creosote_bucket")));
		CLAY_CREOSOTE_BUCKET = item("clay_creosote_bucket", p -> new ClayBucketItem(CREOSOTE, p), p -> p.durability(StoneAge.CLAY_BUCKET_USES));
		CREOSOTE_RAG = item("creosote_rag", Item::new, Function.identity());
		COAL_COKE = item("coal_coke", Item::new, p -> p.cookingFuel(fuel("time_coal_coke")));
		ASH = item("ash", Item::new, Function.identity());

		UNFIRED_COKE_OVEN_BRICK = item("unfired_coke_oven_brick", Item::new, Function.identity());
		COKE_OVEN_BRICK = item("coke_oven_brick", Item::new, Function.identity());
		UNFIRED_FIREBRICK = item("unfired_firebrick", Item::new, Function.identity());
		FIREBRICK = item("firebrick", Item::new, Function.identity());
		COKE_OVEN_BRICKS = block("coke_oven_bricks", Block::new, bricks(MapColor.TERRACOTTA_BROWN), Function.identity());
		COKE_OVEN = block("coke_oven", CokeOvenBlock::new, bricks(MapColor.TERRACOTTA_BROWN)
			.lightLevel(state -> state.getValue(CokeOvenBlock.LIT) ? 10 : 0), Function.identity());
		FIREBRICKS = block("firebricks", Block::new, bricks(MapColor.SAND).strength(3.0F, 8.0F), Function.identity());
		BLAST_FURNACE = block("brick_blast_furnace", BlastFurnaceBlock::new, bricks(MapColor.SAND).strength(3.0F, 8.0F)
			.lightLevel(state -> state.getValue(BlastFurnaceBlock.LIT) ? 13 : 0), Function.identity());
		COAL_COKE_BLOCK = block("coal_coke_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 6.0F).requiresCorrectToolForDrops(), p -> p.cookingFuel(fuel("time_coal_coke_block")));
		TREATED_PLANKS = block("treated_planks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
			.instrument(NoteBlockInstrument.BASS).strength(2.5F, 4.0F).sound(SoundType.WOOD), Function.identity());
		CHARRED_LOG = block("charred_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instrument(NoteBlockInstrument.BASS).strength(1.0F).sound(SoundType.WOOD), Function.identity());
		SMOULDERING_LOG = blockOnly("smouldering_log", SmoulderingLogBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instrument(NoteBlockInstrument.BASS).strength(1.0F).sound(SoundType.WOOD).lightLevel(state -> 6));

		COKE_OVEN_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("coke_oven"),
			new BlockEntityType<>(CokeOvenEntity::new, Set.of(COKE_OVEN)));
		BLAST_FURNACE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("brick_blast_furnace"),
			new BlockEntityType<>(BlastFurnaceEntity::new, Set.of(BLAST_FURNACE)));
		SMOULDERING_LOG_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("smouldering_log"),
			new BlockEntityType<>(SmoulderingLogEntity::new, Set.of(SMOULDERING_LOG)));

		Kiln.add(new Kiln.Recipe(s -> s.is(UNFIRED_COKE_OVEN_BRICK), 1, COKE_OVEN_BRICK, 0));
		Kiln.add(new Kiln.Recipe(s -> s.is(UNFIRED_FIREBRICK), 1, FIREBRICK, 0));
		CokeOvenEntity.bakes();
		CharcoalPit.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(UNFIRED_COKE_OVEN_BRICK, COKE_OVEN_BRICK, UNFIRED_FIREBRICK, FIREBRICK, COAL_COKE, CREOSOTE_RAG, ASH)) {
				output.accept(item);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			for (Block block : List.of(COKE_OVEN_BRICKS, FIREBRICKS, COAL_COKE_BLOCK, TREATED_PLANKS, CHARRED_LOG)) {
				output.accept(block);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(COKE_OVEN);
			output.accept(BLAST_FURNACE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(CREOSOTE_BUCKET);
			output.accept(CLAY_CREOSOTE_BUCKET);
		});
	}

	/** A burn time in data/jaymospowers/context_int_provider/cooking/ (written by the generator, like vanilla's). */
	private static ResourceKey<ContextIntProvider> fuel(String name) {
		return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, JaymosPowers.id("cooking/" + name));
	}

	private static BlockBehaviour.Properties bricks(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F)
			.requiresCorrectToolForDrops();
	}

	private static Item item(String id, Function<Item.Properties, Item> make, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(props.apply(new Item.Properties().setId(key))));
	}

	private static Block blockOnly(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
		Function<Item.Properties, Item.Properties> itemProps) {
		Block block = blockOnly(id, factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProps.apply(new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
		return block;
	}
}
