package com.jaymospowers.core;

import com.jaymospowers.power.arsenal.ArsenalSeal;
import com.jaymospowers.power.rules.RulesPower;
import com.jaymospowers.power.wind.AirlessDome;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * The silencing moves (Airless Dome, the Drowning Pit, the Rift Seal, a Lawgiver's rule) mark who they hold with an entity
 * tag, which is saved with the entity, and take it off from what they remember while running. Whoever logs out, unloads or
 * outlives a crash while marked would stay silenced for good: so anything coming into the world comes in unmarked (a move
 * still running marks them again on its next beat).
 */
public final class StaleTags {
	/** (The choked tag is the Drowning Pit's, whose class is its package's own.) */
	private static final String[] TAGS = {AirlessDome.TAG, "jaymospowers_choked", ArsenalSeal.TAG, RulesPower.MUTE_TAG};

	private StaleTags() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!entity.entityTags().isEmpty()) {
				for (String tag : TAGS) {
					entity.removeTag(tag);
				}
			}
		});
	}
}
