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
	public static EntityType<FrostLance> FROST_LANCE;
	public static EntityType<RiftCannonball> RIFT_CANNONBALL;

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
		ResourceKey<EntityType<?>> lanceKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("arsenal_frost_lance"));
		FROST_LANCE = Registry.register(BuiltInRegistries.ENTITY_TYPE, lanceKey,
			EntityType.Builder.<FrostLance>of(FrostLance::new, MobCategory.MISC)
				.noLootTable()
				.noSummon()
				.sized(0.5F, 0.5F)
				.clientTrackingRange(8)
				.updateInterval(1)
				.build(lanceKey));
		ResourceKey<EntityType<?>> ballKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("arsenal_cannonball"));
		RIFT_CANNONBALL = Registry.register(BuiltInRegistries.ENTITY_TYPE, ballKey,
			EntityType.Builder.<RiftCannonball>of(RiftCannonball::new, MobCategory.MISC)
				.noLootTable()
				.noSummon()
				.sized(0.4F, 0.4F)
				.clientTrackingRange(8)
				.updateInterval(1)
				.build(ballKey));
	}
}
