package com.jaymospowers.core;

import net.minecraft.world.entity.Entity;

/**
 * "Have no powers", written as a rule in a Paladin's oath or a Warlock's pact (Jaymo's Kingdoms' vow book): Kingdoms puts
 * {@link #TAG} on whoever the rule is about while it runs, and here that reads as the Eight-Sign Circle's seal (power keys
 * refused, forms sealed) and a gifted villager's power asleep. Kingdoms' own casters read the same tag as hushed.
 */
public final class RuledMute {
	/** The tag on one a rule says has no powers (a stale one is cleared on load, {@link StaleTags}). */
	public static final String TAG = "jaymospowers_ruled_mute";

	private RuledMute() {
	}

	/** Whether a running rule says {@code entity} has no powers. */
	public static boolean on(Entity entity) {
		return entity.entityTags().contains(TAG);
	}
}
