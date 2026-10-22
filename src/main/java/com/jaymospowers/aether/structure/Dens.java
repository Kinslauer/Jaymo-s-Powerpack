package com.jaymospowers.aether.structure;

import net.minecraft.world.level.block.state.BlockState;

/**
 * An animal den: a dome of woven skyroot sticks on a mound of aether earth, a low entrance, an animal stash inside and twigs and
 * leaf piles round it. Three shapes: small, large and long.
 */
final class Dens {
	// ---- tuning ----
	/** Radii (across, along, up) of the three dens' outer shells; the wall is one block thick. */
	static final double[][] SHAPES = {{3.4, 3.4, 3.4}, {4.3, 4.3, 4.2}, {3.4, 4.3, 3.4}};
	/** Chance a spot on the mound carries a twig or a leaf pile. */
	static final float LITTER = 0.18F;

	private Dens() {
	}

	static void build(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.DEN_SIZE - 1, top = s.box.maxY();
		double[] r = SHAPES[s.variant % SHAPES.length];
		double cu = n / 2.0, cv = n / 2.0;
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("aether_dirt"), 10);
		s.air(0, f, 0, n, top, n);
		BlockState sticks = Kit.b("woven_skyroot_sticks");
		// the mound: grass with dirt patches, as wide as the dome and a little more
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			double d = Math.hypot((u - cu) / (r[0] + 1.2), (v - cv) / (r[1] + 1.2));
			if (d > 1.0) {
				return null;
			}
			return s.rnd(u, y, v, 1) < 0.25F ? Kit.b("coarse_aether_dirt") : Kit.b("aether_grass_block");
		});
		// the dome: the cells between the inner and the outer ellipsoid
		s.fill(0, f, 0, n, f + (int)r[2], n, (u, y, v) -> {
			double du = (u - cu) / r[0], dv = (v - cv) / r[1], dy = (y - f + 0.5) / r[2];
			double outer = du * du + dv * dv + dy * dy;
			double iu = (u - cu) / (r[0] - 1.0), iv = (v - cv) / (r[1] - 1.0), iy = (y - f + 0.5) / (r[2] - 1.0);
			double inner = iu * iu + iv * iv + iy * iy;
			if (outer <= 1.0 && inner > 1.0) {
				// the entrance: two wide, two high, on the low-v side
				boolean door = v < cv && Math.abs(u - cu) < 1.0 && y - f < 2;
				return door ? Kit.air() : sticks;
			}
			return null;
		});
		// the floor inside is packed dirt, the stash in the middle facing the door
		s.fill(1, y0, 1, n - 1, y0, n - 1, (u, y, v) -> Math.hypot((u - cu) / (r[0] - 1.0), (v - cv) / (r[1] - 1.0)) < 1.0 ? Kit.b("coarse_aether_dirt") : null);
		s.stash(4, f, 4, 2, "chests/animal_den");
		s.set(3, f, 5, Kit.b("skyroot_leaf_pile"));
		s.set(5, f, 3, Kit.b("skyroot_leaf_pile"));
		// twigs and leaf piles round the mound
		for (int u = 0; u <= n; u++) {
			for (int v = 0; v <= n; v++) {
				double d = Math.hypot((u - cu) / (r[0] + 1.2), (v - cv) / (r[1] + 1.2));
				if (d > 0.78 && d <= 1.0 && s.rnd(u, f, v, 4) < LITTER * 2) {
					s.set(u, f, v, s.rnd(u, f, v, 5) < 0.6F ? Kit.b("holystone_twig") : Kit.b("skyroot_leaf_pile"));
				}
			}
		}
	}
}
