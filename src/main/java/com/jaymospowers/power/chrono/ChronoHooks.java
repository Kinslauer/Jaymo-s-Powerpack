package com.jaymospowers.power.chrono;

import net.minecraft.world.entity.Entity;

/** Entry points for the chrono mixins (mixin classes can't be referenced from normal code, so they call out to here). */
public final class ChronoHooks {
	private ChronoHooks() {
	}

	/** True while a Stasis Field holds this entity still. Server side only. */
	public static boolean isFrozen(Entity entity) {
		return StasisField.isFrozen(entity);
	}
}
