package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Track C: registers every new species of ours: its entity ({@link WildFish}, or a {@link Critter} for the walkers and the
 * jellyfish), its spawns (each in the waters and climate of its kind, {@link Species#livesIn}), a spawn egg, and its raw and
 * cooked items (a big shark has none: it's cut up on the shore into shark meat, and its jaw kept, {@link #SHARK_JAW}).
 */
public final class FishSpecies {
	/** Food: raw fish and shellfish, then cooked white fish, oily fish and shellfish. */
	static final int RAW_FOOD = 2, SHELL_RAW_FOOD = 1, WHITE_FOOD = 5, OILY_FOOD = 6, SHELL_FOOD = 4;
	static final float RAW_SAT = 0.1F, COOKED_SAT = 0.6F, OILY_SAT = 0.8F;
	/** Health of a fish at its usual weight: a cod's 3, growing with the weight, at most this. */
	static final double HEALTH_MOST = 40.0;

	private static final Map<EntityType<?>, Species> BY_TYPE = new HashMap<>();
	private static final Map<Species, EntityType<?>> TYPES = new EnumMap<>(Species.class);
	private static final Map<Species, Item> RAW = new EnumMap<>(Species.class);
	private static final Map<Species, Item> COOKED = new EnumMap<>(Species.class);
	private static final Map<Item, Species> BY_ITEM = new HashMap<>();
	/** A big shark's jaw: the trophy kept from one cut up on the shore (carries the catch, so it mounts on a plaque). */
	public static Item SHARK_JAW;

	private FishSpecies() {
	}

	static void init() {
		for (Species sp : Species.values()) {
			if (!sp.ours()) {
				continue;
			}
			EntityType<?> type = sp.walker() || sp == Species.JELLYFISH ? critter(sp) : fish(sp);
			BY_TYPE.put(type, sp);
			TYPES.put(sp, type);
			ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosPowers.id(sp.id + "_spawn_egg"));
			Item eggItem = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(type)));
			CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(eggItem));
			if (sp.fillet != Species.Fillet.NONE) {
				boolean shell = sp.fillet == Species.Fillet.SHELL;
				Item raw = food(sp.id, shell ? SHELL_RAW_FOOD : RAW_FOOD, RAW_SAT);
				Item cooked = food("cooked_" + sp.id, shell ? SHELL_FOOD : sp.fillet == Species.Fillet.OILY ? OILY_FOOD : WHITE_FOOD,
					sp.fillet == Species.Fillet.OILY ? OILY_SAT : COOKED_SAT);
				RAW.put(sp, raw);
				COOKED.put(sp, cooked);
				BY_ITEM.put(raw, sp);
				Tackle.liveBait(raw, sp);
			} else if (sp == Species.JELLYFISH) {
				ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(sp.id));
				Item jelly = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
				RAW.put(sp, jelly);
				BY_ITEM.put(jelly, sp);
			}
		}
		ResourceKey<Item> jaw = ResourceKey.create(Registries.ITEM, JaymosPowers.id("shark_jaw"));
		SHARK_JAW = Registry.register(BuiltInRegistries.ITEM, jaw, new Item(new Item.Properties().setId(jaw).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Species sp : Species.values()) {
				if (RAW.containsKey(sp)) {
					output.accept(RAW.get(sp));
				}
				if (COOKED.containsKey(sp)) {
					output.accept(COOKED.get(sp));
				}
			}
		});
	}

	private static EntityType<WildFish> fish(Species sp) {
		SpeciesSizes.Size size = SpeciesSizes.ALL.get(sp.id);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(sp.id));
		EntityType<WildFish> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<WildFish>of(WildFish::new, MobCategory.WATER_AMBIENT).sized(size.width(), size.height())
				.eyeHeight(size.height() * 0.6F).clientTrackingRange(sp.big() ? 8 : 4).build(key));
		FabricDefaultAttributeRegistry.register(type, AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, health(sp))
			.add(Attributes.MOVEMENT_SPEED, sp.big() ? 1.1 : 0.8));
		SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FishSpecies::canSpawn);
		spawns(sp, type);
		return type;
	}

	private static EntityType<Critter> critter(Species sp) {
		SpeciesSizes.Size size = SpeciesSizes.ALL.get(sp.id);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(sp.id));
		EntityType<Critter> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<Critter>of(Critter::new, MobCategory.WATER_AMBIENT).sized(size.width(), size.height())
				.eyeHeight(size.height() * 0.6F).clientTrackingRange(4).build(key));
		FabricDefaultAttributeRegistry.register(type, Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health(sp))
			.add(Attributes.MOVEMENT_SPEED, 0.3));
		SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FishSpecies::canSpawn);
		spawns(sp, type);
		return type;
	}

	private static double health(Species sp) {
		return Math.min(HEALTH_MOST, Math.max(2.0, 3.0 * Math.cbrt(sp.usualKg() / 4.0) * (sp.big() ? 2.0 : 1.0)));
	}

	private static void spawns(Species sp, EntityType<? extends Mob> type) {
		BiomeModifications.addSpawn(ctx -> ctx.canGenerateIn(LevelStem.OVERWORLD) && sp.livesIn(ctx.getBiomeHolder()),
			MobCategory.WATER_AMBIENT, type, sp.spawnWeight, Math.max(1, sp.schoolMin), Math.max(1, sp.schoolMax));
	}

	/**
	 * Fresh water anywhere it's at least two deep (rivers, lakes, mountain tarns), the sea no deeper than vanilla's fish
	 * go; walkers on the bed, everyone else in open water.
	 */
	private static <T extends Mob> boolean canSpawn(EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		Species sp = BY_TYPE.get(type);
		if (sp == null || !level.getFluidState(pos).is(FluidTags.WATER)) {
			return false;
		}
		if (sp.walker()) {
			return !level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getBlockState(pos.below()).isSolid();
		}
		if (!level.getFluidState(pos.below()).is(FluidTags.WATER)) {
			return false;
		}
		if (sp.water == Species.Water.SEA) {
			int sea = level.getSeaLevel();
			return pos.getY() <= sea && pos.getY() >= sea - (sp.deep() ? 40 : 20);
		}
		return true;
	}

	private static Item food(String id, int nutrition, float saturation) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
			.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), Consumables.DEFAULT_FOOD)));
	}

	/** The species of an entity type of ours, or null. */
	public static @Nullable Species of(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	/** Our entity type for {@code sp}, or null (the first six aren't ours). */
	public static @Nullable EntityType<?> type(Species sp) {
		return TYPES.get(sp);
	}

	/** The raw item a landed {@code sp} is, or null. */
	public static @Nullable Item raw(Species sp) {
		return RAW.get(sp);
	}

	/** Which of our species {@code item} is (raw), or null. */
	public static @Nullable Species ofItem(Item item) {
		return BY_ITEM.get(item);
	}

	public static Map<Species, EntityType<?>> types() {
		return TYPES;
	}
}
