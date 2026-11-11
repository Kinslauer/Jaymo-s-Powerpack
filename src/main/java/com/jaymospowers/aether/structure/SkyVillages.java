package com.jaymospowers.aether.structure;

import com.jaymospowers.aether.monster.AetherMonsters;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The villages of the Aether (Andy, on the Sky Villages mod's ideas: "should all instead spawn in the aether proper, and be
 * filled with divine born", in "aether material"). Nothing of that mod is used; these are our own pieces in the Aether's own
 * blocks.
 * <ul>
 * <li>{@link #village}: a round green with a well of light, four homes, a kitchen, a workshop, a windmill, two farm plots
 * and a mooring mast with a balloon skiff tied to it. Three palettes by the land (the highfields' skyroot, the magnetic
 * lands' wisproot, the arctic's greatroot). Variants 3 to 5 are the same village long abandoned: walls and roofs fallen in,
 * vases where the furniture was, swets and cockatrices in the houses, no skiff.</li>
 * <li>{@link #castle}: a sky lord's seat at the scale of a Grim castle: a curtain wall with a walk and four towers, a
 * gatehouse, and a keep of two floors and a roof with a spire.</li>
 * </ul>
 * The people are plain villagers tagged {@link #VILLAGER_TAG} (the lord {@link #LORD_TAG}); MCA makes them its own, and
 * Kingdoms gives everyone born in the Aether their blood (Divine-born, a few Dawnborn).
 */
final class SkyVillages {
	// ---- tuning ----
	/** On every villager a village or castle starts with, and on the castle's lord. */
	static final String VILLAGER_TAG = "jaymospowers_sky_villager", LORD_TAG = "jaymospowers_sky_lord";
	/** A home: 7 by 7, walls this high. */
	static final int HOUSE = 7, WALL = 4;
	/** A ruin: the share of any block gone at the floor, and how much more per block up. */
	static final float RUIN_BASE = 0.12F, RUIN_PER_Y = 0.09F;
	/** A ruin: the share of what's left of the stone that has gone mossy. */
	static final float RUIN_MOSS = 0.35F;
	/** The castle: wall height, tower height, the keep's two storeys. */
	static final int CURTAIN = 7, TOWER = 13, STOREY = 6;

	private SkyVillages() {
	}

	/** A palette: the wood, the stone under it, two roof cloths, the green and the path. */
	private record Palette(String wood, String stone, String roofA, String roofB, String ground, String path) {
		String planks() {
			return this.wood + "_planks";
		}
	}

	private static Palette palette(int variant) {
		return switch (variant) {
			case 1 -> new Palette("wisproot", "holystone_bricks", "purple_cloudwool", "cyan_cloudwool", "aether_grass_block", "coarse_aether_dirt");
			case 2 -> new Palette("greatroot", "icestone_bricks", "cloudwool", "light_blue_cloudwool", "arctic_snow_block", "holystone_flagstones");
			default -> new Palette("skyroot", "holystone_bricks", "cloudwool", "yellow_cloudwool", "aether_grass_block", "aether_dirt_path");
		};
	}

	/** What a ruin leaves of {@code state} at a spot: nothing (more often the higher up), moss on stone, or the block. */
	private static BlockState worn(Site s, boolean ruined, int u, int y, int v, BlockState state) {
		if (!ruined || state == null || state.isAir()) {
			return state;
		}
		if (s.rnd(u, y, v, 41) < RUIN_BASE + RUIN_PER_Y * Math.max(0, y - s.floor)) {
			return Kit.air();
		}
		return state.is(Kit.ours("holystone_bricks")) && s.rnd(u, y, v, 42) < RUIN_MOSS ? Kit.b("mossy_holystone") : state;
	}

	// ---------------------------------------------------------------- the village

	static void village(Site s) {
		boolean ruined = s.variant >= 3;
		Palette p = palette(s.variant % 3);
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.VILLAGE_SIZE - 1, c = n / 2, top = s.box.maxY();
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 10);
		s.air(0, f, 0, n, top, n);
		// the green: round, with a cross of paths to the four sides and a flagged plaza in the middle
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			double d = Math.hypot(u - c, v - c);
			if (d > c + 0.5) {
				return null;
			}
			if (d <= 6.5) {
				return s.rnd(u, y, v, 1) < 0.2F ? Kit.b("mossy_holystone") : Kit.b("holystone_flagstones");
			}
			boolean path = Math.abs(u - c) <= 1 || Math.abs(v - c) <= 1;
			return path && s.rnd(u, y, v, 2) < (ruined ? 0.5F : 0.9F) ? Kit.b(p.path) : Kit.b(p.ground);
		});
		well(s, p, c - 1, c - 1, ruined);
		// homes in the four quarters, their doors on the path between them (local side: 1 is +u, 3 is -u, 0 is +v)
		house(s, p, 12, 6, 1, p.roofA, 0, ruined);
		house(s, p, 28, 6, 3, p.roofB, 0, ruined);
		house(s, p, 12, 34, 1, p.roofB, 0, ruined);
		house(s, p, 28, 34, 3, p.roofA, 0, ruined);
		house(s, p, 4, 14, 0, p.roofA, 1, ruined);
		house(s, p, 36, 14, 0, p.roofB, 2, ruined);
		windmill(s, p, 5, 27, ruined);
		farm(s, p, 36, 27, 0, ruined);
		farm(s, p, 38, 34, 1, ruined);
		mooring(s, p, 20, 41, ruined);
		for (int[] q : new int[][] {{c - 5, c - 5}, {c + 5, c - 5}, {c - 5, c + 5}, {c + 5, c + 5}}) {
			if (!ruined || s.rnd(q[0], f, q[1], 3) < 0.4F) {
				s.set(q[0], f, q[1], Kit.b(p.wood + "_fence"));
				s.set(q[0], f + 1, q[1], Kit.b("ambrosium_torch"));
			}
		}
		if (ruined) {
			s.spawn(AetherMonsters.BLUE_SWET, s.x(c, c - 4) + 0.5, f, s.z(c, c - 4) + 0.5, 0.0F, mob -> {
			});
			s.spawn(AetherMonsters.GOLDEN_SWET, s.x(15, 37) + 0.5, f, s.z(15, 37) + 0.5, 0.0F, mob -> {
			});
			s.spawn(AetherMonsters.BLUE_SWET, s.x(31, 9) + 0.5, f, s.z(31, 9) + 0.5, 0.0F, mob -> {
			});
			s.spawn(AetherMonsters.COCKATRICE, s.x(15, 9) + 0.5, f, s.z(15, 9) + 0.5, 0.0F, mob -> {
			});
			s.spawn(AetherMonsters.COCKATRICE, s.x(31, 37) + 0.5, f, s.z(31, 37) + 0.5, 0.0F, mob -> {
			});
		}
	}

	/** The well of light: a ring of stone round water, four posts, a small roof, an ambrosium torch under it. */
	private static void well(Site s, Palette p, int u0, int v0, boolean ruined) {
		int f = s.floor, y0 = s.box.minY();
		s.fill(u0, y0, v0, u0 + 2, y0, v0 + 2, Kit.b(p.stone));
		s.set(u0 + 1, y0, v0 + 1, Kit.water());
		for (int u = u0; u <= u0 + 2; u++) {
			for (int v = v0; v <= v0 + 2; v++) {
				if (u != u0 + 1 || v != v0 + 1) {
					s.set(u, f, v, Kit.slab("holystone_brick_slab", false));
				}
			}
		}
		if (ruined) {
			return;
		}
		for (int[] q : new int[][] {{u0, v0}, {u0 + 2, v0}, {u0, v0 + 2}, {u0 + 2, v0 + 2}}) {
			s.fill(q[0], f + 1, q[1], q[0], f + 3, q[1], Kit.b(p.wood + "_fence"));
		}
		s.fill(u0, f + 4, v0, u0 + 2, f + 4, v0 + 2, Kit.b(p.roofB));
		s.set(u0 + 1, f + 5, v0 + 1, Kit.b(p.roofB));
		s.set(u0 + 1, f + 3, v0 + 1, Kit.lantern());
	}

	/**
	 * A 7 by 7 building at (u0, v0), its door in the middle of local side {@code door}. {@code kind}: 0 a home (two of the
	 * village's people live in it), 1 the kitchen, 2 the workshop (those two with the door on side 0).
	 */
	private static void house(Site s, Palette p, int u0, int v0, int door, String roof, int kind, boolean ruined) {
		int f = s.floor, y0 = s.box.minY(), u1 = u0 + HOUSE - 1, v1 = v0 + HOUSE - 1, mu = u0 + 3, mv = v0 + 3;
		BlockState log = Kit.log(p.wood + "_log", Direction.Axis.Y), planks = Kit.b(p.planks()), stone = Kit.b(p.stone);
		s.fill(u0, y0, v0, u1, y0, v1, planks);
		s.room(u0, f, v0, u1, f + WALL - 1, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			boolean window = y == f + 1 && (u == mu || v == mv);
			BlockState state = corner ? log : y == f ? stone : window ? Kit.b("scatterglass_pane") : planks;
			return worn(s, ruined, u, y, v, state);
		}, false);
		// the door, and a lintel over it
		int du = door == 1 ? u1 : door == 3 ? u0 : mu, dv = door == 0 ? v1 : door == 2 ? v0 : mv;
		s.air(du, f, dv, du, f + 1, dv);
		if (!ruined) {
			BlockState leaf = Kit.with(Kit.b(p.wood + "_door"), BlockStateProperties.HORIZONTAL_FACING, s.dir(door));
			s.set(du, f, dv, Kit.with(leaf, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			s.set(du, f + 1, dv, Kit.with(leaf, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}
		// the roof: cloth over the eaves, stepping in to a point
		int ru0 = u0 - 1, rv0 = v0 - 1, ru1 = u1 + 1, rv1 = v1 + 1;
		BlockState cloth = Kit.b(roof);
		for (int i = 0; ru0 + i <= ru1 - i; i++) {
			int a = i;
			s.fill(ru0 + a, f + WALL + a, rv0 + a, ru1 - a, f + WALL + a, rv1 - a, (u, y, v) -> {
				// hollow above the first layer, so a fallen roof lets the sky in
				boolean rim = u == ru0 + a || u == ru1 - a || v == rv0 + a || v == rv1 - a;
				return rim || a == 0 ? worn(s, ruined, u, y + 2, v, cloth) : null;
			});
		}
		if (ruined) {
			// what was left behind
			s.vase(u0 + 1, f, v0 + 1, "holystone", "chests/sky_village/home");
			if (s.rnd(u0, f, v0, 5) < 0.5F) {
				s.vase(u1 - 1, f, v1 - 1, "holystone", "chests/sky_village/home");
			}
			return;
		}
		s.set(mu, f + WALL - 1, mv, Kit.lantern());
		// furniture along the wall facing the door
		int back = (door + 2) & 3;
		int bu = back == 1 ? u1 - 1 : back == 3 ? u0 + 1 : mu, bv = back == 0 ? v1 - 1 : back == 2 ? v0 + 1 : mv;
		boolean alongV = back == 1 || back == 3;
		int[][] row = new int[3][];
		for (int i = -1; i <= 1; i++) {
			row[i + 1] = alongV ? new int[] {bu, bv + i * 2} : new int[] {bu + i * 2, bv};
		}
		switch (kind) {
			case 1 -> {
				// the kitchen: Kitchen's stove, cabinet and pot if it is there (a smoker, a barrel and a cauldron if not), crates of what the plots grow
				s.set(row[0][0], f, row[0][1], Kit.with(Kit.other("jaymoskitchen:stove", Kit.v("smoker")), BlockStateProperties.HORIZONTAL_FACING, s.dir(door)));
				s.set(row[1][0], f, row[1][1], Kit.with(Kit.other("jaymoskitchen:oak_cabinet", Kit.v("barrel")), BlockStateProperties.HORIZONTAL_FACING, s.dir(door)));
				s.set(row[2][0], f, row[2][1], Kit.other("jaymoskitchen:cooking_pot", Kit.v("cauldron")));
				s.set(u0 + 1, f, v1 - 1, Kit.other("jaymoskitchen:cabbage_crate", Kit.v("hay_block")));
				s.set(u1 - 1, f, v1 - 1, Kit.other("jaymoskitchen:carrot_crate", Kit.v("hay_block")));
				s.set(mu, f, mv, Kit.b(p.wood + "_fence"));
				s.set(mu, f + 1, mv, Kit.b(p.wood + "_pressure_plate"));
				villager(s, mu + (alongV ? 0 : 1), f, mv + (alongV ? 1 : 0), false);
			}
			case 2 -> {
				// the workshop: our Tinkers tables
				s.set(row[0][0], f, row[0][1], Kit.b("crafting_station"));
				s.set(row[1][0], f, row[1][1], Kit.b("part_chest"));
				s.set(row[2][0], f, row[2][1], Kit.b("pattern_chest"));
				s.set(u0 + 1, f, v1 - 1, Kit.b("skyroot_planks_layer"));
				s.set(u1 - 1, f, v1 - 1, Kit.b("holystone_layer"));
				villager(s, mu, f, mv, false);
			}
			default -> {
				// a home: two bedrolls, a chest, a table
				s.set(row[0][0], f, row[0][1], Kit.b("red_cloudwool_carpet"));
				s.set(row[2][0], f, row[2][1], Kit.b("blue_cloudwool_carpet"));
				s.chest(row[1][0], f, row[1][1], door, "chests/sky_village/home");
				s.set(mu, f, mv, Kit.v("crafting_table"));
				villager(s, mu + (alongV ? 0 : 1), f, mv + (alongV ? 1 : 0), false);
				villager(s, mu - (alongV ? 0 : 1), f, mv - (alongV ? 1 : 0), false);
			}
		}
	}

	private static void villager(Site s, int u, int y, int v, boolean lord) {
		s.spawn(EntityTypes.VILLAGER, s.x(u, v) + 0.5, y, s.z(u, v) + 0.5, s.facing.toYRot(), mob -> {
			mob.addTag(VILLAGER_TAG);
			if (lord) {
				mob.addTag(LORD_TAG);
			}
		});
	}

	/** A windmill: a 5 by 5 tower of planks on a stone foot, a cloth cap, and four cloth sails on its -u face. */
	private static void windmill(Site s, Palette p, int u0, int v0, boolean ruined) {
		int f = s.floor, y0 = s.box.minY(), u1 = u0 + 4, v1 = v0 + 4, h = 10;
		BlockState log = Kit.log(p.wood + "_log", Direction.Axis.Y), planks = Kit.b(p.planks());
		s.fill(u0, y0, v0, u1, y0, v1, Kit.b(p.stone));
		s.room(u0, f, v0, u1, f + h - 1, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			return worn(s, ruined, u, f + (y - f) / 2, v, corner ? log : y < f + 2 ? Kit.b(p.stone) : planks);
		}, false);
		s.air(u0 + 2, f, v0, u0 + 2, f + 1, v0);
		if (ruined) {
			return;
		}
		s.pyramid(u0 - 1, v0 - 1, u1 + 1, v1 + 1, f + h, Kit.b(p.roofA));
		s.chest(u0 + 2, f, v1 - 1, 2, "chests/camp/farmer");
		s.set(u0 + 1, f, v1 - 1, Kit.v("hay_block"));
		s.set(u0 + 3, f, v1 - 1, Kit.v("hay_block"));
		// the hub and the sails, flat against the face
		int hy = f + 7, hv = v0 + 2, su = u0 - 1;
		s.set(su, hy, hv, Kit.log(p.wood + "_log", s.right.getAxis()));
		BlockState sail = Kit.b("cloudwool"), spar = Kit.b(p.wood + "_fence");
		for (int k = 1; k <= 4; k++) {
			BlockState arm = k == 1 ? spar : sail;
			s.set(su, hy + k, hv, arm);
			s.set(su, hy - k, hv, arm);
			s.set(su, hy, hv + k, arm);
			s.set(su, hy, hv - k, arm);
			if (k >= 2) {
				// each sail is two wide, trailing the same way round
				s.set(su, hy + k, hv + 1, sail);
				s.set(su, hy - k, hv - 1, sail);
				s.set(su, hy - 1, hv + k, sail);
				s.set(su, hy + 1, hv - k, sail);
			}
		}
	}

	/** A 5 by 5 farm plot, fenced, water in the middle; a ruin's has gone to seed. */
	private static void farm(Site s, Palette p, int u0, int v0, int salt, boolean ruined) {
		int f = s.floor, y0 = s.box.minY();
		String crop = new String[] {"orange_tree", "berry_bush", "valkyrie_sprout"}[Math.floorMod((int)(s.seed >>> (5 + salt)), 3)];
		s.fill(u0, y0, v0, u0 + 4, y0, v0 + 4, ruined ? Kit.b("coarse_aether_dirt") : Kit.b("aether_farmland"));
		s.set(u0 + 2, y0, v0 + 2, Kit.water());
		for (int u = u0; u <= u0 + 4; u++) {
			for (int v = v0; v <= v0 + 4; v++) {
				boolean edge = u == u0 || u == u0 + 4 || v == v0 || v == v0 + 4;
				if (edge && !(u == u0 + 2 && v == v0)) {
					s.set(u, f, v, worn(s, ruined, u, f + 3, v, Kit.b(p.wood + "_fence")));
				} else if (!edge && !(u == u0 + 2 && v == v0 + 2) && !ruined) {
					s.set(u, f, v, s.rnd(u, f, v, 6) < 0.7F ? Kit.ripe(crop) : Kit.b(crop));
				}
			}
		}
	}

	/**
	 * The mooring: a mast with a ladder and a landing at its top (u0 + 2 to u0 + 4, v0 + 1 to v0 + 3), and tied up beside it a
	 * skiff hung under a long cloth balloon. A ruin keeps half the mast.
	 */
	private static void mooring(Site s, Palette p, int u0, int v0, boolean ruined) {
		int f = s.floor, deck = f + 7, mu = u0 + 3, mv = v0 + 2;
		BlockState log = Kit.log(p.wood + "_log", Direction.Axis.Y), planks = Kit.b(p.planks()), fence = Kit.b(p.wood + "_fence");
		s.fill(mu, f, mv, mu, ruined ? f + 3 : deck, mv, log);
		if (ruined) {
			return;
		}
		s.ladder(mu, f, deck, mv - 1, 2);
		s.fill(mu - 1, deck, mv - 1, mu + 1, deck, mv + 1, (u, y, v) -> u == mu && v == mv - 1 ? null : u == mu && v == mv ? log : planks);
		s.fill(mu - 1, deck + 1, mv + 1, mu + 1, deck + 1, mv + 1, fence);
		s.set(mu, deck + 1, mv, fence);
		s.set(mu, deck + 2, mv, Kit.b("ambrosium_torch"));
		// the skiff: a hull eight long and three wide, off the landing's -u side, a plank between them
		int hu1 = mu - 3, hu0 = hu1 - 7, hv0 = mv - 1, hv1 = mv + 1;
		s.set(mu - 2, deck, mv, planks);
		s.fill(hu0, deck - 1, hv0, hu1, deck - 1, hv1, (u, y, v) -> (u == hu0 || u == hu1) && v != mv ? null : planks);
		s.fill(hu0, deck, hv0, hu1, deck, hv1, (u, y, v) -> {
			boolean side = v == hv0 || v == hv1 || u == hu0 || u == hu1;
			return side && !(u == hu1 && v == mv) ? Kit.b(p.wood + "_slab") : null;
		});
		s.chest(hu0 + 1, deck, mv, 1, "chests/sky_village/skiff");
		// the balloon over it, and four lines up to it
		double cu = (hu0 + hu1) / 2.0, cy = deck + 6.5;
		BlockState bag = Kit.b(p.roofA), band = Kit.b(p.roofB);
		s.fill(hu0 - 1, deck + 4, hv0 - 1, hu1 + 1, deck + 9, hv1 + 1, (u, y, v) -> {
			double a = (u - cu) / 5.2, b = (y - cy) / 2.7, e = (v - mv) / 2.4;
			return a * a + b * b + e * e <= 1.0 ? (Math.abs(u - cu) < 1.0 ? band : bag) : null;
		});
		for (int[] q : new int[][] {{hu0 + 1, hv0}, {hu0 + 1, hv1}, {hu1 - 1, hv0}, {hu1 - 1, hv1}}) {
			s.fill(q[0], deck + 1, q[1], q[0], deck + 4, q[1], (u, y, v) -> s.get(u, y, v).isAir() ? fence : null);
		}
	}

	// ---------------------------------------------------------------- the castle

	static void castle(Site s) {
		int f = s.floor, y0 = s.box.minY(), n = AetherStructure.CASTLE_SIZE - 1, c = n / 2, top = s.box.maxY();
		BlockState brick = Kit.b("holystone_bricks"), pillar = Kit.log("holystone_pillar", Direction.Axis.Y), flag = Kit.b("holystone_flagstones");
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), 16);
		s.air(0, f, 0, n, top, n);
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			float r = s.rnd(u, y, v, 1);
			return r < 0.1F ? Kit.b("mossy_holystone") : r < 0.3F ? Kit.b("aether_grass_block") : flag;
		});
		// the curtain wall: two thick, a walk on top, merlons on its outer edge
		int a = 3, b = n - 3;
		s.fill(a, f, a, b, f + CURTAIN, b, (u, y, v) -> {
			boolean outer = u == a || u == b || v == a || v == b;
			boolean inner = u == a + 1 || u == b - 1 || v == a + 1 || v == b - 1;
			boolean ring = outer || inner && u > a && u < b && v > a && v < b;
			if (!ring) {
				return null;
			}
			if (y < f + CURTAIN) {
				return s.rnd(u, y, v, 2) < 0.12F ? Kit.b("holystone") : brick;
			}
			return outer && (u + v) % 2 == 0 ? brick : null;
		});
		// the gate: an arch three wide through the front wall (v = a), two keystones over it, a lantern in the passage
		s.air(c - 1, f, a, c + 1, f + 3, a + 1);
		s.fill(c - 2, f, a - 1, c - 2, f + CURTAIN + 2, a + 2, (u, y, v) -> v == a - 1 || v == a + 2 ? pillar : brick);
		s.fill(c + 2, f, a - 1, c + 2, f + CURTAIN + 2, a + 2, (u, y, v) -> v == a - 1 || v == a + 2 ? pillar : brick);
		s.fill(c - 1, f + 4, a, c + 1, f + 4, a + 1, Kit.b("holystone_keystone"));
		s.set(c, f + 3, a, Kit.lantern());
		s.fill(c - 1, y0, 0, c + 1, y0, a + 4, flag);
		for (int[] q : new int[][] {{0, 0}, {n - 6, 0}, {0, n - 6}, {n - 6, n - 6}}) {
			tower(s, q[0], q[1], q[0] == 0 ? 1 : 3, q[1] == 0 ? 0 : 2);
		}
		keep(s, c);
		// the yard: a well, two plots, torches along the way to the keep's door
		Palette p = palette(0);
		well(s, p, 9, 14, false);
		farm(s, p, 36, 9, 0, false);
		farm(s, p, 36, 16, 1, false);
		for (int v = 8; v <= 24; v += 8) {
			for (int u : new int[] {c - 3, c + 3}) {
				s.set(u, f, v, Kit.b("holystone_brick_wall"));
				s.set(u, f + 1, v, Kit.b("ambrosium_torch"));
			}
		}
		villager(s, 12, f, 20, false);
		villager(s, 34, f, 22, false);
		villager(s, c + 2, f, 12, false);
	}

	/**
	 * A corner tower 7 by 7 at (u0, v0): doors into the yard on the ground and onto the wall walk on local sides
	 * {@code doorU} (1 or 3) and {@code doorV} (0 or 2), a ladder between its floors, merlons and a cloth cap.
	 */
	private static void tower(Site s, int u0, int v0, int doorU, int doorV) {
		int f = s.floor, y0 = s.box.minY(), u1 = u0 + 6, v1 = v0 + 6, mu = u0 + 3, mv = v0 + 3, walk = f + CURTAIN;
		BlockState brick = Kit.b("holystone_bricks"), pillar = Kit.log("holystone_pillar", Direction.Axis.Y);
		s.fill(u0, y0, v0, u1, y0, v1, brick);
		s.room(u0, f, v0, u1, f + TOWER - 1, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			return corner ? pillar : (y - f) % 4 == 2 && (u == mu || v == mv) ? Kit.b("scatterglass_pane") : brick;
		}, false);
		// onto the wall walk both ways (its inner lane: the outer one has the merlons), and into the yard on the ground
		// (clear of the wall, which is two thick)
		int du = doorU == 1 ? u1 : u0, dv = doorV == 0 ? v1 : v0;
		int laneV = v0 == 0 ? v0 + 4 : v0 + 2, laneU = u0 == 0 ? u0 + 4 : u0 + 2, yardV = v0 == 0 ? v0 + 5 : v0 + 1;
		s.air(du, walk, laneV, du, walk + 1, laneV);
		s.air(laneU, walk, dv, laneU, walk + 1, dv);
		s.air(du, f, yardV, du, f + 1, yardV);
		// floors at the walk and the top, a ladder up the wall away from the doors
		int lu = doorU == 1 ? u0 + 1 : u1 - 1;
		for (int y : new int[] {walk - 1, f + TOWER - 1}) {
			s.fill(u0 + 1, y, v0 + 1, u1 - 1, y, v1 - 1, (u, yy, v) -> u == lu && v == mv ? null : Kit.b("skyroot_planks"));
		}
		s.ladder(lu, f, f + TOWER - 1, mv, doorU == 1 ? 1 : 3);
		s.fill(u0, f + TOWER, v0, u1, f + TOWER, v1, (u, y, v) -> {
			boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
			return edge && (u + v) % 2 == 0 ? brick : null;
		});
		for (int[] q : new int[][] {{u0, v0}, {u1, v0}, {u0, v1}, {u1, v1}}) {
			s.fill(q[0], f + TOWER, q[1], q[0], f + TOWER + 2, q[1], pillar);
		}
		s.pyramid(u0, v0, u1, v1, f + TOWER + 3, Kit.b("blue_cloudwool"));
		s.set(mu, f + TOWER - 2, mv, Kit.lantern());
	}

	/**
	 * The keep: 19 across and 15 deep at the back of the yard, its door toward the gate. The hall with the lord's seat below,
	 * rooms above (bedrolls, chests, the kitchen corner, a Tinkers bench), a walled roof and a spire on it.
	 */
	private static void keep(Site s, int c) {
		int f = s.floor, y0 = s.box.minY();
		int u0 = c - 9, u1 = c + 9, v0 = 28, v1 = 42, upper = f + STOREY, roof = f + STOREY * 2;
		BlockState brick = Kit.b("holystone_bricks"), pillar = Kit.log("holystone_pillar", Direction.Axis.Y), planks = Kit.b("skyroot_planks");
		s.fill(u0, y0, v0, u1, y0, v1, Kit.b("holystone_flagstones"));
		s.room(u0, f, v0, u1, roof, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			if (corner) {
				return pillar;
			}
			int h = (y - f) % STOREY;
			boolean bay = (u - u0) % 4 == 2 && (v == v0 || v == v1) || (v - v0) % 4 == 3 && (u == u0 || u == u1);
			return y < roof && (h == 2 || h == 3) && bay ? Kit.b("scatterglass_pane") : brick;
		}, false);
		// floors: the rooms' and the roof's; a ladder in the front left corner goes up through both
		int lu = u0 + 1, lv = v0 + 1;
		s.fill(u0 + 1, upper - 1, v0 + 1, u1 - 1, upper - 1, v1 - 1, (u, y, v) -> u == lu && v == lv ? null : planks);
		s.fill(u0 + 1, roof - 1, v0 + 1, u1 - 1, roof - 1, v1 - 1, (u, y, v) -> u == lu && v == lv ? null : brick);
		s.ladder(lu, f, roof - 1, lv, 1);
		// merlons round the roof
		s.fill(u0, roof + 1, v0, u1, roof + 1, v1, (u, y, v) -> {
			boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
			return edge && (u + v) % 2 == 0 ? brick : null;
		});
		// the door, three wide, and the way in
		s.air(c - 1, f, v0, c + 1, f + 2, v0);
		s.fill(c - 1, f + 3, v0, c + 1, f + 3, v0, Kit.b("holystone_keystone"));
		// the hall: a runner to the dais, the seat, pillars and lanterns down each side
		s.fill(c, f, v0 + 1, c, f, v1 - 4, Kit.b("blue_cloudwool_carpet"));
		s.fill(c - 2, f, v1 - 3, c + 2, f, v1 - 1, Kit.slab("holystone_brick_slab", false));
		s.set(c, f, v1 - 1, brick);
		s.set(c, f + 1, v1 - 1, Kit.stairs("holystone_brick_stairs", s.dir(0), false));
		s.set(c - 1, f + 1, v1 - 1, Kit.b("holystone_brick_wall"));
		s.set(c + 1, f + 1, v1 - 1, Kit.b("holystone_brick_wall"));
		for (int v = v0 + 3; v <= v1 - 4; v += 4) {
			for (int u : new int[] {c - 4, c + 4}) {
				s.fill(u, f, v, u, upper - 2, v, pillar);
				s.set(u + (u < c ? 1 : -1), upper - 2, v, Kit.lantern());
			}
		}
		s.chest(u1 - 1, f, v1 - 1, 3, "chests/sky_village/keep");
		s.chest(u0 + 1, f, v1 - 1, 1, "chests/sky_village/keep");
		villager(s, c, f + 1, v1 - 2, true);
		villager(s, c - 3, f, v0 + 5, false);
		villager(s, c + 3, f, v0 + 5, false);
		// the rooms above: bedrolls along the back, the kitchen corner, a bench
		for (int u = u0 + 2; u <= u1 - 2; u += 3) {
			s.set(u, upper, v1 - 1, Kit.b(u % 2 == 0 ? "blue_cloudwool_carpet" : "cloudwool_carpet"));
		}
		Direction in = s.dir(3);
		s.set(u1 - 1, upper, v0 + 2, Kit.with(Kit.other("jaymoskitchen:stove", Kit.v("smoker")), BlockStateProperties.HORIZONTAL_FACING, in));
		s.set(u1 - 1, upper, v0 + 3, Kit.with(Kit.other("jaymoskitchen:oak_cabinet", Kit.v("barrel")), BlockStateProperties.HORIZONTAL_FACING, in));
		s.set(u1 - 1, upper, v0 + 4, Kit.other("jaymoskitchen:cooking_pot", Kit.v("cauldron")));
		s.set(u1 - 1, upper, v0 + 6, Kit.b("crafting_station"));
		s.chest(u0 + 1, upper, v0 + 5, 1, "chests/sky_village/home");
		s.set(c, roof - 2, (v0 + v1) / 2, Kit.lantern());
		villager(s, c + 2, upper, v0 + 6, false);
		villager(s, c - 2, upper, v0 + 8, false);
		// the spire: a small room on the roof, a tall cloth cap
		int su0 = c - 3, su1 = c + 3, sv0 = v0 + 4, sv1 = v0 + 10;
		s.room(su0, roof, sv0, su1, roof + 5, sv1, (u, y, v) -> {
			boolean corner = (u == su0 || u == su1) && (v == sv0 || v == sv1);
			return corner ? pillar : y == roof + 2 && (u == c || v == sv0 + 3) ? Kit.b("scatterglass_pane") : brick;
		}, false);
		s.air(c, roof, sv0, c, roof + 1, sv0);
		s.pyramid(su0 - 1, sv0 - 1, su1 + 1, sv1 + 1, roof + 6, Kit.b("blue_cloudwool"));
		s.vase(c, roof, sv1 - 1, "holystone", "chests/sky_village/keep");
		s.candle(c - 1, roof + 2, sv1 - 1, 2);
		s.candle(c + 1, roof + 2, sv1 - 1, 2);
	}
}
