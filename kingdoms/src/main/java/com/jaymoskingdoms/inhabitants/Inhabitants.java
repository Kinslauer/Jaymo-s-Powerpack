package com.jaymoskingdoms.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Kingdoms' GeckoLib mobs (GeckoLib 5, MIT, Tslat and contributors), the home of the Inhabitants port (JeremySeq / Team
 * Synapse, MIT) and the Stormborn Tomb's dead (the tomb itself: Tada_no_ahiru, MIT). A mob ported from another mod keeps
 * that mod's licence and credit beside its art (kingdoms/art/). Each mob is registered here: its type (with its attributes
 * and spawn egg, through {@link Beasts#type}), where it may spawn and where in the world it does. How to add one:
 * docs/AI-HANDBOOK.md, "Adding a GeckoLib mob".
 *
 * <p>The barrow beetle is the small example the others copy; the draugr guards the Stormborn Tomb's halls (placed by the
 * tomb's spawners, never in the open). The tomb's skeletons and its boss are the silver skeleton and the Skeleton Lord,
 * drawn with GeckoLib too ({@code SilverSkeleton}, {@code SkeletonLord}).
 */
public final class Inhabitants {
	public static EntityType<BarrowBeetle> BARROW_BEETLE;
	public static EntityType<Draugr> DRAUGR;

	/** The beetle's natural spawn: its weight among the ambient mobs, and how many come together. */
	static final int BEETLE_WEIGHT = 6, BEETLE_MIN = 2, BEETLE_MAX = 4;
	/** Where beetles live: the cold biomes the Stormborn Tomb is built in (its own tag, so a datapack can change it). */
	static final TagKey<Biome> HAS_BARROW_BEETLE = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_barrow_beetle"));

	private Inhabitants() {
	}

	public static void register() {
		BARROW_BEETLE = Beasts.type("barrow_beetle", EntityType.Builder.of(BarrowBeetle::new, MobCategory.AMBIENT).sized(0.6F, 0.4F)
			.clientTrackingRange(8), BarrowBeetle.createAttributes());
		DRAUGR = Beasts.type("draugr", EntityType.Builder.of(Draugr::new, MobCategory.MONSTER).sized(0.7F, 2.2F).clientTrackingRange(8),
			Draugr.createAttributes());

		SpawnPlacements.register(BARROW_BEETLE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BarrowBeetle::checkSpawn);
		SpawnPlacements.register(DRAUGR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_BARROW_BEETLE), MobCategory.AMBIENT, BARROW_BEETLE, BEETLE_WEIGHT, BEETLE_MIN, BEETLE_MAX);
	}
}
