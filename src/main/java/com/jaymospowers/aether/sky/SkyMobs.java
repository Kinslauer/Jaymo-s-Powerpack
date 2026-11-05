package com.jaymospowers.aether.sky;

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

/** (0.20.96) The Aether sky's mob: the sky warden (seven-mods plan, patch 10). */
public final class SkyMobs {
	public static EntityType<SkyWarden> SKY_WARDEN;
	public static Item SKY_WARDEN_SPAWN_EGG;

	private SkyMobs() {
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("sky_warden"));
		SKY_WARDEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(SkyWarden::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F).eyeHeight(1.8F).fireImmune().clientTrackingRange(10).notInPeaceful().build(key));
		FabricDefaultAttributeRegistry.register(SKY_WARDEN, SkyWarden.createAttributes());
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("sky_warden_spawn_egg"));
		SKY_WARDEN_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, eggKey, new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(SKY_WARDEN)));
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(SKY_WARDEN_SPAWN_EGG));
	}
}
