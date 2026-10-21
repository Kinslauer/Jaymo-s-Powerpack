package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A watchtower: a skyroot frame 9 by 10 at three heights (16, 20 or 24 up to the platform), a ladder up the middle, landings every 8
 * blocks, a closed ground room, and a railed platform under a cloudwool roof with a chest of crossbow gear.
 */
final class Towers {
	// ---- tuning ----
	/** Vertical gap between cross beams and between landings. */
	static final int BEAM_EVERY = 6, LANDING_EVERY = 8;
	/** The ground room's wall height. */
	static final int ROOM_H = 4;

	private Towers() {
	}

	static void build(Site s) {
		int f = s.floor, y0 = s.box.minY();
		int th = AetherStructure.TOWER_HEIGHTS[s.variant % AetherStructure.TOWER_HEIGHTS.length];
		int w = AetherStructure.TOWER_W - 1, l = AetherStructure.TOWER_L - 1, top = s.box.maxY();
		s.foundation(0, 0, w, l, y0 - 1, Kit.b("holystone"), 12);
		s.air(0, f, 0, w, top, l);
		s.fill(0, y0, 0, w, y0, l, (u, y, v) -> s.rnd(u, y, v, 1) < 0.3F ? Kit.b("faded_holystone_flagstones") : Kit.b("faded_holystone_bricks"));
		BlockState post = Kit.log("skyroot_log", Direction.Axis.Y);
		BlockState fence = Kit.b("skyroot_fence");
		// corner posts, and rings of beams
		for (int[] c : new int[][] {{1, 1}, {w - 1, 1}, {1, l - 1}, {w - 1, l - 1}}) {
			s.fill(c[0], f, c[1], c[0], f + th + 3, c[1], post);
		}
		for (int y = f + BEAM_EVERY - 1; y <= f + th; y += BEAM_EVERY) {
			ring(s, 1, 1, w - 1, l - 1, y);
			// fence lattice between the beams, in the middle of each side
			for (int yy = y + 1; yy < y + BEAM_EVERY - 1 && yy < f + th; yy++) {
				for (int[] m : new int[][] {{w / 2, 1}, {w / 2, l - 1}, {1, l / 2}, {w - 1, l / 2}}) {
					s.set(m[0], yy, m[1], fence);
				}
			}
		}
		// the ground room: planks, a door in the front (v 1), a window each side
		s.room(1, f, 1, w - 1, f + ROOM_H - 1, l - 1, (u, y, v) -> (u == 1 || u == w - 1) && (v == 1 || v == l - 1) ? post : Kit.b("skyroot_planks"), false);
		s.air(w / 2, f, 1, w / 2, f + 1, 1);
		s.set(1, f + 1, l / 2, Kit.b("scatterglass_pane"));
		s.set(w - 1, f + 1, l / 2, Kit.b("scatterglass_pane"));
		// the spine and its ladder, landings
		int su = w / 2, sv = l / 2;
		s.fill(su, f + ROOM_H, sv, su, f + th + 1, sv, Kit.b("skyroot_planks"));
		s.ladder(su, f, f + th + 1, sv + 1, 0);
		s.fill(su, f, sv, su, f + ROOM_H - 1, sv, Kit.b("skyroot_planks"));
		for (int y = f + LANDING_EVERY; y < f + th - 1; y += LANDING_EVERY) {
			s.fill(2, y, 3, w - 2, y, l - 3, (u, yy, v) -> u == su && v == sv + 1 ? null : Kit.b("skyroot_planks"));
		}
		// the platform: planks over everything, a hole for the ladder, a railing, the roof on four posts
		s.fill(0, f + th, 0, w, f + th, l, (u, y, v) -> u == su && v == sv + 1 ? null : Kit.b("skyroot_planks"));
		s.fill(0, f + th + 1, 0, w, f + th + 1, l, (u, y, v) -> u == 0 || u == w || v == 0 || v == l ? fence : null);
		for (int[] c : new int[][] {{1, 1}, {w - 1, 1}, {1, l - 1}, {w - 1, l - 1}}) {
			s.fill(c[0], f + th + 1, c[1], c[0], f + th + 3, c[1], post);
		}
		s.pyramid(0, 0, w, l, f + th + 4, Kit.b("cloudwool"));
		s.chest(2, f + th + 1, 2, 1, "chests/watchtower");
		s.set(w - 2, f + th + 1, 2, Kit.b("ambrosium_torch"));
		s.set(2, f + th + 1, l - 2, Kit.b("ambrosium_torch"));
		s.set(su, f + th + 3, sv - 1, Kit.lantern());
	}

	/** A ring of horizontal logs round the rectangle at height y. */
	private static void ring(Site s, int u0, int v0, int u1, int v1, int y) {
		s.fill(u0, y, v0, u1, y, v0, Kit.log("skyroot_log", s.right.getAxis()));
		s.fill(u0, y, v1, u1, y, v1, Kit.log("skyroot_log", s.right.getAxis()));
		s.fill(u0, y, v0 + 1, u0, y, v1 - 1, Kit.log("skyroot_log", s.facing.getAxis()));
		s.fill(u1, y, v0 + 1, u1, y, v1 - 1, Kit.log("skyroot_log", s.facing.getAxis()));
	}
}
