package com.jaymoskingdoms;

import ckathode.weaponmod.entity.projectile.EntityProjectile;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Balkon's WeaponMod (MIT; nothing of it is copied here): how much its own shots (javelins, musket balls, boomerangs) slow in
 * the air each tick, for {@link ShieldSense}'s look ahead at a shot in flight. This names WeaponMod's classes, so it's only
 * touched while {@link Javelins#WEAPONMOD_LOADED}.
 */
final class WeaponModFlight {
	private WeaponModFlight() {
	}

	/** {@code shot}'s air drag (its speed is multiplied by this each tick), or null if it isn't one of WeaponMod's. */
	static @Nullable Float drag(Entity shot) {
		return shot instanceof EntityProjectile<?> projectile && !shot.isInWater() ? projectile.getAirResistance() : null;
	}
}
