package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Andy's TFC Hardrock picks: "Make veins bigger and rarer. Meaning most villages will not have iron armor or tools and
 * will be more encouraged to trade for it. Diamond and netherite become IMPOSSIBLY rare." Vanilla's iron, copper, gold,
 * diamond and ancient debris blobs come out of every biome ({@link #VANILLA}), and a few great veins go in
 * ({@link BigVeinFeature}): iron about one chunk in 24, copper one in 20, gold one in 48, all of 60 to 110 blocks; a
 * diamond pocket of 3 one chunk in 900, buried; ancient debris, 2 blocks, one Nether chunk in 1200. Powers' silver is a
 * great vein too (worldgen/placed_feature/ore_silver.json). The numbers are the placed features' rarity filters and the
 * features' sizes (worldgen/feature/big_vein_*.json). New chunks only: land already made keeps what it has.
 * Kingdoms' GroundChanges (the ore a world tier lays under loaded chunks) is cut to match.
 */
public final class BigVeins {
	/** Vanilla's ore placements taken out of every biome. Coal, redstone, lapis, emerald and Nether gold stay. */
	static final List<String> VANILLA = List.of("ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_copper", "ore_copper_large",
		"ore_gold", "ore_gold_lower", "ore_gold_extra", "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried",
		"ore_ancient_debris_large", "ore_debris_small");

	private BigVeins() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("big_vein"), BigVeinFeature.CODEC);
		BiomeModifications.create(JaymosPowers.id("smaller_vanilla_ores")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
			for (String id : VANILLA) {
				context.getGenerationSettings().removeFeature(GenerationStep.Decoration.UNDERGROUND_ORES,
					ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(id)));
			}
		});
		for (String id : List.of("big_vein_iron", "big_vein_copper", "big_vein_gold", "big_vein_diamond")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed(id));
		}
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
			placed("big_vein_ancient_debris"));
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}
