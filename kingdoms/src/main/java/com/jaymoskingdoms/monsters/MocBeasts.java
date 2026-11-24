package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The beasts of Mo' Creatures that Kingdoms keeps (after Mo' Creatures 1.10.2, GPL-3, see {@code licenses/mocreatures}), and
 * where each stands in the world:
 * <ul>
 * <li>the Nether scorpion ({@link Scorpion}) on the dry floors of the Nether, fireproof, a mother with her brood on her back;</li>
 * <li>the manticore ({@link Manticore}) in the halls of Jaymo's Powers' Sentry Ruins, the Aether's own dungeon, and nowhere else:
 * it spawns only where a spawn spot lies inside one of those ruins' pieces, so the badlands and the Nether stay without it;</li>
 * <li>the zebra ({@link Zebra}) in herds on the savannas, and its cross with the horse, the zorse, that is only ever bred;</li>
 * <li>the big golem ({@link BigGolem}) among the ruins of the old age (the structure files list it among their spawns): the old
 * peoples left guardians in their halls, and the golem is one that builds itself out of the stone about it.</li>
 * </ul>
 * The beasts that are not monsters (the wyvern, the winged and horned horses, the zebra) are registered by their own
 * classes ({@code Dragons}, {@code WonderHorses}, {@code Zebras}).
 */
public final class MocBeasts {
	static final int SCORPION_WEIGHT = 12, MANTICORE_WEIGHT = 6;
	/** The zebra herds of the savannas: how likely, and how many in a herd. */
	static final int ZEBRA_WEIGHT = 10, ZEBRA_MIN = 3, ZEBRA_MAX = 6;
	/** The Aether's dungeon the manticore keeps to (Jaymo's Powers; optional, so only its id is named here). */
	static final Identifier SENTRY_RUINS = Identifier.fromNamespaceAndPath("jaymospowers", "sentry_ruins");
	static final TagKey<Biome> SENTRY_RUINS_BIOMES = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("jaymospowers", "has_structure/sentry_ruins"));

	public static EntityType<Scorpion> NETHER_SCORPION;
	public static EntityType<Manticore> MANTICORE;
	public static EntityType<BigGolem> BIG_GOLEM;
	public static EntityType<GolemRock> GOLEM_ROCK;
	public static EntityType<Zebra> ZEBRA, ZORSE;
	public static Item MANTICORE_SPINE;
	public static Item NETHER_SCORPION_SPAWN_EGG, MANTICORE_SPAWN_EGG, BIG_GOLEM_SPAWN_EGG, ZEBRA_SPAWN_EGG, ZORSE_SPAWN_EGG;

	private MocBeasts() {
	}

	public static void register() {
		NETHER_SCORPION = type("nether_scorpion", EntityType.Builder.of(Scorpion::new, MobCategory.MONSTER).sized(1.1F, 0.8F).fireImmune().clientTrackingRange(8));
		MANTICORE = type("manticore", EntityType.Builder.of(Manticore::new, MobCategory.MONSTER).sized(1.5F, 1.6F).clientTrackingRange(10));
		BIG_GOLEM = type("big_golem", EntityType.Builder.of(BigGolem::new, MobCategory.MONSTER).sized(1.8F, 3.4F).clientTrackingRange(10));
		GOLEM_ROCK = type("golem_rock", EntityType.Builder.<GolemRock>of(GolemRock::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1));
		ZEBRA = type("zebra", EntityType.Builder.<Zebra>of((t, l) -> new Zebra(t, l, false), MobCategory.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F)
			.passengerAttachments(1.44375F).clientTrackingRange(10));
		ZORSE = type("zorse", EntityType.Builder.<Zebra>of((t, l) -> new Zebra(t, l, true), MobCategory.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F)
			.passengerAttachments(1.44375F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(ZEBRA, Zebra.createAttributes(false));
		FabricDefaultAttributeRegistry.register(ZORSE, Zebra.createAttributes(true));
		FabricDefaultAttributeRegistry.register(NETHER_SCORPION, Scorpion.createAttributes());
		FabricDefaultAttributeRegistry.register(MANTICORE, Manticore.createAttributes());
		FabricDefaultAttributeRegistry.register(BIG_GOLEM, BigGolem.createAttributes());

		// the wyvern, its egg and its nests (Dragons' own package: it is a WindDragon)
		com.jaymoskingdoms.dragons.Wyverns.register();

		MANTICORE_SPINE = item("manticore_spine", Item::new, new Item.Properties());
		NETHER_SCORPION_SPAWN_EGG = item("nether_scorpion_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(NETHER_SCORPION));
		MANTICORE_SPAWN_EGG = item("manticore_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(MANTICORE));
		BIG_GOLEM_SPAWN_EGG = item("big_golem_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BIG_GOLEM));
		ZEBRA_SPAWN_EGG = item("zebra_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ZEBRA));
		ZORSE_SPAWN_EGG = item("zorse_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ZORSE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(ZEBRA_SPAWN_EGG);
			output.accept(ZORSE_SPAWN_EGG);
			output.accept(NETHER_SCORPION_SPAWN_EGG);
			output.accept(MANTICORE_SPAWN_EGG);
			output.accept(BIG_GOLEM_SPAWN_EGG);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(MANTICORE_SPINE));

		SpawnPlacements.register(NETHER_SCORPION, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Scorpion::checkSpawn);
		SpawnPlacements.register(MANTICORE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, MocBeasts::checkManticoreSpawn);
		SpawnPlacements.register(BIG_GOLEM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.NETHER_WASTES, net.minecraft.world.level.biome.Biomes.BASALT_DELTAS),
			MobCategory.MONSTER, NETHER_SCORPION, SCORPION_WEIGHT, 1, 2);
		SpawnPlacements.register(ZEBRA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.SAVANNA, net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU,
			net.minecraft.world.level.biome.Biomes.WINDSWEPT_SAVANNA), MobCategory.CREATURE, ZEBRA, ZEBRA_WEIGHT, ZEBRA_MIN, ZEBRA_MAX);
		BiomeModifications.addSpawn(ctx -> ctx.hasTag(SENTRY_RUINS_BIOMES), MobCategory.MONSTER, MANTICORE, MANTICORE_WEIGHT, 1, 1);
	}

	/** Only inside a hall of the Sentry Ruins, whatever the biome. */
	public static boolean checkManticoreSpawn(EntityType<Manticore> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) {
			return true;
		}
		return level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && level.getLevel() instanceof ServerLevel server
			&& server.structureManager().getStructureWithPieceAt(pos, holder -> holder.unwrapKey().map(k -> k.identifier().equals(SENTRY_RUINS)).orElse(false)).isValid();
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	static Item item(String name, java.util.function.Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}
}
