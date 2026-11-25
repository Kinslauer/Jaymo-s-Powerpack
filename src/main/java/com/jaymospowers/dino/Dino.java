package com.jaymospowers.dino;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.network.DinoNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Amber Age's dinosaurs (Andy's own mod, ported from its jar) on the Verdant World: nine dinosaurs and a dodo, found
 * there and in Alex's Caves' Primordial Caves ({@link #PRIMORDIAL_CAVES}). Each country of the far land keeps the
 * ones that suit it ({@link #HOMES}); the home plains keep only grazers, and the brainstorm none (its trees discharge into anything living). See {@code docs/Dinosaurs.md}.
 */
public final class Dino {
	public static final ResourceKey<Level> VERDANT_WORLD = ResourceKey.create(Registries.DIMENSION, id("verdant_world"));

	/** One species' place in one Verdant biome: its weight among the biome's creatures (herd sizes come from the species). */
	private record Home(String biome, Species species, int weight) {
	}

	/**
	 * Who lives where, by biome key (the Verdant World's eight biomes are used by no other dimension; the spawn rule also
	 * checks the dimension). The plains are the quiet green home: grazers only. Hunters keep to the far country, and
	 * the big ones to open ground where the herds pass.
	 */
	private static final List<Home> HOMES = List.of(
		new Home("verdant_plains", Species.GALLIMIMUS, 10), new Home("verdant_plains", Species.DODO, 8),
		new Home("verdant_plains", Species.PARASAUROLOPHUS, 6), new Home("verdant_plains", Species.TRICERATOPS, 4),
		new Home("wind_valley", Species.GALLIMIMUS, 10), new Home("wind_valley", Species.PARASAUROLOPHUS, 6),
		new Home("wind_valley", Species.TRICERATOPS, 5), new Home("wind_valley", Species.VELOCIRAPTOR, 3),
		new Home("wind_valley", Species.CARNOTAURUS, 2), new Home("wind_valley", Species.TYRANNOSAURUS, 1),
		new Home("stone_garden", Species.TRICERATOPS, 6), new Home("stone_garden", Species.GALLIMIMUS, 4),
		new Home("stone_garden", Species.CARNOTAURUS, 3), new Home("stone_garden", Species.VELOCIRAPTOR, 3),
		new Home("mycotic_forest", Species.DILOPHOSAURUS, 6), new Home("mycotic_forest", Species.PARASAUROLOPHUS, 6),
		new Home("mycotic_forest", Species.DODO, 6), new Home("mycotic_forest", Species.GALLIMIMUS, 3),
		new Home("old_mycotic_forest", Species.BRACHIOSAURUS, 4), new Home("old_mycotic_forest", Species.PARASAUROLOPHUS, 5),
		new Home("old_mycotic_forest", Species.DILOPHOSAURUS, 4), new Home("old_mycotic_forest", Species.DODO, 4),
		new Home("pulse_forest", Species.VELOCIRAPTOR, 5), new Home("pulse_forest", Species.DILOPHOSAURUS, 5),
		new Home("pulse_forest", Species.GALLIMIMUS, 4), new Home("pulse_forest", Species.DODO, 4),
		new Home("pulse_forest", Species.PARASAUROLOPHUS, 3),
		new Home("lakeside_desert", Species.SPINOSAURUS, 3), new Home("lakeside_desert", Species.CARNOTAURUS, 3),
		new Home("lakeside_desert", Species.GALLIMIMUS, 3), new Home("lakeside_desert", Species.DODO, 3));

	/**
	 * Alex's Caves' Primordial Caves, under the far land (Andy, 2026-11-24: "the same dinos in the prehistoric cave should
	 * spawn on verdant and vice versa"; Lore.md, "The old fighters under the ferns"). Ours live there too ({@link #IN_THE_CAVES}),
	 * and Alex's beasts walk the Verdant World ({@link #FROM_THE_CAVES}). Nothing here needs Alex's to load.
	 */
	public static final ResourceKey<Biome> PRIMORDIAL_CAVES = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("alexscaves", "primordial_caves"));

	/** Our species' weights among the Primordial Caves' beasts (Alex's own there: grottoceratops 27, relicheirus 13, raptors and fliers 6, tremorsaurus 5). */
	private static final Map<Species, Integer> IN_THE_CAVES = Map.of(
		Species.GALLIMIMUS, 8, Species.PARASAUROLOPHUS, 8, Species.TRICERATOPS, 8, Species.VELOCIRAPTOR, 5, Species.DILOPHOSAURUS, 5,
		Species.DODO, 5, Species.CARNOTAURUS, 3, Species.TYRANNOSAURUS, 2, Species.BRACHIOSAURUS, 2, Species.SPINOSAURUS, 1);

	/** One of Alex's Caves' primordial beasts in one Verdant biome: its weight among the biome's creatures and its herd size. */
	private record Visitor(String biome, String beast, int weight, int least, int most) {
	}

	/** Alex's beasts where they suit the Verdant World: grazers on the open ground, raptors in the woods, fliers over the stone. */
	private static final List<Visitor> FROM_THE_CAVES = List.of(
		new Visitor("verdant_plains", "grottoceratops", 5, 2, 4), new Visitor("stone_garden", "grottoceratops", 5, 2, 4),
		new Visitor("pulse_forest", "vallumraptor", 3, 3, 5), new Visitor("wind_valley", "vallumraptor", 2, 3, 5),
		new Visitor("mycotic_forest", "relicheirus", 3, 1, 1), new Visitor("old_mycotic_forest", "relicheirus", 4, 1, 1),
		new Visitor("wind_valley", "tremorsaurus", 1, 1, 1), new Visitor("lakeside_desert", "tremorsaurus", 2, 1, 1),
		new Visitor("old_mycotic_forest", "atlatitan", 1, 1, 1),
		new Visitor("stone_garden", "subterranodon", 3, 2, 4), new Visitor("wind_valley", "subterranodon", 2, 2, 4));

	/** Chance a freshly generated chunk of a dinosaur country grows another herd (vanilla's default is 0.1). */
	private static final float CHUNK_HERD_CHANCE = 0.25F;

	private Dino() {
	}

	public static Identifier id(String path) {
		return JaymosPowers.id(path);
	}

	public static void init() {
		DinoSounds.init();
		DinoEntities.init();
		DinoBlocks.init();
		DinoItems.init();
		DinoNetwork.init();
		for (Species s : Species.values()) {
			SpawnPlacements.register(DinoEntities.dinosaur(s), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Dinosaur::checkSpawnRules);
		}
		List<ResourceKey<Biome>> lands = new ArrayList<>();
		for (Home h : HOMES) {
			ResourceKey<Biome> biome = ResourceKey.create(Registries.BIOME, id(h.biome));
			if (!lands.contains(biome)) {
				lands.add(biome);
			}
			BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biome), MobCategory.CREATURE, DinoEntities.dinosaur(h.species), h.weight,
				h.species.packMin(), h.species.packMax());
		}
		// the Verdant biomes name no creatures of their own, so chunks make a herd about one time in four (vanilla: one in ten)
		BiomeModifications.create(id("dinosaur_herds")).add(ModificationPhase.ADDITIONS, BiomeSelectors.includeByKey(lands),
			context -> context.getMobSpawnSettings().setCreatureGenerationProbability(CHUNK_HERD_CHANCE));
		// both ways between the Verdant World and the Primordial Caves; types are looked up when biomes are built, by
		// then every mod's are registered
		BiomeModifications.create(id("primordial_herds")).add(ModificationPhase.ADDITIONS, BiomeSelectors.includeByKey(PRIMORDIAL_CAVES), context -> {
			var spawns = context.getMobSpawnSettings();
			// Alex's own category for its cave beasts (it spawns them as caves generate), found by what it holds
			MobCategory cave = spawns.getMobs().entrySet().stream()
				.filter(e -> e.getValue().stream().anyMatch(w -> "alexscaves:grottoceratops".equals(BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type()).toString())))
				.map(Map.Entry::getKey).findFirst().orElse(MobCategory.CREATURE);
			IN_THE_CAVES.forEach((s, weight) -> spawns.addSpawn(cave,
				new MobSpawnSettings.SpawnerData(DinoEntities.dinosaur(s), UniformInt.of(s.packMin(), s.packMax())), weight));
		});
		for (Visitor v : FROM_THE_CAVES) {
			BiomeModifications.create(id("visitors_" + v.biome + "_" + v.beast)).add(ModificationPhase.ADDITIONS,
				BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, id(v.biome))), context -> {
					EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath("alexscaves", v.beast)).orElse(null);
					if (type != null) {
						context.getMobSpawnSettings().addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(type, UniformInt.of(v.least, v.most)), v.weight);
					}
				});
		}
	}
}
