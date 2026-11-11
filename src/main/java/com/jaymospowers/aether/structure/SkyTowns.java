package com.jaymospowers.aether.structure;

import com.jaymospowers.aether.structure.SkyVillages.Palette;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The sky towns: a town of the Aether at the scale of the great towns below, laid out in lots on a grid round a bell
 * plaza, all on one level. Each lot that has ground under it is one ground piece (its share of the streets, a foundation
 * under it) and one building piece: the great hall beside the plaza, homes of one and two storeys, a library, a smithy, a
 * tavern, a workshop, a windmill, farm plots and one mooring with its balloon skiff. Lots off the island's edge are left
 * out, so a town takes the shape of its island; one with too few lots is not built at all.
 * <p>
 * Every piece says what it is ({@link #name}), the way a village's templates do, so Jaymo's Kingdoms can read the town as
 * it reads any village: its bell founds a kingdom, the great hall is the seat, the library the storehouse, the farm
 * plots the first farm. Without Kingdoms it is a town of villagers.
 */
final class SkyTowns {
	// ---- tuning ----
	/** A lot's side (blocks), how far the grid runs from the plaza each way (lots), and the fewest lots a town may have. */
	static final int LOT = 17, REACH = 3, LEAST_LOTS = 14;
	/** A lot's ground may lie this far above or below the plaza's and be this uneven and still be built on (blocks). */
	static final int LOT_OFF = 5, LOT_TOLERANCE = 8;
	/** How high a lot is cleared, and how deep its foundation goes. */
	static final int LOT_H = 14, FOUNDATION = 16;
	/** Streets: this wide along each side of a lot (two lots make a street of twice this). */
	static final int STREET = 2;
	/** The great hall: its side, its lower storey's height, its gallery's height, and its whole height with the roof and spire. */
	static final int HALL_SIDE = 13, HALL_STOREY = 6, HALL_GALLERY = 4, HALL_H = 23;
	/** Of the lots left once the named buildings are placed: the share that are farm plots, and of the homes the share with two storeys. */
	static final float FARMS = 0.18F, TALL = 0.4F;

	/** What stands on a lot. The order is kept in saves (a piece's variant). */
	enum Lot {
		HALL("great_hall", HALL_SIDE, HALL_SIDE, HALL_H), HOUSE("house", 9, 9, 10), TALL_HOUSE("tall_house", 9, 9, 14), LIBRARY("library", 11, 9, 10),
		SMITHY("weaponsmith", 9, 9, 10), TAVERN("tavern", 9, 9, 10), WORKSHOP("toolsmith", 9, 9, 10), MILL("windmill", 11, 11, 18),
		FARM("farm", 13, 13, 4), MOORING("decor/mooring", 15, 7, 19);

		final String id;
		final int across, along, height;

		Lot(String id, int across, int along, int height) {
			this.id = id;
			this.across = across;
			this.along = along;
			this.height = height;
		}
	}

	private SkyTowns() {
	}

	/** What a building piece is, as a village template's path would say it. */
	static String name(int variant) {
		Lot[] lots = Lot.values();
		return "jaymospowers:sky_town/" + lots[Math.floorMod(variant >> 2, lots.length)].id;
	}

	/** What a ground piece is: the plaza, or a lot's streets. */
	static String groundName(int variant) {
		return "jaymospowers:sky_town/" + ((variant & 4) != 0 ? "town_centers/plaza" : "terrain/streets/lot");
	}

	// ---------------------------------------------------------------- laying it out

	/** The town round (cx, cz), or nothing if the island has no room for one. */
	static Optional<Structure.GenerationStub> plan(Ground ground, RandomSource random, int cx, int cz, Direction facing, int palette) {
		int y = ground.flat(cx, cz, LOT / 2, LOT / 2, LOT_TOLERANCE);
		if (y == Ground.NONE) {
			return Optional.empty();
		}
		Direction right = facing.getClockWise();
		int n = REACH * 2 + 1;
		boolean[][] has = new boolean[n][n];
		// the lots with ground under them, reached from the plaza lot by lot (so the town is one piece)
		ArrayDeque<int[]> open = new ArrayDeque<>();
		List<int[]> lots = new ArrayList<>();
		has[REACH][REACH] = true;
		open.add(new int[] {0, 0});
		while (!open.isEmpty()) {
			int[] at = open.poll();
			lots.add(at);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int i = at[0] + d.getStepX(), j = at[1] + d.getStepZ();
				if (Math.abs(i) > REACH || Math.abs(j) > REACH || has[i + REACH][j + REACH]) {
					continue;
				}
				int x = cx + right.getStepX() * i * LOT + facing.getStepX() * j * LOT, z = cz + right.getStepZ() * i * LOT + facing.getStepZ() * j * LOT;
				int top = ground.flat(x, z, LOT / 2, LOT / 2, LOT_TOLERANCE);
				if (top != Ground.NONE && Math.abs(top - y) <= LOT_OFF) {
					has[i + REACH][j + REACH] = true;
					open.add(new int[] {i, j});
				}
			}
		}
		if (lots.size() < LEAST_LOTS) {
			return Optional.empty();
		}
		// what stands where: the hall on the first lot beside the plaza, then one of each named building on lots drawn at
		// random, the mooring as far out as there is, and homes and farm plots on the rest
		List<int[]> rest = new ArrayList<>(lots.subList(2, lots.size()));
		List<AetherPiece> pieces = new ArrayList<>();
		pieces.add(ground(cx, cz, y, facing, random, palette, true));
		int[] hallAt = lots.get(1);
		List<Object[]> buildings = new ArrayList<>();
		buildings.add(new Object[] {hallAt, Lot.HALL});
		int[] far = rest.get(rest.size() - 1);
		rest.remove(rest.size() - 1);
		buildings.add(new Object[] {far, Lot.MOORING});
		for (Lot named : new Lot[] {Lot.LIBRARY, Lot.SMITHY, Lot.TAVERN, Lot.WORKSHOP, Lot.MILL, Lot.FARM}) {
			if (!rest.isEmpty()) {
				buildings.add(new Object[] {rest.remove(random.nextInt(rest.size())), named});
			}
		}
		for (int[] at : rest) {
			float roll = random.nextFloat();
			buildings.add(new Object[] {at, roll < FARMS ? Lot.FARM : random.nextFloat() < TALL ? Lot.TALL_HOUSE : Lot.HOUSE});
		}
		for (Object[] b : buildings) {
			int[] at = (int[])b[0];
			Lot lot = (Lot)b[1];
			int x = cx + right.getStepX() * at[0] * LOT + facing.getStepX() * at[1] * LOT, z = cz + right.getStepZ() * at[0] * LOT + facing.getStepZ() * at[1] * LOT;
			pieces.add(ground(x, z, y, facing, random, palette, false));
			// its door toward the plaza where that is a straight line, else any way
			Direction door = at[0] == 0 ? (at[1] > 0 ? facing.getOpposite() : facing) : at[1] == 0 ? (at[0] > 0 ? right.getOpposite() : right)
				: Direction.from2DDataValue(random.nextInt(4));
			boolean swap = door.getAxis() == Direction.Axis.X;
			int w = swap ? lot.along : lot.across, l = swap ? lot.across : lot.along;
			BoundingBox box = new BoundingBox(x - w / 2, y, z - l / 2, x - w / 2 + w - 1, y + lot.height - 1, z - l / 2 + l - 1);
			pieces.add(new AetherPiece(AetherPiece.Kind.TOWN_BUILDING, box, door, random.nextLong(), palette | lot.ordinal() << 2));
		}
		return Optional.of(new Structure.GenerationStub(new BlockPos(cx, y, cz), builder -> pieces.forEach(builder::addPiece)));
	}

	private static AetherPiece ground(int x, int z, int y, Direction facing, RandomSource random, int palette, boolean plaza) {
		BoundingBox box = new BoundingBox(x - LOT / 2, y, z - LOT / 2, x + LOT / 2, y + LOT_H - 1, z + LOT / 2);
		return new AetherPiece(AetherPiece.Kind.TOWN_LOT, box, facing, random.nextLong(), palette | (plaza ? 4 : 0));
	}

	// ---------------------------------------------------------------- the ground

	/** A lot's ground: a foundation, the air cleared over it, streets along its sides, green in the middle (or the plaza). */
	static void lot(Site s) {
		Palette p = SkyVillages.palette(s.variant & 3);
		boolean plaza = (s.variant & 4) != 0;
		int f = s.floor, y0 = s.box.minY(), n = LOT - 1, c = n / 2;
		s.foundation(0, 0, n, n, y0 - 1, Kit.b("holystone"), FOUNDATION);
		s.air(0, f, 0, n, s.box.maxY(), n);
		s.fill(0, y0, 0, n, y0, n, (u, y, v) -> {
			boolean street = u < STREET || v < STREET || u > n - STREET || v > n - STREET;
			if (plaza || street) {
				float r = s.rnd(u, y, v, 1);
				return plaza ? (r < 0.15F ? Kit.b("mossy_holystone") : Kit.b("holystone_flagstones")) : r < 0.9F ? Kit.b(p.path()) : Kit.b(p.ground());
			}
			return Kit.b(p.ground());
		});
		if (!plaza) {
			return;
		}
		// the bell on its dais, the well of light beside it, lamps at the corners, two stalls
		s.fill(c - 1, f, c - 1, c + 1, f, c + 1, Kit.slab("holystone_brick_slab", false));
		s.set(c, f, c, Kit.b("holystone_bricks"));
		s.set(c, f + 1, c, Kit.v("bell"));
		SkyVillages.well(s, p, c - 6, c - 1, false);
		for (int[] q : new int[][] {{3, 3}, {n - 3, 3}, {3, n - 3}, {n - 3, n - 3}}) {
			s.set(q[0], f, q[1], Kit.b("holystone_brick_wall"));
			s.set(q[0], f + 1, q[1], Kit.b(p.wood() + "_fence"));
			s.set(q[0], f + 2, q[1], Kit.b("ambrosium_torch"));
		}
		for (int[] q : new int[][] {{c + 4, c - 3}, {c + 4, c + 2}}) {
			stall(s, p, q[0], q[1]);
		}
		SkyVillages.villager(s, c + 3, f, c - 2, false);
		SkyVillages.villager(s, c + 3, f, c + 3, false);
	}

	/** A market stall, three by two: posts, a cloth awning, a counter with something on it. */
	private static void stall(Site s, Palette p, int u0, int v0) {
		int f = s.floor;
		for (int[] q : new int[][] {{u0, v0}, {u0 + 2, v0}, {u0, v0 + 1}, {u0 + 2, v0 + 1}}) {
			s.fill(q[0], f, q[1], q[0], f + 1, q[1], Kit.b(p.wood() + "_fence"));
		}
		s.fill(u0, f + 2, v0, u0 + 2, f + 2, v0 + 1, (u, y, v) -> Kit.b(u == u0 + 1 ? p.roofB() : p.roofA()));
		s.set(u0 + 1, f, v0, Kit.v("barrel"));
		s.set(u0 + 1, f, v0 + 1, Kit.other("jaymoskitchen:cabbage_crate", Kit.v("hay_block")));
	}

	// ---------------------------------------------------------------- the buildings

	static void building(Site s) {
		Palette p = SkyVillages.palette(s.variant & 3);
		Lot lot = Lot.values()[Math.floorMod(s.variant >> 2, Lot.values().length)];
		String roof = s.rnd(0, 0, 0, 9) < 0.5F ? p.roofA() : p.roofB();
		switch (lot) {
			case HALL -> hall(s, p);
			case HOUSE -> SkyVillages.house(s, p, 1, 1, 0, roof, 0, false);
			case TALL_HOUSE -> tallHouse(s, p, roof);
			case LIBRARY -> library(s, p, roof);
			case SMITHY, WORKSHOP -> SkyVillages.house(s, p, 1, 1, 0, roof, 2, false);
			case TAVERN -> SkyVillages.house(s, p, 1, 1, 0, roof, 1, false);
			case MILL -> SkyVillages.windmill(s, p, 4, 3, false);
			case FARM -> {
				SkyVillages.farm(s, p, 1, 1, 0, false);
				SkyVillages.farm(s, p, 7, 1, 1, false);
				SkyVillages.farm(s, p, 1, 7, 2, false);
				SkyVillages.farm(s, p, 7, 7, 3, false);
			}
			case MOORING -> SkyVillages.mooring(s, p, 9, 1, false);
		}
	}

	private static void door(Site s, Palette p, int u, int v, int side) {
		int f = s.floor;
		s.air(u, f, v, u, f + 1, v);
		BlockState leaf = Kit.with(Kit.b(p.wood() + "_door"), BlockStateProperties.HORIZONTAL_FACING, s.dir(side));
		s.set(u, f, v, Kit.with(leaf, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		s.set(u, f + 1, v, Kit.with(leaf, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/**
	 * The great hall: one tall room of stone with pillars, a gallery round it above (a ladder in the corner by the door), a
	 * cloth roof and a spire. The floor is left open from the door to the back wall: whoever rules here sets their own seat.
	 */
	private static void hall(Site s, Palette p) {
		int f = s.floor, y0 = s.box.minY(), n = HALL_SIDE - 1, c = n / 2, gallery = f + HALL_STOREY, eaves = gallery + HALL_GALLERY + 1;
		BlockState brick = Kit.b("holystone_bricks"), pillar = Kit.log("holystone_pillar", Direction.Axis.Y), planks = Kit.b(p.planks());
		s.fill(0, y0, 0, n, y0, n, Kit.b("holystone_flagstones"));
		s.room(0, f, 0, n, eaves - 1, n, (u, y, v) -> {
			boolean corner = (u == 0 || u == n) && (v == 0 || v == n);
			if (corner) {
				return pillar;
			}
			boolean bay = (u == 0 || u == n) && v % 4 == 2 || (v == 0 || v == n) && u % 4 == 2;
			boolean glass = bay && (y >= f + 2 && y <= f + 3 || y == gallery + 2);
			return glass ? Kit.b("scatterglass_pane") : y <= gallery ? (s.rnd(u, y, v, 2) < 0.1F ? Kit.b("holystone") : brick) : planks;
		}, false);
		// the door toward the town, a keystone over it
		door(s, p, c, n, 0);
		s.set(c, f + 2, n, Kit.b("holystone_keystone"));
		// pillars down both sides, a lantern on each
		for (int v = 3; v <= n - 3; v += 3) {
			for (int u : new int[] {3, n - 3}) {
				s.fill(u, f, v, u, gallery - 1, v, pillar);
				s.set(u + (u < c ? -1 : 1), gallery - 2, v, Kit.lantern());
			}
		}
		// the gallery: three wide round the walls, a rail along its inner edge, the ladder up in the corner left of the door
		int lu = 1, lv = n - 1;
		s.fill(1, gallery - 1, 1, n - 1, gallery - 1, n - 1, (u, y, v) -> {
			boolean walk = u <= 3 || u >= n - 3 || v <= 3 || v >= n - 3;
			return walk && !(u == lu && v == lv) ? planks : null;
		});
		s.fill(3, gallery, 3, n - 3, gallery, n - 3, (u, y, v) -> u == 3 || u == n - 3 || v == 3 || v == n - 3 ? Kit.b(p.wood() + "_fence") : null);
		s.ladder(lu, f, gallery - 1, lv, 1);
		s.chest(n - 1, gallery, 1, 3, "chests/sky_village/keep");
		s.set(1, gallery, 1, Kit.b("blue_cloudwool_carpet"));
		s.set(1, gallery, 3, Kit.b("cloudwool_carpet"));
		s.set(n - 1, gallery, n - 1, Kit.v("bookshelf"));
		// the roof and the spire
		s.pyramid(0, 0, n, n, eaves, Kit.b("blue_cloudwool"));
		s.fill(c, eaves + c + 1, c, c, eaves + c + 2, c, pillar);
		s.set(c, eaves + c + 3, c, Kit.b("ambrosium_torch"));
		SkyVillages.villager(s, c - 2, f, 2, false);
		SkyVillages.villager(s, c + 2, f, 2, false);
	}

	/** A two-storey home: stone below, planks above, a ladder between, a cloth roof. Two live in it. */
	private static void tallHouse(Site s, Palette p, String roof) {
		int f = s.floor, y0 = s.box.minY(), u0 = 1, v0 = 1, u1 = 7, v1 = 7, mu = 4, mv = 4, upper = f + 4, eaves = upper + 4;
		BlockState log = Kit.log(p.wood() + "_log", Direction.Axis.Y), planks = Kit.b(p.planks()), stone = Kit.b(p.stone());
		s.fill(u0, y0, v0, u1, y0, v1, planks);
		s.room(u0, f, v0, u1, eaves - 1, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			boolean window = (y == f + 1 || y == upper + 1) && (u == mu || v == mv);
			return corner ? log : window ? Kit.b("scatterglass_pane") : y < upper ? stone : planks;
		}, false);
		door(s, p, mu, v1, 0);
		int lu = u0 + 1, lv = v0 + 1;
		s.fill(u0 + 1, upper - 1, v0 + 1, u1 - 1, upper - 1, v1 - 1, (u, y, v) -> u == lu && v == lv ? null : planks);
		s.ladder(lu, f, upper - 1, lv, 1);
		s.pyramid(u0 - 1, v0 - 1, u1 + 1, v1 + 1, eaves, Kit.b(roof));
		s.set(mu, upper - 2, mv, Kit.lantern());
		s.set(u1 - 1, f, v0 + 1, Kit.v("crafting_table"));
		s.chest(u1 - 1, f, v0 + 3, 3, "chests/sky_village/home");
		s.set(u1 - 1, upper, v0 + 1, Kit.b("red_cloudwool_carpet"));
		s.set(u1 - 1, upper, v0 + 3, Kit.b("blue_cloudwool_carpet"));
		s.set(u0 + 1, upper, v1 - 1, Kit.v("bookshelf"));
		SkyVillages.villager(s, mu, f, mv, false);
		SkyVillages.villager(s, mu + 1, f, mv + 1, false);
	}

	/** The library: nine by seven, shelves two high along the back and the ends, a lectern, a keeper. */
	private static void library(Site s, Palette p, String roof) {
		int f = s.floor, y0 = s.box.minY(), u0 = 1, v0 = 1, u1 = 9, v1 = 7, mu = 5;
		BlockState log = Kit.log(p.wood() + "_log", Direction.Axis.Y), planks = Kit.b(p.planks()), stone = Kit.b(p.stone());
		s.fill(u0, y0, v0, u1, y0, v1, planks);
		s.room(u0, f, v0, u1, f + 3, v1, (u, y, v) -> {
			boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
			boolean window = y == f + 1 && v == v1 && (u == mu - 2 || u == mu + 2);
			return corner ? log : window ? Kit.b("scatterglass_pane") : y == f ? stone : planks;
		}, false);
		door(s, p, mu, v1, 0);
		s.fill(u0 + 1, f, v0 + 1, u1 - 1, f + 1, v0 + 1, Kit.v("bookshelf"));
		s.fill(u0 + 1, f, v0 + 2, u0 + 1, f + 1, v1 - 2, Kit.v("bookshelf"));
		s.fill(u1 - 1, f, v0 + 2, u1 - 1, f + 1, v1 - 2, Kit.v("bookshelf"));
		s.set(mu, f, v0 + 3, Kit.with(Kit.v("lectern"), BlockStateProperties.HORIZONTAL_FACING, s.dir(0)));
		s.chest(u0 + 1, f, v1 - 1, 1, "chests/sky_village/keep");
		s.set(mu, f + 2, v0 + 3, Kit.lantern());
		s.pyramid(u0 - 1, v0 - 1, u1 + 1, v1 + 1, f + 4, Kit.b(roof));
		SkyVillages.villager(s, mu + 1, f, v0 + 4, false);
	}
}
