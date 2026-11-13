package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The animals of the blossom lands, each made to the three rules (it does what the real one does, it changes its
 * surroundings and they come back on it, and looking after it is rewarded): the {@link Koi} of the town ponds, the
 * {@link SnowMonkey}, the {@link Crane}, the {@link ShrineDeer}, the {@link RaccoonDog}, the {@link Serow}, the paper
 * {@link Hornet} and its {@link HornetNest}, and the giant {@link Salamander}. Their
 * shapes and sheets are drawn by {@code kingdoms/tools/gen_blossom_creatures.py}.
 */
public final class BlossomAnimals {
	public static EntityType<Koi> KOI;
	public static EntityType<SnowMonkey> SNOW_MONKEY;
	public static EntityType<Crane> CRANE;
	public static EntityType<ShrineDeer> SHRINE_DEER;
	public static EntityType<RaccoonDog> RACCOON_DOG;
	public static EntityType<Serow> SEROW;
	public static EntityType<Hornet> HORNET;
	public static EntityType<Salamander> SALAMANDER;
	public static Block HORNET_NEST;
	public static Item PAPER_PULP;
	static final List<Item> EGGS = new ArrayList<>();

	/** Natural spawning: koi, in the cherry groves' waters (and then only inside a settlement, {@link Koi#checkSpawn}). */
	static final int KOI_WEIGHT = 6;
	/** The land animals' weights and group sizes among the cherry and on Mohknee ({@link BlossomAnimal#checkSpawn}). */
	static final int MONKEY_WEIGHT = 4, CRANE_WEIGHT = 3, DEER_WEIGHT = 6, RACCOON_DOG_WEIGHT = 3, SEROW_WEIGHT = 2, SALAMANDER_WEIGHT = 2;

	private BlossomAnimals() {
	}

	public static void register() {
		KOI = type("koi", EntityType.Builder.of(Koi::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.3F).clientTrackingRange(6));
		FabricDefaultAttributeRegistry.register(KOI, Koi.createAttributes());
		SpawnPlacements.register(KOI, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Koi::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE), MobCategory.WATER_AMBIENT, KOI, KOI_WEIGHT, 1, 2);
		egg("koi", KOI);

		SNOW_MONKEY = land("snow_monkey", EntityType.Builder.of(SnowMonkey::new, MobCategory.CREATURE).sized(0.6F, 0.9F), SnowMonkey.createAttributes(), MONKEY_WEIGHT, 3, 5);
		CRANE = land("crane", EntityType.Builder.of(Crane::new, MobCategory.CREATURE).sized(0.6F, 1.5F), Crane.createAttributes(), CRANE_WEIGHT, 2, 2);
		SHRINE_DEER = land("shrine_deer", EntityType.Builder.of(ShrineDeer::new, MobCategory.CREATURE).sized(0.7F, 1.1F), ShrineDeer.createAttributes(), DEER_WEIGHT, 2, 4);
		RACCOON_DOG = land("raccoon_dog", EntityType.Builder.of(RaccoonDog::new, MobCategory.CREATURE).sized(0.6F, 0.6F), RaccoonDog.createAttributes(), RACCOON_DOG_WEIGHT, 1, 2);
		SEROW = land("serow", EntityType.Builder.of(Serow::new, MobCategory.CREATURE).sized(0.8F, 1.2F), Serow.createAttributes(), SEROW_WEIGHT, 1, 1);

		HORNET = type("hornet", EntityType.Builder.of(Hornet::new, MobCategory.CREATURE).sized(0.4F, 0.4F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(HORNET, Hornet.createAttributes());
		egg("hornet", HORNET);
		HORNET_NEST = BlossomBlocks.block("hornet_nest", new HornetNest(BlossomBlocks.props("hornet_nest").mapColor(MapColor.TERRACOTTA_WHITE).strength(0.6F)
			.sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.POPPED)), 30, 60);
		ResourceKey<Item> pulp = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("paper_pulp"));
		PAPER_PULP = Registry.register(BuiltInRegistries.ITEM, pulp, new Item(new Item.Properties().setId(pulp)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(PAPER_PULP));

		SALAMANDER = type("giant_salamander", EntityType.Builder.of(Salamander::new, MobCategory.WATER_CREATURE).sized(1.2F, 0.4F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(SALAMANDER, Salamander.createAttributes());
		SpawnPlacements.register(SALAMANDER, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Salamander::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.RIVER), MobCategory.WATER_CREATURE, SALAMANDER, SALAMANDER_WEIGHT, 1, 1);
		egg("giant_salamander", SALAMANDER);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.forEach(output::accept));
	}

	/** A land animal: its type, attributes, egg, and its spawning among the cherry and in Mohknee's forests and meadows. */
	private static <T extends BlossomAnimal> EntityType<T> land(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes, int weight, int least, int most) {
		EntityType<T> type = type(name, builder.clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(type, attributes);
		SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlossomAnimal::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.MEADOW),
			MobCategory.CREATURE, type, weight, least, most);
		egg(name, type);
		return type;
	}

	static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	static void egg(String name, EntityType<? extends Mob> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
		EGGS.add(Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type))));
	}
}
