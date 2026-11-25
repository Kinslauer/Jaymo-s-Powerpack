package com.jaymoskitchen.corpse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A cap on how many corpses' ragdolls simulate at once ({@link GoreRules#RAGDOLL_CAP_RULE}). Every {@link #EVERY} ticks,
 * if more are moving than the cap, the farthest from any player and the longest dead settle and freeze where they lie
 * first. A settled body wakes again if something moves it, and is settled again at the next count. Only the server's
 * corpse ragdolls count (a living player's own limp body is simulated on their own client).
 */
final class RagdollBudget {
	/** Ticks between counts. */
	static final int EVERY = 20;
	/** A body's rank for settling: its distance to the nearest player (blocks) plus its age in seconds; the highest settle first. */
	private static final double PER_SECOND = 1.0 / 20.0;

	private static final Set<CorpseRagdoll> AWAKE = Collections.newSetFromMap(new WeakHashMap<>());
	private static long counted = Long.MIN_VALUE;

	private RagdollBudget() {
	}

	/** {@code ragdoll} is moving ({@code awake}) or at rest. */
	static void note(CorpseRagdoll ragdoll, boolean awake) {
		if (awake) {
			AWAKE.add(ragdoll);
		} else {
			AWAKE.remove(ragdoll);
		}
	}

	/** Counts the moving ragdolls in {@code level} (once a count a tick, whoever asks) and settles the excess. */
	static void enforce(ServerLevel level) {
		long now = level.getGameTime();
		if (now % EVERY != 0 || now == counted) {
			return;
		}
		counted = now;
		List<CorpseRagdoll> moving = new ArrayList<>();
		for (CorpseRagdoll ragdoll : AWAKE) {
			if (ragdoll.settleable(level)) {
				moving.add(ragdoll);
			}
		}
		int excess = moving.size() - GoreRules.cap(level);
		if (excess <= 0) {
			return;
		}
		moving.sort((a, b) -> Double.compare(rank(level, b), rank(level, a)));
		for (int i = 0; i < excess; i++) {
			moving.get(i).settle();
		}
	}

	private static double rank(ServerLevel level, CorpseRagdoll ragdoll) {
		CorpseEntity corpse = ragdoll.corpse();
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			nearest = Math.min(nearest, player.distanceToSqr(corpse));
		}
		return Math.sqrt(nearest) + corpse.tickCount * PER_SECOND;
	}
}
