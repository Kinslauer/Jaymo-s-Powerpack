package com.jaymoskingdoms.sailing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.179) The open deck of a crewed ship, in her frame, for her crew to walk about: every place one can stand
 * ({@link Fleet#stations}: her open deck and the place inboard of each gun, not her wheel), each joined to its
 * neighbours a step apart, and the way from one place to another along them. Her crew ride her, so nothing pathfinds
 * on her: they walk her deck by moving their station ({@link Ship#SEAT}) along these paths.
 */
public final class Deck {
	/** The most a neighbouring place may be above or below (blocks): a slab's step, no more. */
	static final double STEP = 0.6;

	/** Every place on her deck (packed cell → where one stands there, in her frame). */
	public final Map<Long, Vec3> places = new HashMap<>();
	/** The place inboard of each gun (packed gun → where its gunner stands). */
	public final Map<Long, Vec3> guns;
	/** The hull this was worked out from. */
	final Hull of;

	private Deck(Hull of, Fleet.Stations stations) {
		this.of = of;
		this.guns = stations.guns();
		for (Vec3 p : stations.deck()) {
			this.places.put(cell(p), p);
		}
		for (Vec3 p : stations.guns().values()) {
			this.places.put(cell(p), p);
		}
	}

	/** {@code ship}'s deck, worked out again only when her hull changes. Null with no hull. */
	public static @Nullable Deck of(Ship ship, Crew crew) {
		Hull h = ship.hull();
		if (h == null) {
			return null;
		}
		if (crew.deck == null || crew.deck.of != h) {
			crew.deck = new Deck(h, Fleet.stations(ship));
		}
		return crew.deck;
	}

	static long cell(Vec3 p) {
		return BlockPos.asLong(Mth.floor(p.x + 0.5), Mth.floor(p.y + 0.01), Mth.floor(p.z + 0.5));
	}

	/** The place nearest {@code at} (in her frame), or null with none. */
	public @Nullable Vec3 nearest(Vec3 at) {
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (Vec3 p : this.places.values()) {
			double d = p.distanceToSqr(at);
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** The places a step from {@code p}: the four beside it, at much the same height. */
	private List<Vec3> neighbours(Vec3 p) {
		List<Vec3> out = new ArrayList<>(4);
		int x = Mth.floor(p.x + 0.5), z = Mth.floor(p.z + 0.5), y = Mth.floor(p.y + 0.01);
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : steps) {
			for (int dy = -1; dy <= 1; dy++) {
				Vec3 n = this.places.get(BlockPos.asLong(x + s[0], y + dy, z + s[1]));
				if (n != null && Math.abs(n.y - p.y) <= STEP) {
					out.add(n);
					break;
				}
			}
		}
		return out;
	}

	/**
	 * The way along her deck from {@code from} (where one stands now, in her frame) to {@code to}: each place to walk
	 * through in turn, ending at {@code to}. Empty if one's there already; null if there's no way.
	 */
	public @Nullable List<Vec3> path(Vec3 from, Vec3 to) {
		Vec3 start = this.nearest(from);
		if (start == null || !this.places.containsKey(cell(to))) {
			return null;
		}
		Vec3 goal = this.places.get(cell(to));
		Map<Vec3, Vec3> came = new HashMap<>();
		ArrayDeque<Vec3> open = new ArrayDeque<>();
		came.put(start, start);
		open.add(start);
		while (!open.isEmpty()) {
			Vec3 p = open.poll();
			if (p == goal) {
				ArrayList<Vec3> way = new ArrayList<>();
				for (Vec3 q = p; q != start; q = came.get(q)) {
					way.add(0, q);
				}
				if (start.distanceToSqr(from) > 0.01) {
					way.add(0, start);
				}
				return way;
			}
			for (Vec3 n : this.neighbours(p)) {
				if (!came.containsKey(n)) {
					came.put(n, p);
					open.add(n);
				}
			}
		}
		return null;
	}

	/** Every place one can walk to from {@code from} (where one stands now, in her frame). */
	public List<Vec3> reachable(Vec3 from) {
		List<Vec3> out = new ArrayList<>();
		Vec3 start = this.nearest(from);
		if (start == null) {
			return out;
		}
		Map<Vec3, Boolean> seen = new HashMap<>();
		ArrayDeque<Vec3> open = new ArrayDeque<>();
		seen.put(start, true);
		open.add(start);
		while (!open.isEmpty()) {
			Vec3 p = open.poll();
			out.add(p);
			for (Vec3 n : this.neighbours(p)) {
				if (seen.putIfAbsent(n, true) == null) {
					open.add(n);
				}
			}
		}
		return out;
	}
}
