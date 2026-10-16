package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** The Armory's entities: a thrown weapon, and the bomb glider (with its hold's menu). */
public final class ArmoryEntities {
	public static EntityType<ThrownWeapon> THROWN_WEAPON;
	public static EntityType<BombGlider> BOMB_GLIDER;
	public static MenuType<BombGliderMenu> BOMB_GLIDER_MENU;

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
		ResourceKey<EntityType<?>> glider = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bomb_glider"));
		BOMB_GLIDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, glider,
			EntityType.Builder.<BombGlider>of(BombGlider::new, MobCategory.MISC)
				.noLootTable()
				.sized(1.5F, 0.5F)
				.clientTrackingRange(10)
				.updateInterval(1)
				.build(glider));
		BOMB_GLIDER_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("bomb_glider"),
			new MenuType<>(BombGliderMenu::new, FeatureFlags.VANILLA_SET));
	}
}
