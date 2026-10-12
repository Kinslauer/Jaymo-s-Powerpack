package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The Armory's entities: a thrown weapon. */
public final class ArmoryEntities {
	public static EntityType<ThrownWeapon> THROWN_WEAPON;

	private ArmoryEntities() {
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("thrown_weapon"));
		THROWN_WEAPON = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC)
				.noLootTable()
				.sized(0.5F, 0.5F)
				.eyeHeight(0.13F)
				.clientTrackingRange(4)
				.updateInterval(20)
				.build(key));
	}
}
