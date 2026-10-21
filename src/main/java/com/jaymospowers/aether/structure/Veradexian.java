package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Veradexians' surface works: ruins (temperate and arctic), the ancient henge, the library under the ground and the aqueduct.
 * Their vases and chests hold the loot.
 */
final class Veradexian {
	// ---- tuning ----
	/** How much of a ruined wall is left: its columns are cut to a random height of this share or more of full. */
	static final float WALL_MIN_LEFT = 0.25F;
	/** Chance a ruin's wall column is missing entirely. */
	static final float WALL_GAP = 0.18F;
	/** Henge: the stones' heights, and the chance a stone has fallen (stands 1 or 2 high). */
	static final int STONE_MIN = 3, STONE_MAX = 5;
	static final float FALLEN = 0.25F;
	/** Library: the hall's height inside, and how many steps lead down. */
	static final int HALL_H = 7, STEPS = 11;
	/** Aqueduct: spacing of pillars (every this many blocks) and the channel's height above the base. */
	static final int PILLAR_EVERY = 8, CHANNEL_Y = 8;

	private Veradexian() {
	}

	private static boolean arctic(Site s) {
		return s.variant == 1;
	}

	private static BlockState brick(Site s, int u, int y, int v) {
		float r = s.rnd(u, y, v, 11);
		if (arctic(s)) {
			return r < 0.25F ? Kit.b("icestone_bricks") : r < 0.35F ? Kit.b("arctic_packed_ice") : Kit.b("faded_holystone_bricks");
		}
		return r < 0.3F ? Kit.b("mossy_holystone") : r < 0.5F ? Kit.b("faded_holystone_bricks") : Kit.b("holystone_bricks");
	}

	private static BlockState floorBlock(Site s, int u, int y, int v) {
		float r = s.rnd(u, y, v, 12);
		if (arctic(s)) {
			return r < 0.3F ? Kit.b("arctic_packed_ice") : r < 0.6F ? Kit.b("icestone_bricks") : Kit.b("faded_holystone_flagstones");
		}
		return r < 0.25F ? Kit.b("mossy_holystone") : Kit.b("faded_holystone_flagstones");
	}

	private static BlockState pillar() {
		return Kit.log("faded_holystone_pillar", Direction.Axis.Y);
	}

	/** A ruined wall: columns along the rectangle's edge, each cut to a random height (some gone). */
	private static void ruinedWall(Site s, int u0, int v0, int u1, int v1, int h, int salt) {
		int f = s.floor;
		s.fill(u0, f, v0, u1, f + h - 1, v1, (u, y, v) -> {
			if (u != u0 && u != u1 && v != v0 && v != v1) {
				return null;
			}
			float cut = s.rnd(u, 0, v, salt);
			if (cut < WALL_GAP) {
				return null;
			}
			int left = Math.max(1, (int)Math.ceil(h * (WALL_MIN_LEFT + (1 - WALL_MIN_LEFT) * s.rnd(u, 1, v, salt + 1))));
			return y - f < left ? brick(s, u, y, v) : null;
		});
	}

	/** A vase at a spot, holystone or veradexian by chance, with the ruins' loot. */
	private static void vase(Site s, int u, int v, String table) {
		s.vase(u, s.floor, v, s.rnd(u, 0, v, 13) < 0.5F ? "holystone" : "veradexian", table);
	}

	// ---- the ruins ----

	static void ruins(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.RUINS_SIZE - 1, top = s.box.maxY();
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 12);
		s.air(0, f, 0, n, top, n);
		boolean cold = arctic(s);
		// the ground, rough: grass or snow, with bare patches
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			if (Math.hypot(u - 13, v - 13) > 14) {
				return null;
			}
			float r = s.rnd(u, y, v, 14);
			return cold ? (r < 0.5F ? Kit.b("arctic_snow_block") : r < 0.7F ? Kit.b("arctic_packed_ice") : Kit.b("coarse_aether_dirt"))
				: (r < 0.7F ? Kit.b("aether_grass_block") : r < 0.85F ? Kit.b("coarse_aether_dirt") : Kit.b("mossy_holystone"));
		});
		// a plaza in the middle: flags, four pillars and a vase on a plinth
		s.fill(9, y0, 9, 17, y0, 17, (u, y, v) -> floorBlock(s, u, y, v));
		for (int[] c : new int[][] {{10, 10}, {16, 10}, {10, 16}, {16, 16}}) {
			int h = 2 + (int)(s.rnd(c[0], 2, c[1], 15) * 4);
			s.fill(c[0], f, c[1], c[0], f + h, c[1], pillar());
		}
		s.set(13, f, 13, Kit.b("faded_holystone_bricks"));
		s.vase(13, f + 1, 13, "veradexian", "vases/veradexian_ruins");
		// small ruins in three corners and a large one in the fourth (chosen by the seed)
		int large = Math.floorMod((int)(s.seed >>> 9), 4);
		int[][] corners = {{1, 1}, {19, 1}, {1, 19}, {19, 19}};
		for (int i = 0; i < 4; i++) {
			int cu = corners[i][0], cv = corners[i][1];
			if (i == large) {
				int lu = i % 2 == 0 ? 1 : n - 11, lv = i < 2 ? 1 : n - 8;
				ruinedWall(s, lu, lv, lu + 10, lv + 7, 6, 20 + i);
				s.fill(lu + 1, y0, lv + 1, lu + 9, y0, lv + 6, (u, y, v) -> floorBlock(s, u, y, v));
				ruinedWall(s, lu + 3, lv + 2, lu + 7, lv + 5, 3, 30 + i);
				for (int k = 0; k < 4; k++) {
					vase(s, lu + 1 + k * 2, lv + 1, "vases/veradexian_ruins");
				}
				vase(s, lu + 9, lv + 6, "vases/veradexian_ruins");
				s.set(lu + 5, f, lv + 6, Kit.b("holystone_vase"));
			} else {
				ruinedWall(s, cu, cv, cu + 5, cv + 5, 4, 40 + i);
				s.fill(cu + 1, y0, cv + 1, cu + 4, y0, cv + 4, (u, y, v) -> floorBlock(s, u, y, v));
				vase(s, cu + 1, cv + 1, "vases/veradexian_ruins");
				vase(s, cu + 4, cv + 3, "vases/veradexian_ruins");
			}
		}
		// fallen stones and snow or moss between
		for (int u = 0; u <= n; u++) {
			for (int v = 0; v <= n; v++) {
				float r = s.rnd(u, f, v, 16);
				if (s.get(u, f, v).isAir() && r < 0.06F) {
					s.set(u, f, v, cold ? Kit.b("arctic_snow") : r < 0.03F ? Kit.b("holystone_rock") : Kit.b("bryalinn_moss_carpet"));
				}
			}
		}
	}

	// ---- the henge ----

	static void henge(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.HENGE_SIZE - 1, top = s.box.maxY();
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 12);
		s.air(0, f, 0, n, top, n);
		// the platform, 7x7 and two high: holystone and grass over it, four vases at the corners
		s.fill(10, y0, 10, 16, y0, 16, Kit.b("holystone_bricks"));
		s.fill(10, f, 10, 16, f, 16, Kit.b("aether_grass_block"));
		for (int[] c : new int[][] {{10, 10}, {16, 10}, {10, 16}, {16, 16}}) {
			s.vase(c[0], f + 1, c[1], "holystone", "vases/ancient_henge");
		}
		s.set(13, f + 1, 13, Kit.b("holystone_keystone"));
		// four henges, one off each side, each with a path to the platform
		for (int k = 0; k < 4; k++) {
			henge(s, k);
		}
	}

	/** One henge on local side k (0 +v, 1 +u, 2 -v, 3 -u): a path, a gate of two tall stones and a lintel, flanking stones. */
	private static void henge(Site s, int k) {
		int f = s.floor, y0 = s.box.minY();
		// work in a frame where the side is "up" (+a), the platform at a = 0: map (a, b) to (u, v)
		for (int a = 4; a <= 11; a++) {
			for (int b = -1; b <= 1; b++) {
				int[] p = side(k, a, b);
				s.set(p[0], y0, p[1], Kit.b("aether_dirt_path"));
			}
		}
		// the gate
		int gh = STONE_MIN + 1 + (int)(s.rnd(k, 3, 0, 17) * 2);
		for (int b : new int[] {-2, 2}) {
			int[] p = side(k, 9, b);
			s.fill(p[0], f, p[1], p[0], f + gh - 1, p[1], pillar());
		}
		for (int b = -2; b <= 2; b++) {
			int[] p = side(k, 9, b);
			s.set(p[0], f + gh, p[1], b == 0 ? Kit.b("holystone_keystone") : Kit.b("holystone_bricks"));
		}
		// flanking stones, some fallen
		for (int i = 0; i < 6; i++) {
			int a = 6 + (i % 3) * 3, b = (i < 3 ? -1 : 1) * (4 + (int)(s.rnd(k, i, 1, 18) * 2));
			int[] p = side(k, a, b);
			boolean fallen = s.rnd(k, i, 2, 19) < FALLEN;
			int h = fallen ? 1 + (int)(s.rnd(k, i, 3, 20) * 2) : STONE_MIN + (int)(s.rnd(k, i, 4, 21) * (STONE_MAX - STONE_MIN + 1));
			s.fill(p[0], f, p[1], p[0], f + h - 1, p[1], fallen ? Kit.b("mossy_holystone") : pillar());
		}
	}

	/** The local (u, v) of the henge frame's cell (a outward from the middle on side k, b across). */
	private static int[] side(int k, int a, int b) {
		return switch (k) {
			case 0 -> new int[] {13 + b, 13 + a};
			case 1 -> new int[] {13 + a, 13 + b};
			case 2 -> new int[] {13 + b, 13 - a};
			default -> new int[] {13 - a, 13 + b};
		};
	}

	// ---- the library ----

	static void library(Site s) {
		boolean cold = arctic(s);
		int g = s.box.minY() + AetherStructure.LIBRARY_DEPTH;
		int f = s.floor;
		int w = AetherStructure.LIBRARY_W - 1, l = AetherStructure.LIBRARY_L - 1, top = s.box.maxY();
		// the ground over the whole footprint is cleared above the surface; the rest is carved out of the rock
		s.air(0, g + 1, 0, w, top, l);
		BlockState wallB = cold ? Kit.b("faded_holystone_bricks") : Kit.b("holystone_bricks");
		// the entrance building: u 8 to 16, v 0 to 8, on the surface
		s.foundation(8, 0, 16, 8, g - 1, Kit.b("holystone"), 10);
		s.fill(8, g, 0, 16, g, 8, (u, y, v) -> floorBlock(s, u, y, v));
		s.room(8, g + 1, 0, 16, g + 5, 8, (u, y, v) -> {
			boolean corner = (u == 8 || u == 16) && (v == 0 || v == 8);
			return corner ? pillar() : brick(s, u, y, v);
		}, false);
		s.air(11, g + 1, 0, 13, g + 3, 0);
		s.air(11, g + 1, 8, 13, g + 3, 8);
		s.pyramid(8, 0, 16, 8, g + 6, cold ? Kit.b("cloudwool") : Kit.b("skyroot_planks"));
		s.set(10, g + 4, 0, Kit.b("faded_holystone_keystone"));
		s.set(14, g + 4, 0, Kit.b("faded_holystone_keystone"));
		s.candle(10, g + 2, 1, 0);
		s.candle(14, g + 2, 1, 0);
		// the stairs down: 11 steps, a tunnel 3 wide with walls and a roof of brick
		BlockState step = Kit.stairs(cold ? "faded_holystone_brick_stairs" : "holystone_brick_stairs", s.dir(2), false);
		for (int k = 0; k < STEPS; k++) {
			int v = 9 + k, y = g - k;
			s.fill(10, y, v, 14, y + 4, v, (u, yy, vv) -> {
				boolean wall = u == 10 || u == 14 || yy == y + 4;
				return yy == y ? (u == 10 || u == 14 ? wallB : step) : wall ? brick(s, u, yy, vv) : Kit.air();
			});
			if (k % 3 == 1) {
				s.candle(11, y + 2, v, 1);
			}
		}
		// the hall: shell u 6 to 18, v 19 to 35; inside u 7 to 17, v 20 to 34
		int y0 = s.box.minY();
		s.fill(6, y0, 19, 18, f + HALL_H, l, (u, y, v) -> {
			boolean edge = u == 6 || u == 18 || v == 19 || v == l;
			boolean cap = y == y0 || y == f + HALL_H;
			if (y == y0) {
				return floorBlock(s, u, y, v);
			}
			return edge || cap ? brick(s, u, y, v) : Kit.air();
		});
		s.air(11, f, 19, 13, f + 3, 19);
		// bookshelves along both long walls, a reading table in the middle, lamps and candles
		for (int v = 22; v <= 32; v++) {
			if (v % 5 == 2 && v > 22 && v < 32) {
				continue;
			}
			s.fill(7, f, v, 8, f + 2, v, Kit.v("bookshelf"));
			s.fill(16, f, v, 17, f + 2, v, Kit.v("bookshelf"));
		}
		s.fill(11, f, 26, 13, f, 28, Kit.b("skyroot_planks"));
		s.set(12, f + 1, 27, Kit.v("flower_pot"));
		for (int v : new int[] {24, 30}) {
			s.set(9, f + HALL_H - 1, v, Kit.lantern());
			s.set(15, f + HALL_H - 1, v, Kit.lantern());
		}
		for (int v : new int[] {23, 27, 31}) {
			s.candle(9, f + 2, v, 1);
			s.candle(15, f + 2, v, 3);
		}
		// common chests in the hall's corners
		s.chest(8, f, 33, 3, "chests/veradexian_library/common");
		s.chest(16, f, 33, 1, "chests/veradexian_library/common");
		s.chest(12, f, 33, 2, "chests/veradexian_library/common");
		// two secret rooms behind a double wall of bookshelves, west and east, each with vault chests and a vase
		for (int side = 0; side < 2; side++) {
			int u0 = side == 0 ? 0 : 19, u1 = side == 0 ? 5 : 24;
			s.fill(u0, y0, 24, u1, f + 4, 30, (u, y, v) -> {
				boolean edge = u == u0 || u == u1 || v == 24 || v == 30;
				if (y == y0 || y == f + 4 || edge) {
					return y == y0 ? floorBlock(s, u, y, v) : brick(s, u, y, v);
				}
				return Kit.air();
			});
			int doorU = side == 0 ? 5 : 19, hallU = side == 0 ? 6 : 18;
			for (int y = f; y <= f + 1; y++) {
				s.set(doorU, y, 27, Kit.v("bookshelf"));
				s.set(hallU, y, 27, Kit.v("bookshelf"));
			}
			int in = side == 0 ? 1 : 23, face = side == 0 ? 1 : 3;
			s.chest(in, f, 26, face, "chests/veradexian_library/vaults");
			s.chest(in, f, 28, face, "chests/veradexian_library/vaults");
			s.set(side == 0 ? 3 : 21, f + 3, 27, Kit.lantern());
			s.vase(side == 0 ? 3 : 21, f, 25, "veradexian", "vases/veradexian_ruins");
		}
	}

	// ---- the aqueduct ----

	static void aqueduct(Site s) {
		int y0 = s.box.minY(), l = AetherStructure.AQUEDUCT_L - 1;
		int yc = y0 + CHANNEL_Y;
		BlockState brickB = Kit.b("faded_holystone_bricks");
		s.air(0, y0 + 1, 0, 8, s.box.maxY(), l);
		// the walkway and channel: a floor of brick 7 wide, walls at u 2 and 6, water between
		s.fill(1, yc, 0, 7, yc, l, (u, y, v) -> brick(s, u, y, v));
		s.fill(2, yc + 1, 0, 6, yc + 2, l, (u, y, v) -> {
			boolean wall = u == 2 || u == 6 || v == 0 || v == l;
			if (wall) {
				return y == yc + 1 ? brick(s, u, y, v) : (u == 2 || u == 6 ? Kit.b("faded_holystone_brick_slab") : null);
			}
			return y == yc + 1 ? Kit.water() : Kit.air();
		});
		// pillars every 8 blocks (2 thick) down to the ground, and arches between them
		for (int p = 0; p * PILLAR_EVERY <= l; p++) {
			int v0 = p * PILLAR_EVERY, v1 = Math.min(v0 + 1, l);
			s.foundation(3, v0, 5, v1, yc - 1, brickB, 60);
			if (p * PILLAR_EVERY + PILLAR_EVERY <= l) {
				int a = v0 + 2, b = v0 + PILLAR_EVERY - 1;
				double mid = (a + b) / 2.0;
				s.fill(3, yc - 4, a, 5, yc - 1, b, (u, y, v) -> Math.hypot(v - mid, y - (yc - 4)) > 3.0 ? brick(s, u, y, v) : null);
			}
		}
		// vases and lamps on the walkway rim
		for (int v : new int[] {4, 20, 28, 44}) {
			s.vase(1, yc + 1, v, s.rnd(v, 0, 0, 22) < 0.5F ? "holystone" : "veradexian", "vases/veradexian_ruins");
			s.vase(7, yc + 1, v + 2, s.rnd(v, 1, 0, 23) < 0.5F ? "holystone" : "veradexian", "vases/veradexian_ruins");
		}
		for (int v = 6; v < l; v += 12) {
			s.set(1, yc + 1, v, Kit.b("ambrosium_torch"));
			s.set(7, yc + 1, v, Kit.b("ambrosium_torch"));
		}
	}
}
