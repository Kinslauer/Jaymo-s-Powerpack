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
 * <p>
 * At start-up the first {@code chunky continue} waits {@link #START_WAIT}: run five seconds in, while the world was still
 * settling, the task came up but never made a chunk (0 a second, at 0, 0) after every restart on 2026-11-18, until it was
 * paused and continued by hand. So it is also paused and continued once more {@link #START_KICK} later, which costs a
 * running task nothing.
 */
public final class ChunkyCompat {
	private static final String MOD_ID = "chunky";
	/** How often (ticks) who's on is looked at. */
	static final int CHECK_TICKS = 100;
	/** Ticks after start-up before Chunky is first told to continue, and before it is paused and continued once more. */
	static final int START_WAIT = 60 * 20, START_KICK = 3 * 60 * 20;
	/** Ticks between that pause and its continue (a continue right after a pause finds the old task still stopping). */
	static final int KICK_GAP = 15 * 20;

	/** Whether the server had players on at the last look (null: not looked yet since it started). */
	private static Boolean busy;
	/** The once-a-start pause and continue is still to come. */
	private static boolean kickDue = true;

	private ChunkyCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(ChunkyCompat::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			busy = null;
			kickDue = true;
		});
	}

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		boolean now = !server.getPlayerList().getPlayers().isEmpty();
		if (kickDue && busy != null && !busy && !now) {
			if (ticks == START_KICK) {
				run(server, "chunky pause", "Start-up kick");
			} else if (ticks == START_KICK + KICK_GAP) {
				kickDue = false;
				run(server, "chunky continue", "Start-up kick");
			}
		}
		if (ticks % CHECK_TICKS != 0 || busy == null && !now && ticks < START_WAIT) {
			return;
		}
		if (busy != null && busy == now) {
			return;
		}
		busy = now;
		if (now) {
			// (a player came on: the start-up kick isn't wanted any more)
			kickDue = false;
		}
		run(server, now ? "chunky pause" : "chunky continue", now ? "A player is on" : "Nobody is on");
	}

	private static void run(MinecraftServer server, String command, String why) {
		JaymosPowers.LOGGER.info("{}: running '{}'", why, command);
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't run '{}'", command, e);
		}
	}
}
