package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherSaplingBlock;
import com.jaymospowers.aether.FloraPlantBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The strange growth of the Verdant World (Andy's picks from Eden Ring, MIT, by paulevs: its art is used under that
 * licence, see {@code tools/eden_ring_license.txt}): the mycotic forests with their balloon mushrooms and lanterns, the
 * pulse forest whose plants glow and dim together, and the stone garden's mossy stone. No portal and no world of its
 * own: these are regions of the Verdant World. Every model, loot table, recipe, tag, lang line and the worldgen data
 * come from {@code tools/gen_eden.py}; the shapes that grow are {@link EdenFeature}.
 */
public final class Eden {
	// ---- tuning ----
	/** Light of a mycotic lantern, a pulse tree's head, its stem and its log. */
	public static final int LANTERN_LIGHT = 15, PULSE_HEAD_LIGHT = 10, PULSE_STEM_LIGHT = 4, PULSE_LOG_LIGHT = 5;
	/** Hardness: mycelium, a balloon cap, a thin stem, logs and planks (blast resistance of planks 3.0), a lantern, mossy stone. */
	public static final float MYCELIUM_HARDNESS = 0.6F, CAP_HARDNESS = 0.3F, STEM_HARDNESS = 0.8F, LOG_HARDNESS = 2.0F, PLANKS_BLAST = 3.0F,
		LANTERN_HARDNESS = 0.5F, STONE_HARDNESS = 1.5F, STONE_BLAST = 6.0F;

	/** Hardness of a brain tree's flesh and of an aquatus head. */
	public static final float BRAIN_HARDNESS = 1.2F, AQUATUS_HARDNESS = 0.6F;

	/** The biome whose ground is sand (the lakeside desert). */
	public static final TagKey<Biome> SAND_GROUND = TagKey.create(Registries.BIOME, JaymosPowers.id("eden_sand_ground"));
	/** The biomes whose turf is mycelium, and the one whose ground is worn stone. */
	public static final TagKey<Biome> MYCELIUM_GROUND = TagKey.create(Registries.BIOME, JaymosPowers.id("eden_mycelium_ground"));
	public static final TagKey<Biome> STONE_GROUND = TagKey.create(Registries.BIOME, JaymosPowers.id("eden_stone_ground"));

	public static Block MYCELIUM, MYCOTIC_GRASS, BALLOON_SMALL, BALLOON_STEM, BALLOON_LOG, BALLOON_CAP, BALLOON_PLANKS;
	public static Block PULSE_TREE, PULSE_LOG, PULSE_PLANKS, PULSE_SAPLING;
	public static Block MOSSY_STONE;
	public static Block BRAIN_LOG, BRAIN_PLANKS, BRAIN_COPPER, BRAIN_IRON, BRAIN_GOLD, COPPER_GRASS, IRON_GRASS, GOLD_GRASS;
	public static Block AQUATUS, AQUATUS_ROOTS, AQUATUS_SAPLING;
	/** The mycotic lanterns, by colour. */
	public static final Map<DyeColor, Block> LANTERNS = new LinkedHashMap<>();

	private static final List<ItemLike> TAB = new ArrayList<>();

	private Eden() {
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		TAB.add(Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
		return block;
	}

	private static BlockBehaviour.Properties wood(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(LOG_HARDNESS).sound(SoundType.NETHER_WOOD);
	}

	private static BlockBehaviour.Properties plant(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).noCollision().instabreak().sound(SoundType.FUNGUS).pushReaction(PushReaction.POPPED);
	}

	private static Block sapling(String id, String tree, MapColor color) {
		ResourceKey<Feature> feature = ResourceKey.create(Registries.FEATURE, JaymosPowers.id(tree));
		return block(id, p -> new AetherSaplingBlock(feature, p), plant(color).randomTicks());
	}

	private static Block brain(String metal, MapColor color) {
		return block("brain_tree_block_" + metal, BrainBlock::new, BlockBehaviour.Properties.of().mapColor(color).strength(BRAIN_HARDNESS)
			.sound(SoundType.NETHER_WART).randomTicks().lightLevel(state -> state.getValue(BrainBlock.ACTIVE) ? BrainBlock.LIT_LIGHT : 0));
	}

	private static Block metalGrass(String id) {
		return block(id, p -> new FloraPlantBlock(Block.box(2, 0, 2, 14, 10, 14), false, p),
			plant(MapColor.PLANT).replaceable().offsetType(BlockBehaviour.OffsetType.XZ));
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("eden"), EdenFeature.CODEC);

		// ---- the mycotic forest ----
		MYCELIUM = block("eden_mycelium", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(MYCELIUM_HARDNESS).sound(SoundType.NYLIUM));
		MYCOTIC_GRASS = block("mycotic_grass", p -> new FloraPlantBlock(Block.box(2, 0, 2, 14, 12, 14), false, p),
			plant(MapColor.COLOR_PINK).replaceable().offsetType(BlockBehaviour.OffsetType.XZ));
		BALLOON_SMALL = sapling("balloon_mushroom_small", "balloon_mushroom", MapColor.COLOR_PINK);
		BALLOON_STEM = block("balloon_mushroom_stem", p -> new ThinStemBlock(4, p),
			wood(MapColor.TERRACOTTA_WHITE).strength(STEM_HARDNESS).noOcclusion());
		BALLOON_LOG = block("balloon_mushroom_log", RotatedPillarBlock::new, wood(MapColor.TERRACOTTA_WHITE));
		BALLOON_CAP = block("balloon_mushroom_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(CAP_HARDNESS).sound(SoundType.WOOL));
		BALLOON_PLANKS = block("balloon_mushroom_planks", Block::new, wood(MapColor.COLOR_PINK).strength(LOG_HARDNESS, PLANKS_BLAST));
		for (DyeColor color : DyeColor.values()) {
			LANTERNS.put(color, block("mycotic_lantern_" + color.getName(), Block::new, BlockBehaviour.Properties.of().mapColor(color)
				.strength(LANTERN_HARDNESS).sound(SoundType.WART_BLOCK).lightLevel(state -> LANTERN_LIGHT)));
		}

		// ---- the pulse forest ----
		PULSE_TREE = block("pulse_tree", PulseTreeBlock::new, wood(MapColor.COLOR_CYAN).strength(STEM_HARDNESS).noOcclusion()
			.lightLevel(state -> state.getValue(PulseTreeBlock.SHAPE) == PulseTreeBlock.Shape.STEM ? PULSE_STEM_LIGHT : PULSE_HEAD_LIGHT)
			.pushReaction(PushReaction.POPPED));
		PULSE_LOG = block("pulse_tree_log", RotatedPillarBlock::new, wood(MapColor.COLOR_PURPLE).lightLevel(state -> PULSE_LOG_LIGHT));
		PULSE_PLANKS = block("pulse_tree_planks", Block::new, wood(MapColor.COLOR_CYAN).strength(LOG_HARDNESS, PLANKS_BLAST));
		PULSE_SAPLING = sapling("pulse_tree_sapling", "pulse_tree", MapColor.COLOR_CYAN);

		// ---- the stone garden ----
		MOSSY_STONE = block("eden_mossy_stone", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(STONE_HARDNESS, STONE_BLAST));

		// ---- the brainstorm ----
		BRAIN_LOG = block("brain_tree_log", RotatedPillarBlock::new, wood(MapColor.COLOR_GRAY));
		BRAIN_PLANKS = block("brain_tree_planks", Block::new, wood(MapColor.COLOR_GRAY).strength(LOG_HARDNESS, PLANKS_BLAST));
		BRAIN_COPPER = brain("copper", MapColor.COLOR_ORANGE);
		BRAIN_IRON = brain("iron", MapColor.TERRACOTTA_WHITE);
		BRAIN_GOLD = brain("gold", MapColor.GOLD);
		COPPER_GRASS = metalGrass("copper_grass");
		IRON_GRASS = metalGrass("iron_grass");
		GOLD_GRASS = metalGrass("gold_grass");

		// ---- the lakeside desert ----
		AQUATUS = block("aquatus_block", AquatusBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(AQUATUS_HARDNESS)
			.sound(SoundType.WET_GRASS).randomTicks());
		AQUATUS_ROOTS = block("aquatus_roots", p -> new ThinStemBlock(5, p), wood(MapColor.COLOR_BROWN).strength(STEM_HARDNESS).noOcclusion());
		AQUATUS_SAPLING = sapling("aquatus_sapling", "aquatus", MapColor.COLOR_CYAN);

		EdenWind.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> TAB.forEach(output::accept));
	}
}
