package com.jaymospowers.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.function.Function;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The Sentry Ruins' mobs: the Slider, detonation sentry, sentry golem and mimic, the demolition bomb the golem lobs, and
 * their spawn eggs. None spawns by itself in the open: the structure places the Slider and golems, spawners and traps send
 * the sentries, a crate turns into a mimic.
 */
public final class DungeonMobs {
	public static EntityType<Slider> SLIDER;
	public static EntityType<DetonationSentry> DETONATION_SENTRY;
	public static EntityType<SentryGolem> SENTRY_GOLEM;
	public static EntityType<Mimic> MIMIC;
	public static EntityType<DemolitionBomb> DEMOLITION_BOMB;
	/** The bomb is drawn as this item's sprite (never in the creative tab). */
	public static Item DEMOLITION_BOMB_ITEM;
	public static Item SLIDER_SPAWN_EGG, DETONATION_SENTRY_SPAWN_EGG, SENTRY_GOLEM_SPAWN_EGG, MIMIC_SPAWN_EGG;

	private DungeonMobs() {
	}

	static void init() {
		DEMOLITION_BOMB_ITEM = item("demolition_bomb", Item::new);

		SLIDER = type("slider", EntityType.Builder.of(Slider::new, MobCategory.MONSTER).sized(2.0F, 2.0F).fireImmune().noLootTable()
			.clientTrackingRange(10).updateInterval(1).notInPeaceful());
		DETONATION_SENTRY = type("detonation_sentry", EntityType.Builder.of(DetonationSentry::new, MobCategory.MONSTER).sized(0.9F, 0.9F).eyeHeight(0.45F)
			.noLootTable().clientTrackingRange(8).notInPeaceful());
		SENTRY_GOLEM = type("sentry_golem", EntityType.Builder.of(SentryGolem::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.8F)
			.noLootTable().clientTrackingRange(10).notInPeaceful());
		MIMIC = type("mimic", EntityType.Builder.of(Mimic::new, MobCategory.MONSTER).sized(0.9F, 1.35F).noLootTable().clientTrackingRange(8).notInPeaceful());
		ResourceKey<EntityType<?>> bombKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("demolition_bomb"));
		DEMOLITION_BOMB = Registry.register(BuiltInRegistries.ENTITY_TYPE, bombKey, EntityType.Builder.<DemolitionBomb>of(DemolitionBomb::new, MobCategory.MISC)
			.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(2).build(bombKey));

		FabricDefaultAttributeRegistry.register(SLIDER, Slider.createAttributes());
		FabricDefaultAttributeRegistry.register(DETONATION_SENTRY, DetonationSentry.createAttributes());
		FabricDefaultAttributeRegistry.register(SENTRY_GOLEM, SentryGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(MIMIC, Mimic.createAttributes());

		SLIDER_SPAWN_EGG = egg("slider_spawn_egg", SLIDER);
		DETONATION_SENTRY_SPAWN_EGG = egg("detonation_sentry_spawn_egg", DETONATION_SENTRY);
		SENTRY_GOLEM_SPAWN_EGG = egg("sentry_golem_spawn_egg", SENTRY_GOLEM);
		MIMIC_SPAWN_EGG = egg("mimic_spawn_egg", MIMIC);

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			for (Item item : List.of(SLIDER_SPAWN_EGG, DETONATION_SENTRY_SPAWN_EGG, SENTRY_GOLEM_SPAWN_EGG, MIMIC_SPAWN_EGG)) {
				output.accept(item);
			}
		});
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String id, EntityType<? extends Mob> type) {
		return item(id, p -> new SpawnEggItem(p.spawnEgg(type)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
