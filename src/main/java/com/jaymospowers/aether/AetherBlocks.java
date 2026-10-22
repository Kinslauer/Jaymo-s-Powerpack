package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Aether's blocks (see docs/aether.md): holystone and the sky soils, the three aerclouds, the ores that float or
 * freeze, skyroot and golden oak, the crystal trees' leaves, the berry bush and the flowers. Each block's own rules
 * live in its own class beside this one; this class only registers them (and their items).
 */
public final class AetherBlocks {
	// ---- tuning ----
	/** Hardness and blast resistance (Aether II's numbers: holystone 1.5/6, bricks 2.0/6.0, ores 3.0, metal blocks 5.0/6.0). */
	public static final float HOLYSTONE_HARDNESS = 1.5F, HOLYSTONE_BLAST = 6.0F, BRICKS_HARDNESS = 2.0F, BRICKS_BLAST = 6.0F;
	public static final float ICESTONE_HARDNESS = 0.5F, ICESTONE_BLAST = 0.5F;
	public static final float ORE_HARDNESS = 3.0F, ORE_BLAST = 3.0F, METAL_HARDNESS = 5.0F, METAL_BLAST = 6.0F;
	/** The other stones (hardness, blast): undershale 3.0/6, agiosite 1.5/6, ichorite 4.0/6, ferrosite 1.5/6; their ores 4.5/3.0 when in undershale. */
	public static final float UNDERSHALE_HARDNESS = 3.0F, AGIOSITE_HARDNESS = 1.5F, ICHORITE_HARDNESS = 4.0F, FERROSITE_HARDNESS = 1.5F, STONE_BLAST = 6.0F;
	public static final float UNDERSHALE_ORE_HARDNESS = 4.5F;
	/** Hardness of the soils (silt, sands, mud 0.5; path 0.65; farmland 0.6), the ices (0.5) and glass (scatterglass 0.2, crude 1.5/6). */
	public static final float SAND_HARDNESS = 0.5F, PATH_HARDNESS = 0.65F, FARMLAND_HARDNESS = 0.6F, ICE_HARDNESS = 0.5F, SNOW_HARDNESS = 0.1F;
	public static final float SCATTERGLASS_HARDNESS = 0.2F, CRUDE_SCATTERGLASS_HARDNESS = 1.5F;
	/** Friction of the arctic ices (vanilla ice is 0.98). */
	public static final float ARCTIC_ICE_FRICTION = 0.98F;
	/** Light of an arctic ice crystal, a corrobonite cluster, an arkenium lantern; ambrosium ore's glow is the worldgen's business. */
	public static final int ICE_CRYSTAL_LIGHT = 3, CLUSTER_LIGHT = 4, LANTERN_LIGHT = 15;
	/** Dust colours of the two falling sands. */
	public static final int SILT_DUST = 0x8D9FB8, FERROSITE_SAND_DUST = 0xB96A3D;
	/** Experience (min, max) of holystone quartz ore 2-5 and glint ore 3-5 (arkenium ore gives none). */
	public static final int QUARTZ_XP_MIN = 2, QUARTZ_XP_MAX = 5, GLINT_XP_MIN = 3, GLINT_XP_MAX = 5;
	public static final float SOIL_HARDNESS = 0.2F, QUICKSOIL_HARDNESS = 0.5F, AERCLOUD_HARDNESS = 0.3F;
	/** The friction a quicksoil block gives (1.0 would be no loss at all, vanilla ice is 0.98). */
	public static final float QUICKSOIL_FRICTION = 1.1F;
	/** Light of quicksoil glass, and of an ambrosium torch (a vanilla torch is 14). */
	public static final int QUICKSOIL_GLASS_LIGHT = 11, TORCH_LIGHT = 14;
	/** Share of animate ticks on which golden oak and crystal leaves let a speck drift down (with open air below). */
	public static final float GOLDEN_LEAF_PARTICLES = 0.1F, CRYSTAL_LEAF_PARTICLES = 0.03F;
	/** Particle colours of the drifting leaves. */
	public static final int GOLDEN_LEAF_COLOR = 0xFFD54A, CRYSTAL_LEAF_COLOR = 0xBFF4F8;
	/** Experience (min, max) the ores give: ambrosium 0-2, zanite 3-5. */
	public static final int AMBROSIUM_XP_MIN = 0, AMBROSIUM_XP_MAX = 2, ZANITE_XP_MIN = 3, ZANITE_XP_MAX = 5;

	public static Block HOLYSTONE, MOSSY_HOLYSTONE, HOLYSTONE_BRICKS;
	public static Block AETHER_DIRT, AETHER_GRASS_BLOCK, ENCHANTED_AETHER_GRASS_BLOCK, QUICKSOIL, QUICKSOIL_GLASS, ICESTONE;
	public static Block AMBROSIUM_ORE, ZANITE_ORE, GRAVITITE_ORE, ENCHANTED_GRAVITITE, AMBROSIUM_BLOCK, ZANITE_BLOCK;
	public static Block COLD_AERCLOUD, BLUE_AERCLOUD, GOLDEN_AERCLOUD;
	public static Block SKYROOT_LOG, SKYROOT_WOOD, STRIPPED_SKYROOT_LOG, STRIPPED_SKYROOT_WOOD, SKYROOT_PLANKS, SKYROOT_LEAVES, SKYROOT_SAPLING;
	public static Block GOLDEN_OAK_LOG, GOLDEN_OAK_WOOD, GOLDEN_OAK_LEAVES, GOLDEN_OAK_SAPLING;
	public static Block CRYSTAL_LEAVES, CRYSTAL_FRUIT_LEAVES;
	public static Block BERRY_BUSH, BERRY_BUSH_STEM, WHITE_FLOWER, PURPLE_FLOWER;
	public static Block AMBROSIUM_TORCH, AMBROSIUM_WALL_TORCH;

	// ---- Aether II: stone, soil, ice, ores, clouds, building (track N1) ----
	public static Block UNDERSHALE, AGIOSITE, ICHORITE, FERROSITE, IRRADIATED_HOLYSTONE, CRUDE_SCATTERGLASS, HOLYSTONE_ROCK, POINTED_HOLYSTONE;
	public static Block UNDERSHALE_BRICKS, AGIOSITE_BRICKS, ICESTONE_BRICKS, ICHORITE_BRICKS, FADED_HOLYSTONE_BRICKS;
	public static Block HOLYSTONE_FLAGSTONES, FADED_HOLYSTONE_FLAGSTONES, HOLYSTONE_PILLAR, HOLYSTONE_KEYSTONE, HOLYSTONE_BUTTON, HOLYSTONE_PRESSURE_PLATE;
	public static Block COARSE_AETHER_DIRT, AETHER_DIRT_PATH, AETHER_FARMLAND, SHIMMERING_SILT, FERROSITE_SAND, FERROSITE_MUD;
	public static Block ARCTIC_SNOW_BLOCK, ARCTIC_SNOW, ARCTIC_ICE, ARCTIC_PACKED_ICE, FRAGILE_ARCTIC_ICE, ARCTIC_ICE_CRYSTAL;
	public static Block HOLYSTONE_QUARTZ_ORE, ARKENIUM_ORE, GLINT_ORE, CORROBONITE_ORE, CORROBONITE_CLUSTER;
	public static Block UNDERSHALE_AMBROSIUM_ORE, UNDERSHALE_ZANITE_ORE, UNDERSHALE_ARKENIUM_ORE, UNDERSHALE_GLINT_ORE, UNDERSHALE_GRAVITITE_ORE;
	public static Block ARKENIUM_BLOCK, GLINT_BLOCK, CORROBONITE_BLOCK, GOLDEN_AMBER_BLOCK, GRAVITITE_BLOCK;
	public static Block SCATTERGLASS, SCATTERGLASS_PANE, QUICKSOIL_GLASS_PANE;
	public static Block GREEN_AERCLOUD, PURPLE_AERCLOUD, STORM_AERCLOUD;
	public static Block CLOUDWOOL, CLOUDWOOL_CARPET;
	public static Block ARKENIUM_LANTERN, ARKENIUM_CHAIN, ARKENIUM_BARS;
	/** The stairs, slabs and walls ("holystone_stairs", ...) and the dyed cloudwool and carpets ("red_cloudwool"), by id. */
	public static final Map<String, Block> VARIANTS = new LinkedHashMap<>();
	/** Every block this track registered, in order (the creative tab shows them after the first ones). */
	private static final List<Block> ADDED = new ArrayList<>();

	/** The tree features the saplings grow (written by the dimension's worldgen, not here). */
	public static final ResourceKey<Feature> SKYROOT_TREE = ResourceKey.create(Registries.FEATURE, JaymosPowers.id("skyroot_tree"));
	public static final ResourceKey<Feature> GOLDEN_OAK_TREE = ResourceKey.create(Registries.FEATURE, JaymosPowers.id("golden_oak_tree"));

	private AetherBlocks() {
	}

	/** The blocks of Aether II's stone, soil, ice, ore, cloud and building set, in registration order. */
	public static List<Block> added() {
		return ADDED;
	}

	/** A fresh set of properties each time (they are changed in place). */
	private static BlockBehaviour.Properties stone(MapColor color, float hardness, float blast) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(hardness, blast)
			.requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties plant(SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(sound).pushReaction(PushReaction.POPPED);
	}

	private static BlockBehaviour.Properties sapling() {
		return plant(SoundType.GRASS).randomTicks();
	}

	private static BlockBehaviour.Properties leaves(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
			.isValidSpawn(Blocks::never).isSuffocating(Blocks::never).isRedstoneConductor(Blocks::never);
	}

	private static BlockBehaviour.Properties wood(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F).sound(SoundType.WOOD);
	}

	private static BlockBehaviour.Properties cloud(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(AERCLOUD_HARDNESS).sound(SoundType.WOOL).noOcclusion()
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never);
	}

	static void init() {
		HOLYSTONE = block("holystone", Block::new, stone(MapColor.WOOL, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST));
		MOSSY_HOLYSTONE = block("mossy_holystone", Block::new, stone(MapColor.WOOL, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST));
		HOLYSTONE_BRICKS = block("holystone_bricks", Block::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));

		AETHER_DIRT = block("aether_dirt", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN)
			.strength(SOIL_HARDNESS).sound(SoundType.GRAVEL));
		AETHER_GRASS_BLOCK = block("aether_grass_block", p -> new AetherGrassBlock(true, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.WARPED_WART_BLOCK).strength(SOIL_HARDNESS).sound(SoundType.GRASS).randomTicks());
		ENCHANTED_AETHER_GRASS_BLOCK = block("enchanted_aether_grass_block", p -> new AetherGrassBlock(false, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(SOIL_HARDNESS).sound(SoundType.GRASS).randomTicks());
		QUICKSOIL = block("quicksoil", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(QUICKSOIL_HARDNESS).sound(SoundType.SAND).friction(QUICKSOIL_FRICTION));
		QUICKSOIL_GLASS = block("quicksoil_glass", TransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(SOIL_HARDNESS).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> QUICKSOIL_GLASS_LIGHT)
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
		ICESTONE = block("icestone", IcestoneBlock::new, stone(MapColor.ICE, ICESTONE_HARDNESS, ICESTONE_BLAST)
			.sound(SoundType.AMETHYST).randomTicks());

		AMBROSIUM_ORE = block("ambrosium_ore", p -> new AetherOreBlock(AMBROSIUM_XP_MIN, AMBROSIUM_XP_MAX, p), stone(MapColor.WOOL, ORE_HARDNESS, ORE_BLAST));
		ZANITE_ORE = block("zanite_ore", p -> new AetherOreBlock(ZANITE_XP_MIN, ZANITE_XP_MAX, p), stone(MapColor.WOOL, ORE_HARDNESS, ORE_BLAST));
		GRAVITITE_ORE = block("gravitite_ore", p -> new FloatingBlock(p, false), stone(MapColor.WOOL, ORE_HARDNESS, ORE_BLAST));
		ENCHANTED_GRAVITITE = block("enchanted_gravitite", p -> new FloatingBlock(p, true),
			stone(MapColor.COLOR_PINK, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
		AMBROSIUM_BLOCK = block("ambrosium_block", Block::new, stone(MapColor.COLOR_YELLOW, METAL_HARDNESS, METAL_BLAST));
		ZANITE_BLOCK = block("zanite_block", Block::new, stone(MapColor.COLOR_PURPLE, METAL_HARDNESS, METAL_BLAST));

		COLD_AERCLOUD = block("cold_aercloud", AercloudBlock::new, cloud(MapColor.SNOW));
		BLUE_AERCLOUD = block("blue_aercloud", BlueAercloudBlock::new, cloud(MapColor.COLOR_LIGHT_BLUE));
		GOLDEN_AERCLOUD = block("golden_aercloud", AercloudBlock::new, cloud(MapColor.COLOR_YELLOW));

		SKYROOT_LOG = block("skyroot_log", RotatedPillarBlock::new, wood(MapColor.COLOR_GRAY));
		SKYROOT_WOOD = block("skyroot_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_GRAY));
		STRIPPED_SKYROOT_LOG = block("stripped_skyroot_log", RotatedPillarBlock::new, wood(MapColor.COLOR_LIGHT_GREEN));
		STRIPPED_SKYROOT_WOOD = block("stripped_skyroot_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_LIGHT_GREEN));
		SKYROOT_PLANKS = block("skyroot_planks", Block::new, wood(MapColor.COLOR_LIGHT_GREEN).strength(2.0F, 3.0F));
		SKYROOT_LEAVES = block("skyroot_leaves", p -> new UntintedParticleLeavesBlock(0.0F, ParticleTypes.SMOKE, AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(MapColor.GRASS));
		SKYROOT_SAPLING = block("skyroot_sapling", p -> new AetherSaplingBlock(SKYROOT_TREE, p), sapling());

		GOLDEN_OAK_LOG = block("golden_oak_log", RotatedPillarBlock::new, wood(MapColor.GOLD));
		GOLDEN_OAK_WOOD = block("golden_oak_wood", RotatedPillarBlock::new, wood(MapColor.GOLD));
		GOLDEN_OAK_LEAVES = block("golden_oak_leaves", p -> new UntintedParticleLeavesBlock(GOLDEN_LEAF_PARTICLES,
			new DustParticleOptions(GOLDEN_LEAF_COLOR, 1.0F), AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(MapColor.GOLD));
		GOLDEN_OAK_SAPLING = block("golden_oak_sapling", p -> new AetherSaplingBlock(GOLDEN_OAK_TREE, p), sapling());

		CRYSTAL_LEAVES = block("crystal_leaves", p -> new UntintedParticleLeavesBlock(CRYSTAL_LEAF_PARTICLES,
			new DustParticleOptions(CRYSTAL_LEAF_COLOR, 1.0F), AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(MapColor.DIAMOND));
		CRYSTAL_FRUIT_LEAVES = block("crystal_fruit_leaves", p -> new UntintedParticleLeavesBlock(CRYSTAL_LEAF_PARTICLES,
			new DustParticleOptions(CRYSTAL_LEAF_COLOR, 1.0F), AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(MapColor.DIAMOND));

		BERRY_BUSH = block("berry_bush", BerryBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(SOIL_HARDNESS)
			.noCollision().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
		BERRY_BUSH_STEM = block("berry_bush_stem", BerryBushStemBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(SOIL_HARDNESS)
			.noCollision().randomTicks().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
		WHITE_FLOWER = block("white_flower", AetherFlowerBlock::new, plant(SoundType.GRASS));
		PURPLE_FLOWER = block("purple_flower", AetherFlowerBlock::new, plant(SoundType.GRASS));

		// the torch: a vanilla torch with smoke where the flame was; the wall one gives no item of its own
		ResourceKey<Block> torchKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("ambrosium_torch"));
		AMBROSIUM_TORCH = Registry.register(BuiltInRegistries.BLOCK, torchKey, new TorchBlock(ParticleTypes.SMOKE, torch().setId(torchKey)));
		ResourceKey<Block> wallKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("ambrosium_wall_torch"));
		AMBROSIUM_WALL_TORCH = Registry.register(BuiltInRegistries.BLOCK, wallKey, new WallTorchBlock(ParticleTypes.SMOKE, torch()
			.overrideLootTable(AMBROSIUM_TORCH.getLootTable()).overrideDescription(AMBROSIUM_TORCH.getDescriptionId()).setId(wallKey)));
		ResourceKey<Item> torchItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("ambrosium_torch"));
		Registry.register(BuiltInRegistries.ITEM, torchItem, new StandingAndWallBlockItem(AMBROSIUM_TORCH, AMBROSIUM_WALL_TORCH, Direction.DOWN,
			new Item.Properties().setId(torchItem).useBlockDescriptionPrefix()));

		initStones();
		initSoils();
		initIce();
		initOres();
		initGlassAndClouds();
		initBuilding();
	}

	/** A block of the second set: registered like any, and remembered for the creative tab. */
	private static Block add(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = block(id, factory, properties);
		ADDED.add(block);
		return block;
	}

	/** Stairs, slab and wall of {@code base} (ids {@code <id>_stairs}, {@code _slab}, {@code _wall}), as hard as it is. */
	private static void family(String id, Block base, MapColor color, float hardness, float blast) {
		VARIANTS.put(id + "_stairs", add(id + "_stairs", p -> new StairBlock(base.defaultBlockState(), p), stone(color, hardness, blast)));
		VARIANTS.put(id + "_slab", add(id + "_slab", SlabBlock::new, stone(color, hardness, blast)));
		VARIANTS.put(id + "_wall", add(id + "_wall", WallBlock::new, stone(color, hardness, blast).forceSolidOn()));
	}

	private static void initStones() {
		UNDERSHALE = add("undershale", Block::new, stone(MapColor.COLOR_GRAY, UNDERSHALE_HARDNESS, STONE_BLAST));
		AGIOSITE = add("agiosite", Block::new, stone(MapColor.COLOR_PURPLE, AGIOSITE_HARDNESS, STONE_BLAST));
		ICHORITE = add("ichorite", Block::new, stone(MapColor.COLOR_BROWN, ICHORITE_HARDNESS, STONE_BLAST));
		FERROSITE = add("ferrosite", Block::new, stone(MapColor.TERRACOTTA_GRAY, FERROSITE_HARDNESS, STONE_BLAST));
		IRRADIATED_HOLYSTONE = add("irradiated_holystone", IrradiatedHolystoneBlock::new, stone(MapColor.COLOR_LIGHT_GREEN, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST));
		CRUDE_SCATTERGLASS = add("crude_scatterglass", TransparentBlock::new, stone(MapColor.COLOR_CYAN, CRUDE_SCATTERGLASS_HARDNESS, STONE_BLAST)
			.sound(SoundType.GLASS).noOcclusion().isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
		HOLYSTONE_ROCK = add("holystone_rock", HolystoneRockBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).noCollision()
			.instabreak().sound(SoundType.STONE).pushReaction(PushReaction.POPPED));
		POINTED_HOLYSTONE = add("pointed_holystone", PointedHolystoneBlock::new, stone(MapColor.WOOL, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST)
			.noOcclusion().pushReaction(PushReaction.POPPED));

		UNDERSHALE_BRICKS = add("undershale_bricks", Block::new, stone(MapColor.COLOR_GRAY, UNDERSHALE_HARDNESS, STONE_BLAST));
		AGIOSITE_BRICKS = add("agiosite_bricks", Block::new, stone(MapColor.COLOR_PURPLE, AGIOSITE_HARDNESS, STONE_BLAST));
		ICESTONE_BRICKS = add("icestone_bricks", Block::new, stone(MapColor.ICE, HOLYSTONE_HARDNESS, STONE_BLAST));
		ICHORITE_BRICKS = add("ichorite_bricks", Block::new, stone(MapColor.COLOR_BROWN, ICHORITE_HARDNESS, STONE_BLAST));
		FADED_HOLYSTONE_BRICKS = add("faded_holystone_bricks", Block::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));
		HOLYSTONE_FLAGSTONES = add("holystone_flagstones", Block::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));
		FADED_HOLYSTONE_FLAGSTONES = add("faded_holystone_flagstones", Block::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));
		HOLYSTONE_PILLAR = add("holystone_pillar", RotatedPillarBlock::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));
		HOLYSTONE_KEYSTONE = add("holystone_keystone", Block::new, stone(MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST));

		family("holystone", HOLYSTONE, MapColor.WOOL, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST);
		family("mossy_holystone", MOSSY_HOLYSTONE, MapColor.WOOL, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST);
		family("holystone_brick", HOLYSTONE_BRICKS, MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST);
		family("faded_holystone_brick", FADED_HOLYSTONE_BRICKS, MapColor.WOOL, BRICKS_HARDNESS, BRICKS_BLAST);
		family("undershale", UNDERSHALE, MapColor.COLOR_GRAY, UNDERSHALE_HARDNESS, STONE_BLAST);
		family("undershale_brick", UNDERSHALE_BRICKS, MapColor.COLOR_GRAY, UNDERSHALE_HARDNESS, STONE_BLAST);
		family("agiosite", AGIOSITE, MapColor.COLOR_PURPLE, AGIOSITE_HARDNESS, STONE_BLAST);
		family("agiosite_brick", AGIOSITE_BRICKS, MapColor.COLOR_PURPLE, AGIOSITE_HARDNESS, STONE_BLAST);
		family("icestone_brick", ICESTONE_BRICKS, MapColor.ICE, HOLYSTONE_HARDNESS, STONE_BLAST);
		family("ichorite", ICHORITE, MapColor.COLOR_BROWN, ICHORITE_HARDNESS, STONE_BLAST);
		family("ichorite_brick", ICHORITE_BRICKS, MapColor.COLOR_BROWN, ICHORITE_HARDNESS, STONE_BLAST);

		HOLYSTONE_BUTTON = add("holystone_button", p -> new ButtonBlock(BlockSetType.STONE, 20, p), BlockBehaviour.Properties.of().noCollision()
			.strength(0.5F).pushReaction(PushReaction.POPPED));
		HOLYSTONE_PRESSURE_PLATE = add("holystone_pressure_plate", p -> new PressurePlateBlock(BlockSetType.STONE, p), stone(MapColor.WOOL, 0.5F, 0.5F)
			.forceSolidOn().noCollision().pushReaction(PushReaction.POPPED));
	}

	private static void initSoils() {
		COARSE_AETHER_DIRT = add("coarse_aether_dirt", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN)
			.strength(SOIL_HARDNESS).sound(SoundType.GRAVEL));
		AETHER_DIRT_PATH = add("aether_dirt_path", AetherPathBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN)
			.strength(PATH_HARDNESS).sound(SoundType.GRASS).isSuffocating((state, level, pos) -> true));
		AETHER_FARMLAND = add("aether_farmland", p -> new FarmlandBlock(AETHER_DIRT, p), BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN)
			.randomTicks().strength(FARMLAND_HARDNESS).sound(SoundType.GRAVEL).isViewBlocking((state, level, pos, box) -> true).isSuffocating((state, level, pos) -> true));
		SHIMMERING_SILT = add("shimmering_silt", p -> new ColoredFallingBlock(new ColorRGBA(SILT_DUST), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.SNARE).strength(SAND_HARDNESS).sound(SoundType.SAND));
		FERROSITE_SAND = add("ferrosite_sand", p -> new ColoredFallingBlock(new ColorRGBA(FERROSITE_SAND_DUST), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.SNARE).strength(SAND_HARDNESS).sound(SoundType.SAND));
		FERROSITE_MUD = add("ferrosite_mud", MudBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.MUD));
	}

	private static void initIce() {
		ARCTIC_SNOW_BLOCK = add("arctic_snow_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F)
			.sound(SoundType.SNOW).requiresCorrectToolForDrops());
		ARCTIC_SNOW = add("arctic_snow", SnowLayerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).replaceable().forceSolidOff()
			.randomTicks().strength(SNOW_HARDNESS).sound(SoundType.SNOW).requiresCorrectToolForDrops().isViewBlocking((state, level, pos, box) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
			.pushReaction(PushReaction.POPPED));
		ARCTIC_ICE = add("arctic_ice", IceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(ARCTIC_ICE_FRICTION).randomTicks()
			.strength(ICE_HARDNESS).sound(SoundType.GLASS).noOcclusion().isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor(Blocks::never));
		ARCTIC_PACKED_ICE = add("arctic_packed_ice", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(ARCTIC_ICE_FRICTION)
			.strength(ICE_HARDNESS).sound(SoundType.GLASS));
		FRAGILE_ARCTIC_ICE = add("fragile_arctic_ice", FragileArcticIceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE)
			.friction(ARCTIC_ICE_FRICTION).randomTicks().strength(ICE_HARDNESS).sound(SoundType.GLASS).noOcclusion()
			.isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor(Blocks::never));
		ARCTIC_ICE_CRYSTAL = add("arctic_ice_crystal", ArcticIceCrystalBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).noCollision()
			.strength(0.3F).sound(SoundType.AMETHYST_CLUSTER).lightLevel(state -> ICE_CRYSTAL_LIGHT).noOcclusion().pushReaction(PushReaction.POPPED));
	}

	/** A holystone ore (3.0/3.0) or its undershale twin (4.5/3.0). */
	private static Block ore(String id, int minXp, int maxXp, boolean undershale) {
		return add(id, p -> new AetherOreBlock(minXp, maxXp, p), stone(undershale ? MapColor.COLOR_GRAY : MapColor.WOOL,
			undershale ? UNDERSHALE_ORE_HARDNESS : ORE_HARDNESS, ORE_BLAST));
	}

	private static void initOres() {
		HOLYSTONE_QUARTZ_ORE = ore("holystone_quartz_ore", QUARTZ_XP_MIN, QUARTZ_XP_MAX, false);
		ARKENIUM_ORE = ore("arkenium_ore", 0, 0, false);
		GLINT_ORE = ore("glint_ore", GLINT_XP_MIN, GLINT_XP_MAX, false);
		CORROBONITE_ORE = add("corrobonite_ore", CorroboniteOreBlock::new, stone(MapColor.COLOR_GRAY, UNDERSHALE_ORE_HARDNESS, ORE_BLAST).randomTicks());
		CORROBONITE_CLUSTER = add("corrobonite_cluster", p -> new AmethystClusterBlock(7.0F, 3.0F, p), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
			.forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER).strength(1.5F).lightLevel(state -> CLUSTER_LIGHT).pushReaction(PushReaction.POPPED));
		UNDERSHALE_AMBROSIUM_ORE = ore("undershale_ambrosium_ore", AMBROSIUM_XP_MIN, AMBROSIUM_XP_MAX, true);
		UNDERSHALE_ZANITE_ORE = ore("undershale_zanite_ore", ZANITE_XP_MIN, ZANITE_XP_MAX, true);
		UNDERSHALE_ARKENIUM_ORE = ore("undershale_arkenium_ore", 0, 0, true);
		UNDERSHALE_GLINT_ORE = ore("undershale_glint_ore", GLINT_XP_MIN, GLINT_XP_MAX, true);
		UNDERSHALE_GRAVITITE_ORE = add("undershale_gravitite_ore", p -> new FloatingBlock(p, false), stone(MapColor.COLOR_GRAY, UNDERSHALE_ORE_HARDNESS, ORE_BLAST));

		ARKENIUM_BLOCK = add("arkenium_block", Block::new, stone(MapColor.COLOR_LIGHT_BLUE, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
		GLINT_BLOCK = add("glint_block", Block::new, stone(MapColor.COLOR_CYAN, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
		CORROBONITE_BLOCK = add("corrobonite_block", Block::new, stone(MapColor.COLOR_GREEN, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
		GOLDEN_AMBER_BLOCK = add("golden_amber_block", Block::new, stone(MapColor.GOLD, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
		GRAVITITE_BLOCK = add("gravitite_block", Block::new, stone(MapColor.COLOR_PINK, METAL_HARDNESS, METAL_BLAST).sound(SoundType.METAL));
	}

	private static BlockBehaviour.Properties glass(MapColor color, float hardness) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(hardness).sound(SoundType.GLASS).noOcclusion()
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never);
	}

	private static void initGlassAndClouds() {
		SCATTERGLASS = add("scatterglass", TransparentBlock::new, glass(MapColor.COLOR_CYAN, SCATTERGLASS_HARDNESS));
		SCATTERGLASS_PANE = add("scatterglass_pane", IronBarsBlock::new, glass(MapColor.COLOR_CYAN, SCATTERGLASS_HARDNESS));
		QUICKSOIL_GLASS_PANE = add("quicksoil_glass_pane", IronBarsBlock::new, glass(MapColor.COLOR_YELLOW, SOIL_HARDNESS)
			.lightLevel(state -> QUICKSOIL_GLASS_LIGHT));
		GREEN_AERCLOUD = add("green_aercloud", GreenAercloudBlock::new, cloud(MapColor.COLOR_LIGHT_GREEN));
		PURPLE_AERCLOUD = add("purple_aercloud", PurpleAercloudBlock::new, cloud(MapColor.COLOR_PURPLE));
		STORM_AERCLOUD = add("storm_aercloud", StormAercloudBlock::new, cloud(MapColor.COLOR_GRAY));
	}

	private static void initBuilding() {
		// cloudwool: white wool that is puffy, and its sixteen dyed kinds (the plain one is the white one)
		CLOUDWOOL = add("cloudwool", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.GUITAR)
			.strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
		CLOUDWOOL_CARPET = add("cloudwool_carpet", CarpetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.1F)
			.sound(SoundType.WOOL).ignitedByLava());
		for (DyeColor dye : DyeColor.values()) {
			if (dye == DyeColor.WHITE) {
				continue;
			}
			String id = dye.getName() + "_cloudwool";
			VARIANTS.put(id, add(id, Block::new, BlockBehaviour.Properties.of().mapColor(dye).instrument(NoteBlockInstrument.GUITAR)
				.strength(0.8F).sound(SoundType.WOOL).ignitedByLava()));
			VARIANTS.put(id + "_carpet", add(id + "_carpet", CarpetBlock::new, BlockBehaviour.Properties.of().mapColor(dye).strength(0.1F)
				.sound(SoundType.WOOL).ignitedByLava()));
		}
		ARKENIUM_LANTERN = add("arkenium_lantern", LanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn()
			.strength(3.5F).sound(SoundType.LANTERN).lightLevel(state -> LANTERN_LIGHT).noOcclusion().pushReaction(PushReaction.POPPED));
		ARKENIUM_CHAIN = add("arkenium_chain", ChainBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn()
			.requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion());
		ARKENIUM_BARS = add("arkenium_bars", IronBarsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.IRON).noOcclusion());
	}

	private static BlockBehaviour.Properties torch() {
		return BlockBehaviour.Properties.of().noCollision().instabreak().lightLevel(state -> TORCH_LIGHT).sound(SoundType.WOOD)
			.pushReaction(PushReaction.POPPED);
	}

	/** A block and its block item. */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
