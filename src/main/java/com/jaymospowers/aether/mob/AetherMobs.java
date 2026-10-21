package com.jaymospowers.aether.mob;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Aether's passive animals: the moa, phyg, flying cow, sheepuff and aerbunny (docs/aether.md). Their spawn eggs,
 * the three moa eggs, their attributes, and where they live: every Aether biome but the Hestveil caverns (the moa only
 * in the highfields), added to their spawn lists here by biome tag, never in the biome files, on any block of the {@code animals_spawnable_on} tag (aether grass is in it).
 */
public final class AetherMobs {
	// (spawn weights and group sizes are in AetherAnimals, with the new animals': Aether II's table)

	public static EntityType<Phyg> PHYG;
	public static EntityType<FlyingCow> FLYING_COW;
	public static EntityType<Sheepuff> SHEEPUFF;
	public static EntityType<Aerbunny> AERBUNNY;
	public static EntityType<Moa> MOA;

	public static Item PHYG_SPAWN_EGG, FLYING_COW_SPAWN_EGG, SHEEPUFF_SPAWN_EGG, AERBUNNY_SPAWN_EGG, MOA_SPAWN_EGG;
	public static Item BLUE_MOA_EGG, WHITE_MOA_EGG, BLACK_MOA_EGG;

	/** Whether the jump key is down on this client (set by the client; always false on a server). */
	public static BooleanSupplier jumpHeld = () -> false;

	private AetherMobs() {
	}

	public static void init() {
		AerbunnyRide.init();
		PHYG = type("phyg", EntityType.Builder.of(Phyg::new, MobCategory.CREATURE).sized(0.95F, 0.85F).clientTrackingRange(10));
		FLYING_COW = type("flying_cow", EntityType.Builder.of(FlyingCow::new, MobCategory.CREATURE).sized(0.95F, 1.1F).eyeHeight(1.05F).clientTrackingRange(10));
		SHEEPUFF = type("sheepuff", EntityType.Builder.of(Sheepuff::new, MobCategory.CREATURE).sized(0.9F, 1.25F).eyeHeight(0.85F).clientTrackingRange(10));
		AERBUNNY = type("aerbunny", EntityType.Builder.of(Aerbunny::new, MobCategory.CREATURE).sized(0.55F, 0.45F).eyeHeight(0.25F).clientTrackingRange(8));
		MOA = type("moa", EntityType.Builder.of(Moa::new, MobCategory.CREATURE).sized(1.25F, 2.35F).eyeHeight(2.1F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(PHYG, Phyg.createAttributes());
		FabricDefaultAttributeRegistry.register(FLYING_COW, FlyingCow.createAttributes());
		FabricDefaultAttributeRegistry.register(SHEEPUFF, Sheepuff.createAttributes());
		FabricDefaultAttributeRegistry.register(AERBUNNY, Aerbunny.createAttributes());
		FabricDefaultAttributeRegistry.register(MOA, Moa.createAttributes());

		PHYG_SPAWN_EGG = egg("phyg_spawn_egg", PHYG);
		FLYING_COW_SPAWN_EGG = egg("flying_cow_spawn_egg", FLYING_COW);
		SHEEPUFF_SPAWN_EGG = egg("sheepuff_spawn_egg", SHEEPUFF);
		AERBUNNY_SPAWN_EGG = egg("aerbunny_spawn_egg", AERBUNNY);
		MOA_SPAWN_EGG = egg("moa_spawn_egg", MOA);
		BLUE_MOA_EGG = item("blue_moa_egg", p -> new MoaEggItem(Moa.Kind.BLUE, p), new Item.Properties().stacksTo(16));
		WHITE_MOA_EGG = item("white_moa_egg", p -> new MoaEggItem(Moa.Kind.WHITE, p), new Item.Properties().stacksTo(16));
		BLACK_MOA_EGG = item("black_moa_egg", p -> new MoaEggItem(Moa.Kind.BLACK, p), new Item.Properties().stacksTo(16));

		// they stand on anything the animals_spawnable_on tag lists (aether grass), in daylight
		place(PHYG);
		place(FLYING_COW);
		place(SHEEPUFF);
		place(AERBUNNY);
		place(MOA);
		// (where they spawn is in AetherAnimals.spawns, by the three climates' biome tags; the moa has no natural spawn, only nests)

		AetherAnimals.init();

		// the eggs and spawn eggs sit in the Aether's creative tab (made in AetherItems)
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			for (Item item : List.of(BLUE_MOA_EGG, WHITE_MOA_EGG, BLACK_MOA_EGG, MOA_SPAWN_EGG, PHYG_SPAWN_EGG, FLYING_COW_SPAWN_EGG,
				SHEEPUFF_SPAWN_EGG, AERBUNNY_SPAWN_EGG)) {
				output.accept(item);
			}
		});
	}

	private static <T extends Animal> void place(EntityType<T> type) {
		SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
	}

	/** An Aether biome tag (the biomes are data, made with the dimension). */
	private static net.minecraft.tags.TagKey<Biome> biomeTag(String name) {
		return net.minecraft.tags.TagKey.create(Registries.BIOME, JaymosPowers.id(name));
	}

	/** An Aether biome's key. */
	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, JaymosPowers.id(name));
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String id, EntityType<? extends Mob> type) {
		return item(id, p -> new SpawnEggItem(p.spawnEgg(type)), new Item.Properties());
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
