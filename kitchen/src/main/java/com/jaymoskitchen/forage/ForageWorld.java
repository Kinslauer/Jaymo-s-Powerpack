package com.jaymoskitchen.forage;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Where the wild mushrooms grow, in the biomes Spore &amp; Stem puts them (its worldgen, Apache-2.0, as sparse patches;
 * the placed features are data/jaymoskitchen/worldgen/placed_feature/forage_*). Morels in forests, chanterelles in
 * taigas and birch woods, deathcaps in dark forests, frostcaps in the snow, puffballs on the plains, emberstools in
 * deep caves and the Nether's crimson forest and basalt deltas; Truffle Soil in pockets under woods. What stands
 * there keeps the year ({@link ForageMushroomBlock}).
 */
public final class ForageWorld {
	private ForageWorld() {
	}

	public static void register() {
		vegetal("forage_morel", Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.FLOWER_FOREST);
		vegetal("forage_chanterelle", Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.BIRCH_FOREST,
			Biomes.OLD_GROWTH_BIRCH_FOREST);
		vegetal("forage_deathcap", Biomes.DARK_FOREST);
		vegetal("forage_frostcap", Biomes.SNOWY_TAIGA, Biomes.SNOWY_PLAINS, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.ICE_SPIKES);
		vegetal("forage_puffball", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU);
		vegetal("forage_emberstool_nether", Biomes.CRIMSON_FOREST, Biomes.BASALT_DELTAS);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.VEGETAL_DECORATION, key("forage_emberstool_cave"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST,
				Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA),
			GenerationStep.Decoration.UNDERGROUND_ORES, key("forage_truffle_soil"));
	}

	@SafeVarargs
	private static void vegetal(String placed, ResourceKey<net.minecraft.world.level.biome.Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Decoration.VEGETAL_DECORATION, key(placed));
	}

	private static ResourceKey<PlacedFeature> key(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id(id));
	}
}
