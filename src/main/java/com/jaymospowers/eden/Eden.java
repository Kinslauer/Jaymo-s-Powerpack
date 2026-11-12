package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherSaplingBlock;
import com.jaymospowers.aether.FloraPlantBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
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
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
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

	/** Hardness of doors and trapdoors; ticks a wooden button stays pressed. */
	public static final float DOOR_HARDNESS = 3.0F;
	public static final int BUTTON_TICKS = 30;
	/** Light of gravilite: the wild shards, the block, the lamp and lantern; hardness of the crystal and of the compressor. */
	public static final int SHARDS_LIGHT = 9, GRAVILITE_LIGHT = 12, GRAVILITE_LAMP_LIGHT = 15;
	public static final float GRAVILITE_HARDNESS = 1.5F, COMPRESSOR_HARDNESS = 3.5F;
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
	/** Light of a violum's bloom, of parignum's flowers and of the glowing mould. */
	public static final int VIOLUM_LIGHT = 6, PARIGNUM_LIGHT = 4, MOLD_LIGHT = 10;
	/** Food and saturation of a limphium leaf, fresh and dried. */
	public static final int LEAF_FOOD = 1, DRIED_LEAF_FOOD = 3;
	public static final float LEAF_SATURATION = 0.1F, DRIED_LEAF_SATURATION = 0.5F;
	public static Block VIOLUM, LONLIX, PARIGNUM, LIMPHIUM, EDEN_VINE, EDEN_MOSS, SYMBIOTIC_MOLD, SYMBIOTIC_MOLD_EMISSIVE;
	public static Item LIMPHIUM_LEAF, DRIED_LIMPHIUM_LEAF;
	/** The diskwing's spawning: weight among the ambient mobs, and how many come together. */
	public static final int DISKWING_WEIGHT = 6, DISKWING_MIN = 1, DISKWING_MAX = 2;
	public static EntityType<Diskwing> DISKWING;
	public static Item DISKWING_SPAWN_EGG;
	/** Hardness of volvox, and the light the dense kind gives. */
	public static final float VOLVOX_HARDNESS = 0.4F;
	public static final int DENSE_VOLVOX_LIGHT = 7;
	public static Block SUNROOT_LOG, SUNROOT_PLANKS, SUNROOT_LEAVES, SUNROOT_SAPLING, VOLVOX, DENSE_VOLVOX;
	public static Block GRAVILITE_SHARDS, GRAVILITE_BLOCK, GRAVILITE_LAMP, GRAVILITE_LANTERN, GRAVITY_COMPRESSOR;
	/** The mycotic lanterns, by colour. */
	public static final Map<DyeColor, Block> LANTERNS = new LinkedHashMap<>();

	private static final List<ItemLike> TAB = new ArrayList<>();

	private Eden() {
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return block(id, factory, properties, BlockItem::new);
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
		BiFunction<Block, Item.Properties, Item> itemFactory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		TAB.add(Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
		return block;
	}

	private static Item food(String id, int nutrition, float saturation) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).food(
			new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), Consumables.defaultFood().build())));
		TAB.add(item);
		return item;
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

	/** Stairs, slab, fence, gate, door, trapdoor, button and pressure plate of a wood, from its planks. */
	private static void shaped(String name, Block planks, MapColor color) {
		BlockBehaviour.Properties board = wood(color).strength(LOG_HARDNESS, PLANKS_BLAST);
		block(name + "_stairs", p -> new StairBlock(planks.defaultBlockState(), p), wood(color).strength(LOG_HARDNESS, PLANKS_BLAST));
		block(name + "_slab", SlabBlock::new, wood(color).strength(LOG_HARDNESS, PLANKS_BLAST));
		block(name + "_fence", FenceBlock::new, wood(color).strength(LOG_HARDNESS, PLANKS_BLAST));
		block(name + "_fence_gate", p -> new FenceGateBlock(WoodType.CRIMSON, p), board.forceSolidOn());
		block(name + "_door", p -> new DoorBlock(BlockSetType.CRIMSON, p), wood(color).strength(DOOR_HARDNESS).noOcclusion()
			.pushReaction(PushReaction.POPPED), DoubleHighBlockItem::new);
		block(name + "_trapdoor", p -> new TrapDoorBlock(BlockSetType.CRIMSON, p), wood(color).strength(DOOR_HARDNESS).noOcclusion()
			.isValidSpawn(Blocks::never));
		block(name + "_button", p -> new ButtonBlock(BlockSetType.CRIMSON, BUTTON_TICKS, p), BlockBehaviour.Properties.of().noCollision()
			.strength(0.5F).sound(SoundType.NETHER_WOOD).pushReaction(PushReaction.POPPED));
		block(name + "_pressure_plate", p -> new PressurePlateBlock(BlockSetType.CRIMSON, p), BlockBehaviour.Properties.of().mapColor(color)
			.forceSolidOn().noCollision().strength(0.5F).sound(SoundType.NETHER_WOOD).pushReaction(PushReaction.POPPED));
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

		// ---- the Aether's share: the sunroot of the golden grove, and the volvox of the high sky ----
		SUNROOT_LOG = block("sunroot_log", RotatedPillarBlock::new, wood(MapColor.COLOR_BROWN).sound(SoundType.WOOD));
		SUNROOT_PLANKS = block("sunroot_planks", Block::new, wood(MapColor.GOLD).sound(SoundType.WOOD).strength(LOG_HARDNESS, PLANKS_BLAST));
		SUNROOT_LEAVES = block("sunroot_leaves", p -> new UntintedParticleLeavesBlock(0.0F, ParticleTypes.SMOKE,
			AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.2F).randomTicks()
			.sound(SoundType.GRASS).noOcclusion().isValidSpawn(Blocks::never).isSuffocating(Blocks::never).isRedstoneConductor(Blocks::never));
		SUNROOT_SAPLING = sapling("sunroot_sapling", "sunroot_tree", MapColor.GOLD);
		VOLVOX = block("volvox_block", HalfTransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
			.strength(VOLVOX_HARDNESS).sound(SoundType.SLIME_BLOCK).noOcclusion().isSuffocating(Blocks::never));
		DENSE_VOLVOX = block("volvox_block_dense", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
			.strength(VOLVOX_HARDNESS).sound(SoundType.SLIME_BLOCK).lightLevel(state -> DENSE_VOLVOX_LIGHT));

		// ---- the shaped wood ----
		shaped("balloon_mushroom", BALLOON_PLANKS, MapColor.COLOR_PINK);
		shaped("pulse_tree", PULSE_PLANKS, MapColor.COLOR_CYAN);
		shaped("brain_tree", BRAIN_PLANKS, MapColor.COLOR_GRAY);
		shaped("sunroot", SUNROOT_PLANKS, MapColor.GOLD);

		// ---- the small growth ----
		VIOLUM = block("violum", TallFlowerBlock::new, plant(MapColor.COLOR_PURPLE).sound(SoundType.GRASS).lightLevel(state -> VIOLUM_LIGHT)
			.offsetType(BlockBehaviour.OffsetType.XZ), DoubleHighBlockItem::new);
		LONLIX = block("lonlix", p -> new FloraPlantBlock(Block.box(2, 0, 2, 14, 13, 14), false, p),
			plant(MapColor.PLANT).sound(SoundType.GRASS).replaceable().offsetType(BlockBehaviour.OffsetType.XZ));
		PARIGNUM = block("parignum", CarpetBlock::new, plant(MapColor.PLANT).sound(SoundType.MOSS_CARPET).lightLevel(state -> PARIGNUM_LIGHT));
		LIMPHIUM = block("limphium", TallFlowerBlock::new, plant(MapColor.PLANT).sound(SoundType.GRASS), DoubleHighBlockItem::new);
		EDEN_VINE = block("eden_vine", p -> new HangingPlantBlock(true, p), plant(MapColor.PLANT).sound(SoundType.VINE));
		EDEN_MOSS = block("eden_moss", CarpetBlock::new, plant(MapColor.COLOR_LIGHT_GRAY).sound(SoundType.MOSS_CARPET));
		SYMBIOTIC_MOLD = block("symbiotic_mold", p -> new HangingPlantBlock(false, p), plant(MapColor.COLOR_GREEN));
		SYMBIOTIC_MOLD_EMISSIVE = block("symbiotic_mold_emissive", p -> new HangingPlantBlock(false, p),
			plant(MapColor.COLOR_YELLOW).lightLevel(state -> MOLD_LIGHT));
		LIMPHIUM_LEAF = food("limphium_leaf", LEAF_FOOD, LEAF_SATURATION);
		DRIED_LIMPHIUM_LEAF = food("dried_limphium_leaf", DRIED_LEAF_FOOD, DRIED_LEAF_SATURATION);

		// ---- the diskwing ----
		ResourceKey<EntityType<?>> diskwing = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("diskwing"));
		DISKWING = Registry.register(BuiltInRegistries.ENTITY_TYPE, diskwing, EntityType.Builder.of(Diskwing::new, MobCategory.AMBIENT)
			.sized(1.4F, 0.4F).eyeHeight(0.2F).clientTrackingRange(10).noLootTable().build(diskwing));
		FabricDefaultAttributeRegistry.register(DISKWING, Diskwing.createAttributes());
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosPowers.id("diskwing_spawn_egg"));
		DISKWING_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(DISKWING)));
		// in open air under the sky, not over water
		SpawnPlacements.register(DISKWING, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> level.getBlockState(pos).isAir() && level.canSeeSky(pos) && level.getFluidState(pos.below()).isEmpty());
		List<ResourceKey<Biome>> skies = new ArrayList<>();
		for (String biome : new String[] {"verdant_plains", "mycotic_forest", "old_mycotic_forest", "pulse_forest", "stone_garden", "brainstorm",
			"lakeside_desert", "wind_valley"}) {
			skies.add(ResourceKey.create(Registries.BIOME, JaymosPowers.id(biome)));
		}
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(skies), MobCategory.AMBIENT, DISKWING, DISKWING_WEIGHT, DISKWING_MIN, DISKWING_MAX);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(DISKWING_SPAWN_EGG));

		// ---- gravilite ----
		GRAVILITE_SHARDS = block("gravilite_shards", p -> new FloraPlantBlock(Block.box(3, 0, 3, 13, 13, 13), true, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollision().strength(0.3F).sound(SoundType.AMETHYST_CLUSTER)
				.lightLevel(state -> SHARDS_LIGHT).pushReaction(PushReaction.POPPED));
		GRAVILITE_BLOCK = block("gravilite_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(GRAVILITE_HARDNESS).sound(SoundType.AMETHYST).requiresCorrectToolForDrops().lightLevel(state -> GRAVILITE_LIGHT));
		GRAVILITE_LAMP = block("gravilite_lamp", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(0.3F).sound(SoundType.GLASS).lightLevel(state -> GRAVILITE_LAMP_LIGHT));
		GRAVILITE_LANTERN = block("gravilite_lantern", LanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.forceSolidOn().strength(GRAVILITE_HARDNESS).sound(SoundType.LANTERN).lightLevel(state -> GRAVILITE_LAMP_LIGHT).noOcclusion()
			.pushReaction(PushReaction.POPPED));
		GRAVITY_COMPRESSOR = block("gravity_compressor", GravityCompressorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(COMPRESSOR_HARDNESS).sound(SoundType.COPPER).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(GravityCompressorBlock.POWER) > 0 ? 7 : 0));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> TAB.forEach(output::accept));
	}
}
