package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Irradiated remnants, in the contaminated lands: a bunker sunk into the ground (variant 0) or the ruins of a settlement (variant 1),
 * both overgrown with sickly leaves, poisonous stone underfoot and chests of old supplies.
 */
final class Irradiated {
	// ---- tuning ----
	/** Share of ground that is irradiated stone, and of ruin blocks that are missing. */
	static final float TAINT = 0.25F, DECAY = 0.2F;

	private Irradiated() {
	}

	private static BlockState wall(Site s, int u, int y, int v) {
		float r = s.rnd(u, y, v, 41);
		return r < 0.25F ? Kit.b("irradiated_holystone") : r < 0.4F ? Kit.b("faded_holystone_flagstones") : Kit.b("faded_holystone_bricks");
	}

	static void build(Site s) {
		if (s.variant == 0) {
			bunker(s);
		} else {
			settlement(s);
		}
	}

	/** The ground round a remnant: patches of irradiated stone, drifts of irradiated leaves. */
	private static void ground(Site s, int radius) {
		int y0 = s.box.minY() + (s.variant == 0 ? AetherStructure.BUNKER_DEPTH : 0), f = y0 + 1, n = AetherStructure.IRRADIATED_SIZE - 1;
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			if (Math.hypot(u - 11, v - 11) > radius) {
				return null;
			}
			float r = s.rnd(u, y, v, 42);
			return r < TAINT ? Kit.b("irradiated_holystone") : r < 0.4F ? Kit.b("coarse_aether_dirt") : null;
		});
		for (int u = 0; u <= n; u++) {
			for (int v = 0; v <= n; v++) {
				if (Math.hypot(u - 11, v - 11) < radius && s.rnd(u, f, v, 43) < 0.05F) {
					s.set(u, f, v, Kit.b("irradiated_leaves"));
				}
			}
		}
	}

	// ---- the bunker ----

	private static void bunker(Site s) {
		int f = s.floor, g = s.box.minY() + AetherStructure.BUNKER_DEPTH, n = AetherStructure.IRRADIATED_SIZE - 1, top = s.box.maxY();
		s.foundation(0, 0, n, n, g - 1, Kit.b("holystone"), 12);
		s.air(0, g + 1, 0, n, top, n);
		ground(s, 11);
		// the hull: u 4 to 18, v 6 to 16, floor at f, the roof just over the ground
		int u0 = 4, u1 = 18, v0 = 6, v1 = 16, h = 4;
		s.fill(u0, f - 1, v0, u1, f + h, v1, (u, y, v) -> {
			boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
			if (y == f - 1) {
				return Kit.b("faded_holystone_flagstones");
			}
			if (y == f + h) {
				return s.rnd(u, y, v, 44) < DECAY ? null : wall(s, u, y, v);
			}
			return edge ? wall(s, u, y, v) : Kit.air();
		});
		// a dividing wall with a doorway: the research room (west) and the store (east)
		s.fill(11, f, v0 + 1, 11, f + h - 1, v1 - 1, (u, y, v) -> v >= 10 && v <= 11 && y - f < 3 ? Kit.air() : wall(s, u, y, v));
		// windows of bars, some broken
		for (int v = v0 + 2; v < v1; v += 3) {
			if (s.rnd(v, 0, 0, 45) > 0.3F) {
				s.set(u0, f + 2, v, Kit.b("arkenium_bars"));
				s.set(u1, f + 2, v, Kit.b("arkenium_bars"));
			}
		}
		// the way in: a stair trench down to the door at v 6, walls of brick
		BlockState step = Kit.stairs("faded_holystone_brick_stairs", s.dir(2), false);
		for (int k = 0; k < 4; k++) {
			int v = 2 + k, y = g - k;
			s.fill(9, y, v, 13, y + 4, v, (u, yy, vv) -> {
				if (yy == y) {
					return u == 9 || u == 13 ? wall(s, u, yy, vv) : step;
				}
				return u == 9 || u == 13 ? wall(s, u, yy, vv) : Kit.air();
			});
		}
		s.air(10, f, v0, 12, f + 2, v0);
		// inside: terminals of scatterglass and arkenium, supplies, lamps
		s.chest(5, f, 7, 0, "chests/irradiated_remnants");
		s.chest(5, f, 14, 2, "chests/irradiated_remnants");
		s.chest(17, f, 9, 3, "chests/irradiated_remnants");
		s.chest(17, f, 14, 3, "chests/irradiated_remnants");
		for (int v = 8; v <= 14; v += 3) {
			s.set(6, f, v, Kit.b("arkenium_block"));
			s.set(6, f + 1, v, Kit.b("scatterglass"));
		}
		s.set(8, f + 3, 11, Kit.lantern());
		s.set(14, f + 3, 11, Kit.lantern());
		s.set(14, f, 7, Kit.v("barrel"));
	}

	// ---- the settlement ----

	private static void settlement(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.IRRADIATED_SIZE - 1, top = s.box.maxY();
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 12);
		s.air(0, f, 0, n, top, n);
		ground(s, 11);
		// the old outpost in the middle: a decayed 7x7 tower with a chest, a floor of flags
		s.fill(8, y0, 8, 14, y0, 14, Kit.b("faded_holystone_flagstones"));
		decayedWalls(s, 8, 8, 14, 14, 6, 50);
		s.air(11, f, 8, 11, f + 2, 8);
		s.chest(12, f, 13, 2, "chests/irradiated_remnants");
		s.chest(9, f, 13, 2, "chests/irradiated_remnants");
		s.set(11, f, 11, Kit.b("ambrosium_torch"));
		// houses round it: three of skyroot planks and cloudwool, falling in
		int[][] houses = {{1, 1}, {16, 2}, {2, 16}, {16, 16}};
		int skip = Math.floorMod((int)(s.seed >>> 6), 4);
		for (int i = 0; i < 4; i++) {
			if (i == skip) {
				continue;
			}
			int hu = houses[i][0], hv = houses[i][1];
			s.fill(hu, y0, hv, hu + 5, y0, hv + 5, Kit.b("skyroot_planks"));
			decayedWalls(s, hu, hv, hu + 5, hv + 5, 4, 60 + i);
			s.pyramid(hu, hv, hu + 5, hv + 5, f + 4, Kit.b("cloudwool"));
			s.air(hu + 2, f, hv + 5, hu + 3, f + 1, hv + 5);
			s.chest(hu + 1, f, hv + 1, 0, "chests/irradiated_remnants");
		}
	}

	/** Walls of a rectangle, of planks or brick by chance, with holes and cut-down stretches. */
	private static void decayedWalls(Site s, int u0, int v0, int u1, int v1, int h, int salt) {
		int f = s.floor;
		s.fill(u0, f, v0, u1, f + h - 1, v1, (u, y, v) -> {
			if (u != u0 && u != u1 && v != v0 && v != v1) {
				return null;
			}
			if (s.rnd(u, y, v, salt) < DECAY) {
				return Kit.air();
			}
			if (s.rnd(u, 0, v, salt + 1) < 0.3F && y - f >= h - 2) {
				return Kit.air();
			}
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			return corner ? Kit.log("faded_holystone_pillar", Direction.Axis.Y) : s.rnd(u, y, v, salt + 2) < 0.5F ? Kit.b("skyroot_planks") : wall(s, u, y, v);
		});
	}
}
