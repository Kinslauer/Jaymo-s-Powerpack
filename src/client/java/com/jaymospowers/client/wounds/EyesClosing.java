package com.jaymospowers.client.wounds;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.21.65) Andy: "make sure the eyes animate closed too". A body passing out ({@code Downed.passedOut}) doesn't shut its
 * eyes all at once: over {@link #CLOSING_TICKS} they droop, shut a little longer each flutter ({@link #FLUTTER} ticks a
 * flutter), until they stay shut. The skins only have open and shut (Kitchen's {@code ClosedEyes}), so the closing is in the
 * timing. Client side, per entity, from when it was first seen out cold.
 */
public final class EyesClosing {
	/** How long the eyes take to stay shut (ticks), and the length of one flutter. */
	static final int CLOSING_TICKS = 30, FLUTTER = 8;

	/** Per entity id: the game time it was first seen out cold. */
	private static final Map<Integer, Long> SINCE = new HashMap<>();

	private EyesClosing() {
	}

	/** {@code entity} is out cold: whether its eyes are shut this tick. */
	public static boolean shut(LivingEntity entity) {
		long now = entity.level().getGameTime();
		Long since = SINCE.get(entity.getId());
		if (since == null || now < since) {
			SINCE.put(entity.getId(), now);
			since = now;
			if (SINCE.size() > 256) {
				SINCE.entrySet().removeIf(e -> now - e.getValue() > 20L * 60);
			}
		}
		long t = now - since;
		if (t >= CLOSING_TICKS) {
			return true;
		}
		// shut for a growing share of each flutter: none at first, the whole of it by the end
		return t % FLUTTER < (long)Math.ceil(FLUTTER * t / (double)CLOSING_TICKS);
	}

	/** {@code entity} isn't out cold: its eyes are open (it's forgotten, so the next passing out closes them again). */
	public static boolean open(LivingEntity entity) {
		SINCE.remove(entity.getId());
		return false;
	}
}
