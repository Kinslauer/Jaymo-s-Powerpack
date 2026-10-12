package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The building catalogue of kingdoms, fiefdoms and villages: every design drawn in code from the faction's own
 * {@link Palette} (and its colour, for wool and beds), like the castles. All are drawn with their door on the north side
 * (z = 0) and y = 0 the floor laid in place of the ground; the construction code turns them to face the seat. Each
 * type's growth order is registered here ({@link Blueprint#register}): an entry may come back later with a higher
 * count and a bigger settlement (villagers plus folk) needed, so the town grows in stages, outward ring by ring.
 * <p>
 * (0.5.7) <b>No two alike.</b> Each building is drawn from its own variant ({@link Blueprint.Varied}): a house may be
 * small or long, one storey or two, with a porch, a chimney, a gabled, hipped or flat roof, timber-framed, plank,
 * stone or two-toned walls, its own windows and furnishings, and the other buildings their own sizes, roofs, crops,
 * lamps and wares. Their materials are taken from the buildings already standing round them ({@link Townscape}):
 * the woods, roofing, stonework and colours of the village or ruin the settlement was founded in, each building
 * picking its own mix. Walls, towers, gatehouses and the palisade share one look across the settlement (so they join
 * up) and vary only in their details. Variant 0 is each building exactly as first designed.
 */
final class Buildings {
	/** The ring (blocks from the seat) where a realm's walls, towers and gatehouses go up, and a village's palisade. */
	static final int WALL_RING_MIN = 56, WALL_RING_MAX = 92, PALISADE_RING_MIN = 26, PALISADE_RING_MAX = 46;

	private Buildings() {
	}

	static void register() {
		for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM}) {
			// (0.5.2) after the mine ({@link Mines}): a tree farm, a pen, a field, then homes and the rest
			realm(type, "tree_farm", Buildings::treeFarm, 1, 0);
			realm(type, "pen", Buildings::pen, 1, 0);
			realm(type, "farm", Buildings::farm, 1, 0);
			realm(type, "house", Buildings::house, 2, 0);
			realm(type, "smithy", Buildings::smithy, 1, 0);
			realm(type, "road", Buildings::road, 2, 0);
			realm(type, "well", Buildings::well, 1, 0);
			realm(type, "house", Buildings::house, 4, 16);
			realm(type, "mason", Buildings::mason, 1, 20);
			// (0.5.23) where the guards throw dead monsters ({@link Undertakers})
			realm(type, "fire_pit", Buildings::firePit, 1, 20);
			realm(type, "market", Buildings::market, 1, 24);
			// (0.5.25) a coaching inn or a tavern with a beer garden
			realm(type, "tavern", Buildings::tavern, 1, 26);
			realm(type, "stable", Buildings::stable, 1, 28);
			realm(type, "farm", Buildings::farm, 2, 28);
			realm(type, "road", Buildings::road, 4, 28);
			realm(type, "barracks", Buildings::barracks, 1, 32);
			realm(type, "house", Buildings::house, 7, 36);
			town(type, "wall", Buildings::wall, 12, 40);
			town(type, "tower", Buildings::tower, 4, 40);
			town(type, "gatehouse", Buildings::gatehouse, 2, 40);
			realm(type, "farm", Buildings::farm, 4, 44);
			realm(type, "house", Buildings::house, 10, 48);
			town(type, "wall", Buildings::wall, 24, 56);
			town(type, "tower", Buildings::tower, 8, 56);
		}
		realm(FactionType.VILLAGE, "tree_farm", Buildings::treeFarm, 1, 0);
		realm(FactionType.VILLAGE, "pen", Buildings::pen, 1, 0);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 1, 0);
		realm(FactionType.VILLAGE, "house", Buildings::house, 2, 0);
		realm(FactionType.VILLAGE, "well", Buildings::well, 1, 0);
		realm(FactionType.VILLAGE, "stall", Buildings::stall, 1, 8);
		realm(FactionType.VILLAGE, "fire_pit", Buildings::firePit, 1, 10);
		realm(FactionType.VILLAGE, "road", Buildings::road, 2, 8);
		realm(FactionType.VILLAGE, "house", Buildings::house, 4, 12);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 2, 14);
		// (0.5.25) a row stable, its horses bought in when it's done ({@link Gatherers#built})
		realm(FactionType.VILLAGE, "stable", Buildings::stable, 1, 18);
		realm(FactionType.VILLAGE, "stall", Buildings::stall, 2, 16);
		// (0.5.25) an alehouse
		realm(FactionType.VILLAGE, "tavern", Buildings::tavern, 1, 16);
		realm(FactionType.VILLAGE, "road", Buildings::road, 4, 16);
		realm(FactionType.VILLAGE, "house", Buildings::house, 6, 20);
		town(FactionType.VILLAGE, "palisade", Buildings::palisade, 10, 22);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 3, 24);
		realm(FactionType.VILLAGE, "house", Buildings::house, 8, 28);
		for (String ring : new String[] {"wall", "tower", "gatehouse"}) {
			Planner.setRings(ring, WALL_RING_MIN, WALL_RING_MAX);
		}
		Planner.setRings("palisade", PALISADE_RING_MIN, PALISADE_RING_MAX);
	}

	private interface Design {
		Blueprint draw(Kit k);
	}

	/** A building whose materials and details are its own. */
	private static void realm(FactionType type, String id, Design design, int max, int minSize) {
		Blueprint.register(type, new Blueprint.Entry(id, (level, faction, variant) -> design.draw(Kit.of(level, faction, variant, variant)), max,
			minSize));
	}

	/** A piece of the town's defences: one look for the whole settlement (so they join up), its own details. */
	private static void town(FactionType type, String id, Design design, int max, int minSize) {
		Blueprint.register(type, new Blueprint.Entry(id,
			(level, faction, variant) -> design.draw(Kit.of(level, faction, variant == 0L ? 0L : townSeed(level, faction), variant)), max, minSize));
	}

	/** The settlement's own seed for its defences' materials (never 0). */
	private static long townSeed(ServerLevel level, Faction f) {
		long v = level.getSeed() ^ f.id() * 0x9E3779B97F4A7C15L ^ 0x70776E5EEDL;
		v ^= v >>> 33;
		v *= 0xFF51AFD7ED558CCDL;
		v ^= v >>> 33;
		return v == 0L ? 1L : v;
	}

	// ---- the designs ----------------------------------------------------------------------------------------------

	private enum Walls { TIMBER, PLANK, MASONRY, FRAMED, TWO_TONE }

	private enum Roof { GABLE, GABLE_ACROSS, HIP, FLAT }

	/** Roofs in green lands, and in dry ones (the desert, the savanna, the badlands), where flat roofs are common. */
	private static final Roof[] ROOFS = {Roof.GABLE, Roof.GABLE, Roof.GABLE_ACROSS, Roof.GABLE_ACROSS, Roof.HIP, Roof.HIP, Roof.FLAT};
	private static final Roof[] DRY_ROOFS = {Roof.GABLE, Roof.GABLE_ACROSS, Roof.HIP, Roof.FLAT, Roof.FLAT, Roof.FLAT};

	/** What a house may keep by its walls: nothing, a chest, books, a furnace, a lamp, (0.5.8) a fruit basket, a tool rack. */
	private enum Keep { NOTHING, CHEST, BOOKS, FURNACE, LAMP, BASKET, RACK }

	/**
	 * A cottage: 5-9 wide and 7-9 deep, maybe with a porch and a second storey (reached by a ladder); timber, plank,
	 * stone, framed or two-toned walls; a gabled roof (either way), a hipped or a flat one; maybe a chimney; beds, a
	 * table, a rug and its own furnishings. The plain design: 7 x 7, a stone course and timber walls (all timber in a
	 * village), a gabled roof, a bed, a table.
	 */
	static Blueprint house(Kit k) {
		Palette p = k.p;
		int w = k.pickInt(7, 9, 7, 5, 9);
		int d = k.pickInt(7, 9, 7, 9);
		boolean porch = d >= 9 && k.chance(0.45);
		int front = porch ? 2 : 0;
		int deep = d - front;
		boolean upstairs = w >= 7 && deep >= 7 && k.chance(0.35);
		int top = upstairs ? 8 : 4;
		Walls walls = k.classic ? Walls.TIMBER
			: k.pick(upstairs ? Walls.values() : new Walls[] {Walls.TIMBER, Walls.PLANK, Walls.MASONRY, Walls.FRAMED});
		Roof roof = k.classic ? Roof.GABLE : k.pick(k.dry ? DRY_ROOFS : ROOFS);
		Sketch s = new Sketch("house", w, d, 24);
		BlockState foundation = k.village ? Blocks.COBBLESTONE.defaultBlockState() : p.wall();
		for (int x = 0; x < w; x++) {
			for (int z = front; z < d; z++) {
				boolean alongX = z == front || z == d - 1;
				boolean edge = x == 0 || x == w - 1 || alongX;
				boolean corner = (x == 0 || x == w - 1) && alongX;
				s.set(x, 0, z, edge ? foundation : k.village ? p.planks() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y < top; y++) {
					s.set(x, y, z, houseWall(k, walls, x, y, z, corner, alongX, front, upstairs));
				}
			}
		}
		int mid = w / 2, zm = (front + d - 1) / 2;
		s.door(mid, 1, front, Direction.NORTH, k.door);
		// windows: the sides, the back, the front either side of the door (a second row upstairs)
		boolean tall = !upstairs && k.chance(0.3);
		for (int wy : upstairs ? new int[] {2, 6} : new int[] {2}) {
			List<int[]> spots = new ArrayList<>();
			int[] sideZ = deep >= 9 && k.chance(0.6) ? new int[] {zm - 2, zm + 2} : new int[] {zm};
			for (int x : new int[] {0, w - 1}) {
				for (int z : sideZ) {
					spots.add(new int[] {x, z, 1});
				}
			}
			for (int x : w >= 9 && k.chance(0.6) ? new int[] {2, w - 3} : new int[] {mid}) {
				spots.add(new int[] {x, d - 1, 0});
			}
			spots.add(new int[] {1, front, 1});
			spots.add(new int[] {w - 2, front, 1});
			for (int[] at : spots) {
				s.set(at[0], wy, at[1], k.pane);
				if (tall && at[2] == 1) {
					s.set(at[0], wy + 1, at[1], k.pane);
				}
			}
		}
		// the roof
		BlockState stairs = k.classic ? k.village ? k.woodStairs : p.stairs() : k.roofStairs;
		BlockState ridge = k.classic ? p.woodSlab() : k.roofSlab;
		BlockState fill = walls == Walls.MASONRY ? p.wall() : walls == Walls.FRAMED ? k.infill : p.planks();
		int peak;
		switch (roof) {
			case GABLE -> {
				s.gable(0, w - 1, front, d - 1, top, stairs, ridge, fill);
				if (k.classic || k.chance(0.6)) {
					s.set(mid, top + 1, front, k.pane);
				}
				peak = top + (w - 1) / 2;
			}
			case GABLE_ACROSS -> {
				s.gableX(0, w - 1, front, d - 1, top, stairs, ridge, fill);
				if (k.chance(0.6)) {
					s.set(0, top + 1, zm, k.pane);
					s.set(w - 1, top + 1, zm, k.pane);
				}
				peak = top + (deep - 1) / 2;
			}
			case HIP -> {
				s.hip(0, w - 1, front, d - 1, top, stairs, ridge);
				peak = top + (Math.min(w, deep) - 1) / 2;
			}
			default -> {
				s.flat(0, w - 1, front, d - 1, top, p.wall(), p.slab());
				peak = top + 1;
			}
		}
		// a porch along the front: a plank floor, two posts, a rail, a lean-to roof and a lamp over the door
		if (porch) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < front; z++) {
					s.set(x, 0, z, p.planks());
					s.set(x, 4, z, k.roofSlab);
				}
				if (x > 0 && x < w - 1 && x != mid) {
					s.set(x, 1, 0, p.fence());
				}
			}
			for (int y = 1; y <= 3; y++) {
				s.set(0, y, 0, p.log());
				s.set(w - 1, y, 0, p.log());
			}
			s.set(mid, 3, 1, Sketch.hanging(Blocks.LANTERN));
		}
		// a chimney up the back wall, smoking
		if (roof != Roof.FLAT && k.chance(0.45)) {
			int cx = k.pickInt(1, w - 2);
			BlockState brick = k.pick(p.wall(), Blocks.BRICKS.defaultBlockState(), p.cracked());
			for (int y = 1; y <= peak + 1; y++) {
				s.set(cx, y, d - 1, brick);
			}
			s.set(cx, peak + 2, d - 1, Blocks.CAMPFIRE.defaultBlockState());
		}
		// upstairs: a plank floor with a ladder up through it, a lamp
		if (upstairs) {
			for (int x = 1; x < w - 1; x++) {
				for (int z = front + 1; z < d - 1; z++) {
					s.set(x, 4, z, p.planks());
				}
			}
			for (int y = 1; y <= 4; y++) {
				s.set(1, y, front + 1, Sketch.facing(Blocks.LADDER, Direction.EAST));
			}
			s.set(mid, 7, d - 2, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		}
		// beds at the back (upstairs if there is one), a table, a rug, a lamp
		boolean bedLeft = k.classic || k.chance(0.5);
		int bx = bedLeft ? 1 : w - 2, tx = bedLeft ? w - 2 : 1;
		int by = upstairs ? 5 : 1;
		s.bed(bx, by, d - 3, Direction.SOUTH, k.bed);
		boolean twoBeds = (upstairs || w >= 9) && k.chance(0.7);
		if (twoBeds) {
			s.bed(tx, by, d - 3, Direction.SOUTH, k.bed);
		}
		s.set(twoBeds && !upstairs ? mid : tx, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(mid, 1, zm, k.carpet);
		if (k.chance(0.4)) {
			s.set(mid - 1, 1, zm, k.carpet);
			s.set(mid + 1, 1, zm, k.carpet);
		}
		s.set(mid, 3, d - 2, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		// by the front wall inside: a chest, books, a furnace, a lamp, or nothing
		if (!k.classic) {
			if (!upstairs) {
				keep(k, s, 1, 1, front + 1, Direction.EAST);
			}
			keep(k, s, w - 2, 1, front + 1, Direction.WEST);
			if (upstairs) {
				keep(k, s, w - 2, 5, front + 1, Direction.WEST);
			}
			if (k.chance(0.5)) {
				s.set(twoBeds && !upstairs ? mid : tx, 2, d - 2, k.flowerPot());
			}
		}
		return s.done();
	}

	/** One of a house's walls (y 1 up to its roof). */
	private static BlockState houseWall(Kit k, Walls walls, int x, int y, int z, boolean corner, boolean alongX, int front, boolean upstairs) {
		Palette p = k.p;
		if (corner) {
			return walls == Walls.MASONRY ? p.trim() : p.log();
		}
		if (upstairs && y == 4) {
			return Sketch.lying(p.log(), alongX ? Direction.Axis.X : Direction.Axis.Z);
		}
		return switch (walls) {
			case TIMBER -> y == 1 && !k.village ? p.wall() : p.planks();
			case PLANK -> p.planks();
			case MASONRY -> Sketch.weathered(p, x, y, z, k.salt);
			case FRAMED -> (alongX ? x : z - front) % 3 == 0 ? p.log() : y == 1 ? p.wall() : k.infill;
			case TWO_TONE -> y < 4 ? Sketch.weathered(p, x, y, z, k.salt) : p.planks();
		};
	}

	/** Something by a side wall at x, y, z; {@code out} is the way out from that wall. */
	private static void keep(Kit k, Sketch s, int x, int y, int z, Direction out) {
		switch (k.pick(Keep.values())) {
			case CHEST -> s.set(x, y, z, Sketch.facing(Blocks.CHEST, Direction.SOUTH));
			case BOOKS -> s.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
			case FURNACE -> s.set(x, y, z, Sketch.facing(Blocks.FURNACE, Direction.SOUTH));
			case LAMP -> {
				s.set(x, y, z, k.p.fence());
				s.set(x, y + 1, z, Blocks.LANTERN.defaultBlockState());
			}
			case BASKET -> {
				BlockState basket = Displays.basket(out);
				if (basket != null) {
					s.set(x, y, z, basket);
				}
			}
			case RACK -> {
				BlockState rack = Displays.rack(out);
				if (rack != null) {
					s.set(x, y + 1, z, rack);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * A field: a kerb round farmland, a water hole (or a channel) to keep it wet, its own crops in halves, rows or
	 * quarters, a fence with a gate, a composter, maybe a scarecrow. The plain design: 9 x 7, wheat, carrots and potatoes.
	 */
	static Blueprint farm(Kit k) {
		int w = k.pickInt(9, 9, 11), d = k.pickInt(7, 9, 7);
		Sketch s = new Sketch("farm", w, d, 6);
		BlockState kerb = k.pick(k.p.log(), k.p.wall(), k.stripped);
		BlockState rail = k.pick(k.p.fence(), k.p.fence(), k.wallTop);
		Block[] crops = k.classic ? new Block[] {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES} : k.crops();
		int layout = k.pickInt(0, 1, 2);
		boolean channel = k.chance(0.35);
		int wx = w / 2, wz = d / 2;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
				if (edge) {
					s.set(x, 0, z, kerb);
					s.set(x, 1, z, rail);
				} else if (channel ? z == wz && x >= 2 && x <= w - 3 : x == wx && z == wz) {
					s.set(x, 0, z, Blocks.WATER.defaultBlockState());
				} else {
					s.set(x, 0, z, Blocks.FARMLAND.defaultBlockState());
					Block crop = switch (layout) {
						case 1 -> crops[(x - 1) / 2 % crops.length];
						case 2 -> crops[((x < wx ? 0 : 1) + (z < wz ? 0 : 2)) % crops.length];
						default -> x < wx ? crops[0] : crops[(z % 2 == 0 ? 1 : 2) % crops.length];
					};
					s.set(x, 1, z, crop.defaultBlockState());
				}
			}
		}
		s.set(wx, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		s.set(w - 1, 1, d - 1, Blocks.COMPOSTER.defaultBlockState());
		if (k.chance(0.35)) {
			s.set(1, 1, d - 2, k.p.fence());
			s.set(1, 2, d - 2, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(1, 3, d - 2, Sketch.facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		}
		if (k.chance(0.4)) {
			for (int[] c : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}}) {
				s.set(c[0], 2, c[1], k.light());
			}
		}
		return s.done();
	}

	/** Five saplings spaced to grow (the plain design), four in a square, or five in a cross. */
	private static final int[][] DICE = {{3, 3}, {7, 3}, {5, 5}, {3, 7}, {7, 7}}, SQUARE = {{3, 3}, {7, 3}, {3, 7}, {7, 7}},
		CROSS = {{5, 2}, {2, 5}, {5, 5}, {8, 5}, {5, 8}};

	/**
	 * A tree farm (11 x 11, 0.5.2): grass inside a kerb and a fence with a gate, saplings of the land's own wood spaced to
	 * grow, lamps on the corner posts (saplings want light). Its woodcutter fells what grows and plants again.
	 */
	static Blueprint treeFarm(Kit k) {
		Sketch s = new Sketch("tree_farm", 11, 11, 3);
		BlockState sapling = Gatherers.sapling(k.p.log());
		BlockState kerb = k.pick(k.p.log(), k.p.wall(), k.stripped);
		BlockState rail = k.pick(k.p.fence(), k.p.fence(), k.wallTop);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 11; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
				s.set(x, 0, z, edge ? kerb : Blocks.GRASS_BLOCK.defaultBlockState());
				if (edge) {
					s.set(x, 1, z, rail);
				}
			}
		}
		for (int[] at : k.pick(DICE, SQUARE, CROSS)) {
			s.set(at[0], 1, at[1], sapling != null ? sapling : Blocks.OAK_SAPLING.defaultBlockState());
		}
		s.set(5, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		for (int[] c : new int[][] {{0, 0}, {10, 0}, {0, 10}, {10, 10}}) {
			s.set(c[0], 2, c[1], k.light());
		}
		return s.done();
	}

	/**
	 * A pen (11 x 9, 0.5.2): grass inside a kerb and a fence with a gate, a water trough, hay, maybe a lean-to shelter.
	 * Its first beasts are bought in when it's done ({@link Gatherers#built}); a farmer keeps the herd (and butchers from it).
	 */
	static Blueprint pen(Kit k) {
		Sketch s = new Sketch("pen", 11, 9, 4);
		BlockState kerb = k.pick(k.p.log(), k.p.wall(), k.stripped);
		BlockState rail = k.pick(k.p.fence(), k.p.fence(), k.wallTop);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
				s.set(x, 0, z, edge ? kerb : Blocks.GRASS_BLOCK.defaultBlockState());
				if (edge) {
					s.set(x, 1, z, rail);
				}
			}
		}
		boolean mirrored = k.chance(0.5);
		int trough = mirrored ? 2 : 8, hay = mirrored ? 8 : 1;
		s.set(trough, 0, 6, Blocks.WATER.defaultBlockState());
		s.set(trough, 0, 7, Blocks.WATER.defaultBlockState());
		s.set(hay, 1, 7, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(hay + 1, 1, 7, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(5, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		for (int[] c : new int[][] {{0, 0}, {10, 0}, {0, 8}, {10, 8}}) {
			s.set(c[0], 2, c[1], k.light());
		}
		// a lean-to over the hay
		if (k.chance(0.45)) {
			int x0 = mirrored ? 7 : 1;
			for (int y = 1; y <= 2; y++) {
				s.set(x0, y, 5, k.p.fence());
				s.set(x0 + 2, y, 5, k.p.fence());
			}
			for (int x = x0; x <= x0 + 2; x++) {
				for (int z = 5; z <= 7; z++) {
					s.set(x, 3, z, k.roofSlab);
				}
			}
		}
		return s.done();
	}

	/**
	 * A well (5 x 5): a stone ring round the water, posts and a little roof (flat or peaked), or an open well with lamps
	 * on its rim; maybe a chain hanging down. The plain design: four fence posts and a slab roof.
	 */
	/**
	 * (0.5.23) A fire pit: a campfire (now and then a soul campfire) sunk in the ground in a ring of stone, a path round
	 * it, and stone posts (or lanterns on them) at its corners. The guards throw dead monsters onto it ({@link Undertakers}).
	 */
	static Blueprint firePit(Kit k) {
		Sketch s = new Sketch("fire_pit", 5, 5, 3);
		BlockState stone = k.pick(k.p.wall(), k.p.mossy(), k.p.cracked());
		BlockState fire = k.chance(0.25) ? Blocks.SOUL_CAMPFIRE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState();
		boolean posts = k.chance(0.6), lanterns = posts && k.chance(0.5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				s.set(x, 0, z, edge ? k.road : x == 2 && z == 2 ? fire : stone);
			}
		}
		if (posts) {
			for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
				s.set(c[0], 1, c[1], k.wallTop);
				if (lanterns) {
					s.set(c[0], 2, c[1], Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		return s.done();
	}

	static Blueprint well(Kit k) {
		Sketch s = new Sketch("well", 5, 5, 6);
		BlockState stone = k.pick(k.p.wall(), k.p.mossy(), k.p.cracked());
		BlockState post = k.pick(k.p.fence(), k.wallTop, k.p.log());
		int roof = k.pickInt(0, 1, 2);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean middle = x == 2 && z == 2;
				s.set(x, 0, z, edge ? k.road : middle ? Blocks.WATER.defaultBlockState() : stone);
				if (!edge && !middle) {
					s.set(x, 1, z, stone);
				}
				if (!edge && roof == 0) {
					s.set(x, 4, z, k.p.woodSlab());
				}
			}
		}
		if (roof == 2) {
			s.set(1, 2, 1, Blocks.LANTERN.defaultBlockState());
			s.set(3, 2, 3, Blocks.LANTERN.defaultBlockState());
			return s.done();
		}
		for (int[] c : new int[][] {{1, 1}, {3, 1}, {1, 3}, {3, 3}}) {
			s.set(c[0], 2, c[1], post);
			s.set(c[0], 3, c[1], post);
		}
		if (roof == 1) {
			for (int z = 1; z <= 3; z++) {
				s.set(1, 4, z, Sketch.stair(k.roofStairs, Direction.EAST));
				s.set(3, 4, z, Sketch.stair(k.roofStairs, Direction.WEST));
				s.set(2, 4, z, k.roofBlock);
			}
		}
		if (k.chance(0.5)) {
			s.set(2, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
		}
		return s.done();
	}

	/**
	 * A village market stall (5 x 5): posts, an awning in the village's colours (checked, striped or bordered), a
	 * counter, a barrel (its trade) and wares.
	 */
	static Blueprint stall(Kit k) {
		Sketch s = new Sketch("stall", 5, 5, 4);
		int pattern = k.pickInt(0, 1, 2, 3);
		BlockState post = k.pick(k.p.log(), k.p.fence(), k.stripped);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, k.road);
				s.set(x, 3, z, awning(pattern, x, z, 5, 5) ? k.wool : k.accentWool);
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					s.set(x, 1, z, post);
					s.set(x, 2, z, post);
				}
			}
		}
		for (int x = 1; x <= 3; x++) {
			s.set(x, 1, 1, Sketch.top(k.p.woodSlab()));
		}
		s.set(1, 1, 3, Blocks.BARREL.defaultBlockState());
		k.display(s, 3, 3);
		s.set(2, 1, 3, k.carpet);
		if (k.chance(0.5)) {
			s.set(k.pickInt(1, 3), 2, 1, k.flowerPot());
		}
		// (0.5.8) produce set out in fruit baskets along the counter
		BlockState basket = Displays.basket(Direction.NORTH);
		if (basket != null && !k.classic) {
			for (int x = 1; x <= 3; x++) {
				if (k.chance(0.55)) {
					s.set(x, 2, 1, basket);
				}
			}
		}
		return s.done();
	}

	/** Whether an awning's cell is in the main colour: checked (the plain design), striped either way, or bordered. */
	private static boolean awning(int pattern, int x, int z, int w, int d) {
		return switch (pattern) {
			case 1 -> x % 2 == 0;
			case 2 -> z % 2 == 0;
			case 3 -> x > 0 && x < w - 1 && z > 0 && z < d - 1;
			default -> (x + z) % 2 == 0;
		};
	}

	/** Lamp posts at two corners across (the plain design), the other two, or all four. */
	private static final int[][] DIAGONAL = {{2, 2}, {6, 6}}, ACROSS = {{2, 6}, {6, 2}}, ALL_FOUR = {{2, 2}, {6, 6}, {2, 6}, {6, 2}};

	/** A crossroads (9 x 9): two roads three wide (dirt paths in a village, the realm's road stone), lamp posts, maybe benches. */
	static Blueprint road(Kit k) {
		Sketch s = new Sketch("road", 9, 9, 4);
		BlockState patch = k.village ? Blocks.COARSE_DIRT.defaultBlockState() : k.p.cracked();
		boolean patchy = k.chance(0.5);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				if ((x >= 3 && x <= 5) || (z >= 3 && z <= 5)) {
					s.set(x, 0, z, patchy && k.chance(0.15) ? patch : k.road);
				}
			}
		}
		BlockState post = k.pick(k.p.fence(), k.wallTop, k.p.log());
		BlockState light = k.pick(Blocks.TORCH.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
		for (int[] c : k.pick(DIAGONAL, ACROSS, ALL_FOUR)) {
			s.set(c[0], 1, c[1], post);
			s.set(c[0], 2, c[1], post);
			s.set(c[0], 3, c[1], light);
		}
		// a bench facing the road
		if (k.chance(0.4)) {
			s.set(1, 1, 2, Sketch.stair(k.woodStairs, Direction.WEST));
			s.set(7, 1, 6, Sketch.stair(k.woodStairs, Direction.EAST));
		}
		return s.done();
	}

	/**
	 * A smithy (9 x 7): stone (or plank) walls open to the front, a blast furnace, smithing table, anvil and grindstone,
	 * a smoking chimney at either end, a flat or a gabled roof.
	 */
	static Blueprint smithy(Kit k) {
		Sketch s = new Sketch("smithy", 9, 7, 12);
		Palette p = k.p;
		boolean wooden = k.chance(0.3);
		boolean flat = k.classic || k.chance(0.4);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, p.floor());
				boolean wall = z == 6 || x == 0 || x == 8;
				boolean post = (x == 0 || x == 8) && z == 0;
				for (int y = 1; y <= 3; y++) {
					if (post) {
						s.set(x, y, z, p.log());
					} else if (wall) {
						s.set(x, y, z, wooden ? p.planks() : Sketch.weathered(p, x, y, z, k.salt));
					}
				}
				if (flat) {
					s.set(x, 4, z, p.slab());
				}
			}
		}
		boolean mirrored = k.chance(0.5);
		int[] row = mirrored ? new int[] {7, 6, 4, 2} : new int[] {1, 2, 4, 6};
		s.set(row[0], 1, 5, Sketch.facing(Blocks.BLAST_FURNACE, Direction.NORTH));
		s.set(row[1], 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
		s.set(row[2], 1, 5, k.pick(Blocks.ANVIL.defaultBlockState(), Blocks.CHIPPED_ANVIL.defaultBlockState()));
		s.set(row[3], 1, 5, Blocks.GRINDSTONE.defaultBlockState());
		int cx = mirrored ? 1 : 7, peak = 3;
		if (!flat) {
			// a roof beam to hang the lamp from, under a gable running along the forge
			for (int x = 1; x < 8; x++) {
				s.set(x, 4, 3, Sketch.lying(p.log(), Direction.Axis.X));
			}
			s.gableX(0, 8, 0, 6, 4, k.roofStairs, k.roofSlab, wooden ? p.planks() : p.wall());
			peak = 7;
		}
		for (int y = 1; y <= peak + 3; y++) {
			s.set(cx, y, 5, p.wall());
		}
		s.set(cx, peak + 4, 5, Blocks.CAMPFIRE.defaultBlockState());
		s.set(4, 3, 3, Sketch.hanging(Blocks.LANTERN));
		if (k.chance(0.5)) {
			s.set(mirrored ? 1 : 7, 1, 1, Sketch.facing(Blocks.CHEST, Direction.EAST));
		}
		// (0.5.8) its work hung on the back wall
		BlockState rack = Displays.rack(Direction.NORTH);
		if (rack != null && k.chance(0.8)) {
			s.set(3, 2, 5, rack);
			s.set(5, 2, 5, rack);
		}
		return s.done();
	}

	/** A mason's yard (7 x 7): weathered stone walls, a door, a flat or gabled roof, a stonecutter and piles of cut stone. */
	static Blueprint mason(Kit k) {
		Sketch s = new Sketch("mason", 7, 7, 10);
		Palette p = k.p;
		boolean flat = k.classic || k.chance(0.5);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
				s.set(x, 0, z, p.floor());
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, corner ? p.trim() : Sketch.weathered(p, x, y, z, k.salt));
					}
				}
				if (flat) {
					s.set(x, 4, z, p.slab());
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		s.set(0, 2, 3, k.pane);
		s.set(6, 2, 3, k.pane);
		s.set(5, 1, 5, Sketch.facing(Blocks.STONECUTTER, Direction.NORTH));
		s.set(1, 1, 5, p.wall());
		s.set(1, 2, 5, p.slab());
		s.set(1, 1, 4, p.slab());
		s.set(3, 3, 5, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		if (!flat) {
			s.gable(0, 6, 0, 6, 4, k.roofStairs, k.roofSlab, p.wall());
		}
		if (k.chance(0.5)) {
			s.set(5, 1, 1, p.wall());
			s.set(5, 2, 1, Sketch.stair(p.stairs(), Direction.SOUTH));
			s.set(4, 1, 1, p.slab());
		}
		BlockState rack = Displays.rack(Direction.NORTH);
		if (rack != null && k.chance(0.7)) {
			s.set(2, 2, 5, rack);
		}
		return s.done();
	}

	/**
	 * (0.5.25) A stable: a village builds the row stable; a kingdom or fief the coaching yard or (half the time, in a varied
	 * realm) the aisle barn. Its horses are bought in when it's done ({@link Gatherers#built}).
	 */
	static Blueprint stable(Kit k) {
		return k.village ? stableRow(k) : k.classic || k.chance(0.5) ? stableYard(k) : stableBarn(k);
	}

	/** A row stable (13 x 7): four open-front stalls under a shed roof, a covered walk along the front. */
	static Blueprint stableRow(Kit k) {
		Sketch s = new Sketch("stable", 13, 7, 8);
		Palette p = k.p;
		BlockState boards = k.pick(p.planks(), k.stripped);
		for (int x = 0; x < 13; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, z <= 1 ? k.road : Blocks.COARSE_DIRT.defaultBlockState());
				int roof = 4 + z / 2;
				s.set(x, roof, z, z % 2 == 0 ? k.roofSlab : Sketch.top(k.roofSlab));
				boolean post = x % 3 == 0 && (z == 0 || z == 2 || z == 6);
				int top = z % 2 == 0 ? roof - 1 : roof;
				if (post) {
					for (int y = 1; y <= top; y++) {
						s.set(x, y, z, p.log());
					}
				} else if (z == 6 || (z >= 2 && (x == 0 || x == 12))) {
					for (int y = 1; y <= top; y++) {
						s.set(x, y, z, boards);
					}
				} else if (z > 2 && x % 3 == 0) {
					s.set(x, 1, z, p.fence());
					s.set(x, 2, z, p.fence());
				} else if (z == 2) {
					s.set(x, 1, z, x % 3 == 1 ? Sketch.facing(k.gate, Direction.NORTH) : p.fence());
					s.set(x, 3, z, Sketch.lying(p.log(), Direction.Axis.X));
				}
			}
		}
		for (int x = 1; x < 12; x += 3) {
			s.set(x, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x + 1, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			s.set(x + 1, 3, 1, Sketch.hanging(Blocks.LANTERN));
		}
		return s.done();
	}

	/** An aisle barn (11 x 15): a tall gabled barn, a cart-wide aisle end to end, six stalls, a hayloft. */
	static Blueprint stableBarn(Kit k) {
		Sketch s = new Sketch("stable", 11, 15, 11);
		Palette p = k.p;
		BlockState boards = k.pick(p.planks(), k.stripped);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 15; z++) {
				boolean aisle = x >= 4 && x <= 6;
				s.set(x, 0, z, aisle ? k.road : Blocks.COARSE_DIRT.defaultBlockState());
				boolean wall = x == 0 || x == 10 || z == 0 || z == 14;
				if (wall) {
					boolean door = aisle && (z == 0 || z == 14);
					for (int y = 1; y <= 4; y++) {
						if (door && y <= 3) {
							continue;
						}
						boolean log = (x == 0 || x == 10) && (z == 0 || z == 14 || z % 4 == 1) || door && y == 4 || (x == 3 || x == 7) && (z == 0 || z == 14);
						s.set(x, y, z, log ? p.log() : y == 1 ? p.wall() : boards);
					}
					if ((x == 0 || x == 10) && z % 4 == 3) {
						s.set(x, 2, z, k.pane);
					}
				} else if (!aisle) {
					s.set(x, 4, z, Sketch.top(p.woodSlab()));
					if (x == 3 || x == 7) {
						if (z % 4 == 1) {
							for (int y = 1; y <= 3; y++) {
								s.set(x, y, z, p.log());
							}
						} else {
							s.set(x, 1, z, z % 4 == 3 ? Sketch.facing(k.gate, x == 3 ? Direction.EAST : Direction.WEST) : p.fence());
						}
						s.set(x, 5, z, p.fence());
					} else if (z % 4 == 1) {
						s.set(x, 1, z, p.fence());
						s.set(x, 2, z, p.fence());
					}
				}
			}
		}
		for (int z = 2; z < 13; z += 4) {
			for (int x : new int[] {1, 9}) {
				s.set(x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
				s.set(x, 1, z + 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
				s.set(x, 5, z, Blocks.HAY_BLOCK.defaultBlockState());
				s.set(x, 5, z + 1, Blocks.HAY_BLOCK.defaultBlockState());
				s.set(x, 6, z, Blocks.HAY_BLOCK.defaultBlockState());
			}
			for (int x = 4; x <= 6; x++) {
				s.set(x, 5, z + 1, Sketch.lying(p.log(), Direction.Axis.X));
			}
			s.set(5, 4, z + 1, Sketch.hanging(Blocks.LANTERN));
		}
		s.gable(0, 10, 0, 14, 5, k.roofStairs, k.roofSlab, boards);
		s.set(5, 7, 0, Blocks.AIR.defaultBlockState());
		s.set(5, 7, 14, Blocks.AIR.defaultBlockState());
		return s.done();
	}

	/** A coaching yard (13 x 13): four stalls under a gabled roof behind a fenced yard with a wide gate. */
	static Blueprint stableYard(Kit k) {
		Sketch s = new Sketch("stable", 13, 13, 8);
		Palette p = k.p;
		BlockState boards = k.pick(p.planks(), k.stripped);
		for (int x = 0; x < 13; x++) {
			for (int z = 0; z < 13; z++) {
				if (z < 8) {
					boolean edge = x == 0 || x == 12 || z == 0;
					s.set(x, 0, z, edge ? k.pick(p.log(), p.wall()) : k.chance(0.35) ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
					if (edge) {
						s.set(x, 1, z, x >= 5 && x <= 7 && z == 0 ? Sketch.facing(k.gate, Direction.NORTH) : p.fence());
					}
					continue;
				}
				s.set(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
				boolean post = x % 3 == 0 && (z == 8 || z == 12);
				if (post) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, p.log());
					}
				} else if (z == 12 || x == 0 || x == 12) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, boards);
					}
				} else if (x % 3 == 0) {
					s.set(x, 1, z, p.fence());
					s.set(x, 2, z, p.fence());
				} else if (z == 8) {
					s.set(x, 1, z, x % 3 == 1 ? Sketch.facing(k.gate, Direction.NORTH) : p.fence());
					s.set(x, 3, z, Sketch.lying(p.log(), Direction.Axis.X));
				}
			}
		}
		s.gableX(0, 12, 8, 12, 4, k.roofStairs, k.roofSlab, boards);
		for (int x = 1; x < 12; x += 3) {
			s.set(x, 1, 11, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x + 1, 1, 11, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		// the yard: a trough, a hay stack, lamps on the corners
		for (int x = 2; x <= 4; x++) {
			s.set(x, 0, 3, Blocks.WATER.defaultBlockState());
		}
		s.set(10, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(10, 2, 3, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(10, 1, 4, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(9, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
		for (int[] c : new int[][] {{0, 0}, {12, 0}, {4, 0}, {8, 0}}) {
			s.set(c[0], 2, c[1], k.light());
		}
		return s.done();
	}

	/**
	 * (0.5.25) A tavern: a village builds the alehouse; a kingdom or fief the coaching inn or (half the time, in a varied
	 * realm) the tavern with a beer garden.
	 */
	static Blueprint tavern(Kit k) {
		return k.village ? tavernAlehouse(k) : k.classic || k.chance(0.5) ? tavernInn(k) : tavernGarden(k);
	}

	/** Walls round x0..x1, z0..z1 from y0 to y1: posts at the corners and every {@code bay} blocks, {@code wall} between. */
	private static void shell(Sketch s, int x0, int x1, int z0, int z1, int y0, int y1, BlockState wall, BlockState post, int bay) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean xEdge = x == x0 || x == x1, zEdge = z == z0 || z == z1;
				if (!xEdge && !zEdge) {
					continue;
				}
				boolean isPost = xEdge && zEdge || zEdge && (x - x0) % bay == 0 || xEdge && (z - z0) % bay == 0;
				for (int y = y0; y <= y1; y++) {
					s.set(x, y, z, isPost ? post : wall);
				}
			}
		}
	}

	/** A table (a post and a plate) with a stool either side, east and west ({@code ew}) or north and south. */
	private static void table(Sketch s, Kit k, int x, int y, int z, boolean ew) {
		s.set(x, y, z, k.p.fence());
		s.set(x, y + 1, z, k.plate());
		if (ew) {
			s.set(x - 1, y, z, Sketch.stair(k.woodStairs, Direction.WEST));
			s.set(x + 1, y, z, Sketch.stair(k.woodStairs, Direction.EAST));
		} else {
			s.set(x, y, z - 1, Sketch.stair(k.woodStairs, Direction.NORTH));
			s.set(x, y, z + 1, Sketch.stair(k.woodStairs, Direction.SOUTH));
		}
	}

	/** A chimney from {@code y0} up to {@code y1}, a smoking campfire on top. */
	private static void chimney(Sketch s, Kit k, int x, int z, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			s.set(x, y, z, k.p.wall());
		}
		s.set(x, y1 + 1, z, Blocks.CAMPFIRE.defaultBlockState());
	}

	/** A coaching inn (11 x 9): a stone taproom (bar, barrels, tables, a hearth) and a timber floor of rooms above. */
	static Blueprint tavernInn(Kit k) {
		Sketch s = new Sketch("tavern", 11, 9, 16);
		Palette p = k.p;
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				s.set(x, 0, z, p.floor());
				s.set(x, 5, z, x == 0 || x == 10 || z == 0 || z == 8 ? p.log() : p.planks());
			}
		}
		shell(s, 0, 10, 0, 8, 1, 4, p.wall(), p.log(), 5);
		shell(s, 0, 10, 0, 8, 6, 8, k.infill, p.log(), 5);
		for (int x : new int[] {2, 3, 7, 8}) {
			s.set(x, 2, 0, k.pane);
			s.set(x, 3, 0, k.pane);
		}
		for (int z : new int[] {3, 5}) {
			s.set(0, 2, z, k.pane);
			s.set(10, 2, z, k.pane);
		}
		for (int x : new int[] {2, 8}) {
			s.set(x, 7, 0, k.pane);
			s.set(x, 7, 8, k.pane);
		}
		s.set(0, 7, 3, k.pane);
		s.set(10, 7, 3, k.pane);
		s.door(5, 1, 0, Direction.NORTH, k.door);
		s.gableX(0, 10, 0, 8, 9, k.roofStairs, k.roofSlab, k.infill);
		// the taproom: the bar along the back, barrels behind it, three tables, the hearth
		for (int x = 1; x <= 4; x++) {
			s.set(x, 1, 5, Sketch.top(p.woodSlab()));
			s.set(x, 1, 7, Blocks.BARREL.defaultBlockState());
		}
		s.set(1, 2, 7, Blocks.BARREL.defaultBlockState());
		s.set(2, 2, 7, Blocks.BARREL.defaultBlockState());
		table(s, k, 3, 1, 2, true);
		table(s, k, 7, 1, 2, true);
		table(s, k, 7, 1, 5, true);
		s.set(9, 1, 7, Sketch.facing(Blocks.SMOKER, Direction.WEST));
		chimney(s, k, 9, 7, 2, 12);
		for (int y = 1; y <= 5; y++) {
			s.set(1, y, 1, Sketch.facing(Blocks.LADDER, Direction.EAST));
		}
		s.set(5, 4, 3, Sketch.hanging(Blocks.LANTERN));
		// the rooms above
		for (int x = 1; x <= 9; x++) {
			s.set(x, 9, 4, Sketch.lying(p.log(), Direction.Axis.X));
		}
		s.set(5, 8, 4, Sketch.hanging(Blocks.LANTERN));
		for (int x : new int[] {3, 5, 7}) {
			s.bed(x, 6, 6, Direction.SOUTH, k.bed);
		}
		s.set(4, 6, 2, k.carpet);
		s.set(5, 6, 2, k.carpet);
		s.set(6, 6, 2, k.carpet);
		return s.done();
	}

	/** An alehouse (13 x 11): a long hall under a hipped roof, a bar and a hearth, a covered porch of tables out front. */
	static Blueprint tavernAlehouse(Kit k) {
		Sketch s = new Sketch("tavern", 13, 11, 12);
		Palette p = k.p;
		BlockState boards = k.pick(p.planks(), k.infill);
		for (int x = 0; x < 13; x++) {
			for (int z = 0; z < 11; z++) {
				s.set(x, 0, z, z < 3 ? p.planks() : p.floor());
				if (z < 3) {
					s.set(x, 4, z, k.roofSlab);
				}
			}
		}
		shell(s, 0, 12, 3, 10, 1, 4, boards, p.log(), 4);
		for (int x : new int[] {0, 4, 8, 12}) {
			for (int y = 1; y <= 3; y++) {
				s.set(x, y, 0, p.log());
			}
		}
		for (int x : new int[] {1, 2, 3, 9, 10, 11}) {
			s.set(x, 1, 0, p.fence());
		}
		for (int z : new int[] {5, 8}) {
			s.set(0, 2, z, k.pane);
			s.set(12, 2, z, k.pane);
		}
		for (int x : new int[] {2, 3, 9, 10}) {
			s.set(x, 2, 3, k.pane);
		}
		for (int x : new int[] {3, 6, 9}) {
			s.set(x, 2, 10, k.pane);
		}
		s.door(6, 1, 3, Direction.NORTH, k.door);
		s.hip(0, 12, 3, 10, 5, k.roofStairs, k.roofSlab);
		// the porch
		table(s, k, 2, 1, 1, true);
		table(s, k, 10, 1, 1, true);
		s.set(5, 1, 2, Blocks.BARREL.defaultBlockState());
		s.set(7, 1, 2, Blocks.BARREL.defaultBlockState());
		s.set(5, 2, 2, Blocks.LANTERN.defaultBlockState());
		s.set(7, 2, 2, Blocks.LANTERN.defaultBlockState());
		// the hall: the bar down the east side, the hearth on the west, four tables
		for (int z = 5; z <= 8; z++) {
			s.set(9, 1, z, Sketch.top(p.woodSlab()));
			s.set(11, 1, z, Blocks.BARREL.defaultBlockState());
			s.set(11, 2, z, Blocks.BARREL.defaultBlockState());
		}
		s.set(1, 1, 6, p.wall());
		s.set(1, 1, 8, p.wall());
		s.set(1, 1, 7, Sketch.facing(Blocks.SMOKER, Direction.EAST));
		chimney(s, k, 1, 7, 2, 8);
		table(s, k, 4, 1, 5, true);
		table(s, k, 4, 1, 8, true);
		table(s, k, 7, 1, 5, false);
		for (int x = 1; x <= 11; x++) {
			s.set(x, 5, 6, Sketch.lying(p.log(), Direction.Axis.X));
		}
		s.set(4, 4, 6, Sketch.hanging(Blocks.LANTERN));
		s.set(8, 4, 6, Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	/** A tavern with a beer garden (15 x 11): a two-storey tavern, and beside it a fenced garden of tables and lamps. */
	static Blueprint tavernGarden(Kit k) {
		Sketch s = new Sketch("tavern", 15, 11, 15);
		Palette p = k.p;
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < 11; z++) {
				if (x <= 8) {
					s.set(x, 0, z, p.floor());
					s.set(x, 5, z, x == 0 || x == 8 || z == 0 || z == 10 ? p.log() : p.planks());
				} else if (z >= 2) {
					s.set(x, 0, z, k.chance(0.3) ? k.road : Blocks.GRASS_BLOCK.defaultBlockState());
					if (x == 14 || z == 2 || z == 10) {
						s.set(x, 1, z, p.fence());
					}
				}
			}
		}
		s.set(11, 1, 2, Sketch.facing(k.gate, Direction.NORTH));
		shell(s, 0, 8, 0, 10, 1, 4, p.wall(), p.log(), 4);
		shell(s, 0, 8, 0, 10, 6, 8, k.infill, p.log(), 4);
		for (int x : new int[] {2, 6}) {
			s.set(x, 2, 0, k.pane);
			s.set(x, 7, 0, k.pane);
			s.set(x, 7, 10, k.pane);
		}
		for (int z : new int[] {2, 6, 9}) {
			s.set(0, 7, z, k.pane);
			s.set(8, 7, z, k.pane);
		}
		s.set(0, 2, 2, k.pane);
		s.set(8, 2, 3, k.pane);
		s.set(8, 2, 8, k.pane);
		s.door(4, 1, 0, Direction.NORTH, k.door);
		s.door(8, 1, 6, Direction.EAST, k.door);
		s.gable(0, 8, 0, 10, 9, k.roofStairs, k.roofSlab, k.infill);
		// the taproom
		for (int x = 1; x <= 4; x++) {
			s.set(x, 1, 7, Sketch.top(p.woodSlab()));
			s.set(x, 1, 9, Blocks.BARREL.defaultBlockState());
		}
		s.set(1, 2, 9, Blocks.BARREL.defaultBlockState());
		table(s, k, 2, 1, 3, false);
		table(s, k, 6, 1, 3, false);
		s.set(1, 1, 5, Sketch.facing(Blocks.SMOKER, Direction.EAST));
		chimney(s, k, 1, 5, 2, 11);
		for (int y = 1; y <= 5; y++) {
			s.set(7, y, 9, Sketch.facing(Blocks.LADDER, Direction.WEST));
		}
		s.set(4, 4, 5, Sketch.hanging(Blocks.LANTERN));
		// the rooms above
		for (int z = 1; z <= 9; z++) {
			s.set(4, 9, z, Sketch.lying(p.log(), Direction.Axis.Z));
		}
		s.set(4, 8, 5, Sketch.hanging(Blocks.LANTERN));
		s.bed(2, 6, 2, Direction.SOUTH, k.bed);
		s.bed(6, 6, 2, Direction.SOUTH, k.bed);
		s.bed(3, 6, 7, Direction.SOUTH, k.bed);
		// the garden
		table(s, k, 11, 1, 5, true);
		table(s, k, 11, 1, 8, true);
		for (int[] c : new int[][] {{14, 2}, {14, 6}, {14, 10}, {9, 10}}) {
			s.set(c[0], 2, c[1], p.fence());
			s.set(c[0], 3, c[1], Blocks.LANTERN.defaultBlockState());
		}
		s.set(13, 1, 9, Blocks.BARREL.defaultBlockState());
		s.set(13, 1, 3, k.flowerPot());
		return s.done();
	}

	/**
	 * A market square (9 x 9): paved, four stalls under awnings (each in its own colours and pattern in a varied
	 * market; striped in the realm's colour in the plain one), and in the middle a lamp, a basin or a lamp post.
	 */
	static Blueprint market(Kit k) {
		Sketch s = new Sketch("market", 9, 9, 4);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				s.set(x, 0, z, k.road);
			}
		}
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			BlockState main = k.classic ? k.wool : k.someWool();
			BlockState second = k.classic ? Kit.WHITE_WOOL.defaultBlockState() : k.accentWool;
			int pattern = k.pickInt(0, 1, 2, 3);
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					s.set(c[0] + dx, 3, c[1] + dz, awning(pattern, dx, dz, 3, 3) ? main : second);
					if ((dx == 0 || dx == 2) && (dz == 0 || dz == 2)) {
						s.set(c[0] + dx, 1, c[1] + dz, k.p.fence());
						s.set(c[0] + dx, 2, c[1] + dz, k.p.fence());
					}
				}
			}
			if ((c[0] + c[1]) % 12 == 0) {
				s.set(c[0] + 1, 1, c[1] + 1, Blocks.BARREL.defaultBlockState());
			} else if (k.classic) {
				s.set(c[0] + 1, 1, c[1] + 1, Sketch.top(k.p.woodSlab()));
			} else {
				k.display(s, c[0] + 1, c[1] + 1);
			}
		}
		switch (k.pickInt(0, 1, 2)) {
			case 1 -> {
				s.set(4, 0, 4, Blocks.WATER.defaultBlockState());
				for (int[] e : new int[][] {{3, 4}, {5, 4}, {4, 3}, {4, 5}}) {
					s.set(e[0], 1, e[1], k.p.slab());
				}
			}
			case 2 -> {
				s.set(4, 1, 4, k.wallTop);
				s.set(4, 2, 4, k.wallTop);
				s.set(4, 3, 4, Blocks.LANTERN.defaultBlockState());
			}
			default -> {
				s.set(4, 1, 4, k.p.trim());
				s.set(4, 2, 4, Blocks.LANTERN.defaultBlockState());
			}
		}
		return s.done();
	}

	/** Barracks (11 x 9): a stone hall with a parapet (or a hipped roof), four bunks along the back, a door and windows. */
	static Blueprint barracks(Kit k) {
		Sketch s = new Sketch("barracks", 11, 9, 12);
		Palette p = k.p;
		boolean parapet = k.classic || k.chance(0.5);
		boolean twoTone = k.chance(0.35);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
				boolean corner = (x == 0 || x == 10) && (z == 0 || z == 8);
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, corner ? p.trim() : twoTone && y == 3 ? p.planks() : Sketch.weathered(p, x, y, z, k.salt));
					}
					if (parapet) {
						s.set(x, 4, z, p.wall());
						if ((x + z) % 2 == 0) {
							s.set(x, 5, z, p.wall());
						}
					}
				} else if (parapet) {
					s.set(x, 4, z, p.slab());
				}
			}
		}
		s.door(5, 1, 0, Direction.NORTH, k.door);
		for (int[] w : new int[][] {{2, 0}, {8, 0}, {0, 4}, {10, 4}}) {
			s.set(w[0], 2, w[1], k.pane);
		}
		for (int x : new int[] {1, 3, 7, 9}) {
			s.bed(x, 1, 6, Direction.SOUTH, k.bed);
		}
		s.set(5, 1, 7, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(5, 3, 7, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(5, 3, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		if (!parapet) {
			s.hip(0, 10, 0, 8, 4, k.roofStairs, k.roofSlab);
		}
		if (k.chance(0.5)) {
			s.set(9, 1, 1, Sketch.facing(Blocks.CHEST, Direction.WEST));
			s.set(1, 1, 1, Blocks.BARREL.defaultBlockState());
		}
		// (0.5.8) weapons racked on the front wall
		BlockState rack = Displays.rack(Direction.SOUTH);
		if (rack != null && k.chance(0.8)) {
			s.set(3, 2, 1, rack);
			s.set(7, 2, 1, rack);
		}
		return s.done();
	}

	/** A section of the town wall (9 x 3): five courses of weathered stone, crenellations on the outer side. */
	static Blueprint wall(Kit k) {
		Sketch s = new Sketch("wall", 9, 3, 6);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 3; z++) {
				for (int y = 0; y <= 4; y++) {
					s.set(x, y, z, y == 0 ? k.p.wall() : Sketch.weathered(k.p, x, y, z, k.salt));
				}
			}
			if (x % 2 == 0) {
				s.set(x, 5, 2, k.p.wall());
			}
		}
		// now and then a lamp on the walk
		if (k.chance(0.3)) {
			s.set(k.pickInt(1, 3, 5, 7), 5, 0, Blocks.LANTERN.defaultBlockState());
		}
		return s.done();
	}

	/**
	 * A wall tower (7 x 7): ten courses, a door, arrow slits, a floor halfway, a ladder to the top: crenellated (the
	 * plain design), with lamps on its corners, or under a pointed roof.
	 */
	static Blueprint tower(Kit k) {
		Sketch s = new Sketch("tower", 7, 7, 16);
		Palette p = k.p;
		int crown = k.pickInt(0, 0, 1, 2);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				for (int y = 1; y <= 8; y++) {
					boolean slit = (y == 3 || y == 6) && ((x == 3 && z == 6) || (z == 3 && (x == 0 || x == 6)));
					if (edge && !slit) {
						s.set(x, y, z, Sketch.weathered(p, x, y, z, k.salt));
					}
				}
				if (!edge) {
					s.set(x, 5, z, p.planks());
				}
				s.set(x, 9, z, p.wall());
				if (edge && (x + z) % 2 == 0 && crown != 2) {
					s.set(x, 10, z, p.wall());
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		for (int y = 1; y <= 9; y++) {
			s.set(3, y, 5, Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		s.set(1, 3, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		s.set(1, 7, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		if (crown == 1) {
			for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
				s.set(c[0], 11, c[1], Blocks.LANTERN.defaultBlockState());
			}
		} else if (crown == 2) {
			s.hip(0, 6, 0, 6, 10, k.roofStairs, k.roofSlab);
		}
		return s.done();
	}

	/** A gatehouse (9 x 5): two stone piers, an arch three wide over the road, crenellations along the top, maybe lamps. */
	static Blueprint gatehouse(Kit k) {
		Sketch s = new Sketch("gatehouse", 9, 5, 9);
		Palette p = k.p;
		for (int x = 0; x < 9; x++) {
			boolean arch = x >= 3 && x <= 5;
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, arch ? k.road : p.wall());
				for (int y = 1; y <= 6; y++) {
					if (!arch || y >= 4) {
						s.set(x, y, z, Sketch.weathered(p, x, y, z, k.salt));
					}
				}
				if ((z == 0 || z == 4) && x % 2 == 0) {
					s.set(x, 7, z, p.wall());
				}
			}
		}
		for (int z : new int[] {0, 4}) {
			s.set(3, 3, z, Sketch.upsideDown(p.stairs(), Direction.WEST));
			s.set(5, 3, z, Sketch.upsideDown(p.stairs(), Direction.EAST));
		}
		if (k.chance(0.5)) {
			for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 4}, {8, 4}}) {
				s.set(c[0], 8, c[1], Blocks.LANTERN.defaultBlockState());
			}
		}
		return s.done();
	}

	/** A village palisade section (9 x 2): sharpened logs (two or three high) on a cobble footing. */
	static Blueprint palisade(Kit k) {
		Sketch s = new Sketch("palisade", 9, 2, 5);
		for (int x = 0; x < 9; x++) {
			s.set(x, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
			s.set(x, 0, 1, Blocks.COBBLESTONE.defaultBlockState());
			int high = k.classic ? x % 2 == 0 ? 3 : 2 : 2 + k.nextInt(2);
			for (int y = 1; y <= high; y++) {
				s.set(x, y, 1, k.p.log());
			}
			if (k.chance(0.25)) {
				s.set(x, high + 1, 1, k.p.fence());
			}
		}
		return s.done();
	}

	// ---- drawing helpers ------------------------------------------------------------------------------------------

	/**
	 * A faction's materials: its palette plus the wood's door, stairs, gate, and wool, bed and carpet in its colour; and
	 * (0.5.7) a building's own picks: its roofing, a second colour, a light infill for timber frames, its windows, and the
	 * chances it rolls for everything else. The plain kit ({@link #classic}) picks nothing: every choice is the first.
	 */
	static final class Kit {
		/** (coloured blocks are looked up by name: 26.3 keeps them in collections, not fields) */
		static final Block WHITE_WOOL = block("white_wool", Blocks.HAY_BLOCK);
		/** How strongly the land's own palette counts against what the settlement's buildings are made of. */
		static final double HOME_SHARE = 0.5;
		/** How often windows are stained glass in the building's colour. */
		static final double STAINED_WINDOWS = 0.18;
		private static final String[] FLOWERS = {"poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
			"oxeye_daisy", "cornflower", "lily_of_the_valley", "fern"};

		final Palette p;
		final boolean village;
		final Block door, gate;
		final BlockState woodStairs, wool, bed, carpet, road;
		/** (0.5.7) roofing (stairs, slab, the full block), a second colour, timber-frame infill, windows */
		final BlockState roofStairs, roofSlab, roofBlock, accentWool, infill, pane;
		/** (0.5.7) the stripped log, the stonework's wall (a fence if it has none) */
		final BlockState stripped, wallTop;
		/** (0.5.7) the plain design (variant 0): every choice is the first, nothing is rolled */
		final boolean classic;
		/** (0.5.7) the land is dry (flat roofs are common), and how this building's stone is weathered */
		final boolean dry;
		final int salt;
		private final List<String> colours;
		private final RandomSource r;

		/** The plain kit (variant 0), as before 0.5.7. */
		private Kit(Palette p, boolean village, String wood, String color) {
			this.p = p;
			this.village = village;
			this.door = block(wood + "_door", Blocks.OAK_DOOR);
			this.gate = block(wood + "_fence_gate", Blocks.OAK_FENCE_GATE);
			this.woodStairs = block(wood + "_stairs", Blocks.OAK_STAIRS).defaultBlockState();
			this.wool = block(color + "_wool", Kit.WHITE_WOOL).defaultBlockState();
			this.bed = block(color + "_bed", Kit.WHITE_WOOL).defaultBlockState();
			this.carpet = block(color + "_carpet", Kit.WHITE_WOOL).defaultBlockState();
			this.road = village ? Blocks.DIRT_PATH.defaultBlockState() : p.path();
			this.roofStairs = village ? this.woodStairs : p.stairs();
			this.roofSlab = p.woodSlab();
			this.roofBlock = p.planks();
			this.accentWool = WHITE_WOOL.defaultBlockState();
			this.infill = p.planks();
			this.pane = Blocks.GLASS_PANE.defaultBlockState();
			this.stripped = stripped(p.log());
			this.wallTop = wallOf(p.wall(), p.fence());
			this.classic = true;
			this.dry = false;
			this.salt = 0;
			this.colours = List.of(color);
			this.r = RandomSource.create(0L);
		}

		/** A building's own kit: its materials from {@code look} (and the land's palette), its details rolled from {@code r}. */
		private Kit(Palette p, boolean village, String wood, String color, String accent, BlockState roofStairs, BlockState roofSlab, BlockState roofBlock,
			BlockState infill, List<String> colours, boolean dry, RandomSource r) {
			this.p = p;
			this.village = village;
			this.door = block(wood + "_door", Blocks.OAK_DOOR);
			this.gate = block(wood + "_fence_gate", Blocks.OAK_FENCE_GATE);
			this.woodStairs = block(wood + "_stairs", Blocks.OAK_STAIRS).defaultBlockState();
			this.wool = block(color + "_wool", Kit.WHITE_WOOL).defaultBlockState();
			this.bed = block(color + "_bed", Kit.WHITE_WOOL).defaultBlockState();
			this.carpet = block(color + "_carpet", Kit.WHITE_WOOL).defaultBlockState();
			this.road = village ? Blocks.DIRT_PATH.defaultBlockState() : p.path();
			this.roofStairs = roofStairs;
			this.roofSlab = roofSlab;
			this.roofBlock = roofBlock;
			this.accentWool = block(accent + "_wool", Kit.WHITE_WOOL).defaultBlockState();
			this.infill = infill;
			this.stripped = stripped(p.log());
			this.wallTop = wallOf(p.wall(), p.fence());
			this.classic = false;
			this.dry = dry;
			this.colours = colours;
			this.r = r;
			this.salt = r.nextInt(11);
			this.pane = r.nextDouble() < STAINED_WINDOWS ? block(color + "_stained_glass_pane", Blocks.GLASS_PANE).defaultBlockState()
				: Blocks.GLASS_PANE.defaultBlockState();
		}

		/** The plain kit (variant 0). */
		static Kit of(ServerLevel level, Faction faction) {
			return of(level, faction, 0L, 0L);
		}

		/**
		 * A building's kit: its materials picked with {@code materials} (0: the plain kit) from the settlement's look and
		 * the land's palette, its details with {@code details}.
		 */
		static Kit of(ServerLevel level, Faction faction, long materials, long details) {
			Palette land = Palette.at(level, faction.seat());
			boolean village = faction.type() == FactionType.VILLAGE;
			String colour = faction.styleColor(level.getServer()).getName();
			if (materials == 0L) {
				return new Kit(land, village, woodOf(land), colour);
			}
			RandomSource m = RandomSource.create(materials);
			Townscape.Look look = Townscape.of(level, faction);
			// the wood: one the settlement builds in, or the land's own
			String wood = Townscape.pick(look.woods(), woodOf(land), HOME_SHARE, m);
			Block planks = Townscape.named(wood + "_planks");
			Block log = logOf(wood);
			if (planks == null || log == null) {
				wood = woodOf(land);
				planks = land.planks().getBlock();
				log = land.log().getBlock();
			}
			// the stonework: what its buildings are built of, or the land's own
			Block stone = Townscape.pick(look.masonry(), land.wall().getBlock(), HOME_SHARE, m);
			Palette p = new Palette(stone.defaultBlockState(), or(Townscape.named("mossy_" + name(stone)), land.mossy()),
				or(Townscape.named("cracked_" + name(stone)), stone == land.wall().getBlock() ? land.cracked() : stone.defaultBlockState()),
				m.nextDouble() < 0.35 ? planks.defaultBlockState() : land.floor(), land.trim(), or(shapeOf(stone, "stairs"), land.stairs()),
				or(shapeOf(stone, "slab"), land.slab()), land.fortified(), land.path(), log.defaultBlockState(), planks.defaultBlockState(),
				or(Townscape.named(wood + "_slab"), land.woodSlab()), or(Townscape.named(wood + "_fence"), land.fence()),
				or(Townscape.named(wood + "_trapdoor"), land.trapdoor()));
			// the roofing: what the settlement's roofs are made of, or the land's way (wood in a village, stone in a realm)
			String home = village ? wood : name(land.stairs().getBlock()).replace("_stairs", "");
			String roof = Townscape.pick(look.roofs(), home, HOME_SHARE, m);
			Block roofStairs = Townscape.named(roof + "_stairs"), roofSlab = Townscape.named(roof + "_slab"), roofBlock = fullOf(roof);
			if (roofStairs == null || roofSlab == null || roofBlock == null) {
				roofStairs = village ? block(wood + "_stairs", Blocks.OAK_STAIRS) : land.stairs().getBlock();
				roofSlab = p.woodSlab().getBlock();
				roofBlock = planks;
			}
			// its colours: the realm's own or one of the settlement's, and a second for stripes
			List<String> colours = new ArrayList<>(look.colours().keySet());
			if (!colours.contains(colour)) {
				colours.add(colour);
			}
			String main = m.nextDouble() < 0.5 ? colour : Townscape.pick(look.colours(), colour, HOME_SHARE, m);
			String accent = Townscape.pick(look.colours(), "white", 0.6, m);
			if (accent.equals(main)) {
				accent = "white";
			}
			// a timber frame's infill: a light block the settlement uses, or plaster-like white, or planks
			BlockState infill = planks.defaultBlockState();
			for (Block b : look.masonry().keySet()) {
				String n = name(b);
				if (n.contains("white") || n.contains("calcite") || n.contains("smooth") || n.contains("mud")) {
					infill = b.defaultBlockState();
					break;
				}
			}
			if (infill.is(planks) && m.nextDouble() < 0.3) {
				infill = or(Townscape.named("white_terracotta"), infill);
			}
			boolean dry = land == Palette.DESERT || land == Palette.BADLANDS || land == Palette.SAVANNA;
			return new Kit(p, village, wood, main, accent, roofStairs.defaultBlockState(), roofSlab.defaultBlockState(), roofBlock.defaultBlockState(),
				infill, colours, dry, RandomSource.create(details ^ 0x5DEECE66DL));
		}

		// ---- rolling choices (the plain kit always takes the first) ----

		boolean chance(double p) {
			return !this.classic && this.r.nextDouble() < p;
		}

		int pickInt(int... options) {
			return this.classic ? options[0] : options[this.r.nextInt(options.length)];
		}

		@SafeVarargs
		final <T> T pick(T... options) {
			return this.classic ? options[0] : options[this.r.nextInt(options.length)];
		}

		int nextInt(int bound) {
			return this.classic ? 0 : this.r.nextInt(bound);
		}

		/** A torch, or (in a varied building) sometimes a lantern. */
		/** A pressure plate of the land's wood (a table top). */
		BlockState plate() {
			return or(Townscape.named(woodOf(this.p) + "_pressure_plate"), Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
		}

		BlockState light() {
			return this.chance(0.5) ? Blocks.LANTERN.defaultBlockState() : Blocks.TORCH.defaultBlockState();
		}

		/** Wool in one of the settlement's colours. */
		BlockState someWool() {
			return block(this.colours.get(this.r.nextInt(this.colours.size())) + "_wool", WHITE_WOOL).defaultBlockState();
		}

		/** A flower in a pot. */
		BlockState flowerPot() {
			return block("potted_" + FLOWERS[this.r.nextInt(FLOWERS.length)], Blocks.FLOWER_POT).defaultBlockState();
		}

		/** A stall's wares (with Cooking for Blockheads, often a basket of produce). */
		BlockState wares() {
			BlockState basket = Displays.basket(Direction.NORTH);
			BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();
			return this.pick(hay, Blocks.MELON.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(), Sketch.facing(Blocks.CHEST, Direction.NORTH),
				basket != null ? basket : hay, basket != null ? basket : hay);
		}

		/** A stall's wares at x, z: set on the ground, or a basket of produce up on a counter (a top slab). */
		void display(Sketch s, int x, int z) {
			BlockState wares = this.wares();
			if (!this.classic && wares.getBlock() != Blocks.HAY_BLOCK && Displays.isBasket(wares)) {
				s.set(x, 1, z, Sketch.top(this.p.woodSlab()));
				s.set(x, 2, z, wares);
			} else {
				s.set(x, 1, z, wares);
			}
		}

		/** Two or three of the field crops, in an order of their own. */
		Block[] crops() {
			List<Block> all = new ArrayList<>(List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS));
			Block[] out = new Block[2 + this.r.nextInt(2)];
			for (int i = 0; i < out.length; i++) {
				out[i] = all.remove(this.r.nextInt(all.size()));
			}
			return out;
		}

		// ---- materials by name ----

		private static String name(Block b) {
			return BuiltInRegistries.BLOCK.getKey(b).getPath();
		}

		private static String woodOf(Palette p) {
			String planks = name(p.planks().getBlock());
			return planks.endsWith("_planks") ? planks.substring(0, planks.length() - "_planks".length()) : "oak";
		}

		private static @Nullable Block logOf(String wood) {
			for (String n : new String[] {wood + "_log", wood + "_stem", wood + "_block"}) {
				Block b = Townscape.named(n);
				if (b != null) {
					return b;
				}
			}
			return null;
		}

		private static BlockState stripped(BlockState log) {
			Block b = Townscape.named("stripped_" + name(log.getBlock()));
			return b == null ? log : b.defaultBlockState();
		}

		/** A stone block's stairs, slab or wall ("stone_bricks" → "stone_brick_stairs", "cobblestone" → "cobblestone_slab"). */
		private static @Nullable Block shapeOf(Block full, String shape) {
			String n = name(full);
			String stem = n.endsWith("bricks") || n.endsWith("tiles") ? n.substring(0, n.length() - 1) : n.endsWith("_block") ? n.substring(0, n.length() - 6) : n;
			Block b = Townscape.named(stem + "_" + shape);
			return b != null ? b : Townscape.named(n + "_" + shape);
		}

		private static BlockState wallOf(BlockState stone, BlockState fallback) {
			Block wall = shapeOf(stone.getBlock(), "wall");
			return wall == null ? fallback : wall.defaultBlockState();
		}

		/** The full block of a roofing material named by its stairs ("stone_brick" → stone bricks, "oak" → oak planks). */
		private static @Nullable Block fullOf(String material) {
			for (String n : new String[] {material + "_planks", material + "s", material, material + "_block"}) {
				Block b = Townscape.named(n);
				if (b != null && !(b instanceof StairBlock) && !(b instanceof SlabBlock)) {
					return b;
				}
			}
			return null;
		}

		private static BlockState or(@Nullable Block b, BlockState fallback) {
			return b == null ? fallback : b.defaultBlockState();
		}

		private static Block block(String name, Block fallback) {
			Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
			return b == Blocks.AIR ? fallback : b;
		}

		/** A wooden sign of this wood (for the foreman's stakes). */
		static Block sign(Palette p) {
			return block(woodOf(p) + "_sign", Blocks.OAK_SIGN);
		}
	}

	/** A blueprint being drawn: later blocks at the same spot replace earlier ones; laid bottom up. */
	static final class Sketch {
		private final String id;
		private final int w, d, h;
		private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		/** {@code h} is how high it may reach; the blueprint's height is what it really reaches. */
		Sketch(String id, int w, int d, int h) {
			this.id = id;
			this.w = w;
			this.d = d;
			this.h = h;
		}

		void set(int x, int y, int z, BlockState state) {
			if (x < 0 || x >= this.w || z < 0 || z >= this.d || y < 0 || y >= this.h) {
				return;
			}
			BlockPos at = new BlockPos(x, y, z);
			this.blocks.remove(at);
			this.blocks.put(at, state);
		}

		void door(int x, int y, int z, Direction facing, Block door) {
			BlockState lower = door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			this.set(x, y, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			this.set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}

		/** A bed: its foot here, its head toward {@code facing}. */
		void bed(int x, int y, int z, Direction facing, BlockState bed) {
			if (!bed.hasProperty(BlockStateProperties.BED_PART)) {
				return;
			}
			BlockState foot = bed.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			this.set(x, y, z, foot.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			this.set(x + facing.getStepX(), y, z + facing.getStepZ(), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}

		/** A gabled roof over x0..x1 (its ridge running along z0..z1), from {@code y}; the gable ends filled. */
		void gable(int x0, int x1, int z0, int z1, int y, BlockState stairs, BlockState ridge, BlockState fill) {
			int half = (x1 - x0) / 2;
			for (int k = 0; k <= half; k++) {
				for (int z = z0; z <= z1; z++) {
					if (x0 + k == x1 - k) {
						this.set(x0 + k, y + k, z, ridge);
					} else {
						this.set(x0 + k, y + k, z, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
						this.set(x1 - k, y + k, z, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
					}
				}
				for (int x = x0 + k + 1; x <= x1 - k - 1; x++) {
					this.set(x, y + k, z0, fill);
					this.set(x, y + k, z1, fill);
				}
			}
		}

		/** (0.5.7) A gabled roof over z0..z1 (its ridge running along x0..x1), from {@code y}; the gable ends filled. */
		void gableX(int x0, int x1, int z0, int z1, int y, BlockState stairs, BlockState ridge, BlockState fill) {
			int half = (z1 - z0) / 2;
			for (int k = 0; k <= half; k++) {
				for (int x = x0; x <= x1; x++) {
					if (z0 + k == z1 - k) {
						this.set(x, y + k, z0 + k, ridge);
					} else {
						this.set(x, y + k, z0 + k, stair(stairs, Direction.SOUTH));
						this.set(x, y + k, z1 - k, stair(stairs, Direction.NORTH));
					}
				}
				for (int z = z0 + k + 1; z <= z1 - k - 1; z++) {
					this.set(x0, y + k, z, fill);
					this.set(x1, y + k, z, fill);
				}
			}
		}

		/**
		 * (0.5.7) A hipped roof over x0..x1, z0..z1 from {@code y}: rings of stairs rising inward to a ridge of slabs. Each
		 * ring's corners are laid first, so the stairs beside them turn them into outer corners.
		 */
		void hip(int x0, int x1, int z0, int z1, int y, BlockState stairs, BlockState ridge) {
			for (int k = 0; ; k++) {
				int ax0 = x0 + k, ax1 = x1 - k, az0 = z0 + k, az1 = z1 - k;
				if (ax0 > ax1 || az0 > az1) {
					return;
				}
				if (ax0 == ax1 || az0 == az1) {
					for (int x = ax0; x <= ax1; x++) {
						for (int z = az0; z <= az1; z++) {
							this.set(x, y + k, z, ridge);
						}
					}
					return;
				}
				this.set(ax0, y + k, az0, stair(stairs, Direction.SOUTH));
				this.set(ax1, y + k, az0, stair(stairs, Direction.SOUTH));
				this.set(ax0, y + k, az1, stair(stairs, Direction.NORTH));
				this.set(ax1, y + k, az1, stair(stairs, Direction.NORTH));
				for (int x = ax0 + 1; x < ax1; x++) {
					this.set(x, y + k, az0, stair(stairs, Direction.SOUTH));
					this.set(x, y + k, az1, stair(stairs, Direction.NORTH));
				}
				for (int z = az0 + 1; z < az1; z++) {
					this.set(ax0, y + k, z, stair(stairs, Direction.EAST));
					this.set(ax1, y + k, z, stair(stairs, Direction.WEST));
				}
			}
		}

		/** (0.5.7) A flat roof over x0..x1, z0..z1 at {@code y}: slabs inside a parapet, crenellated. */
		void flat(int x0, int x1, int z0, int z1, int y, BlockState parapet, BlockState slab) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					this.set(x, y, z, edge ? parapet : slab);
					if (edge && (x + z) % 2 == 0) {
						this.set(x, y + 1, z, parapet);
					}
				}
			}
		}

		static BlockState facing(Block block, Direction facing) {
			BlockState state = block.defaultBlockState();
			return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
		}

		/** Stairs rising toward {@code facing}. */
		static BlockState stair(BlockState stairs, Direction facing) {
			return stairs.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : stairs;
		}

		/** A log laid on its side along {@code axis}. */
		static BlockState lying(BlockState log, Direction.Axis axis) {
			return log.hasProperty(BlockStateProperties.AXIS) ? log.setValue(BlockStateProperties.AXIS, axis) : log;
		}

		/** A lantern (or anything that hangs) hanging from the block above. */
		static BlockState hanging(Block block) {
			BlockState state = block.defaultBlockState();
			return state.hasProperty(BlockStateProperties.HANGING) ? state.setValue(BlockStateProperties.HANGING, true) : state;
		}

		static BlockState top(BlockState slab) {
			return slab.hasProperty(BlockStateProperties.SLAB_TYPE) ? slab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP) : slab;
		}

		static BlockState upsideDown(BlockState stairs, Direction facing) {
			if (!stairs.hasProperty(BlockStateProperties.HALF)) {
				return stairs;
			}
			return stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, Half.TOP);
		}

		/** Mostly the palette's wall, here and there mossy or cracked (the same every time for the same spot). */
		static BlockState weathered(Palette p, int x, int y, int z) {
			return weathered(p, x, y, z, 0);
		}

		/** (0.5.7) The same, its mossy and cracked stones placed by {@code salt} (each building its own). */
		static BlockState weathered(Palette p, int x, int y, int z, int salt) {
			int n = Math.floorMod(x * 31 + y * 17 + z * 7 + salt, 11);
			return n == 0 ? p.mossy() : n == 5 ? p.cracked() : p.wall();
		}

		Blueprint done() {
			List<Blueprint.Placement> out = new ArrayList<>();
			int high = 0;
			for (Map.Entry<BlockPos, BlockState> e : this.blocks.entrySet()) {
				out.add(new Blueprint.Placement(e.getKey(), e.getValue()));
				high = Math.max(high, e.getKey().getY() + 1);
			}
			out.sort(Comparator.comparingInt(pl -> pl.offset().getY()));
			return new Blueprint(this.id, this.w, this.d, high, List.copyOf(out));
		}
	}
}
