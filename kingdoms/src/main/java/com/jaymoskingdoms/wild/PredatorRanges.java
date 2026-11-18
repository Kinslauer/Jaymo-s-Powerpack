package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-18): "Tigers and other predators should spawn in their respective areas (tigers in jungle w other big
 * cats, bears forests etc)". The other mods put them all over: Naturalist's big cats came in deserts, badlands, snowy
 * plains, cherry groves and mountains, Bear Minimum's brown bears on the frozen peaks and ice spikes. Each beast in
 * {@link #RANGES} loses every spawn its own mod gave it and spawns only in its home ({@code jaymoskingdoms:has_<home>}),
 * by the weight and pack size here:
 * <ul>
 * <li><b>The jungle's big cats</b> ({@code has_big_cats}): Naturalist's tiger, which is drawn by where it's born, so the
 * jungles hold tigers, the sparse jungle leopards, the bamboo jungle white tigers and the mangroves black panthers.</li>
 * <li><b>Lions</b> on the savannas ({@code has_lion}), in prides; <b>snow leopards</b> on the snowy slopes, groves and
 * peaks ({@code has_snow_leopard}).</li>
 * <li><b>Brown bears</b> (Naturalist's and Bear Minimum's) in the taiga and the high woods and meadows
 * ({@code has_brown_bear}); <b>black bears</b> (Naturalist's, Bear Minimum's, Cube Animals') in the forests
 * ({@code has_black_bear}).</li>
 * </ul>
 * A mod that isn't installed is passed over. The removals run after the mods add theirs and after {@code Encounters}
 * takes out Cube Animals' duplicate tiger; ours go in last.
 */
public final class PredatorRanges {
	/** One beast's home: its entity id, the biome tag it lives in, its weight, and how many come together. */
	record Range(String mob, String home, int weight, int min, int max) {
	}

	static final List<Range> RANGES = List.of(
		new Range("naturalist:tiger", "has_big_cats", 6, 1, 1),
		new Range("naturalist:lion", "has_lion", 6, 2, 4),
		new Range("cubeanimals:snowleopard", "has_snow_leopard", 4, 1, 1),
		new Range("naturalist:bear", "has_brown_bear", 3, 1, 1),
		new Range("bearminimum:brown_bear", "has_brown_bear", 3, 1, 1),
		new Range("naturalist:black_bear", "has_black_bear", 2, 1, 1),
		new Range("bearminimum:black_bear", "has_black_bear", 2, 1, 1),
		new Range("cubeanimals:blackbear", "has_black_bear", 2, 1, 1));

	private PredatorRanges() {
	}

	public static void register() {
		BiomeModifications.create(JaymosKingdoms.id("predator_ranges_cleared")).add(ModificationPhase.REPLACEMENTS, BiomeSelectors.all(), context -> {
			for (Range r : RANGES) {
				EntityType<?> type = type(r.mob());
				if (type != null) {
					context.getMobSpawnSettings().removeSpawnsOfEntityType(type);
				}
			}
		});
		for (Range r : RANGES) {
			TagKey<net.minecraft.world.level.biome.Biome> home = TagKey.create(Registries.BIOME, JaymosKingdoms.id(r.home()));
			BiomeModifications.create(JaymosKingdoms.id("predator_range_" + Identifier.parse(r.mob()).getPath() + "_"
				+ Identifier.parse(r.mob()).getNamespace())).add(ModificationPhase.POST_PROCESSING, BiomeSelectors.tag(home), context -> {
					EntityType<?> type = type(r.mob());
					if (type != null) {
						context.getMobSpawnSettings().addSpawn(type, r.weight(), r.min(), r.max());
					}
				});
		}
	}

	private static @Nullable EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
	}
}
