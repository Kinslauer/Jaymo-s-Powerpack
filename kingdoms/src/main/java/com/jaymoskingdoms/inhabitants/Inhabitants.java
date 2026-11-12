package com.jaymoskingdoms.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
 * <p>The barrow beetle is the small example the others copy; the Bogre (Inhabitants') keeps to itself in the mangroves; the
 * Impaler (Inhabitants') lurks in the deep caves at night; the Warped Clam (Inhabitants') sits on the End's islands; the draugr guards the Stormborn Tomb's halls (placed by the
 * tomb's spawners, never in the open). The tomb's skeletons and its boss are the silver skeleton and the Skeleton Lord,
 * drawn with GeckoLib too ({@code SilverSkeleton}, {@code SkeletonLord}).
 */
public final class Inhabitants {
	public static EntityType<BarrowBeetle> BARROW_BEETLE;
	public static EntityType<Draugr> DRAUGR;
	public static EntityType<Bogre> BOGRE;
	public static EntityType<Impaler> IMPALER;
	public static EntityType<WarpedClam> WARPED_CLAM;
	/** The spikes an Impaler throws off its arm. */
	public static EntityType<ImpalerSpike> IMPALER_SPIKE;

	/** The beetle's natural spawn: its weight among the ambient mobs, and how many come together. */
	static final int BEETLE_WEIGHT = 6, BEETLE_MIN = 2, BEETLE_MAX = 4;
	/** Where beetles live: the cold biomes the Stormborn Tomb is built in (its own tag, so a datapack can change it). */
	static final TagKey<Biome> HAS_BARROW_BEETLE = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_barrow_beetle"));
	/** Where Bogres live: the mangrove swamps (its own tag). One at a time, and never near another ({@link Bogre#checkSpawn}). */
	static final TagKey<Biome> HAS_BOGRE = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_bogre"));
	/** Where Impalers come up at night: the Overworld's caves (its own tag; {@link Impaler#checkSpawn} keeps it deep, dark and night). */
	static final TagKey<Biome> HAS_IMPALER = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_impaler"));
	/** Where Warped Clams live: the End's outer islands, and so Arsenal's pocket rooms, which lie on the End's side of the rift. */
	static final TagKey<Biome> HAS_WARPED_CLAM = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_warped_clam"));
	/** How many clams come together. */
	static final int CLAM_MIN = 1, CLAM_MAX = 2;

	private Inhabitants() {
	}

	public static void register() {
		BARROW_BEETLE = Beasts.type("barrow_beetle", EntityType.Builder.of(BarrowBeetle::new, MobCategory.AMBIENT).sized(0.6F, 0.4F)
			.clientTrackingRange(8), BarrowBeetle.createAttributes());
		DRAUGR = Beasts.type("draugr", EntityType.Builder.of(Draugr::new, MobCategory.MONSTER).sized(0.7F, 2.2F).clientTrackingRange(8),
			Draugr.createAttributes());
		BOGRE = Beasts.type("bogre", EntityType.Builder.of(Bogre::new, MobCategory.MONSTER).sized(1.75F, 3.5F).clientTrackingRange(10),
			Bogre.createAttributes());
		IMPALER = Beasts.type("impaler", EntityType.Builder.of(Impaler::new, MobCategory.MONSTER).sized(1.2F, 2.2F).clientTrackingRange(10),
			Impaler.createAttributes());
		WARPED_CLAM = Beasts.type("warped_clam", EntityType.Builder.of(WarpedClam::new, MobCategory.CREATURE).sized(1.0F, 0.45F).clientTrackingRange(8),
			WarpedClam.createAttributes());
		ResourceKey<EntityType<?>> spike = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("impaler_spike"));
		IMPALER_SPIKE = Registry.register(BuiltInRegistries.ENTITY_TYPE, spike, EntityType.Builder.<ImpalerSpike>of(ImpalerSpike::new, MobCategory.MISC)
			.sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(10).build(spike));

		SpawnPlacements.register(BARROW_BEETLE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BarrowBeetle::checkSpawn);
		SpawnPlacements.register(DRAUGR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		SpawnPlacements.register(BOGRE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Bogre::checkSpawn);
		SpawnPlacements.register(IMPALER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Impaler::checkSpawn);
		SpawnPlacements.register(WARPED_CLAM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WarpedClam::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_IMPALER), MobCategory.MONSTER, IMPALER, Impaler.SPAWN_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_WARPED_CLAM), MobCategory.CREATURE, WARPED_CLAM, WarpedClam.SPAWN_WEIGHT, CLAM_MIN, CLAM_MAX);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_BOGRE), MobCategory.MONSTER, BOGRE, Bogre.SPAWN_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_BARROW_BEETLE), MobCategory.AMBIENT, BARROW_BEETLE, BEETLE_WEIGHT, BEETLE_MIN, BEETLE_MAX);
	}
}
