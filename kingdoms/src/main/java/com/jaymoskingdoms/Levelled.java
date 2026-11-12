package com.jaymoskingdoms;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Mob;

/**
 * A guard who challenges someone levels their polearm at them (Powers draws it from {@link Mob#isAggressive}: its
 * {@code AttentionPose}; a guard with nobody to fight stands with it upright). The challenge is words, not a fight,
 * so nothing else would set the flag: this holds it for the length of the challenge and lets it go after, unless a
 * real fight has begun by then.
 */
public final class Levelled {
	/** How often the held polearms are looked at (ticks). */
	static final int CHECK_EVERY = 10;

	private static final Map<Mob, Long> UNTIL = new WeakHashMap<>();

	private Levelled() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (UNTIL.isEmpty() || server.getTickCount() % CHECK_EVERY != 0) {
				return;
			}
			for (Iterator<Map.Entry<Mob, Long>> it = UNTIL.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Mob, Long> e = it.next();
				Mob mob = e.getKey();
				if (!mob.isAlive() || mob.level().getGameTime() >= e.getValue()) {
					if (mob.isAlive() && mob.getTarget() == null) {
						mob.setAggressive(false);
					}
					it.remove();
				}
			}
		});
	}

	/** {@code mob} holds its weapon levelled for {@code ticks}. */
	public static void level(Mob mob, int ticks) {
		mob.setAggressive(true);
		UNTIL.put(mob, mob.level().getGameTime() + ticks);
	}
}
