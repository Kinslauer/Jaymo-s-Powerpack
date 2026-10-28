package com.jaymospowers.frost;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.17.114) The ice of Minecraft's 26.4 snapshots, built for our 26.3 pack (Andy, 2026-10-28: "can we get the ice
 * blocks/items from snapshot 26.4?"). Our own code and art, from the snapshot notes; Mojang's isn't copied. What it has:
 * <ul>
 * <li>{@link Freezing}, the Potion of Freezing (and its splash, lingering and arrows), brewed from an {@link #ICE_BALL}.</li>
 * <li>The ice ball ({@link ThrownIceBall}) and the {@link Frostbite} that throws them.</li>
 * <li>Icicles, ice crystals and brittle ice; packed ice bricks (stairs, slab, wall, chiseled); snow bricks (stairs,
 * slab, wall).</li>
 * <li>Snow settles on packed ice now (data: {@code tags/block/cannot_support_snow_layer.json}).</li>
 * </ul>
 * Without 26.4's ice caves, the icicles and crystals grow in frozen patches of the caves under snowy biomes, brittle ice
 * crusts frozen lakes, and Frostbites roam the snow ({@link FrostFeatures}).
 */
public final class Frost {
	// ---- tuning ----
	/** Frostbite spawn weight in snowy biomes (a zombie's is 95) and group size. */
	public static final int FROSTBITE_WEIGHT = 40, FROSTBITE_MIN = 1, FROSTBITE_MAX = 2;
	/** Ice crystal light (26.4: 4). */
	public static final int CRYSTAL_LIGHT = 4;

	public static Item ICE_BALL, FROSTBITE_SPAWN_EGG;
	public static EntityType<ThrownIceBall> ICE_BALL_ENTITY;
	public static EntityType<Frostbite> FROSTBITE;
	public static Block ICICLE, ICE_CRYSTAL, BRITTLE_ICE;
	public static Block PACKED_ICE_BRICKS, PACKED_ICE_BRICK_STAIRS, PACKED_ICE_BRICK_SLAB, PACKED_ICE_BRICK_WALL, CHISELED_PACKED_ICE_BRICKS;
	public static Block SNOW_BRICKS, SNOW_BRICK_STAIRS, SNOW_BRICK_SLAB, SNOW_BRICK_WALL;

	private Frost() {
	}

	public static void init() {
		Freezing.register();
		blocks();
		items();
		entities();
		world();
		tabs();
	}

	private static void blocks() {
		ICICLE = block("icicle", IcicleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.5F).sound(SoundType.GLASS)
			.noOcclusion().randomTicks().pushReaction(PushReaction.POPPED).isRedstoneConductor(Blocks::never));
		ICE_CRYSTAL = block("ice_crystal", p -> new AmethystClusterBlock(5.0F, 10.0F, p), BlockBehaviour.Properties.of().mapColor(MapColor.ICE)
			.strength(0.5F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> CRYSTAL_LIGHT).pushReaction(PushReaction.POPPED));
		BRITTLE_ICE = block("brittle_ice", BrittleIceBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F)
			.strength(0.3F).sound(SoundType.GLASS).noOcclusion().isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor(Blocks::never));

		PACKED_ICE_BRICKS = block("packed_ice_bricks", Block::new, packedIceBricks());
		PACKED_ICE_BRICK_STAIRS = block("packed_ice_brick_stairs", p -> new StairBlock(PACKED_ICE_BRICKS.defaultBlockState(), p), packedIceBricks());
		PACKED_ICE_BRICK_SLAB = block("packed_ice_brick_slab", SlabBlock::new, packedIceBricks());
		PACKED_ICE_BRICK_WALL = block("packed_ice_brick_wall", WallBlock::new, packedIceBricks().forceSolidOn());
		CHISELED_PACKED_ICE_BRICKS = block("chiseled_packed_ice_bricks", Block::new, packedIceBricks());

		SNOW_BRICKS = block("snow_bricks", Block::new, snowBricks());
		SNOW_BRICK_STAIRS = block("snow_brick_stairs", p -> new StairBlock(SNOW_BRICKS.defaultBlockState(), p), snowBricks());
		SNOW_BRICK_SLAB = block("snow_brick_slab", SlabBlock::new, snowBricks());
		SNOW_BRICK_WALL = block("snow_brick_wall", WallBlock::new, snowBricks().forceSolidOn());
	}

	/** Packed ice's hardness, but not slippery, and only a pickaxe gets it back (26.4). */
	private static BlockBehaviour.Properties packedIceBricks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.8F).sound(SoundType.GLASS).requiresCorrectToolForDrops();
	}

	/** A little harder than a snow block, and only a shovel gets it back (26.4). */
	private static BlockBehaviour.Properties snowBricks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW).requiresCorrectToolForDrops();
	}

	private static void items() {
		ICE_BALL = item("ice_ball", IceBallItem::new, new Item.Properties().stacksTo(16));
	}

	private static void entities() {
		ResourceKey<EntityType<?>> ballKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("ice_ball"));
		ICE_BALL_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ballKey, EntityType.Builder.<ThrownIceBall>of(ThrownIceBall::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(ballKey));
		ResourceKey<EntityType<?>> frostbiteKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("frostbite"));
		FROSTBITE = Registry.register(BuiltInRegistries.ENTITY_TYPE, frostbiteKey, EntityType.Builder.<Frostbite>of(Frostbite::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).build(frostbiteKey));
		FabricDefaultAttributeRegistry.register(FROSTBITE, Zombie.createAttributes());
		SpawnPlacements.register(FROSTBITE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		FROSTBITE_SPAWN_EGG = item("frostbite_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(FROSTBITE)), new Item.Properties());
	}

	private static void world() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("frozen_cave_patch"), FrostFeatures.FrozenCave.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("thin_ice"), FrostFeatures.ThinIce.CODEC);
		var cold = BiomeSelectors.foundInOverworld().and(BiomeSelectors.tag(ConventionalBiomeTags.IS_SNOWY)
			.or(BiomeSelectors.tag(ConventionalBiomeTags.IS_ICY)));
		BiomeModifications.addFeature(cold, GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("frozen_cave_patch"));
		BiomeModifications.addFeature(cold, GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placed("thin_ice"));
		BiomeModifications.addSpawn(cold, MobCategory.MONSTER, FROSTBITE, FROSTBITE_WEIGHT, FROSTBITE_MIN, FROSTBITE_MAX);
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}

	private static void tabs() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			for (Block block : new Block[] {PACKED_ICE_BRICKS, PACKED_ICE_BRICK_STAIRS, PACKED_ICE_BRICK_SLAB, PACKED_ICE_BRICK_WALL,
				CHISELED_PACKED_ICE_BRICKS, SNOW_BRICKS, SNOW_BRICK_STAIRS, SNOW_BRICK_SLAB, SNOW_BRICK_WALL}) {
				output.accept(block);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(ICICLE);
			output.accept(ICE_CRYSTAL);
			output.accept(BRITTLE_ICE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(ICE_BALL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(ICE_BALL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(FROSTBITE_SPAWN_EGG));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
