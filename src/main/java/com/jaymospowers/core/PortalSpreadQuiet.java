package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Nether Portal Spread (Serilum, in the pack since 0.10.65) keeps spreading the nether round lit portals, but its chat
 * lines ("You feel a corrupted energy coming from the portal...", the coal-block fade and the portal-broken lines) are
 * switched off, as Andy asked. Its options are public static fields read whenever a message would be sent, so they're
 * set here each time a server starts, whatever its config file says. Only touched through reflection, and only when
 * the mod is loaded.
 */
public final class PortalSpreadQuiet {
	private static final String CONFIG = "com.serilum.netherportalspread_common_fabric.config.ConfigHandler";
	/** Every message switch the mod has; the spread itself is left alone. */
	private static final String[] MESSAGES = {
		"sendMessageOnPortalCreation", "sendMessageOnPreventSpreadBlocksFound", "sendMessageOnPortalBroken"
	};

	private PortalSpreadQuiet() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("netherportalspread")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTING.register(server -> silence());
	}

	private static void silence() {
		try {
			Class<?> config = Class.forName(CONFIG);
			for (String field : MESSAGES) {
				config.getField(field).setBoolean(null, false);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't switch off Nether Portal Spread's messages", e);
		}
	}
}
