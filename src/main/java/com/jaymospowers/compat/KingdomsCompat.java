package com.jaymospowers.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/**
 * (0.20.8) Jaymo's Kingdoms is optional for Powers: this is the only place that touches it, by reflection, and only when it is
 * loaded. A missing jar (or a changed method) answers "no" instead of crashing.
 */
public final class KingdomsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method boss;
	private static boolean looked;

	private KingdomsCompat() {
	}

	/** Whether Kingdoms calls {@code entity} a boss (one with a boss bar: a king, a lord, a warlord, an Aether Giant, a wild dragon). */
	public static boolean boss(Entity entity) {
		if (!LOADED) {
			return false;
		}
		if (!looked) {
			looked = true;
			try {
				boss = Class.forName("com.jaymoskingdoms.BossBars").getMethod("boss", Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				boss = null;
			}
		}
		if (boss == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(boss.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
