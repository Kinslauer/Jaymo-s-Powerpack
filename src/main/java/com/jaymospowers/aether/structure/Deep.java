package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The buried works: Brexallen ruins (a vaulted courtyard with rooms round it, very common) and the undercloud mineshaft (a hub,
 * timbered corridors and storage rooms, cut into the rock).
 */
final class Deep {
	// ---- tuning ----
	/** Chance each of Brexallen's four side rooms is there, by variant (at least one always is). */
	static final float[] ROOM_CHANCE = {1.0F, 0.7F, 0.5F};
	/** Chance a side room holds a chest, and that a wall block is the accent stone. */
	static final float ROOM_CHEST = 0.5F, ACCENT = 0.1F;
	/** The mineshaft: spacing of timber frames along a corridor, and the chance of a vase between frames. */
	static final int FRAME_EVERY = 4;
	static final float TUNNEL_VASE = 0.2F;
	/** Where each side room's doorway starts (the cell nearest the north-west), by side. */
	private static final int[][] DOORS = {{10, 6}, {13, 10}, {10, 13}, {6, 10}};

	private Deep() {
	}

	// ---- Brexallen ruins ----

	private static BlockState wall(Site s, int u, int y, int v) {
		float r = s.rnd(u, y, v, 31);
		return r < ACCENT ? Kit.b("agiosite_bricks") : r < 0.3F ? Kit.b("ichorite") : Kit.b("ichorite_bricks");
	}

	private static BlockState floorBlock(Site s, int u, int y, int v) {
		float r = s.rnd(u, y, v, 32);
		return r < 0.15F ? Kit.b("shayelinn_moss_block") : r < 0.5F ? Kit.b("undershale") : Kit.b("undershale_bricks");
	}

	static void brexallen(Site s) {
		int f = s.floor, y0 = s.box.minY();
		float chance = ROOM_CHANCE[s.variant % ROOM_CHANCE.length];
		boolean[] rooms = new boolean[4];
		int count = 0;
		for (int k = 0; k < 4; k++) {
			rooms[k] = s.rnd(k, 0, 0, 33) < chance;
			count += rooms[k] ? 1 : 0;
		}
		if (count == 0) {
			rooms[Math.floorMod((int)(s.seed >>> 5), 4)] = true;
		}
		// the courtyard: shell u 7 to 13, v 7 to 13; the rooms: north v 0-6, east u 14-20, south v 14-20, west u 0-6
		shell(s, 7, 7, 13, 13, 4);
		s.set(10, f + 4, 10, Kit.lantern());
		s.chest(10, f, 10, 0, "chests/brexallen_ruins");
		for (int[] c : new int[][] {{8, 8}, {12, 8}, {8, 12}, {12, 12}}) {
			s.vase(c[0], f, c[1], "brexallen", "vases/brexallen_ruins");
		}
		int[][] boxes = {{7, 0, 13, 6}, {14, 7, 20, 13}, {7, 14, 13, 20}, {0, 7, 6, 13}};
		for (int k = 0; k < 4; k++) {
			if (!rooms[k]) {
				continue;
			}
			int[] b = boxes[k];
			shell(s, b[0], b[1], b[2], b[3], 3);
			// the door through both walls, two wide, three high
			boolean alongV = k == 0 || k == 2;
			int[] door = DOORS[k];
			for (int i = 0; i < 2; i++) {
				for (int j = 0; j < 2; j++) {
					int u = alongV ? door[0] + i : door[0] + j, v = alongV ? door[1] + j : door[1] + i;
					s.air(u, f, v, u, f + 2, v);
				}
			}
			int cu = (b[0] + b[2]) / 2, cv = (b[1] + b[3]) / 2;
			s.set(cu, f + 3, cv, Kit.lantern());
			s.vase(b[0] + 1, f, b[1] + 1, "brexallen", "vases/brexallen_ruins");
			s.vase(b[2] - 1, f, b[3] - 1, "brexallen", "vases/brexallen_ruins");
			if (s.rnd(k, 1, 0, 34) < ROOM_CHEST) {
				s.chest(b[2] - 1, f, b[1] + 1, 0, "chests/brexallen_ruins");
			}
			if (s.rnd(k, 2, 0, 35) < 0.5F) {
				s.set(cu - 1, f, cv + 1, Kit.b("bryalinn_moss_carpet"));
				s.set(cu + 1, f, cv - 1, Kit.b("shayelinn_moss_carpet"));
			}
		}
	}

	/** A walled room (u0..u1, v0..v1), {@code h} high inside, with floor and ceiling, pillars at its inner corners. */
	private static void shell(Site s, int u0, int v0, int u1, int v1, int h) {
		int f = s.floor, y0 = s.box.minY();
		s.fill(u0, y0, v0, u1, f + h, v1, (u, y, v) -> {
			boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
			if (y == y0) {
				return floorBlock(s, u, y, v);
			}
			return edge || y == f + h ? wall(s, u, y, v) : Kit.air();
		});
	}

	// ---- the undercloud mineshaft ----

	private static BlockState post() {
		return Kit.log("skyroot_log", Direction.Axis.Y);
	}

	static void mineHub(Site s) {
		int f = s.floor, y0 = s.box.minY();
		s.air(0, f, 0, 8, f + 4, 8);
		s.fill(2, y0, 2, 6, y0, 6, (u, y, v) -> s.rnd(u, y, v, 36) < 0.5F ? Kit.b("undershale_bricks") : Kit.b("agiosite_bricks"));
		for (int[] c : new int[][] {{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
			s.fill(c[0], f, c[1], c[0], f + 3, c[1], post());
		}
		// a beam round the ceiling and across
		s.fill(1, f + 4, 1, 7, f + 4, 7, (u, y, v) -> u == 1 || u == 7 || v == 1 || v == 7 || u == 4 || v == 4 ? Kit.b("skyroot_planks") : null);
		s.set(4, f + 3, 4, Kit.lantern());
		s.vase(6, f, 2, "holystone", "vases/undercloud_mineshaft");
		s.set(2, f, 6, Kit.b("ambrosium_torch"));
	}

	static void mineTunnel(Site s) {
		int f = s.floor, y0 = s.box.minY(), len = s.length;
		s.air(0, f, 0, 4, f + 2, len - 1);
		for (int v = 1; v < len - 1; v += FRAME_EVERY) {
			s.fill(0, f, v, 0, f + 2, v, post());
			s.fill(4, f, v, 4, f + 2, v, post());
			s.fill(0, f + 3, v, 4, f + 3, v, Kit.b("skyroot_planks"));
			if (v % (FRAME_EVERY * 2) == 1) {
				s.set(1, f, v, Kit.b("ambrosium_torch"));
			} else if (s.rnd(v, 0, 0, 37) < TUNNEL_VASE) {
				s.vase(s.rnd(v, 1, 0, 38) < 0.5F ? 1 : 3, f, v + 2, s.rnd(v, 2, 0, 39) < 0.5F ? "holystone" : "brexallen", "vases/undercloud_mineshaft");
			}
		}
	}

	static void mineRoom(Site s) {
		int f = s.floor;
		s.air(2, f, 0, 6, f + 3, 0);
		s.air(1, f, 1, 7, f + 3, 7);
		for (int[] c : new int[][] {{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
			s.fill(c[0], f, c[1], c[0], f + 3, c[1], post());
		}
		s.fill(1, f + 4, 1, 7, f + 4, 7, (u, y, v) -> u == 1 || u == 7 || v == 1 || v == 7 ? Kit.b("skyroot_planks") : null);
		s.chest(2, f, 7, 0, "chests/undercloud_mineshaft");
		s.chest(6, f, 7, 0, "chests/undercloud_mineshaft");
		s.vase(7, f, 4, "holystone", "vases/undercloud_mineshaft");
		s.vase(1, f, 4, "brexallen", "vases/undercloud_mineshaft");
		s.set(4, f + 3, 4, Kit.lantern());
		s.set(4, f, 7, Kit.b("ambrosium_torch"));
	}
}
