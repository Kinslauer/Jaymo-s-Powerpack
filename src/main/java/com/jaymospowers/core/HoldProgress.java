package com.jaymospowers.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * (0.21.39) Andy: "the bar should stay where it is even after you stop ... the same way tree chopping works, every meter
 * should work this way, especially washing rags" (throws and combat charges excepted). A held job's bar (washing a rag,
 * a treatment, a revive, giving up, Second Wind, coming to, a lift) no longer empties when you let go: how far
 * it got is kept here, by player and by job (a job names what it was at, such as the body and part being treated), and
 * the next hold at the same job carries on from there. A job done is forgotten; so is a hold a blow broke off: only stopping
 * keeps it. (Getting up is the exception: let go and you sink back down, see Downed.rise.) Not saved (a restart empties every bar).
 */
public final class HoldProgress {
	/** How many jobs are kept per player; past it, the oldest goes. */
	static final int MOST = 32;

	private static final Map<UUID, LinkedHashMap<String, Integer>> KEPT = new HashMap<>();

	private HoldProgress() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KEPT.clear());
	}

	/** How far {@code player} got at {@code job} (ticks), 0 if never begun or done. */
	public static int get(UUID player, String job) {
		Map<String, Integer> jobs = KEPT.get(player);
		Integer ticks = jobs == null ? null : jobs.get(job);
		return ticks == null ? 0 : ticks;
	}

	/** Keeps {@code ticks} as how far {@code player} got at {@code job} (0 or less forgets it). */
	public static void set(UUID player, String job, int ticks) {
		if (ticks <= 0) {
			forget(player, job);
			return;
		}
		LinkedHashMap<String, Integer> jobs = KEPT.computeIfAbsent(player, id -> new LinkedHashMap<>());
		jobs.remove(job);
		jobs.put(job, ticks);
		while (jobs.size() > MOST) {
			jobs.remove(jobs.keySet().iterator().next());
		}
	}

	/** Adds {@code ticks} to {@code player}'s {@code job} and returns how far it is now. */
	public static int add(UUID player, String job, int ticks) {
		int now = get(player, job) + ticks;
		set(player, job, now);
		return now;
	}

	/** The job is done (or spoiled): its bar starts empty next time. */
	public static void forget(UUID player, String job) {
		Map<String, Integer> jobs = KEPT.get(player);
		if (jobs != null && jobs.remove(job) != null && jobs.isEmpty()) {
			KEPT.remove(player);
		}
	}
}
