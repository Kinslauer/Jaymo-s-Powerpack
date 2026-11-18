package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.popcraft.chunky.ChunkyProvider;
import org.popcraft.chunky.api.ChunkyAPI;

/**
 * Chunky (pre-generation, only on the server) waits while anyone plays. Andy, after the Steam Deck's server ran out of
 * memory with a 2,500-block pre-generation running while he explored a new world (crash 2026-11-13 18:22): "pause Chunky
 * whenever a player is on". It uses Chunky's API: its task is paused when the first player is on, and continued once
 * nobody is.
 * <p>
 * A pause only asks the task to stop; on the Steam Deck it can take 20 seconds or more to drain, and a continue sent
 * before that does nothing ({@code isRunning} still true). So every continue waits until {@code isRunning} is false.
 * <p>
 * After every restart on 2026-11-18 the task came up but never made a chunk (0 a second) until it was paused and
 * continued by hand. So with nobody on, a watchdog looks at Chunky's progress events: running but no event for
 * {@link #STALL_TICKS}, it pauses the task, waits for it to drain, and continues it, at most {@link #MAX_KICKS} times
 * in a row (a progress event starts the count again).
 */
public final class ChunkyCompat {
	private static final String MOD_ID = "chunky";
	/** How often (ticks) the state is looked at. */
	static final int CHECK_TICKS = 20;
	/** Ticks after start-up before Chunky is first told to continue (the worlds are still settling before that). */
	static final int START_WAIT = 60 * 20;
	/** Ticks of running with no progress event before the watchdog kicks the task. */
	static final int STALL_TICKS = 60 * 20;
	/** Kicks in a row, without a progress event between, before the watchdog gives up. */
	static final int MAX_KICKS = 3;
	/** Ticks a kick's pause is waited on before that attempt is dropped. */
	static final int DRAIN_LIMIT_TICKS = 3 * 60 * 20;

	/** Whether the server had players on at the last look (null: not looked yet since it started). */
	private static Boolean busy;
	/** A continue is wanted as soon as the task has drained (start-up, or the last player left). */
	private static boolean continueDue;
	/** Progress events seen, counted by Chunky's thread. */
	private static final AtomicLong progressEvents = new AtomicLong();
	private static long seenEvents;
	/** The tick of the last progress event, or of the task coming up. */
	private static int lastProgressTick;
	private static int kicks;
	/** The tick the watchdog paused the task at, or -1 when it is not waiting on a drain. */
	private static int kickPausedAt = -1;
	private static boolean listening, gaveUpLogged;

	private ChunkyCompat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(ChunkyCompat::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			busy = null;
			continueDue = false;
			seenEvents = 0;
			progressEvents.set(0);
			lastProgressTick = 0;
			kicks = 0;
			kickPausedAt = -1;
			gaveUpLogged = false;
		});
	}

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		if (ticks % CHECK_TICKS != 0 || !Api.ready()) {
			return;
		}
		Api.listen();
		boolean now = !server.getPlayerList().getPlayers().isEmpty();
		List<String> worlds = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			worlds.add(level.dimension().identifier().toString());
		}
		boolean running = Api.anyRunning(worlds);

		long events = progressEvents.get();
		if (events != seenEvents || !running) {
			if (events != seenEvents) {
				kicks = 0;
				gaveUpLogged = false;
			}
			seenEvents = events;
			lastProgressTick = ticks;
		}

		if (busy == null && !now && ticks >= START_WAIT) {
			busy = false;
			continueDue = true;
		} else if (busy != null && busy != now) {
			busy = now;
			kickPausedAt = -1;
			if (now) {
				continueDue = false;
				kicks = 0;
				log("A player is on", "pause", Api.pause(worlds));
			} else {
				continueDue = true;
			}
		}

		if (busy == null || busy) {
			return;
		}
		if (continueDue && !running) {
			continueDue = false;
			log("Nobody is on", "continue", Api.resume(worlds));
			lastProgressTick = ticks;
		} else if (kickPausedAt >= 0) {
			// (the watchdog's pause is still draining: the continue waits for it)
			if (!running) {
				kickPausedAt = -1;
				log("Watchdog", "continue", Api.resume(worlds));
				lastProgressTick = ticks;
			} else if (ticks - kickPausedAt >= DRAIN_LIMIT_TICKS) {
				kickPausedAt = -1;
				JaymosPowers.LOGGER.warn("Chunky is still draining {} s after the watchdog paused it", DRAIN_LIMIT_TICKS / 20);
			}
		} else if (!continueDue && running && ticks - lastProgressTick >= STALL_TICKS) {
			if (kicks >= MAX_KICKS) {
				if (!gaveUpLogged) {
					gaveUpLogged = true;
					JaymosPowers.LOGGER.warn("Chunky made no progress after {} pause and continue kicks; leaving it", MAX_KICKS);
				}
				return;
			}
			kicks++;
			kickPausedAt = ticks;
			log("Chunky made no progress for " + STALL_TICKS / 20 + " s", "pause", Api.pause(worlds));
		}
	}

	private static void log(String why, String what, int worlds) {
		JaymosPowers.LOGGER.info("{}: Chunky {} sent to {} world(s)", why, what, worlds);
	}

	/** The only place Chunky's classes are named, so nothing loads them while Chunky is absent. */
	private static final class Api {
		static boolean ready() {
			return ChunkyProvider.get() != null;
		}

		static void listen() {
			if (!listening) {
				listening = true;
				ChunkyProvider.get().getApi().onGenerationProgress(event -> progressEvents.incrementAndGet());
			}
		}

		static boolean anyRunning(List<String> worlds) {
			ChunkyAPI api = ChunkyProvider.get().getApi();
			for (String world : worlds) {
				if (api.isRunning(world)) {
					return true;
				}
			}
			return false;
		}

		/** Pauses the tasks that are running; the count is how many were. */
		static int pause(List<String> worlds) {
			ChunkyAPI api = ChunkyProvider.get().getApi();
			int n = 0;
			for (String world : worlds) {
				if (api.isRunning(world) && api.pauseTask(world)) {
					n++;
				}
			}
			return n;
		}

		/** Continues each world's saved task; Chunky says no (quietly) where there is none. */
		static int resume(List<String> worlds) {
			ChunkyAPI api = ChunkyProvider.get().getApi();
			int n = 0;
			for (String world : worlds) {
				if (api.continueTask(world)) {
					n++;
				}
			}
			return n;
		}
	}
}
