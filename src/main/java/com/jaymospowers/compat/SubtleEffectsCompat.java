package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.20.51) Andy (2026-11-05): "skeletons currently drop bone pngs when hit, they should be debris splinter particles if
 * anything, the bones look tacky". Those are Subtle Effects' (its entities config, "damage taken": skeleton and skeleton
 * horse bones). They're switched off here each time a server starts and each time a client joins one (the server's
 * config is synced to clients on join), by setting the two public config fields; our own splinters of bone take their
 * place ({@code BladeBlood.splinters}). Nothing without Subtle Effects, or if its config has moved (a warning, once).
 */
public final class SubtleEffectsCompat {
	private static boolean warned;

	private SubtleEffectsCompat() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("subtle_effects")) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> noBones());
		}
	}

	/** Subtle Effects' bone sprites on a hurt skeleton (or skeleton horse) are switched off. */
	public static void noBones() {
		if (!FabricLoader.getInstance().isModLoaded("subtle_effects")) {
			return;
		}
		try {
			Object entities = Class.forName("einstein.subtle_effects.init.ModConfigs").getField("ENTITIES").get(null);
			Object damage = entities.getClass().getField("damageTaken").get(entities);
			damage.getClass().getField("damagedSkeletonBones").setBoolean(damage, false);
			damage.getClass().getField("damagedSkeletonHorseBones").setBoolean(damage, false);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			if (!warned) {
				warned = true;
				JaymosPowers.LOGGER.warn("Couldn't switch Subtle Effects' skeleton bone particles off (its config moved?)", e);
			}
		}
	}
}
