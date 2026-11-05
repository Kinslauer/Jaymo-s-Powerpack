package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * (0.7.588) Andy: mob griefing "should be on by default in this pack and always on the server". Giants tearing up trees,
 * creepers felling them, dragons' fire and the rest all need it, so it's set on as the world starts and put back on
 * every {@link #CHECK_TICKS} if anyone turns it off.
 */
public final class PackRules {
	/** How often (ticks) mob griefing is checked and put back on. */
	static final int CHECK_TICKS = 200;

	private PackRules() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(PackRules::enforce);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				enforce(server);
			}
		});
	}

	private static void enforce(MinecraftServer server) {
		GameRules rules = server.overworld().getGameRules();
		if (!rules.get(GameRules.MOB_GRIEFING)) {
			rules.set(GameRules.MOB_GRIEFING, true, server);
		}
	}
}
