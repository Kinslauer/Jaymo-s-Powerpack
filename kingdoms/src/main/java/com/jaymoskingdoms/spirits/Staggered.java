package com.jaymoskingdoms.spirits;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * A foe a spirit has knocked off its feet for a moment (Grimsby's glaive and vault, Varro's hauling hand): it crawls, it
 * has no target, and while it lasts it is open to what only lands on the helpless (Grimsby's {@link GrimsbyArts} drain).
 */
final class Staggered {
	private static final Map<UUID, Entry> ALL = new HashMap<>();

	private record Entry(LivingEntity who, long until) {
	}

	private Staggered() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Iterator<Entry> it = ALL.values().iterator(); it.hasNext();) {
				Entry e = it.next();
				if (!e.who().isAlive() || e.who().level().getGameTime() > e.until()) {
					it.remove();
				} else if (e.who() instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ALL.clear());
	}

	/** {@code foe} is off its feet for {@code ticks} (by {@code by}). */
	static void stagger(LivingEntity by, LivingEntity foe, int ticks) {
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 4), by);
		Entry old = ALL.get(foe.getUUID());
		long until = foe.level().getGameTime() + ticks;
		if (old == null || old.until() < until) {
			ALL.put(foe.getUUID(), new Entry(foe, until));
		}
	}

	static boolean is(LivingEntity foe) {
		Entry e = ALL.get(foe.getUUID());
		return e != null && foe.level().getGameTime() <= e.until();
	}
}
