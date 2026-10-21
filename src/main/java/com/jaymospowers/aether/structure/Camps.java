package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A camp: tents of cloudwool round a campfire, farm plots, a well and a storage chest (hunter, farmer or fletcher loot). Three
 * palettes: the highfields (white and orange tents, skyroot), the magnetic (purple and cyan, wisproot) and the arctic (white and
 * light blue, snowy ground).
 */
final class Camps {
	// ---- tuning ----
	/** Tent: 5 across, 6 long, 3 high. */
	static final int TENT_W = 5, TENT_L = 6;
	/** Chance (per camp) that a farm plot is ripe. */
	static final float RIPE = 0.7F;

	private Camps() {
	}

	/** A palette: tent colours, the wood, the ground and the path. */
	private record Palette(String tentA, String tentB, String planks, String fence, String log, String ground, String path) {
	}

	private static Palette palette(int variant) {
		return switch (variant) {
			case 1 -> new Palette("purple_cloudwool", "cyan_cloudwool", "wisproot_planks", "wisproot_fence", "wisproot_log", "aether_dirt", "coarse_aether_dirt");
			case 2 -> new Palette("cloudwool", "light_blue_cloudwool", "skyroot_planks", "skyroot_fence", "skyroot_log", "arctic_snow_block", "arctic_packed_ice");
			default -> new Palette("cloudwool", "orange_cloudwool", "skyroot_planks", "skyroot_fence", "skyroot_log", "aether_dirt", "aether_dirt_path");
		};
	}

	static void build(Site s) {
		Palette p = palette(s.variant);
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.CAMP_SIZE - 1, top = s.box.maxY();
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 10);
		s.air(0, f, 0, n, top, n);
		// the ground: a round clearing, trampled to the palette's earth; paths out to the four sides
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			double d = Math.hypot(u - 15, v - 15);
			if (d > 15.5) {
				return null;
			}
			float r = s.rnd(u, y, v, 1);
			if (Math.abs(u - 15) <= 1 || Math.abs(v - 15) <= 1) {
				return d > 4 ? Kit.b(p.path) : Kit.b(p.ground);
			}
			return r < 0.2F ? Kit.b(p.path) : Kit.b(p.ground);
		});
		// the fire: a campfire in a ring of seats
		s.set(15, f, 15, Kit.v("campfire"));
		for (int[] q : new int[][] {{14, 15, 1}, {16, 15, 3}, {15, 14, 0}, {15, 16, 2}}) {
			s.set(q[0], f, q[1], Kit.stairs(p.planks.replace("planks", "stairs"), s.dir(q[2]), false));
		}
		// tents round the fire: (u, v, which side the opening faces: local 0 +v, 1 +u, 2 -v, 3 -u)
		tent(s, p, 3, 10, 1, p.tentA, false);
		tent(s, p, 22, 10, 3, p.tentB, false);
		tent(s, p, 10, 3, 0, p.tentB, true);
		tent(s, p, 10, 22, 2, p.tentA, false);
		// farm plots, a well, the storage
		farm(s, p, 1, 24, 0);
		farm(s, p, 24, 1, 1);
		well(s, p, 4, 1);
		storage(s, p, 24, 24);
		// torches by the fire
		for (int[] q : new int[][] {{13, 13}, {17, 13}, {13, 17}, {17, 17}}) {
			s.set(q[0], f, q[1], Kit.b("ambrosium_torch"));
		}
	}

	/** A tent whose 5x6 footprint starts at (u0, v0); {@code open} is the side its entrance is on; {@code storage} puts the chest inside. */
	private static void tent(Site s, Palette p, int u0, int v0, int open, String wool, boolean storage) {
		int f = s.floor;
		boolean alongV = open == 0 || open == 2;
		int w = alongV ? TENT_W : TENT_L, l = alongV ? TENT_L : TENT_W;
		int u1 = u0 + w - 1, v1 = v0 + l - 1;
		BlockState cloth = Kit.b(wool);
		// cross-section: a low wall at the edge, stepping in to a ridge down the middle (its profile is the highest cloth at each distance from the edge)
		int lo = alongV ? v0 : u0, hi = alongV ? v1 : u1;
		s.fill(u0, f, v0, u1, f + 3, v1, (u, y, v) -> {
			int across = alongV ? Math.min(u - u0, u1 - u) : Math.min(v - v0, v1 - v);
			int h = y - f, along = alongV ? v : u;
			boolean end = along == lo || along == hi;
			if (end) {
				boolean entrance = (open == 0 && v == v1 || open == 2 && v == v0 || open == 1 && u == u1 || open == 3 && u == u0) && across == 2 && h < 2;
				return h <= across + 1 && !entrance ? cloth : Kit.air();
			}
			boolean shell = across == 0 && h <= 1 || across == 1 && h == 2 || across == 2 && h == 3;
			return shell ? cloth : Kit.air();
		});
		// a bedroll and a lantern, or the storage chest
		int cu = (u0 + u1) / 2, cv = (v0 + v1) / 2;
		if (storage) {
			int cdir = open;
			s.chest(cu, f, cv, cdir, "chests/camp/" + new String[] {"hunter", "farmer", "fletcher"}[Math.floorMod((int)(s.seed >>> 7), 3)]);
			s.chest(cu + (alongV ? 1 : 0), f, cv + (alongV ? 0 : 1), cdir, "chests/camp/" + new String[] {"farmer", "fletcher", "hunter"}[Math.floorMod((int)(s.seed >>> 7), 3)]);
		} else {
			s.set(cu, f, cv, Kit.b("brown_cloudwool_carpet"));
			s.set(alongV ? cu : cu + 1, f, alongV ? cv + 1 : cv, Kit.b("red_cloudwool_carpet"));
		}
		s.set(cu, f + 2, cv, Kit.lantern());
	}

	/** A 5x4 farm plot: farmland with a water source in the middle, fenced; crops of orange, blue berry or valkyrie sprout. */
	private static void farm(Site s, Palette p, int u0, int v0, int salt) {
		int f = s.floor, y0 = s.box.minY();
		String crop = new String[] {"orange_tree", "berry_bush", "valkyrie_sprout"}[Math.floorMod((int)(s.seed >>> (3 + salt)), 3)];
		s.fill(u0, y0, v0, u0 + 4, y0, v0 + 4, Kit.b("aether_farmland"));
		s.set(u0 + 2, y0, v0 + 2, Kit.water());
		for (int u = u0; u <= u0 + 4; u++) {
			for (int v = v0; v <= v0 + 4; v++) {
				boolean edge = u == u0 || u == u0 + 4 || v == v0 || v == v0 + 4;
				if (edge && !(u == u0 + 2 && v == v0)) {
					s.set(u, f, v, Kit.b(p.fence));
				} else if (u == u0 + 2 && v == v0 + 2) {
					s.set(u, f, v, Kit.air());
				} else if (!edge) {
					s.set(u, f, v, s.rnd(u, f, v, 5) < RIPE ? Kit.ripe(crop) : Kit.b(crop));
				}
			}
		}
	}

	/** A well: a 3x3 ring of faded bricks round a water source, with posts and a small cloudwool roof. */
	private static void well(Site s, Palette p, int u0, int v0) {
		int f = s.floor, y0 = s.box.minY();
		s.fill(u0, y0, v0, u0 + 2, y0, v0 + 2, Kit.b("faded_holystone_bricks"));
		s.set(u0 + 1, y0, v0 + 1, Kit.water());
		for (int u = u0; u <= u0 + 2; u++) {
			for (int v = v0; v <= v0 + 2; v++) {
				if (u != u0 + 1 || v != v0 + 1) {
					s.set(u, f, v, Kit.b("faded_holystone_brick_slab"));
				}
			}
		}
		for (int[] q : new int[][] {{u0, v0}, {u0 + 2, v0}, {u0, v0 + 2}, {u0 + 2, v0 + 2}}) {
			s.fill(q[0], f + 1, q[1], q[0], f + 3, q[1], Kit.b(p.fence));
		}
		s.fill(u0, f + 4, v0, u0 + 2, f + 4, v0 + 2, Kit.b("cloudwool"));
		s.set(u0 + 1, f + 5, v0 + 1, Kit.b("cloudwool"));
	}

	/** The storage corner: a log pile and a fenced chest on a plank floor. */
	private static void storage(Site s, Palette p, int u0, int v0) {
		int f = s.floor, y0 = s.box.minY();
		s.fill(u0, y0, v0, u0 + 5, y0, v0 + 5, Kit.b(p.planks));
		String[] tables = {"hunter", "farmer", "fletcher"};
		String pick = tables[Math.floorMod((int)(s.seed >>> 11), 3)];
		s.chest(u0 + 1, f, v0 + 1, 2, "chests/camp/" + pick);
		s.fill(u0 + 3, f, v0 + 4, u0 + 5, f, v0 + 5, Kit.log(p.log, s.right.getAxis()));
		s.fill(u0 + 3, f + 1, v0 + 4, u0 + 5, f + 1, v0 + 5, Kit.log(p.log, s.right.getAxis()));
		s.fill(u0 + 4, f + 2, v0 + 4, u0 + 5, f + 2, v0 + 5, Kit.log(p.log, s.right.getAxis()));
		for (int u = u0; u <= u0 + 5; u++) {
			s.set(u, f, v0, Kit.b(p.fence));
		}
	}
}
