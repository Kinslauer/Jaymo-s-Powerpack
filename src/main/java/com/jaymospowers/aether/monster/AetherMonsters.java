package com.jaymospowers.aether.monster;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AercloudBlock;
import com.jaymospowers.aether.AetherBlocks;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Aether II hostile mobs (docs/aether.md, "Aether II monsters"): the zephyr, tempest, cockatrice, blue and golden
 * swets, aechor plant, carrion sprout, skephid, arkenium and gravitite talutons and bladeshroom hunter; their six
 * kinds of shot; the four afflictions ({@link AetherEffects}); their drops and spawn eggs; and where they spawn (the
 * Aether's biome tags, which the dimension's biomes carry; they are looked up by tag, never by biome).
 */
public final class AetherMonsters {
	// ---- tuning: spawn weight, smallest and largest group (Aether II's numbers) ----
	public static final int ZEPHYR_WEIGHT = 1, ZEPHYR_MIN = 1, ZEPHYR_MAX = 1, ZEPHYR_IRRADIATED_MAX = 2;
	public static final int TEMPEST_WEIGHT = 1, COCKATRICE_WEIGHT = 4;
	public static final int BLUE_SWET_WEIGHT = 2, GOLDEN_SWET_WEIGHT = 1, SWET_MIN = 1, SWET_MAX = 2;
	public static final int ARKENIUM_WEIGHT = 3, GRAVITITE_WEIGHT = 4, SKEPHID_WEIGHT = 1;
	public static final int AECHOR_WEIGHT = 5, CARRION_WEIGHT = 3;
	/** Ours: Aether II places the hunter with structures; here it also turns up rarely in the magnetic mushroom woods. */
	public static final int HUNTER_WEIGHT = 2;
	/** A zephyr's natural spawn passes only one roll in this many. */
	public static final int ZEPHYR_ROLL = 11;
	/** A plant needs more light than this on its block (it grows in the open). */
	public static final int PLANT_LIGHT = 8;

	public static EntityType<Zephyr> ZEPHYR;
	public static EntityType<Tempest> TEMPEST;
	public static EntityType<Cockatrice> COCKATRICE;
	public static EntityType<Swet> BLUE_SWET, GOLDEN_SWET;
	public static EntityType<AechorPlant> AECHOR_PLANT;
	public static EntityType<CarrionSprout> CARRION_SPROUT;
	public static EntityType<Skephid> SKEPHID;
	public static EntityType<ArkeniumTaluton> ARKENIUM_TALUTON;
	public static EntityType<GravititeTaluton> GRAVITITE_TALUTON;
	public static EntityType<BladeshroomHunter> BLADESHROOM_HUNTER;

	private static final Map<AetherShot.Kind, EntityType<AetherShot>> SHOT_TYPES = new EnumMap<>(AetherShot.Kind.class);
	private static final Map<AetherShot.Kind, Item> SHOT_ITEMS = new EnumMap<>(AetherShot.Kind.class);

	/** The drops: gel and sugar from swets, petals from aechor plants, feathers from cockatrices, twine from skephids. */
	public static Item SWET_GEL, SWET_SUGAR, AECHOR_PETAL, COCKATRICE_FEATHER, CLOUDTWINE;
	public static Item ZEPHYR_SPAWN_EGG, TEMPEST_SPAWN_EGG, COCKATRICE_SPAWN_EGG, BLUE_SWET_SPAWN_EGG, GOLDEN_SWET_SPAWN_EGG, AECHOR_PLANT_SPAWN_EGG,
		CARRION_SPROUT_SPAWN_EGG, SKEPHID_SPAWN_EGG, ARKENIUM_TALUTON_SPAWN_EGG, GRAVITITE_TALUTON_SPAWN_EGG, BLADESHROOM_HUNTER_SPAWN_EGG;

	private AetherMonsters() {
	}

	public static EntityType<AetherShot> shotType(AetherShot.Kind kind) {
		return SHOT_TYPES.get(kind);
	}

	public static Item shotItem(AetherShot.Kind kind) {
		return SHOT_ITEMS.get(kind);
	}

	/** Which kind of shot an entity type is. */
	public static AetherShot.Kind kindOf(EntityType<?> type) {
		for (Map.Entry<AetherShot.Kind, EntityType<AetherShot>> entry : SHOT_TYPES.entrySet()) {
			if (entry.getValue() == type) {
				return entry.getKey();
			}
		}
		return AetherShot.Kind.ZEPHYR_WEB;
	}

	public static void init() {
		AetherEffects.init();

		SWET_GEL = item("swet_gel", new Item.Properties());
		SWET_SUGAR = item("swet_sugar", new Item.Properties());
		AECHOR_PETAL = item("aechor_petal", new Item.Properties());
		COCKATRICE_FEATHER = item("cockatrice_feather", new Item.Properties());
		CLOUDTWINE = item("cloudtwine", new Item.Properties());

		// the shots: each is drawn as a small item sprite (never in the creative tab)
		for (AetherShot.Kind kind : AetherShot.Kind.values()) {
			SHOT_ITEMS.put(kind, item(kind.id, new Item.Properties()));
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(kind.id));
			SHOT_TYPES.put(kind, Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<AetherShot>of(AetherShot::new, MobCategory.MISC)
				.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).build(key)));
		}

		ZEPHYR = type("zephyr", EntityType.Builder.of(Zephyr::new, MobCategory.MONSTER).sized(2.0F, 1.75F).eyeHeight(1.2F).clientTrackingRange(10).notInPeaceful());
		TEMPEST = type("tempest", EntityType.Builder.of(Tempest::new, MobCategory.MONSTER).sized(1.5F, 1.4F).eyeHeight(0.85F).clientTrackingRange(10).notInPeaceful());
		COCKATRICE = type("cockatrice", EntityType.Builder.of(Cockatrice::new, MobCategory.MONSTER).sized(0.9F, 2.15F).clientTrackingRange(10).notInPeaceful());
		BLUE_SWET = type("blue_swet", EntityType.Builder.of(Swet::new, MobCategory.MONSTER).sized(0.95F, 0.95F).clientTrackingRange(8).notInPeaceful());
		GOLDEN_SWET = type("golden_swet", EntityType.Builder.of(Swet::new, MobCategory.MONSTER).sized(0.95F, 0.95F).clientTrackingRange(8).notInPeaceful());
		AECHOR_PLANT = type("aechor_plant", EntityType.Builder.of(AechorPlant::new, MobCategory.MONSTER).sized(0.9F, 0.6F).clientTrackingRange(8).notInPeaceful());
		CARRION_SPROUT = type("carrion_sprout", EntityType.Builder.of(CarrionSprout::new, MobCategory.MONSTER).sized(1.0F, 1.0F).clientTrackingRange(8).notInPeaceful());
		SKEPHID = type("skephid", EntityType.Builder.of(Skephid::new, MobCategory.MONSTER).sized(0.8F, 0.8F).clientTrackingRange(8).notInPeaceful());
		ARKENIUM_TALUTON = type("arkenium_taluton", EntityType.Builder.of(ArkeniumTaluton::new, MobCategory.MONSTER).sized(1.0F, 1.65F).eyeHeight(1.25F).clientTrackingRange(10).notInPeaceful());
		GRAVITITE_TALUTON = type("gravitite_taluton", EntityType.Builder.of(GravititeTaluton::new, MobCategory.MONSTER).sized(0.75F, 1.9F).eyeHeight(1.4F).clientTrackingRange(10).notInPeaceful());
		BLADESHROOM_HUNTER = type("bladeshroom_hunter", EntityType.Builder.of(BladeshroomHunter::new, MobCategory.MONSTER).sized(0.9F, 1.3F).eyeHeight(1.2F).clientTrackingRange(8).notInPeaceful());

		FabricDefaultAttributeRegistry.register(ZEPHYR, Zephyr.createAttributes());
		FabricDefaultAttributeRegistry.register(TEMPEST, Tempest.createAttributes());
		FabricDefaultAttributeRegistry.register(COCKATRICE, Cockatrice.createAttributes());
		FabricDefaultAttributeRegistry.register(BLUE_SWET, Swet.createAttributes());
		FabricDefaultAttributeRegistry.register(GOLDEN_SWET, Swet.createAttributes());
		FabricDefaultAttributeRegistry.register(AECHOR_PLANT, AechorPlant.createAttributes());
		FabricDefaultAttributeRegistry.register(CARRION_SPROUT, CarrionSprout.createAttributes());
		FabricDefaultAttributeRegistry.register(SKEPHID, Skephid.createAttributes());
		FabricDefaultAttributeRegistry.register(ARKENIUM_TALUTON, ArkeniumTaluton.createAttributes());
		FabricDefaultAttributeRegistry.register(GRAVITITE_TALUTON, GravititeTaluton.createAttributes());
		FabricDefaultAttributeRegistry.register(BLADESHROOM_HUNTER, BladeshroomHunter.createAttributes());

		ZEPHYR_SPAWN_EGG = egg("zephyr_spawn_egg", ZEPHYR);
		TEMPEST_SPAWN_EGG = egg("tempest_spawn_egg", TEMPEST);
		COCKATRICE_SPAWN_EGG = egg("cockatrice_spawn_egg", COCKATRICE);
		BLUE_SWET_SPAWN_EGG = egg("blue_swet_spawn_egg", BLUE_SWET);
		GOLDEN_SWET_SPAWN_EGG = egg("golden_swet_spawn_egg", GOLDEN_SWET);
		AECHOR_PLANT_SPAWN_EGG = egg("aechor_plant_spawn_egg", AECHOR_PLANT);
		CARRION_SPROUT_SPAWN_EGG = egg("carrion_sprout_spawn_egg", CARRION_SPROUT);
		SKEPHID_SPAWN_EGG = egg("skephid_spawn_egg", SKEPHID);
		ARKENIUM_TALUTON_SPAWN_EGG = egg("arkenium_taluton_spawn_egg", ARKENIUM_TALUTON);
		GRAVITITE_TALUTON_SPAWN_EGG = egg("gravitite_taluton_spawn_egg", GRAVITITE_TALUTON);
		BLADESHROOM_HUNTER_SPAWN_EGG = egg("bladeshroom_hunter_spawn_egg", BLADESHROOM_HUNTER);

		placements();
		spawns();

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			for (Item item : List.of(SWET_GEL, SWET_SUGAR, AECHOR_PETAL, COCKATRICE_FEATHER, CLOUDTWINE, ZEPHYR_SPAWN_EGG, TEMPEST_SPAWN_EGG,
				COCKATRICE_SPAWN_EGG, BLUE_SWET_SPAWN_EGG, GOLDEN_SWET_SPAWN_EGG, AECHOR_PLANT_SPAWN_EGG, CARRION_SPROUT_SPAWN_EGG, SKEPHID_SPAWN_EGG,
				ARKENIUM_TALUTON_SPAWN_EGG, GRAVITITE_TALUTON_SPAWN_EGG, BLADESHROOM_HUNTER_SPAWN_EGG)) {
				output.accept(item);
			}
		});
	}

	// ---- where they may stand ----

	/** Stone a darkness monster stands on: the Aether's underground. */
	private static boolean underground(BlockState state) {
		return state.is(AetherBlocks.HOLYSTONE) || state.is(AetherBlocks.MOSSY_HOLYSTONE) || state.is(AetherBlocks.UNDERSHALE)
			|| state.is(AetherBlocks.AGIOSITE) || state.is(AetherBlocks.ICHORITE) || state.is(AetherBlocks.FERROSITE)
			|| state.is(AetherBlocks.IRRADIATED_HOLYSTONE);
	}

	private static boolean groundLike(BlockState state) {
		return PlantMonster.grass(state) || state.is(AetherBlocks.AETHER_DIRT);
	}

	private static boolean awake(LevelAccessor level) {
		return level.getDifficulty() != Difficulty.PEACEFUL;
	}

	/** Dark enough for the dimension's monster light, and not peaceful. */
	private static boolean dark(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		return awake(level) && Monster.isDarkEnoughToSpawn(level, pos, random);
	}

	private static <T extends Mob> void place(EntityType<T> type, SpawnPlacementType where, SpawnPlacements.SpawnPredicate<T> rule) {
		SpawnPlacements.register(type, where, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rule);
	}

	private static void placements() {
		// the zephyr: open sky only, one natural spawn roll in eleven
		place(ZEPHYR, SpawnPlacementTypes.NO_RESTRICTIONS, (t, level, reason, pos, random) -> awake(level) && level.canSeeSky(pos)
			&& (reason != EntitySpawnReason.NATURAL || random.nextInt(ZEPHYR_ROLL) == 0));
		// blighted: on cloud, grass or the stone below, in the dark
		SpawnPlacements.SpawnPredicate<Tempest> tempest = (t, level, reason, pos, random) -> dark(level, pos, random) && blightGround(level.getBlockState(pos.below()));
		place(TEMPEST, SpawnPlacementTypes.ON_GROUND, tempest);
		place(COCKATRICE, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && blightGround(level.getBlockState(pos.below())));
		// the dark: swets on ichorite or aether ground, the rest on the stone
		place(BLUE_SWET, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && swetGround(level.getBlockState(pos.below())));
		place(GOLDEN_SWET, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && swetGround(level.getBlockState(pos.below())));
		place(SKEPHID, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && underground(level.getBlockState(pos.below())));
		place(ARKENIUM_TALUTON, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && underground(level.getBlockState(pos.below())));
		place(GRAVITITE_TALUTON, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && underground(level.getBlockState(pos.below())));
		place(BLADESHROOM_HUNTER, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> dark(level, pos, random) && underground(level.getBlockState(pos.below()))
			|| awake(level) && Monster.isDarkEnoughToSpawn(level, pos, random) && groundLike(level.getBlockState(pos.below())));
		// the plants: aether grass, open sky, in the light
		place(AECHOR_PLANT, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> plantRule(level, pos));
		place(CARRION_SPROUT, SpawnPlacementTypes.ON_GROUND, (t, level, reason, pos, random) -> plantRule(level, pos));
	}

	private static boolean blightGround(BlockState state) {
		return state.getBlock() instanceof AercloudBlock || groundLike(state) || underground(state);
	}

	private static boolean swetGround(BlockState state) {
		return state.is(AetherBlocks.ICHORITE) || groundLike(state) || underground(state);
	}

	private static boolean plantRule(ServerLevelAccessor level, BlockPos pos) {
		return awake(level) && PlantMonster.grass(level.getBlockState(pos.below())) && level.canSeeSky(pos) && level.getRawBrightness(pos, 0) > PLANT_LIGHT;
	}

	// ---- where they live ----

	private static TagKey<Biome> tag(String name) {
		return TagKey.create(Registries.BIOME, JaymosPowers.id(name));
	}

	@SafeVarargs
	private static Predicate<BiomeSelectionContext> any(Predicate<BiomeSelectionContext>... selectors) {
		Predicate<BiomeSelectionContext> all = selectors[0];
		for (int i = 1; i < selectors.length; i++) {
			all = all.or(selectors[i]);
		}
		return all;
	}

	private static void spawns() {
		Predicate<BiomeSelectionContext> everywhere = BiomeSelectors.tag(tag("is_aether"));
		Predicate<BiomeSelectionContext> irradiated = BiomeSelectors.tag(tag("is_aether_irradiated"));
		Predicate<BiomeSelectionContext> magnetic = BiomeSelectors.tag(tag("is_aether_magnetic"));
		// the twelve ordinary biomes (highfields, magnetic, arctic) and those plus the irradiated two
		Predicate<BiomeSelectionContext> twelve = any(BiomeSelectors.tag(tag("is_aether_highfields")), magnetic, BiomeSelectors.tag(tag("is_aether_arctic")));
		Predicate<BiomeSelectionContext> fourteen = any(twelve, irradiated);

		BiomeModifications.addSpawn(everywhere.and(irradiated.negate()), MobCategory.MONSTER, ZEPHYR, ZEPHYR_WEIGHT, ZEPHYR_MIN, ZEPHYR_MAX);
		BiomeModifications.addSpawn(irradiated, MobCategory.MONSTER, ZEPHYR, ZEPHYR_WEIGHT, ZEPHYR_MIN, ZEPHYR_IRRADIATED_MAX);
		BiomeModifications.addSpawn(fourteen, MobCategory.MONSTER, TEMPEST, TEMPEST_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, COCKATRICE, COCKATRICE_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, BLUE_SWET, BLUE_SWET_WEIGHT, SWET_MIN, SWET_MAX);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, GOLDEN_SWET, GOLDEN_SWET_WEIGHT, SWET_MIN, SWET_MAX);
		BiomeModifications.addSpawn(fourteen, MobCategory.MONSTER, ARKENIUM_TALUTON, ARKENIUM_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(fourteen, MobCategory.MONSTER, GRAVITITE_TALUTON, GRAVITITE_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, SKEPHID, SKEPHID_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, AECHOR_PLANT, AECHOR_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(twelve, MobCategory.MONSTER, CARRION_SPROUT, CARRION_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(magnetic, MobCategory.MONSTER, BLADESHROOM_HUNTER, HUNTER_WEIGHT, 1, 1);
	}

	// ---- registration ----

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String id, EntityType<? extends Mob> type) {
		return item(id, p -> new SpawnEggItem(p.spawnEgg(type)), new Item.Properties());
	}

	private static Item item(String id, Item.Properties properties) {
		return item(id, Item::new, properties);
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
