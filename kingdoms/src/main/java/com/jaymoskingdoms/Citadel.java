package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.server.level.ServerLevel;

/**
 * The citadel (0.5.1): the seat a kingdom builds when its village has no building fit for a king, 61 blocks square in
 * the village's own materials ({@link Palette}), drawn in {@link Castle}'s frame (x across, z inward from the gate, y
 * from the floor):
 * <ul>
 * <li>a <b>curtain wall</b> three thick and ten high, a wall walk along its top behind battlements, reached from the
 * courtyard by a flight of steps inside each side wall;</li>
 * <li><b>towers</b> at the four corners, half way along each side and at the back: hollow, three floors (a lower room,
 * the wall walk's level, the roof behind battlements) joined by a ladder, entered from the wall walk;</li>
 * <li>the <b>gatehouse</b>: two towers either side of a five-wide gate under a raised portcullis, a guard room over the
 * gate passage, reached from the towers' lower rooms;</li>
 * <li>the <b>courtyard</b>: a road from the gate to the keep, barracks, a smithy, a well, a training yard and a stable;</li>
 * <li>the <b>keep</b> at the back, 25 by 23 and three storeys under a battlemented roof with a turret at each corner,
 * joined by stairs inside: the throne room (the panic room beneath it), the king's chamber, the armoury.</li>
 * </ul>
 * Archers keep the corner towers, the gatehouse and the keep's roof.
 */
final class Citadel {
	/** Its half-width (61 across) and depth (61 deep); the curtain's height and thickness. */
	static final int HALF = 30, DEPTH = 60, CURTAIN = 10, THICK = 3;
	/** Looks for a site this far from the bell (clear of the village). */
	static final int SITE_MIN = 52, SITE_MAX = 100;
	/** How high the ground is cleared above the floor. */
	static final int CLEAR = 26;
	/** Towers: their half-width (7 across), height, their lower floor; the gatehouse towers' x, the gate's half-width and height. */
	static final int TOWER_HALF = 3, TOWER_HIGH = 16, TOWER_LOWER = 5, GATE_TOWER_X = 6, GATE_HALF = 2, GATE_HIGH = 5;
	/** The keep: its half-width, its front and back (z), its storeys and their height. */
	static final int KEEP_HALF = 12, KEEP_FRONT = DEPTH - 28, KEEP_BACK = DEPTH - 6, STOREY = 6, STOREYS = 3;
	/** The keep's stairs: inside its right wall (x), from this far behind its front wall. */
	static final int STAIR_X = KEEP_HALF - 3, STAIR_Z = 3;
	/** The steps up to the wall walk: from this z, inside each side wall. */
	static final int STEPS_Z = 8;

	private Citadel() {
	}

	static Castle.Keep build(ServerLevel level, Castle.Frame f, Palette p, Faction kingdom, RandomSource random) {
		// (the towers stand out three past the walls)
		Castle.prepare(level, f, p, -HALF - TOWER_HALF - 1, HALF + TOWER_HALF + 1, -TOWER_HALF - 1, DEPTH + TOWER_HALF + 1, CLEAR);
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				f.set(level, x, -1, z, ring(x, z) ? p.wall() : grass);
			}
		}
		curtain(level, f, p, random);
		List<int[]> towers = new ArrayList<>(List.of(new int[] {-HALF, 0}, new int[] {HALF, 0}, new int[] {-HALF, DEPTH}, new int[] {HALF, DEPTH},
			new int[] {-HALF, DEPTH / 2}, new int[] {HALF, DEPTH / 2}, new int[] {0, DEPTH}, new int[] {-GATE_TOWER_X, 0}, new int[] {GATE_TOWER_X, 0}));
		for (int[] t : towers) {
			tower(level, f, p, random, t[0], t[1], t[1] == 0 && Math.abs(t[0]) == GATE_TOWER_X ? TOWER_HIGH - 1 : TOWER_HIGH);
		}
		// doorways from the wall walk into every tower it meets
		for (int[] t : towers) {
			doorways(level, f, t[0], t[1], towers);
		}
		gatehouse(level, f, p);
		steps(level, f, p, -1);
		steps(level, f, p, 1);
		courtyard(level, f, p);
		BlockPos[] keep = keep(level, f, p, kingdom, random);
		List<BlockPos> posts = new ArrayList<>();
		for (int[] t : new int[][] {{-HALF, 0}, {HALF, 0}, {-HALF, DEPTH}, {HALF, DEPTH}}) {
			posts.add(f.at(t[0], TOWER_HIGH + 1, t[1]));
		}
		posts.add(f.at(-GATE_TOWER_X, TOWER_HIGH, 0));
		posts.add(f.at(GATE_TOWER_X, TOWER_HIGH, 0));
		posts.add(f.at(-KEEP_HALF + 2, STOREY * STOREYS + 1, KEEP_FRONT + 2));
		posts.add(f.at(KEEP_HALF - 2 - 3, STOREY * STOREYS + 1, KEEP_BACK - 2));
		return new Castle.Keep(keep[0], keep[1], f.at(0, 0, -2), f.at(0, 0, KEEP_FRONT + 6), List.copyOf(posts));
	}

	/** In the curtain wall's thickness. */
	static boolean ring(int x, int z) {
		return Math.abs(x) > HALF - THICK || z < THICK || z > DEPTH - THICK;
	}

	/** Inside the walls of one of {@code towers}. */
	private static boolean inTower(int x, int z, List<int[]> towers) {
		for (int[] t : towers) {
			if (Math.abs(x - t[0]) <= TOWER_HALF && Math.abs(z - t[1]) <= TOWER_HALF) {
				return true;
			}
		}
		return false;
	}

	/** The curtain: three thick, ten high, its top the wall walk, battlements along its outer edge. */
	private static void curtain(ServerLevel level, Castle.Frame f, Palette p, RandomSource random) {
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = 0; z <= DEPTH; z++) {
				if (!ring(x, z)) {
					continue;
				}
				for (int y = 0; y < CURTAIN; y++) {
					f.set(level, x, y, z, Castle.weathered(p, random));
				}
				f.set(level, x, CURTAIN, z, p.wall());
				boolean outer = Math.abs(x) == HALF || z == 0 || z == DEPTH;
				if (outer && (x + z) % 2 == 0) {
					f.set(level, x, CURTAIN + 1, z, p.wall());
				}
			}
		}
		// lanterns along the walk's inner edge
		for (int i = -HALF + 8; i <= HALF - 8; i += 8) {
			f.set(level, i, CURTAIN + 1, THICK - 1, Blocks.LANTERN.defaultBlockState());
			f.set(level, i, CURTAIN + 1, DEPTH - THICK + 1, Blocks.LANTERN.defaultBlockState());
		}
	}

	/** A hollow tower centred at ({@code cx}, {@code cz}), {@code high} high: a lower room, a floor at the wall walk, the roof. */
	private static void tower(ServerLevel level, Castle.Frame f, Palette p, RandomSource random, int cx, int cz, int high) {
		int h = TOWER_HALF;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
				f.set(level, cx + dx, -1, cz + dz, p.floor());
				for (int y = 0; y <= high; y++) {
					boolean floor = y == TOWER_LOWER || y == CURTAIN || y == high;
					f.set(level, cx + dx, y, cz + dz, edge ? (y == high ? p.trim() : Castle.weathered(p, random)) : floor ? p.planks() : Blocks.AIR.defaultBlockState());
				}
				if (edge && (dx + dz) % 2 == 0) {
					f.set(level, cx + dx, high + 1, cz + dz, p.wall());
				} else {
					f.set(level, cx + dx, high + 1, cz + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		if (high == TOWER_HIGH || high == TOWER_HIGH - 1) {
			f.set(level, cx, high, cz, p.wall());
		}
		// arrow slits in each face, on the lower room and above the walk
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int y : new int[] {TOWER_LOWER + 2, CURTAIN + 3}) {
				f.set(level, cx + d.getStepX() * h, y, cz + d.getStepZ() * h, Blocks.AIR.defaultBlockState());
			}
		}
		// the ladder up the inside of its back face, through every floor; a trapdoor onto the roof
		BlockState ladder = f.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		for (int y = 0; y < high; y++) {
			f.set(level, cx, y, cz + h - 1, ladder);
		}
		f.set(level, cx, high, cz + h - 1, f.turn(p.trapdoor().setValue(TrapDoorBlock.HALF, Half.TOP)));
		f.set(level, cx - 1, TOWER_LOWER + 3, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		f.set(level, cx - 1, CURTAIN - 1, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** Openings, two high, wherever the wall walk meets a face of the tower at ({@code cx}, {@code cz}). */
	private static void doorways(ServerLevel level, Castle.Frame f, int cx, int cz, List<int[]> towers) {
		int h = TOWER_HALF;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				if (Math.abs(dx) != h && Math.abs(dz) != h || Math.abs(dx) == h && Math.abs(dz) == h) {
					continue;
				}
				int ox = cx + dx + Integer.signum(dx) * (Math.abs(dx) == h ? 1 : 0), oz = cz + dz + Integer.signum(dz) * (Math.abs(dz) == h ? 1 : 0);
				if (Math.abs(ox) <= HALF && oz >= 0 && oz <= DEPTH && ring(ox, oz) && !inTower(ox, oz, towers)) {
					f.set(level, cx + dx, CURTAIN + 1, cz + dz, Blocks.AIR.defaultBlockState());
					f.set(level, cx + dx, CURTAIN + 2, cz + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** The gate between the gatehouse towers: the passage, the raised portcullis, the guard room over it. */
	private static void gatehouse(ServerLevel level, Castle.Frame f, Palette p) {
		int x0 = -GATE_TOWER_X + TOWER_HALF + 1, x1 = GATE_TOWER_X - TOWER_HALF - 1;
		for (int x = x0; x <= x1; x++) {
			for (int z = 0; z < THICK; z++) {
				f.set(level, x, -1, z, p.path());
				for (int y = 0; y < GATE_HIGH; y++) {
					f.set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
				f.set(level, x, GATE_HIGH, z, p.wall());
				for (int y = GATE_HIGH + 1; y < CURTAIN; y++) {
					boolean wall = z == 0 || z == THICK - 1;
					f.set(level, x, y, z, wall && !(y == GATE_HIGH + 2 && x % 2 == 0) ? p.wall() : Blocks.AIR.defaultBlockState());
				}
			}
			// the portcullis's teeth under the arch, raised
			f.set(level, x, GATE_HIGH - 1, 1, Blocks.IRON_BARS.defaultBlockState());
		}
		f.set(level, x0 - 1, GATE_HIGH + 1, 0, p.trim());
		f.set(level, x1 + 1, GATE_HIGH + 1, 0, p.trim());
		// into the guard room from each tower's lower room
		for (int x : new int[] {x0 - 1, x1 + 1}) {
			f.set(level, x, TOWER_LOWER + 1, 1, Blocks.AIR.defaultBlockState());
			f.set(level, x, TOWER_LOWER + 2, 1, Blocks.AIR.defaultBlockState());
		}
		f.set(level, 0, CURTAIN - 1, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** A flight of steps up to the wall walk inside the side wall ({@code side}: -1 left, 1 right), two wide, a landing at the top. */
	private static void steps(ServerLevel level, Castle.Frame f, Palette p, int side) {
		int inner = side * (HALF - THICK);
		BlockState stair = f.turn(p.stairs().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int w = 1; w <= 2; w++) {
			int x = inner - side * (w - 1);
			for (int k = 0; k < CURTAIN; k++) {
				for (int y = 0; y < k; y++) {
					f.set(level, x, y, STEPS_Z + k, p.wall());
				}
				f.set(level, x, k, STEPS_Z + k, stair);
			}
			for (int z = STEPS_Z + CURTAIN; z <= STEPS_Z + CURTAIN + 1; z++) {
				for (int y = 0; y <= CURTAIN; y++) {
					f.set(level, x, y, z, p.wall());
				}
			}
		}
	}

	/** The road to the keep, the barracks, the smithy, the well, the training yard, the stable. */
	private static void courtyard(ServerLevel level, Castle.Frame f, Palette p) {
		for (int z = THICK; z < KEEP_FRONT; z++) {
			for (int x = -2; x <= 2; x++) {
				f.set(level, x, -1, z, p.path());
			}
		}
		for (int x = -HALF + THICK; x <= HALF - THICK; x++) {
			for (int z = KEEP_FRONT - 3; z < KEEP_FRONT; z++) {
				f.set(level, x, -1, z, p.path());
			}
		}
		// barracks along the left wall, behind the steps
		int yard = STEPS_Z + CURTAIN + 3, yardEnd = DEPTH / 2 - TOWER_HALF - 2;
		Castle.barracks(level, f, p, -HALF + THICK, -HALF + THICK + 4, yard, yardEnd);
		// the smithy: an anvil, a furnace, a grindstone, a smithing table under a lean-to
		int sx = 8, sz = 6;
		for (int x = sx; x <= sx + 5; x++) {
			for (int z = sz; z <= sz + 3; z++) {
				f.set(level, x, -1, z, p.floor());
				f.set(level, x, 3, z, p.woodSlab());
			}
		}
		for (int[] post : new int[][] {{sx, sz}, {sx + 5, sz}, {sx, sz + 3}, {sx + 5, sz + 3}}) {
			for (int y = 0; y < 3; y++) {
				f.set(level, post[0], y, post[1], p.log());
			}
		}
		f.set(level, sx + 1, 0, sz + 3, Blocks.BLAST_FURNACE.defaultBlockState());
		f.set(level, sx + 2, 0, sz + 3, Blocks.ANVIL.defaultBlockState());
		f.set(level, sx + 3, 0, sz + 3, Blocks.SMITHING_TABLE.defaultBlockState());
		f.set(level, sx + 4, 0, sz + 3, Blocks.GRINDSTONE.defaultBlockState());
		f.set(level, sx + 4, 0, sz + 1, Blocks.BARREL.defaultBlockState());
		// the well
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				f.set(level, -10 + dx, -2, 14 + dz, p.wall());
				f.set(level, -10 + dx, -1, 14 + dz, dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : p.wall());
				f.set(level, -10 + dx, 0, 14 + dz, dx == 0 && dz == 0 ? Blocks.AIR.defaultBlockState() : p.slab());
			}
		}
		for (int dx : new int[] {-1, 1}) {
			f.set(level, -10 + dx, 1, 14, p.fence());
			f.set(level, -10 + dx, 2, 14, p.fence());
		}
		for (int dx = -1; dx <= 1; dx++) {
			f.set(level, -10 + dx, 3, 14, p.woodSlab());
		}
		// the training yard: dummies of hay on posts, and targets
		for (int z = 11; z <= 17; z += 3) {
			f.set(level, 18, 0, z, p.fence());
			f.set(level, 18, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
			f.set(level, 18, 2, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
			f.set(level, 23, 0, z, p.fence());
			f.set(level, 23, 1, z, Blocks.TARGET.defaultBlockState());
		}
		// the stable: stalls along the right wall, hay in the mangers
		int stX = HALF - THICK - 4;
		for (int z = yard; z <= yardEnd; z++) {
			f.set(level, stX, 0, z, z == yard || z == yardEnd ? p.log() : p.fence());
			for (int x = stX; x <= HALF - THICK; x++) {
				f.set(level, x, 3, z, p.woodSlab());
			}
			f.set(level, HALF - THICK, 0, z, Blocks.HAY_BLOCK.defaultBlockState());
		}
		for (int y = 0; y < 3; y++) {
			f.set(level, stX, y, yard, p.log());
			f.set(level, stX, y, yardEnd, p.log());
		}
		f.set(level, stX, 0, (yard + yardEnd) / 2, f.turn(Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST)));
	}

	/**
	 * The keep: three storeys and a battlemented roof, a turret at each corner, stairs up the inside of its right wall.
	 * Returns where the king stands and where the panic room is.
	 */
	private static BlockPos[] keep(ServerLevel level, Castle.Frame f, Palette p, Faction kingdom, RandomSource random) {
		int h = KEEP_HALF, z0 = KEEP_FRONT, z1 = KEEP_BACK, top = STOREY * STOREYS;
		for (int x = -h; x <= h; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = Math.abs(x) == h || z == z0 || z == z1;
				f.set(level, x, -1, z, edge ? p.wall() : p.floor());
				for (int y = 0; y <= top; y++) {
					boolean floor = y % STOREY == 0 && y > 0;
					f.set(level, x, y, z, edge ? Castle.weathered(p, random) : floor ? (y == top ? p.wall() : p.planks()) : Blocks.AIR.defaultBlockState());
				}
				if (edge && (x + z) % 2 == 0) {
					f.set(level, x, top + 1, z, p.wall());
				}
			}
		}
		for (int cx : new int[] {-h, h}) {
			for (int cz : new int[] {z0, z1}) {
				Castle.column(level, f, p.trim(), cx, cz, top + 4, true);
			}
		}
		// the door: an arch in the middle of its front, four high
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 3; y++) {
				f.set(level, x, y, z0, Blocks.AIR.defaultBlockState());
			}
		}
		f.set(level, -2, 4, z0, p.trim());
		f.set(level, 2, 4, z0, p.trim());
		// windows (iron bars) on every storey
		for (int s = 0; s < STOREYS; s++) {
			int y = s * STOREY + 2;
			for (int z = z0 + 3; z <= z1 - 3; z += 4) {
				for (int x : new int[] {-h, h}) {
					f.set(level, x, y, z, Blocks.IRON_BARS.defaultBlockState());
					f.set(level, x, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
				}
			}
			for (int x = -h + 3; x <= h - 3; x += 4) {
				if (Math.abs(x) > 2 || s > 0) {
					f.set(level, x, y + 1, z0, Blocks.IRON_BARS.defaultBlockState());
				}
				f.set(level, x, y + 1, z1, Blocks.IRON_BARS.defaultBlockState());
			}
			f.set(level, 0, (s + 1) * STOREY - 1, (z0 + z1) / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			f.set(level, -6, (s + 1) * STOREY - 1, (z0 + z1) / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			f.set(level, 6, (s + 1) * STOREY - 1, (z0 + z1) / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// the stairs: a straight flight up the inside of the right wall on every storey, each flight over the last
		BlockState stair = f.turn(p.stairs().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int s = 0; s < STOREYS; s++) {
			for (int k = 0; k < STOREY; k++) {
				for (int x = STAIR_X; x <= STAIR_X + 1; x++) {
					f.set(level, x, s * STOREY + k, z0 + STAIR_Z + k, stair);
				}
			}
			// headroom where the flight comes up through the floor above
			for (int k = STOREY - 2; k < STOREY; k++) {
				for (int x = STAIR_X; x <= STAIR_X + 1; x++) {
					f.set(level, x, (s + 1) * STOREY, z0 + STAIR_Z + k, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// a rail round the hatch on the roof
		for (int k = STOREY - 2; k < STOREY; k++) {
			f.set(level, STAIR_X - 1, top + 1, z0 + STAIR_Z + k, p.fence());
		}
		Castle.torches(level, f, -h + 1, h - 1, z0 + 2, z1 - 1, 2);
		// the ground floor: the throne room, the panic room under it
		BlockPos king = Castle.throneRoom(level, f, p, kingdom, -3, 3, z0 + 1, z1 - 1, 0);
		BlockPos cellar = Castle.panicRoom(level, f, p, random, 2, z1 - 1, 0, z1 - 4);
		// the first floor: the king's chamber at the back, bunks for the household guard at the front
		int y1 = STOREY + 1;
		BlockState royalBed = Castle.byName(kingdom.color().getName() + "_bed", Blocks.AIR);
		if (royalBed.hasProperty(BedBlock.PART)) {
			f.set(level, -1, y1, z1 - 1, f.turn(royalBed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD)));
			f.set(level, -1, y1, z1 - 2, f.turn(royalBed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT)));
			f.set(level, 0, y1, z1 - 1, f.turn(royalBed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD)));
			f.set(level, 0, y1, z1 - 2, f.turn(royalBed.setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT)));
		}
		for (int x = -h + 1; x <= -h + 4; x++) {
			f.set(level, x, y1, z1 - 1, Blocks.BOOKSHELF.defaultBlockState());
			f.set(level, x, y1 + 1, z1 - 1, Blocks.BOOKSHELF.defaultBlockState());
		}
		f.set(level, 3, y1, z1 - 1, Blocks.CHEST.defaultBlockState());
		f.set(level, -3, y1, z1 - 4, Blocks.LECTERN.defaultBlockState());
		BlockState carpet = Castle.byName(kingdom.color().getName() + "_carpet", Blocks.AIR);
		for (int x = -3; x <= 3; x++) {
			for (int z = z1 - 6; z <= z1 - 3; z++) {
				f.set(level, x, y1, z, carpet);
			}
		}
		BlockState bunk = Castle.byName("red_bed", Blocks.AIR);
		for (int x = -h + 1; x <= -h + 7; x += 3) {
			f.set(level, x, y1, z0 + 1, f.turn(bunk.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD)));
			f.set(level, x, y1, z0 + 2, f.turn(bunk.setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT)));
		}
		// the second floor: the armoury
		int y2 = STOREY * 2 + 1;
		for (int x = -h + 1; x <= h - 4; x += 2) {
			f.set(level, x, y2, z1 - 1, Blocks.BARREL.defaultBlockState());
		}
		f.set(level, -h + 1, y2, z0 + 2, Blocks.ANVIL.defaultBlockState());
		f.set(level, -h + 1, y2, z0 + 4, Blocks.GRINDSTONE.defaultBlockState());
		f.set(level, -h + 1, y2, z0 + 6, Blocks.SMITHING_TABLE.defaultBlockState());
		f.set(level, -h + 1, y2, z0 + 8, Blocks.FLETCHING_TABLE.defaultBlockState());
		return new BlockPos[] {king, cellar};
	}
}
