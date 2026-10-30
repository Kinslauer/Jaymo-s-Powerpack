package com.jaymospowers.power.zfighter.divine;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** (0.18.48) The Divine-born moves' entities. */
public final class DivineEntities {
	public static EntityType<FlyingSword> FLYING_SWORD;

	private DivineEntities() {
	}

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("zfighter_flying_sword"));
		FLYING_SWORD = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<FlyingSword>of(FlyingSword::new, MobCategory.MISC)
				.noLootTable()
				.noSummon()
				.sized(0.5F, 0.5F)
				.clientTrackingRange(8)
				.updateInterval(1)
				.build(key));
	}
}
