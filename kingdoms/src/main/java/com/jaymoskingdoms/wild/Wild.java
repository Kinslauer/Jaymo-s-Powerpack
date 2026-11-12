package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

/**
 * (0.5.55) The wild beasts rebuilt after Better Animals Plus (its models and art are CC BY-NC-SA 4.0, by cybercat5555,
 * itsmeowdev and Wolverbyte; credited in {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}, and this
 * package and its client side are under that licence too): feral wolves, coyotes, tarantulas and moose. (0.7.526) They
 * spawn on their own where they roam ({@code Encounters}, biome tags {@code jaymoskingdoms:has_<beast>}), no longer as
 * wilderness encounters. (0.7.610) And the grizzly ({@link Grizzly}), our own mini-boss bear ((0.7.611) its model and
 * coat our own, from {@code docs/Grizzly-Model-Spec.md}).
 */
public final class Wild {
	public static EntityType<FeralWolf> FERAL_WOLF;
	public static EntityType<Coyote> COYOTE;
	public static EntityType<Tarantula> TARANTULA;
	public static EntityType<Moose> MOOSE;
	/** (0.7.610) The grizzly mini-boss, with its spawn egg. */
	public static EntityType<Grizzly> GRIZZLY;
	/** The muskox of the far cold ({@link Muskox}), with its spawn egg; herds ({@link HerdPull}) and fed hunters ({@link FedPredators}) come with it. */
	public static EntityType<Muskox> MUSKOX;
	/** Where grizzlies roam, and their natural spawn weight (rare: one in a wide stretch, see {@link Grizzly#checkSpawn}). */
	static final TagKey<Biome> HAS_GRIZZLY = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_grizzly"));
	static final int GRIZZLY_WEIGHT = 3;

	private Wild() {
	}

	public static void register() {
		FERAL_WOLF = type("feral_wolf", EntityType.Builder.of(FeralWolf::new, MobCategory.CREATURE).sized(1.2F, 1.3F).clientTrackingRange(10));
		COYOTE = type("coyote", EntityType.Builder.of(Coyote::new, MobCategory.CREATURE).sized(0.8F, 0.9F).clientTrackingRange(10));
		TARANTULA = type("tarantula", EntityType.Builder.of(Tarantula::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(8));
		MOOSE = type("moose", EntityType.Builder.of(Moose::new, MobCategory.CREATURE).sized(2.0F, 2.8F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(FERAL_WOLF, FeralWolf.createAttributes());
		FabricDefaultAttributeRegistry.register(COYOTE, Coyote.createAttributes());
		FabricDefaultAttributeRegistry.register(TARANTULA, Tarantula.createAttributes());
		FabricDefaultAttributeRegistry.register(MOOSE, Moose.createAttributes());
		GRIZZLY = com.jaymoskingdoms.beasts.Beasts.type("grizzly", EntityType.Builder.of(Grizzly::new, MobCategory.CREATURE).sized(2.0F, 1.9F)
			.clientTrackingRange(10), Grizzly.createAttributes());
		SpawnPlacements.register(GRIZZLY, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Grizzly::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_GRIZZLY), MobCategory.CREATURE, GRIZZLY, GRIZZLY_WEIGHT, 1, 1);
		MUSKOX = com.jaymoskingdoms.beasts.Beasts.type("muskox", EntityType.Builder.of(Muskox::new, MobCategory.CREATURE).sized(1.4F, 1.55F)
			.clientTrackingRange(10), Muskox.createAttributes());
		SpawnPlacements.register(MUSKOX, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Muskox::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(Muskox.HAS_MUSKOX), MobCategory.CREATURE, MUSKOX, Muskox.SPAWN_WEIGHT, Muskox.SPAWN_LEAST,
			Muskox.SPAWN_MOST);
		HerdPull.register();
		FedPredators.register();
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
