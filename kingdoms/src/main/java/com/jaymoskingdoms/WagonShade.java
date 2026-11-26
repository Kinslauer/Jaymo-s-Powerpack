package com.jaymoskingdoms;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * What riding does for body heat (Kitchen's {@code BodyHeat.addShade} and {@code addColdMount}, registered in
 * {@link KitchenCompat}): under a carriage's roof or a rickshaw's hood the sun doesn't reach you, however open the sky.
 * A cart's driver on the bench, a skiff's rider and a handcart's walker have no canopy. The wind dragons are reptiles:
 * their riders get no warmth from the beast's blood, as they would on a horse.
 */
public final class WagonShade {
	private WagonShade() {
	}

	/** Seated in a carriage, a rickshaw, a hired cab or a coach: all four have a roof or hood over the seat. */
	public static boolean canopy(Player player) {
		if (!(player.getVehicle() instanceof Wagon wagon)) {
			return false;
		}
		int kind = wagon.kind();
		return kind == Wagon.CARRIAGE || kind == Wagon.RICKSHAW || kind == Wagon.CAB || kind == Wagon.COACH;
	}

	/** A mount whose blood is cold. */
	public static boolean coldMount(Entity mount) {
		return mount instanceof com.jaymoskingdoms.dragons.WindDragon;
	}
}
