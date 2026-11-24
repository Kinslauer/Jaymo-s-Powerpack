package com.jaymospowers.dino;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.network.DinoNetwork;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Amber Age's dinosaurs (Andy's own mod, ported from its jar) on the Verdant World: nine dinosaurs and a dodo, found only
 * there. Each country of the far land keeps the ones that suit it ({@link #HOMES}); the home plains keep only grazers,
 * and the brainstorm none (its trees discharge into anything living). See {@code docs/Dinosaurs.md}.
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
	}
}
