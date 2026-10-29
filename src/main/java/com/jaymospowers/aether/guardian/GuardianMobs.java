package com.jaymospowers.aether.guardian;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * (Powers 0.17.148) The infected guardian tree's guardian, the Visage of War ({@link VisageOfWar}), and its spawn egg.
 * It doesn't spawn by itself: the tree places it in its hollow.
 */
public final class GuardianMobs {
	public static EntityType<VisageOfWar> VISAGE_OF_WAR;
	public static Item VISAGE_OF_WAR_SPAWN_EGG;

	private GuardianMobs() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("visage_of_war"));
		VISAGE_OF_WAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(VisageOfWar::new, MobCategory.MONSTER)
			.sized(1.2F, 3.4F).eyeHeight(3.0F).fireImmune().clientTrackingRange(10).updateInterval(1).notInPeaceful().build(key));
		FabricDefaultAttributeRegistry.register(VISAGE_OF_WAR, VisageOfWar.createAttributes());
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("visage_of_war_spawn_egg"));
		VISAGE_OF_WAR_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, eggKey,
			new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(VISAGE_OF_WAR)));
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(VISAGE_OF_WAR_SPAWN_EGG));
	}
}
