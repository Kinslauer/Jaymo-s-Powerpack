package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The satyrs ({@link Satyr}) and the cyclops ({@link Cyclops}): their entities, the satyrs' groves in the woods and
 * meadows ({@code jaymoskingdoms:has_satyr}), and the satyrs' pipe tunes (our own, kingdoms/tools/gen_satyr_pipes.py).
 * The cyclops spawns as a monster of legend ({@code Encounters}) on the peaks and stony shores
 * ({@code jaymoskingdoms:has_cyclops}), its hall cut for it where a slope will take one ({@link CyclopsCaves}).
 */
public final class Satyrs {
	/** A grove's weight among the creatures of its woods (a sheep's is 12), and how many meet. */
	static final int SATYR_WEIGHT = 3, GROVE_MIN = 3, GROVE_MAX = 5;
	static final TagKey<Biome> HAS_SATYR = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_satyr"));
	/** How loud the pipes are played (a sound's range is 16 blocks a unit of volume). */
	static final float TUNE_VOLUME = 3.0F;
	/** Each tune's length (ticks), in the order of {@link #TUNES}. */
	static final int[] TUNE_TICKS = {504, 590, 460};
	static final List<Holder<SoundEvent>> TUNES = new ArrayList<>();

	public static EntityType<Satyr> SATYR;
	public static EntityType<Cyclops> CYCLOPS;

	private Satyrs() {
	}

	static void register() {
		CYCLOPS = Beasts.type("cyclops", EntityType.Builder.of(Cyclops::new, MobCategory.MONSTER).sized(1.8F, 4.2F).eyeHeight(3.7F)
			.clientTrackingRange(12), Cyclops.createAttributes());
		CyclopsCaves.register();
		SATYR = Beasts.type("satyr", EntityType.Builder.of(Satyr::new, MobCategory.CREATURE).sized(0.6F, 1.85F).eyeHeight(1.6F)
			.clientTrackingRange(10), Satyr.createAttributes());
		SpawnPlacements.register(SATYR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Satyr::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_SATYR), MobCategory.CREATURE, SATYR, SATYR_WEIGHT, GROVE_MIN, GROVE_MAX);
		for (int i = 1; i <= TUNE_TICKS.length; i++) {
			Identifier id = JaymosKingdoms.id("satyr.pipes_" + i);
			TUNES.add(Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
		}
	}
}
