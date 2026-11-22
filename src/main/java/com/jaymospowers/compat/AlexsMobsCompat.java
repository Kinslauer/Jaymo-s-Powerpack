package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Alex's Mobs Continued (LGPL-3.0, shipped unchanged) hands every player its Animal Dictionary the first time they join.
 * Andy (2026-11-19): players shouldn't spawn with it. Its {@code giveBookOnStartup} setting is read into a static flag
 * ({@code AMConfig.giveBookOnStartup}) whenever its settings load, so this sets that flag off once the server is up and
 * again as each player connects (before Alex's own join handler runs, and after any reload of its settings). The book
 * itself is untouched: it can still be crafted and read.
 */
public final class AlexsMobsCompat {
	private static final String MOD_ID = "alexsmobs";
	private static final String CONFIG = "com.github.alexthe666.alexsmobs.config.AMConfig";

	private AlexsMobsCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> noBook());
		ServerPlayConnectionEvents.INIT.register((handler, server) -> noBook());
	}

	private static void noBook() {
		try {
			Field give = Class.forName(CONFIG).getField("giveBookOnStartup");
			give.setBoolean(null, false);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Could not turn off Alex's Mobs' join book", e);
		}
	}
}
