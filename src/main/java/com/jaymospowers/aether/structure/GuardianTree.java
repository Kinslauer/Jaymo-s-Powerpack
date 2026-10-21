package com.jaymospowers.aether.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The infected guardian tree: a vast hollow trunk of guardian wood, a spiral stair up its inner wall, a floor every 12 blocks with
 * a chest and vases, and at the top a vault (the Aether II tree has no boss; ours has none either, just the treasure) under a
 * crown of leaves. Roots splay out round the foot.
 */
final class GuardianTree {
	// ---- tuning ----
	/** The trunk: outer radius at the foot and at the top, inner radius, and the height of the hollow (floor to roof plate). */
	static final double R_FOOT = 14.0, R_TOP = 12.5, R_IN = 10.5;
	static final int TRUNK_H = 62;
	/** Floors every this many blocks; plates reach this far from the middle (the stair runs between it and the wall). */
	static final int FLOOR_EVERY = 12;
	static final double PLATE_R = 8.5;
	/** Steps in one turn of the stair (so it climbs this many blocks per turn). */
	static final int TURN = 44;
	/** The crown: how far above the hollow its middle is and its radii (across, up). */
	static final int CROWN_UP = 4;
	static final double CROWN_R = 16.0, CROWN_H = 10.0;
	/** Shares: infected wood in the trunk, roots in the bark, lamps in the inner wall, infected leaves in the crown. */
	static final float INFECTED = 0.12F, BARK_ROOTS = 0.03F, LAMP = 0.015F, SICK_LEAVES = 0.12F;

	private static final double C = AetherStructure.GUARDIAN_SIZE / 2;

	private GuardianTree() {
	}

	private static double radius(int u, int v) {
		return Math.hypot(u - C, v - C);
	}

	static void build(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.GUARDIAN_SIZE - 1;
		BlockState planks = Kit.b("guardian_planks");
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("aether_dirt"), 16);
		s.air(0, f, 0, n, s.box.maxY(), n);
		// the foot: a plank floor inside, bark ground outside, roots
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			double d = radius(u, v);
			if (d <= R_IN) {
				return planks;
			}
			return d <= R_FOOT + 1 ? Kit.b("coarse_aether_dirt") : null;
		});
		roots(s);
		// the trunk's wall: ragged outside, tapering; the hollow inside
		s.fill(0, f, 0, n, f + TRUNK_H + 6, n, (u, y, v) -> {
			double d = radius(u, v);
			double taper = R_FOOT - (R_FOOT - R_TOP) * Math.min(1.0, (y - f) / (double)TRUNK_H);
			double ragged = (s.rnd(u, 0, v, 51) - 0.5) * 1.6;
			if (d > taper + ragged || d > R_FOOT + 1) {
				return null;
			}
			if (d <= R_IN) {
				return Kit.air();
			}
			float r = s.rnd(u, y, v, 52);
			if (d <= R_IN + 1.0 && r < LAMP) {
				return Kit.b("guardian_lamp");
			}
			if (r < BARK_ROOTS) {
				return Kit.b(r < BARK_ROOTS / 3 ? "lucent_guardian_roots" : "guardian_roots");
			}
			return Kit.log(s.rnd(u, y, v, 53) < INFECTED ? "infected_guardian_log" : "guardian_log", Direction.Axis.Y);
		});
		// the door, on the +u side at the foot: three wide, four high, and a path
		s.fill(25, f, (int)C - 1, 31, f + 3, (int)C + 1, Kit.air());
		s.fill(27, y0, (int)C - 1, 32, y0, (int)C + 1, Kit.b("aether_dirt_path"));
		stair(s);
		floors(s);
		vault(s);
		crown(s);
	}

	/** Eight roots flare from the trunk's foot, sinking as they go out. */
	private static void roots(Site s) {
		int f = s.floor;
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4 + (s.rnd(k, 0, 0, 54) - 0.5) * 0.4;
			for (double r = R_FOOT - 1; r <= C; r += 0.7) {
				int u = (int)Math.round(C + Math.cos(a) * r), v = (int)Math.round(C + Math.sin(a) * r);
				int h = Math.max(0, 3 - (int)((r - R_FOOT + 1) * 0.8));
				s.fill(u, f, v, u, f + h, v, Kit.b("guardian_roots"));
			}
		}
	}

	/** The spiral: each cell between the plates and the wall is a stair at the height of its angle's step. */
	private static void stair(Site s) {
		int f = s.floor, n = AetherStructure.GUARDIAN_SIZE - 1;
		for (int u = 0; u <= n; u++) {
			for (int v = 0; v <= n; v++) {
				double d = radius(u, v);
				if (d < PLATE_R || d >= R_IN) {
					continue;
				}
				double a = Math.atan2(v - C, u - C);
				if (a < 0) {
					a += Math.PI * 2;
				}
				int idx = (int)(a / (Math.PI * 2) * TURN) % TURN;
				// which way is up the stair: round the circle, counter-clockwise (a grows)
				double tu = -Math.sin(a), tv = Math.cos(a);
				int local = Math.abs(tv) >= Math.abs(tu) ? (tv > 0 ? 0 : 2) : (tu > 0 ? 1 : 3);
				for (int turn = 0; turn * TURN + idx <= TRUNK_H - 1; turn++) {
					int y = f + turn * TURN + idx;
					s.air(u, y + 1, v, u, y + 3, v);
					s.set(u, y, v, Kit.stairs("guardian_stairs", s.dir(local), false));
				}
			}
		}
	}

	/** A plate every 12 blocks, each with a chest and vases and (the first) the door light; the last is the roof of the hollow. */
	private static void floors(Site s) {
		int f = s.floor;
		for (int k = 1; f + k * FLOOR_EVERY < f + TRUNK_H; k++) {
			int y = f + k * FLOOR_EVERY - 1;
			s.fill(0, y, 0, AetherStructure.GUARDIAN_SIZE - 1, y, AetherStructure.GUARDIAN_SIZE - 1,
				(u, yy, v) -> radius(u, v) < PLATE_R ? (radius(u, v) < 1.5 ? null : Kit.b(s.rnd(u, y, v, 55) < 0.2F ? "stripped_guardian_log" : "guardian_planks")) : null);
			int cu = (int)C, cv = (int)C;
			int chestU = cu + 5, chestV = cv + (k % 2 == 0 ? 3 : -3);
			s.chest(chestU, y + 1, chestV, 3, "chests/infected_guardian_tree");
			s.vase(cu - 5, y + 1, cv + 2, s.rnd(k, 0, 0, 56) < 0.5F ? "holystone" : "veradexian", "vases/veradexian_ruins");
			s.vase(cu - 3, y + 1, cv - 5, "veradexian", "vases/veradexian_ruins");
			s.set(cu, y + 1, cv + 3, Kit.b("guardian_lamp"));
			s.set(cu + 1, y + 4, cv, Kit.b("lucent_guardian_roots"));
		}
	}

	/** The vault at the top: a ring of planks round a small room of lucent roots, with the treasure. */
	private static void vault(Site s) {
		int f = s.floor, base = f + TRUNK_H;
		int cu = (int)C, cv = (int)C;
		// the plate over the whole hollow, roots hanging under it
		s.fill(0, base - 1, 0, AetherStructure.GUARDIAN_SIZE - 1, base - 1, AetherStructure.GUARDIAN_SIZE - 1,
			(u, y, v) -> radius(u, v) < R_IN ? Kit.b("guardian_planks") : null);
		s.fill(0, base, 0, AetherStructure.GUARDIAN_SIZE - 1, base + 5, AetherStructure.GUARDIAN_SIZE - 1, (u, y, v) -> {
			double d = radius(u, v);
			if (d > 7.0) {
				return null;
			}
			boolean ring = d > 5.5;
			boolean door = ring && v > cv && Math.abs(u - cu) <= 1 && y - base < 3;
			if (y == base + 5) {
				return Kit.b("stripped_guardian_log");
			}
			if (ring) {
				return door ? Kit.air() : y - base == 2 && s.rnd(u, y, v, 57) < 0.3F ? Kit.b("guardian_lamp") : Kit.b(s.rnd(u, y, v, 58) < 0.15F ? "lucent_guardian_roots" : "guardian_planks");
			}
			return Kit.air();
		});
		for (int[] c : new int[][] {{cu - 3, cv}, {cu + 3, cv}, {cu, cv - 3}}) {
			s.chest(c[0], base, c[1], c[0] < cu ? 1 : c[0] > cu ? 3 : 0, "chests/infected_guardian_tree_vault");
		}
		s.vase(cu - 2, base, cv + 3, "veradexian", "vases/veradexian_ruins");
		s.vase(cu + 2, base, cv + 3, "holystone", "vases/veradexian_ruins");
		s.fill(0, base + 6, 0, AetherStructure.GUARDIAN_SIZE - 1, base + 6, AetherStructure.GUARDIAN_SIZE - 1, (u, y, v) -> radius(u, v) < R_IN ? Kit.b("guardian_planks") : null);
		s.set(cu, base, cv, Kit.b("guardian_lamp"));
		s.set(cu, base + 4, cv, Kit.lantern());
		for (int[] c : new int[][] {{cu - 4, cv - 4}, {cu + 4, cv - 4}, {cu - 4, cv + 3}, {cu + 4, cv + 3}}) {
			s.set(c[0], base + 1, c[1], Kit.b("lucent_guardian_roots"));
		}
	}

	/** The crown: a flattened ball of leaves over the top, with some sick ones. */
	private static void crown(Site s) {
		int f = s.floor, cy = f + TRUNK_H + CROWN_UP;
		int n = AetherStructure.GUARDIAN_SIZE - 1;
		int vaultTop = f + TRUNK_H + 5;
		s.fill(0, cy - (int)CROWN_H, 0, n, cy + (int)CROWN_H, n, (u, y, v) -> {
			double du = (u - C) / CROWN_R, dv = (v - C) / CROWN_R, dy = (y - cy) / CROWN_H;
			double d = du * du + dv * dv + dy * dy + (s.rnd(u, y, v, 59) - 0.5) * 0.25;
			if (d > 1.0 || y < f + TRUNK_H && radius(u, v) < R_FOOT) {
				return null;
			}
			// the vault's room and the hollow under its roof stay open
			if (y <= vaultTop && radius(u, v) < R_IN) {
				return null;
			}
			// only where nothing else stands (the trunk and the vault are built, the leaves go in the rest)
			if (!s.get(u, y, v).isAir()) {
				return null;
			}
			BlockState leaf = Kit.b(s.rnd(u, y, v, 60) < SICK_LEAVES ? "irradiated_leaves" : "greatoak_leaves");
			return Kit.with(leaf, BlockStateProperties.PERSISTENT, true);
		});
	}
}
