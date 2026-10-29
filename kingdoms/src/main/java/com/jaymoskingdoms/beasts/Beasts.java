package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.ScalebaneArcher;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.327) Creatures drawn from Andy's second batch of Blockbench Workshop models (all CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt): the minotaur (a monster of legend), the Piglin King (a Nether boss), the redstone
 * golem (deep in the mines), the skulk golem (the deep dark's guardian) and the wild boar. The scalebane sellswords
 * are {@code ScalebaneArcher} (they serve barbarian and goblin camps). Each with its spawn egg.
 */
public final class Beasts {
	public static EntityType<Minotaur> MINOTAUR;
	public static EntityType<PiglinKing> PIGLIN_KING;
	public static EntityType<RedstoneGolem> REDSTONE_GOLEM;
	public static EntityType<SkulkGolem> SKULK_GOLEM;
	public static EntityType<WildBoar> WILD_BOAR;
	public static EntityType<ScalebaneArcher> SCALEBANE;

	/** Natural spawn weights (each also gated in its own spawn check), and the boar's herd size. */
	static final int PIGLIN_KING_WEIGHT = 2, REDSTONE_GOLEM_WEIGHT = 3, SKULK_GOLEM_WEIGHT = 10, WILD_BOAR_WEIGHT = 6, BOAR_MIN = 2, BOAR_MAX = 4;
	/** Where boars roam (its own biome tag) and the Nether biomes the Piglin King rules. */
	static final TagKey<Biome> HAS_WILD_BOAR = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_wild_boar"));
	static final TagKey<Biome> HAS_PIGLIN_KING = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_piglin_king"));

	private Beasts() {
	}

	public static void register() {
		MINOTAUR = type("minotaur", EntityType.Builder.of(Minotaur::new, MobCategory.MONSTER).sized(1.4F, 3.3F).clientTrackingRange(10),
			Minotaur.createAttributes());
		PIGLIN_KING = type("piglin_king", EntityType.Builder.of(PiglinKing::new, MobCategory.MONSTER).sized(1.4F, 3.5F).fireImmune().clientTrackingRange(10),
			PiglinKing.createAttributes());
		REDSTONE_GOLEM = type("redstone_golem", EntityType.Builder.of(RedstoneGolem::new, MobCategory.MONSTER).sized(2.0F, 3.6F).clientTrackingRange(10),
			RedstoneGolem.createAttributes());
		SKULK_GOLEM = type("skulk_golem", EntityType.Builder.of(SkulkGolem::new, MobCategory.MONSTER).sized(1.2F, 2.5F).clientTrackingRange(10),
			SkulkGolem.createAttributes());
		WILD_BOAR = type("wild_boar", EntityType.Builder.of(WildBoar::new, MobCategory.CREATURE).sized(1.1F, 1.3F).clientTrackingRange(10),
			WildBoar.createAttributes());
		SCALEBANE = type("scalebane", EntityType.Builder.of(ScalebaneArcher::new, MobCategory.MONSTER).sized(0.6F, 2.0F).clientTrackingRange(8),
			ScalebaneArcher.createAttributes());

		SpawnPlacements.register(PIGLIN_KING, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PiglinKing::checkSpawn);
		SpawnPlacements.register(REDSTONE_GOLEM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, RedstoneGolem::checkSpawn);
		SpawnPlacements.register(SKULK_GOLEM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SkulkGolem::checkSpawn);
		SpawnPlacements.register(WILD_BOAR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_PIGLIN_KING), MobCategory.MONSTER, PIGLIN_KING, PIGLIN_KING_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, REDSTONE_GOLEM, REDSTONE_GOLEM_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DEEP_DARK), MobCategory.MONSTER, SKULK_GOLEM, SKULK_GOLEM_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_WILD_BOAR), MobCategory.CREATURE, WILD_BOAR, WILD_BOAR_WEIGHT, BOAR_MIN, BOAR_MAX);
	}

	/** No other {@code kind} within {@code range} blocks of {@code pos}. */
	static boolean alone(ServerLevelAccessor level, BlockPos pos, Class<? extends Entity> kind, double range) {
		return level.getEntitiesOfClass(kind, new AABB(pos).inflate(range)).isEmpty();
	}

	/** Registers {@code name}, its attributes and its spawn egg. */
	public static <T extends Mob> EntityType<T> type(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		FabricDefaultAttributeRegistry.register((EntityType<? extends LivingEntity>) type, attributes);
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
		Item item = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(type)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(item));
		return type;
	}

	static boolean is(Entity e, EntityType<?> type) {
		return type != null && e.getType() == type;
	}
}
