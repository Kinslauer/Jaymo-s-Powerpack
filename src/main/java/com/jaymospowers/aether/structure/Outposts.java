package com.jaymospowers.aether.structure;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The outpost: a walled yard of faded holystone with four corner towers, a hall with a cloudwool roof and the 2x2 outpost campfire
 * (right-click it and you wake there after dying with no bed). Two variants: low towers, and tall ones with a lookout on top.
 */
final class Outposts {
	// ---- tuning ----
	/** Tower height (floor to ceiling) of the low and the tall variant. */
	static final int LOW_TOWER = 4, TALL_TOWER = 11;
	/** The hall's wall height. */
	static final int HALL_WALL = 3;

	private Outposts() {
	}

	static void build(Site s) {
		int f = s.floor, y0 = s.box.minY(), top = s.box.maxY();
		boolean tall = s.variant == 1;
		int n = AetherStructure.OUTPOST_SIZE - 1;
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 14);
		s.air(0, f, 0, n, top, n);
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			float r = s.rnd(u, y, v, 1);
			return r < 0.12F ? Kit.b("mossy_holystone") : r < 0.55F ? Kit.b("faded_holystone_flagstones") : Kit.b("faded_holystone_bricks");
		});
		// a fence along the edge, open at the gate (the middle of v 0)
		BlockState fence = Kit.b("skyroot_fence");
		for (int i = 0; i <= n; i++) {
			if (i < 9 || i > 12) {
				s.set(i, f, 0, fence);
			}
			s.set(i, f, n, fence);
			s.set(0, f, i, fence);
			s.set(n, f, i, fence);
		}
		// the gate: two pillars and a lintel
		for (int u : new int[] {8, 13}) {
			s.fill(u, f, 0, u, f + 4, 0, Kit.log("faded_holystone_pillar", net.minecraft.core.Direction.Axis.Y));
		}
		s.fill(9, f + 4, 0, 12, f + 4, 0, Kit.b("faded_holystone_bricks"));
		s.set(8, f + 5, 0, Kit.b("faded_holystone_keystone"));
		s.set(13, f + 5, 0, Kit.b("faded_holystone_keystone"));
		// towers in the corners
		int th = tall ? TALL_TOWER : LOW_TOWER;
		for (int[] c : new int[][] {{0, 0, 2}, {n - 4, 0, 2}, {0, n - 4, 0}, {n - 4, n - 4, 0}}) {
			tower(s, c[0], c[1], th, tall, c[2]);
		}
		hall(s);
		// the campfire in the yard, flagstones round it
		s.fill(8, y0, 2, 13, y0, 6, Kit.b("faded_holystone_flagstones"));
		s.outpostCampfire(10, f, 3);
		s.set(8, f, 2, Kit.b("ambrosium_torch"));
		s.set(13, f, 6, Kit.b("ambrosium_torch"));
	}

	/** A 5x5 tower at (u0, v0); its door is on local side {@code door} (0 along, 1 right, 2 back, 3 left) of the tower. */
	private static void tower(Site s, int u0, int v0, int th, boolean lookout, int door) {
		int f = s.floor, u1 = u0 + 4, v1 = v0 + 4;
		s.room(u0, f, v0, u1, f + th - 1, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			return corner ? Kit.log("faded_holystone_pillar", net.minecraft.core.Direction.Axis.Y) : s.rnd(u, y, v, 2) < 0.2F ? Kit.b("faded_holystone_flagstones")
				: Kit.b("faded_holystone_bricks");
		}, false);
		// the door, two high, in the middle of its side
		int mu = u0 + 2, mv = v0 + 2;
		int du = door == 1 ? u1 : door == 3 ? u0 : mu, dv = door == 0 ? v1 : door == 2 ? v0 : mv;
		s.air(du, f, dv, du, f + 1, dv);
		// arrow slits
		for (int y = f + 2; y < f + th - 1; y += 3) {
			s.set(mu, y, v0, Kit.b("scatterglass_pane"));
			s.set(mu, y, v1, Kit.b("scatterglass_pane"));
			s.set(u0, y, mv, Kit.b("scatterglass_pane"));
			s.set(u1, y, mv, Kit.b("scatterglass_pane"));
		}
		s.set(du, f + 2, dv, Kit.b("faded_holystone_keystone"));
		if (lookout) {
			// a ladder up the inner face of the back wall, a planked ceiling with a hole for it, a lookout with a railing and a roof
			int lu = u0 + 2, lv = v0 + 1;
			s.ladder(lu, f, f + th, lv, 0);
			s.fill(u0 + 1, f + th, v0 + 1, u1 - 1, f + th, v1 - 1, (u, y, v) -> u == lu && v == lv ? Kit.air() : Kit.b("skyroot_planks"));
			s.fill(u0, f + th, v0, u1, f + th, v1, (u, y, v) -> u == u0 || u == u1 || v == v0 || v == v1 ? Kit.b("faded_holystone_bricks") : null);
			BlockState fence = Kit.b("skyroot_fence");
			s.fill(u0, f + th + 1, v0, u1, f + th + 1, v1, (u, y, v) -> u == u0 || u == u1 || v == v0 || v == v1 ? fence : null);
			for (int[] p : new int[][] {{u0, v0}, {u1, v0}, {u0, v1}, {u1, v1}}) {
				s.fill(p[0], f + th + 1, p[1], p[0], f + th + 3, p[1], Kit.log("skyroot_log", net.minecraft.core.Direction.Axis.Y));
			}
			s.pyramid(u0, v0, u1, v1, f + th + 4, Kit.b("cloudwool"));
			s.set(u0 + 2, f + th + 1, v0 + 2, Kit.b("ambrosium_torch"));
		} else {
			s.pyramid(u0, v0, u1, v1, f + th, Kit.b("cloudwool"));
		}
	}

	/** The hall: u 6 to 15, v 7 to 15, a planked floor, windows and a cloudwool roof. */
	private static void hall(Site s) {
		int f = s.floor, y0 = s.box.minY();
		s.fill(7, y0, 8, 14, y0, 14, Kit.b("skyroot_planks"));
		s.room(6, f, 7, 15, f + HALL_WALL - 1, 15, (u, y, v) -> {
			boolean corner = (u == 6 || u == 15) && (v == 7 || v == 15);
			return corner ? Kit.log("faded_holystone_pillar", net.minecraft.core.Direction.Axis.Y) : s.rnd(u, y, v, 3) < 0.25F ? Kit.b("faded_holystone_flagstones")
				: Kit.b("faded_holystone_bricks");
		}, false);
		s.air(10, f, 7, 11, f + 1, 7);
		s.set(10, f + 2, 7, Kit.b("faded_holystone_keystone"));
		s.set(11, f + 2, 7, Kit.b("faded_holystone_keystone"));
		for (int v : new int[] {10, 12}) {
			s.set(6, f + 1, v, Kit.b("scatterglass_pane"));
			s.set(15, f + 1, v, Kit.b("scatterglass_pane"));
		}
		s.pyramid(6, 7, 15, 15, f + HALL_WALL, Kit.b("cloudwool"));
		s.set(8, f, 14, Kit.b("ambrosium_torch"));
		s.set(13, f, 14, Kit.b("ambrosium_torch"));
		s.candle(9, f + 2, 8, 0);
		s.candle(12, f + 2, 8, 0);
	}
}
