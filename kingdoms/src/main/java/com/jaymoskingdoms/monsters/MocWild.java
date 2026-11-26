package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.BigCats;
import com.jaymoskingdoms.BoneCharm;
import com.jaymoskingdoms.CatKeeping;
import com.jaymoskingdoms.ClearCuts;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.SmallLife;
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
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The second half of Reference Mods patch 7: the living things of Mo' Creatures that are not monsters (after
 * Mo' Creatures 1.10.2, GPL-3, see {@code licenses/mocreatures}), and the systems that bring them where the world gives them
 * a reason:
 * <ul>
 * <li>the ent ({@link Ent}, woken by {@link ClearCuts}), never found but where a forest was cut hard;</li>
 * <li>the small life ({@link Critter}: cricket, snail, fly, maggot, roach, mouse, mole), brought by {@link SmallLife};</li>
 * <li>the manta ray, the stingray and the jellyfish ({@link SeaRay}, {@link Jellyfish}) in the warm seas, which spawn the
 * game's way: the manta and the jellyfish bloom in the warm, lukewarm and deep lukewarm oceans, the stingray in their
 * sandy shallows;</li>
 * <li>the liger and tigon ({@link Liger}), born only of a lion and a tiger raised by one keeper ({@link BigCats});</li>
 * <li>the bone charm ({@link BoneCharm}) and the keeping of cats ({@link CatKeeping}).</li>
 * </ul>
 */
public final class MocWild {
	/** How likely each is to be found in the sea, and how many together (a jellyfish bloom is many). */
	static final int MANTA_WEIGHT = 4, STINGRAY_WEIGHT = 8, JELLYFISH_WEIGHT = 6, JELLYFISH_MIN = 4, JELLYFISH_MAX = 8;

	public static EntityType<Ent> ENT;
	public static EntityType<Liger> LIGER;
	public static EntityType<SeaRay> MANTA_RAY, STINGRAY;
	public static EntityType<Jellyfish> JELLYFISH;
	public static EntityType<Critter> CRICKET, SNAIL, FLY, MAGGOT, ROACH, MOUSE, MOLE;
	/** What a snail or slug stamped flat leaves behind ({@code wild/SpiderSquash}): its slime, picked up off the ground. */
	public static Item SNAIL_SLIME;
	private static final List<Item> EGGS = new ArrayList<>();

	private MocWild() {
	}

	public static void register() {
		ENT = type("ent", EntityType.Builder.of(Ent::new, MobCategory.MISC).sized(1.4F, 3.6F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(ENT, Ent.createAttributes());
		egg("ent", ENT);

		LIGER = type("liger", EntityType.Builder.of(Liger::new, MobCategory.CREATURE).sized(1.5F, 1.4F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(LIGER, Liger.createAttributes());
		egg("liger", LIGER);

		MANTA_RAY = type("manta_ray", EntityType.Builder.<SeaRay>of((t, l) -> new SeaRay(t, l, true), MobCategory.WATER_CREATURE).sized(2.2F, 0.5F).clientTrackingRange(10));
		STINGRAY = type("stingray", EntityType.Builder.<SeaRay>of((t, l) -> new SeaRay(t, l, false), MobCategory.WATER_AMBIENT).sized(1.0F, 0.3F).clientTrackingRange(8));
		JELLYFISH = type("jellyfish", EntityType.Builder.of(Jellyfish::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.6F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(MANTA_RAY, SeaRay.createAttributes(true));
		FabricDefaultAttributeRegistry.register(STINGRAY, SeaRay.createAttributes(false));
		FabricDefaultAttributeRegistry.register(JELLYFISH, Jellyfish.createAttributes());
		SpawnPlacements.register(MANTA_RAY, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, WaterAnimal::checkSurfaceWaterAnimalSpawnRules);
		SpawnPlacements.register(STINGRAY, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, SeaRay::checkStingraySpawn);
		SpawnPlacements.register(JELLYFISH, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, WaterAnimal::checkSurfaceWaterAnimalSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN),
			MobCategory.WATER_CREATURE, MANTA_RAY, MANTA_WEIGHT, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN), MobCategory.WATER_AMBIENT, STINGRAY, STINGRAY_WEIGHT, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN),
			MobCategory.WATER_AMBIENT, JELLYFISH, JELLYFISH_WEIGHT, JELLYFISH_MIN, JELLYFISH_MAX);
		egg("manta_ray", MANTA_RAY);
		egg("stingray", STINGRAY);
		egg("jellyfish", JELLYFISH);

		CRICKET = critter(Critter.Kind.CRICKET);
		SNAIL = critter(Critter.Kind.SNAIL);
		FLY = critter(Critter.Kind.FLY);
		MAGGOT = critter(Critter.Kind.MAGGOT);
		ROACH = critter(Critter.Kind.ROACH);
		MOUSE = critter(Critter.Kind.MOUSE);
		MOLE = critter(Critter.Kind.MOLE);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.forEach(output::accept));
		ResourceKey<Item> slime = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("snail_slime"));
		SNAIL_SLIME = Registry.register(BuiltInRegistries.ITEM, slime, new Item(new Item.Properties().setId(slime)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(net.minecraft.world.item.Items.SLIME_BALL, SNAIL_SLIME));

		ClearCuts.register();
		SmallLife.register();
		CatKeeping.register();
		BigCats.register();
		BoneCharm.register();
	}

	/** One of the small life: its type (a creature of no category the spawner counts), attributes and egg. */
	private static EntityType<Critter> critter(Critter.Kind kind) {
		EntityType<Critter> type = type(kind.id, EntityType.Builder.<Critter>of((t, l) -> new Critter(t, l, kind), MobCategory.MISC).sized(kind.width, kind.height).clientTrackingRange(6));
		FabricDefaultAttributeRegistry.register(type, Critter.createAttributes(kind));
		egg(kind.id, type);
		return type;
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static void egg(String name, EntityType<? extends Mob> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
		EGGS.add(Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type))));
	}
}
