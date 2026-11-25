package com.jaymoskingdoms;

import ckathode.weaponmod.entity.projectile.EntityBoomerang;
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
 * Balkon's WeaponMod: Legacy's boomerang (item {@code weaponmod:boomerang.<material>}; nothing of the mod is copied here). This names
 * WeaponMod's classes, so it is only touched while {@link Javelins#WEAPONMOD_LOADED} ({@link Boomerangs}).
 */
final class WeaponModBoomerangs {
	private WeaponModBoomerangs() {
	}

	/** A boomerang of {@code material} ("wood", "stone", "iron"...) thrown by {@code thrower}, not yet shot nor added to the world; null if WeaponMod has none of that material. */
	static @Nullable Projectile create(ServerLevel level, LivingEntity thrower, String material) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "boomerang." + material)));
		if (stack.is(Items.AIR)) {
			return null;
		}
		EntityBoomerang boomerang = new EntityBoomerang(level, thrower, stack);
		boomerang.setPickupStatus(EntityProjectile.PickupStatus.DISALLOWED);
		return boomerang;
	}

	/**
	 * Throws {@code boomerang} (made by {@link #create}) from {@code thrower} at {@code xRot} and {@code yRot} degrees, and adds it to the
	 * world. WeaponMod's boomerang takes the strength it flies, strikes and comes back with only from {@code shootFromRotation}: launched
	 * any other way it has none, so it neither hurts what it hits nor returns.
	 */
	static void launch(ServerLevel level, LivingEntity thrower, Projectile boomerang, float xRot, float yRot, float speed, float spread) {
		Projectile.spawnProjectile(boomerang, level, ItemStack.EMPTY, b -> b.shootFromRotation(thrower, xRot, yRot, 0.0F, speed, spread));
	}
}
