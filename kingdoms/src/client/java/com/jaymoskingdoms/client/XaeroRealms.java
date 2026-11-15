package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RealmMap;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Who holds what, on Xaero's Minimap ({@link RealmMap}; medieval pick gb-castles): keeps the realms the server sends and,
 * only when Xaero's Minimap is loaded, hands them to {@link XaeroRealmsMap} (each seat a waypoint in its holder's colour,
 * the realms' borders drawn over the minimap). Without Xaero nothing here touches it. If Xaero's API ever throws, the
 * map is left alone for the rest of the session.
 */
public final class XaeroRealms {
	/** How often the waypoints are checked against the realms and Xaero's current world (ticks). */
	static final int REFRESH_TICKS = 40;
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("xaerominimap");

	private static List<RealmMap.Realm> realms = List.of();
	/** Counts each new list of realms, so the map knows when to redraw. */
	private static int version;
	private static boolean broken;

	private XaeroRealms() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RealmMap.Realms.TYPE, (payload, context) -> {
			realms = payload.realms();
			version++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			realms = List.of();
			version++;
		});
		if (!LOADED) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (broken || minecraft.player == null || minecraft.level == null || minecraft.player.tickCount % REFRESH_TICKS != 0) {
				return;
			}
			try {
				XaeroRealmsMap.refresh(minecraft, realms, version);
			} catch (Throwable t) {
				broken = true;
				JaymosKingdoms.LOGGER.warn("Xaero's Minimap realms turned off", t);
			}
		});
	}

	static List<RealmMap.Realm> realms() {
		return realms;
	}

	static int version() {
		return version;
	}
}
