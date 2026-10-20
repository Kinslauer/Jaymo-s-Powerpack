package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Aether's blocks (see docs/aether.md): holystone and the sky soils, the three aerclouds, the ores that float or
 * freeze, skyroot and golden oak, the crystal trees' leaves, the berry bush and the flowers. Each block's own rules
 * live in its own class beside this one; this class only registers them (and their items).
 */
public final class AetherBlocks {
	// ---- tuning ----
	/** Hardness and blast resistance (the notes' numbers: holystone 0.5, bricks 2.0/6.0, ores 3.0, metal blocks 5.0/6.0). */
	public static final float HOLYSTONE_HARDNESS = 0.5F, HOLYSTONE_BLAST = 0.5F, BRICKS_HARDNESS = 2.0F, BRICKS_BLAST = 6.0F;
	public static final float ORE_HARDNESS = 3.0F, ORE_BLAST = 3.0F, METAL_HARDNESS = 5.0F, METAL_BLAST = 6.0F;
	public static final float SOIL_HARDNESS = 0.2F, QUICKSOIL_HARDNESS = 0.5F, AERCLOUD_HARDNESS = 0.3F;
	/** The friction a quicksoil block gives (1.0 would be no loss at all, vanilla ice is 0.98). */
	public static final float QUICKSOIL_FRICTION = 1.1F;
	/** Light of quicksoil glass, and of an ambrosium torch (a vanilla torch is 14). */
	public static final int QUICKSOIL_GLASS_LIGHT = 11, TORCH_LIGHT = 14;
	/** Share of animate ticks on which golden oak and crystal leaves let a speck drift down (with open air below). */
	public static final float GOLDEN_LEAF_PARTICLES = 0.1F, CRYSTAL_LEAF_PARTICLES = 0.03F;
	/** Particle colours of the drifting leaves. */
	public static final int GOLDEN_LEAF_COLOR = 0xFFD54A, CRYSTAL_LEAF_COLOR = 0xBFF4F8;
	/** Suspicious stew: white flower, purple flower (effect, seconds): slow falling 4 s, nausea 12 s. */
	public static final float WHITE_FLOWER_STEW_SECONDS = 4.0F, PURPLE_FLOWER_STEW_SECONDS = 12.0F;

	public static Block HOLYSTONE, MOSSY_HOLYSTONE, HOLYSTONE_BRICKS;
	public static Block AETHER_DIRT, AETHER_GRASS_BLOCK, ENCHANTED_AETHER_GRASS_BLOCK, QUICKSOIL, QUICKSOIL_GLASS, ICESTONE;
	public static Block AMBROSIUM_ORE, ZANITE_ORE, GRAVITITE_ORE, ENCHANTED_GRAVITITE, AMBROSIUM_BLOCK, ZANITE_BLOCK;
	public static Block COLD_AERCLOUD, BLUE_AERCLOUD, GOLDEN_AERCLOUD;
	public static Block SKYROOT_LOG, SKYROOT_WOOD, STRIPPED_SKYROOT_LOG, STRIPPED_SKYROOT_WOOD, SKYROOT_PLANKS, SKYROOT_LEAVES, SKYROOT_SAPLING;
	public static Block GOLDEN_OAK_LOG, GOLDEN_OAK_WOOD, GOLDEN_OAK_LEAVES, GOLDEN_OAK_SAPLING;
	public static Block CRYSTAL_LEAVES, CRYSTAL_FRUIT_LEAVES;
	public static Block BERRY_BUSH, BERRY_BUSH_STEM, WHITE_FLOWER, PURPLE_FLOWER;
	public static Block AMBROSIUM_TORCH, AMBROSIUM_WALL_TORCH;

	/** The tree features the saplings grow (written by the dimension's worldgen, not here). */
	public static final ResourceKey<ConfiguredFeature<?, ?>> SKYROOT_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE, JaymosPowers.id("skyroot_tree"));
	public static final ResourceKey<ConfiguredFeature<?, ?>> GOLDEN_OAK_TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE, JaymosPowers.id("golden_oak_tree"));

	private AetherBlocks() {
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
		AETHER_GRASS_BLOCK = block("aether_grass_block", AetherGrassBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WARPED_WART_BLOCK).strength(SOIL_HARDNESS).sound(SoundType.GRASS).randomTicks());
		ENCHANTED_AETHER_GRASS_BLOCK = block("enchanted_aether_grass_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(SOIL_HARDNESS).sound(SoundType.GRASS));
		QUICKSOIL = block("quicksoil", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(QUICKSOIL_HARDNESS).sound(SoundType.SAND).friction(QUICKSOIL_FRICTION));
		QUICKSOIL_GLASS = block("quicksoil_glass", TransparentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(SOIL_HARDNESS).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> QUICKSOIL_GLASS_LIGHT)
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
		ICESTONE = block("icestone", IcestoneBlock::new, stone(MapColor.ICE, HOLYSTONE_HARDNESS, HOLYSTONE_BLAST)
			.sound(SoundType.AMETHYST).randomTicks());

		AMBROSIUM_ORE = block("ambrosium_ore", Block::new, stone(MapColor.WOOL, ORE_HARDNESS, ORE_BLAST));
		ZANITE_ORE = block("zanite_ore", Block::new, stone(MapColor.WOOL, ORE_HARDNESS, ORE_BLAST));
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
		SKYROOT_LEAVES = block("skyroot_leaves", p -> new UntintedParticleLeavesBlock(0.0F, ParticleTypes.SMOKE, p), leaves(MapColor.GRASS));
		SKYROOT_SAPLING = block("skyroot_sapling", p -> new AetherSaplingBlock(SKYROOT_TREE, p), sapling());

		GOLDEN_OAK_LOG = block("golden_oak_log", RotatedPillarBlock::new, wood(MapColor.GOLD));
		GOLDEN_OAK_WOOD = block("golden_oak_wood", RotatedPillarBlock::new, wood(MapColor.GOLD));
		GOLDEN_OAK_LEAVES = block("golden_oak_leaves", p -> new UntintedParticleLeavesBlock(GOLDEN_LEAF_PARTICLES,
			new DustParticleOptions(GOLDEN_LEAF_COLOR, 1.0F), p), leaves(MapColor.GOLD));
		GOLDEN_OAK_SAPLING = block("golden_oak_sapling", p -> new AetherSaplingBlock(GOLDEN_OAK_TREE, p), sapling());

		CRYSTAL_LEAVES = block("crystal_leaves", p -> new UntintedParticleLeavesBlock(CRYSTAL_LEAF_PARTICLES,
			new DustParticleOptions(CRYSTAL_LEAF_COLOR, 1.0F), p), leaves(MapColor.DIAMOND));
		CRYSTAL_FRUIT_LEAVES = block("crystal_fruit_leaves", p -> new UntintedParticleLeavesBlock(CRYSTAL_LEAF_PARTICLES,
			new DustParticleOptions(CRYSTAL_LEAF_COLOR, 1.0F), p), leaves(MapColor.DIAMOND));

		BERRY_BUSH = block("berry_bush", BerryBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(SOIL_HARDNESS)
			.noCollision().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
		BERRY_BUSH_STEM = block("berry_bush_stem", BerryBushStemBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(SOIL_HARDNESS)
			.noCollision().randomTicks().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
		WHITE_FLOWER = block("white_flower", p -> new FlowerBlock(MobEffects.SLOW_FALLING, WHITE_FLOWER_STEW_SECONDS, p), plant(SoundType.GRASS));
		PURPLE_FLOWER = block("purple_flower", p -> new FlowerBlock(MobEffects.NAUSEA, PURPLE_FLOWER_STEW_SECONDS, p), plant(SoundType.GRASS));

		// the torch: a vanilla torch with smoke where the flame was; the wall one gives no item of its own
		ResourceKey<Block> torchKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("ambrosium_torch"));
		AMBROSIUM_TORCH = Registry.register(BuiltInRegistries.BLOCK, torchKey, new TorchBlock(ParticleTypes.SMOKE, torch().setId(torchKey)));
		ResourceKey<Block> wallKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("ambrosium_wall_torch"));
		AMBROSIUM_WALL_TORCH = Registry.register(BuiltInRegistries.BLOCK, wallKey, new WallTorchBlock(ParticleTypes.SMOKE, torch()
			.overrideLootTable(AMBROSIUM_TORCH.getLootTable()).overrideDescription(AMBROSIUM_TORCH.getDescriptionId()).setId(wallKey)));
		ResourceKey<Item> torchItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("ambrosium_torch"));
		Registry.register(BuiltInRegistries.ITEM, torchItem, new StandingAndWallBlockItem(AMBROSIUM_TORCH, AMBROSIUM_WALL_TORCH, Direction.DOWN,
			new Item.Properties().setId(torchItem).useBlockDescriptionPrefix()));
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
