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
 * surroundings and they come back on it, and looking after it is rewarded): the {@link Koi} of the town ponds. Their
 * shapes and sheets are drawn by {@code kingdoms/tools/gen_blossom_creatures.py}.
 */
public final class BlossomAnimals {
	public static EntityType<Koi> KOI;
	static final List<Item> EGGS = new ArrayList<>();

	/** Natural spawning: koi, in the cherry groves' waters (and then only inside a settlement, {@link Koi#checkSpawn}). */
	static final int KOI_WEIGHT = 6;

	private BlossomAnimals() {
	}

	public static void register() {
		KOI = type("koi", EntityType.Builder.of(Koi::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.3F).clientTrackingRange(6));
		FabricDefaultAttributeRegistry.register(KOI, Koi.createAttributes());
		SpawnPlacements.register(KOI, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Koi::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.CHERRY_GROVE), MobCategory.WATER_AMBIENT, KOI, KOI_WEIGHT, 1, 2);
		egg("koi", KOI);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.forEach(output::accept));
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
