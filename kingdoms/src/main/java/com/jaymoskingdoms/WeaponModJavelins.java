package com.jaymoskingdoms;

import ckathode.weaponmod.entity.projectile.EntityJavelin;
import ckathode.weaponmod.entity.projectile.EntityMusketBullet;
import ckathode.weaponmod.entity.projectile.EntityProjectile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.28) Balkon's WeaponMod: Legacy (mod id {@value Javelins#WEAPONMOD}, MIT; nothing of it is copied here): when it's
 * loaded, guards of kingdoms neither poor nor elite throw its javelins ({@link Javelins}). This names WeaponMod's
 * classes, so it's only touched while {@link Javelins#WEAPONMOD_LOADED}.
 * <p>(0.5.61) And the musket balls of the richest kingdoms' musketeers ({@link Muskets}).
 */
final class WeaponModJavelins {
	private WeaponModJavelins() {
	}

	/** A javelin thrown by {@code thrower}, not yet shot nor added to the world, that can't be picked up; null if WeaponMod has no javelin. */
	static @Nullable Projectile javelin(ServerLevel level, LivingEntity thrower) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "javelin")));
		if (stack.is(Items.AIR)) {
			return null;
		}
		EntityJavelin javelin = new EntityJavelin(level, thrower, stack);
		javelin.setPickupStatus(EntityProjectile.PickupStatus.DISALLOWED);
		return javelin;
	}

	/** (0.5.61) A musket ball fired by {@code shooter} that hits for {@code damage}, not yet shot nor added to the world; null if the musket is turned off. */
	static @Nullable Projectile bullet(ServerLevel level, LivingEntity shooter, float damage) {
		ItemStack stack = new ItemStack(Muskets.musket());
		EntityMusketBullet ball = new EntityMusketBullet(level, shooter, stack);
		ball.setPickupStatus(EntityProjectile.PickupStatus.DISALLOWED);
		ball.setExtraDamage(damage - ball.getDamage(shooter));
		return ball;
	}
}
