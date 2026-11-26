package com.jaymospowers.compat;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Keeps distant bands still even when their chunk columns are loaded from the ground.
 * Andy: "The skyroot lizards should only spawn in the aether, and that band should be unloaded until you approach."
 * The Overworld stays awake; unreadable bands do too. Reflection results last until the server stops.
 */
public final class BandSleep {
	private static final int REACH = 128 + 16;
	private static final Map<ServerLevel, Map<Integer, ResourceKey<Level>>> REALMS = new IdentityHashMap<>();
	private static final Map<ServerLevel, Map<ResourceKey<Level>, int[]>> RANGES = new IdentityHashMap<>();

	static {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REALMS.clear();
			RANGES.clear();
		});
	}

	private BandSleep() {
	}

	public static boolean asleep(ServerLevel level, double x, double y, double z) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int height = (int) Math.floor(y);
		Map<Integer, ResourceKey<Level>> realms = REALMS.computeIfAbsent(level, key -> new HashMap<>());
		ResourceKey<Level> realm = realms.computeIfAbsent(height, key -> Stacked.realm(level, key));
		if (realm == null || realm == Level.OVERWORLD) {
			return false;
		}
		Map<ResourceKey<Level>, int[]> ranges = RANGES.computeIfAbsent(level, key -> new HashMap<>());
		if (!ranges.containsKey(realm)) {
			ranges.put(realm, Stacked.sleepRange(level.getServer(), realm));
		}
		int[] band = ranges.get(realm);
		if (band == null || y < band[0] || y >= band[1]) {
			return false;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - x, dz = player.getZ() - z;
			double dy = player.getY() < band[0] ? band[0] - player.getY() : player.getY() >= band[1] ? player.getY() - band[1] : 0.0;
			if (dx * dx + dz * dz + dy * dy <= (double) REACH * REACH) {
				return false;
			}
		}
		return true;
	}
}
