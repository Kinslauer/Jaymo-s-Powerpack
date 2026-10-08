package com.jaymospowers.power.arsenal.entity;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Pocket Arsenal's entities. */
public final class ArsenalEntities {
	public static EntityType<RiftBlade> RIFT_BLADE;

	private ArsenalEntities() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("arsenal_rift_blade"));
		RIFT_BLADE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<RiftBlade>of(RiftBlade::new, MobCategory.MISC)
				.noLootTable()
				.noSummon()
				.sized(0.5F, 0.5F)
				.eyeHeight(0.13F)
				.clientTrackingRange(6)
				.updateInterval(2)
				.build(key));
	}
}
