package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Chunky (pre-generation, only on the server) waits while anyone plays. Andy, after the Steam Deck's server ran out of
 * memory with a 2,500-block pre-generation running while he explored a new world (crash 2026-11-13 18:22): "pause Chunky
 * whenever a player is on". Its own commands are run: {@code chunky pause} when the first player is on, {@code chunky
 * continue} once nobody is (and at start-up with nobody on, which also picks up a task a restart left saved). With no
 * task, Chunky just says so (quietly).
 */
public final class ChunkyCompat {
	private static final String MOD_ID = "chunky";
	/** How often (ticks) who's on is looked at. */
	static final int CHECK_TICKS = 100;

	/** Whether the server had players on at the last look (null: not looked yet since it started). */
	private static Boolean busy;

	private ChunkyCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(ChunkyCompat::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> busy = null);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		boolean now = !server.getPlayerList().getPlayers().isEmpty();
		if (busy != null && busy == now) {
			return;
		}
		busy = now;
		String command = now ? "chunky pause" : "chunky continue";
		JaymosPowers.LOGGER.info("{}: running '{}'", now ? "A player is on" : "Nobody is on", command);
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't run '{}'", command, e);
		}
	}
}
