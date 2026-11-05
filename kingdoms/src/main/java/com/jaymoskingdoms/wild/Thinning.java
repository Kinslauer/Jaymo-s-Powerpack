package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * (0.7.595) Fewer wolves and walruses. Andy: "they also spawn way too much ... the spawns need to be way reduced we were
 * flooded with walrus and wolves". After every mod has added its spawns, each kind here keeps only {@link Thin#share} of
 * its weight in every biome (never below 1) and comes in smaller groups (at most {@link Thin#most}). This only changes new
 * spawns: animals already out in explored land stay.
 */
public final class Thinning {
	/** A kind's share of its old weight, and the most that come together. */
	record Thin(double share, int most) {
	}

	static final Map<String, Thin> THIN = Map.of(
		"minecraft:wolf", new Thin(0.25, 3),
		"jaymoskingdoms:feral_wolf", new Thin(0.2, 3),
		"cubeanimals:walrus", new Thin(0.2, 2));

	private Thinning() {
	}

	public static void register() {
		BiomeModifications.create(JaymosKingdoms.id("thinning")).add(ModificationPhase.POST_PROCESSING, BiomeSelectors.all(),
			context -> thin(context.getMobSpawnSettings()));
	}

	private static void thin(BiomeModificationContext.MobSpawnSettingsContext spawns) {
		for (MobCategory category : MobCategory.values()) {
			List<Weighted<MobSpawnSettings.SpawnerData>> redo = new ArrayList<>();
			for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.getMobs(category)) {
				if (THIN.containsKey(id(entry.value()))) {
					redo.add(entry);
				}
			}
			if (redo.isEmpty()) {
				continue;
			}
			spawns.removeSpawns((cat, data) -> cat == category && THIN.containsKey(id(data)));
			for (Weighted<MobSpawnSettings.SpawnerData> entry : redo) {
				MobSpawnSettings.SpawnerData data = entry.value();
				Thin thin = THIN.get(id(data));
				int most = Math.min(data.count().maxInclusive(), thin.most());
				int least = Math.min(data.count().minInclusive(), most);
				int weight = Math.max(1, (int) Math.round(entry.weight() * thin.share()));
				spawns.addSpawn(category, new MobSpawnSettings.SpawnerData(data.type(), net.minecraft.util.valueproviders.UniformInt.of(least, most)), weight);
			}
		}
	}

	private static String id(MobSpawnSettings.SpawnerData data) {
		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(data.type());
		return key.toString();
	}
}
