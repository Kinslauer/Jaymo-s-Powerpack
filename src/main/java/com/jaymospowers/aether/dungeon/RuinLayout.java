package com.jaymospowers.aether.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Where the Sentry Ruins' pieces go. The boss room comes first, a tunnel leads out of it to a chest room, and the rooms
 * follow one after another, each turning left, going straight on or turning right; at the end a lobby, and from the lobby
 * flights of stairs climb to a small ruin standing at the surface. Everything is buried in the island: a layout that would come
 * out into the air, run into itself or reach too far is thrown away and another tried.
 */
final class RuinLayout {
	// ---- tuning ----
	/** The most rooms besides the boss room (the first is always the chest room). */
	public static final int MAX_ROOMS = 5;
	/** Layouts tried before the structure gives up at a spot. */
	public static final int ATTEMPTS = 24;
	/** No piece may reach further than this from the start (a structure may only reach 8 chunks). */
	public static final int REACH = 112;
	/** The first room's floor is this far below the surface: four flights, a ruin sunk 5 blocks, so most stairs come to 4. */
	public static final int DEPTH = 28;
	/** The deepest a floor may sit above the world's bottom, and the stairs' fewest and most flights. */
	public static final int MIN_FLOOR = 12, MIN_FLIGHTS = 2, MAX_FLIGHTS = 8;
	/** How far (blocks) the ruin's floor may stand from four below the ground. */
	public static final int RUIN_TOLERANCE = 3;
	/** Rock that must stand over a buried piece, and the share of its sample columns that must be solid at floor and ceiling. */
	public static final int COVER = 3;
	public static final int SOLID_SAMPLES = 4;
	/** A run of rock must be this thick to count as ground (the Aether islands are far thicker). */
	public static final int ISLAND_THICK = 16;

	private static final RuinPiece.Kind[] ROOMS = {RuinPiece.Kind.LOUNGE, RuinPiece.Kind.DEPOSIT, RuinPiece.Kind.COLD, RuinPiece.Kind.TESTING,
		RuinPiece.Kind.WORKSHOP, RuinPiece.Kind.FORGE};

	private RuinLayout() {
	}

	/** A piece before it is built. */
	private static final class Draft {
		final RuinPiece.Kind kind;
		final BoundingBox box;
		final Direction facing;
		int doors;

		Draft(RuinPiece.Kind kind, BoundingBox box, Direction facing, int doors) {
			this.kind = kind;
			this.box = box;
			this.facing = facing;
			this.doors = doors;
		}
	}

	/** What the ground is like round the structure, looked up once per column. */
	static final class Terrain {
		private final Structure.GenerationContext context;
		private final Map<Long, Integer> tops = new HashMap<>();
		private final Map<Long, NoiseColumn> columns = new HashMap<>();

		Terrain(Structure.GenerationContext context) {
			this.context = context;
		}

		private static long key(int x, int z) {
			return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
		}

		/** The y of the highest block of the ground at a column: the top of the highest run of rock at least ISLAND_THICK thick (thin things floating higher up are not ground). */
		int top(int x, int z) {
			return this.tops.computeIfAbsent(key(x, z), k -> {
				int run = 0;
				for (int y = this.context.heightAccessor().getMaxY(); y >= this.context.heightAccessor().getMinY(); y--) {
					run = this.solid(x, y, z) ? run + 1 : 0;
					if (run >= ISLAND_THICK) {
						return y + run - 1;
					}
				}
				return Integer.MIN_VALUE / 2;
			});
		}

		boolean solid(int x, int y, int z) {
			NoiseColumn column = this.columns.computeIfAbsent(key(x, z), k -> this.context.chunkGenerator().getBaseColumn(x, z, this.context.heightAccessor(),
				this.context.randomState()));
			BlockState state = column.getBlock(y);
			return !state.isAir() && state.getFluidState().isEmpty();
		}

		/** Whether a box lies inside the island: rock under its floor and at its ceiling in most of five columns, and ground well above. */
		boolean buried(BoundingBox box) {
			int[][] spots = {{box.minX() + 1, box.minZ() + 1}, {box.maxX() - 1, box.minZ() + 1}, {box.minX() + 1, box.maxZ() - 1},
				{box.maxX() - 1, box.maxZ() - 1}, {(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2}};
			int floor = 0, ceiling = 0;
			for (int[] spot : spots) {
				if (this.top(spot[0], spot[1]) < box.maxY() + COVER) {
					return false;
				}
				floor += this.solid(spot[0], box.minY(), spot[1]) ? 1 : 0;
				ceiling += this.solid(spot[0], box.maxY(), spot[1]) ? 1 : 0;
			}
			return floor >= SOLID_SAMPLES && ceiling >= SOLID_SAMPLES;
		}
	}

	// ---- boxes ----

	/** A box given in a frame that points {@code f} from (ox, oz): {@code a} along, {@code c} to the right. */
	private static BoundingBox frameBox(Direction f, int ox, int oz, int a0, int a1, int c0, int c1, int minY, int height) {
		Direction r = f.getClockWise();
		int xa = ox + c0 * r.getStepX() + a0 * f.getStepX(), xb = ox + c1 * r.getStepX() + a1 * f.getStepX();
		int za = oz + c0 * r.getStepZ() + a0 * f.getStepZ(), zb = oz + c1 * r.getStepZ() + a1 * f.getStepZ();
		return new BoundingBox(Math.min(xa, xb), minY, Math.min(za, zb), Math.max(xa, xb), minY + height - 1, Math.max(za, zb));
	}

	/** The box {@code length} long standing past a box's side {@code d}, {@code gap} beyond it, its width starting {@code offset} in. */
	private static BoundingBox beyond(BoundingBox from, Direction d, int gap, int length, int offset, int size, int minY, int height) {
		int x0, x1, z0, z1;
		switch (d) {
			case SOUTH -> {
				z0 = from.maxZ() + 1 + gap;
				z1 = z0 + length - 1;
				x0 = from.minX() + offset;
				x1 = x0 + size - 1;
			}
			case NORTH -> {
				z1 = from.minZ() - 1 - gap;
				z0 = z1 - length + 1;
				x0 = from.minX() + offset;
				x1 = x0 + size - 1;
			}
			case EAST -> {
				x0 = from.maxX() + 1 + gap;
				x1 = x0 + length - 1;
				z0 = from.minZ() + offset;
				z1 = z0 + size - 1;
			}
			default -> {
				x1 = from.minX() - 1 - gap;
				x0 = x1 - length + 1;
				z0 = from.minZ() + offset;
				z1 = z0 + size - 1;
			}
		}
		return new BoundingBox(x0, minY, z0, x1, minY + height - 1, z1);
	}

	private static boolean reaches(BoundingBox box, int cx, int cz) {
		return box.minX() >= cx - REACH && box.maxX() <= cx + REACH && box.minZ() >= cz - REACH && box.maxZ() <= cz + REACH;
	}

	private static boolean clear(List<Draft> placed, BoundingBox box, int margin, @Nullable Draft except) {
		BoundingBox wide = box.inflatedBy(margin);
		for (Draft other : placed) {
			if (other != except && other.box.intersects(wide)) {
				return false;
			}
		}
		return true;
	}

	private static int height(RuinPiece.Kind kind) {
		return kind == RuinPiece.Kind.DEPOSIT ? RuinPiece.DEPOSIT_H : kind == RuinPiece.Kind.FORGE ? RuinPiece.FORGE_H : RuinPiece.ROOM_H;
	}

	// ---- the layout ----

	/** A whole layout for the start at (cx, cz), or null if this try does not fit. */
	static @Nullable List<RuinPiece> build(Terrain terrain, RandomSource random, int cx, int cz) {
		int top = terrain.top(cx, cz);
		int floor = top - DEPTH;
		if (floor < MIN_FLOOR) {
			return null;
		}
		int minY = floor - 1;
		Direction e = Direction.from2DDataValue(random.nextInt(4));
		// the boss room, its tunnel and the chest room are all laid out in one frame so their doors line up
		BoundingBox bossBox = frameBox(e, cx, cz, -20, 14, -12, 11, minY, RuinPiece.BOSS_H);
		BoundingBox tunnelBox = frameBox(e, cx, cz, 15, 20, -3, 2, minY, RuinPiece.TUNNEL_H);
		BoundingBox chestBox = frameBox(e, cx, cz, 21, 46, -13, 12, minY, RuinPiece.ROOM_H);
		List<Draft> placed = new ArrayList<>();
		placed.add(new Draft(RuinPiece.Kind.BOSS, bossBox, e.getOpposite(), RuinPiece.doorBit(e)));
		placed.add(new Draft(RuinPiece.Kind.TUNNEL, tunnelBox, e, 0));
		Draft current = new Draft(RuinPiece.Kind.CHEST, chestBox, Direction.from2DDataValue(random.nextInt(4)), RuinPiece.doorBit(e.getOpposite()));
		placed.add(current);
		for (Draft d : placed) {
			if (!reaches(d.box, cx, cz) || d.kind != RuinPiece.Kind.TUNNEL && !terrain.buried(d.box)) {
				return null;
			}
		}
		// more rooms, each turning left, straight on or right
		int want = random.nextInt(MAX_ROOMS);
		Direction arrive = e;
		for (int i = 0; i < want; i++) {
			List<Direction> order = new ArrayList<>(List.of(arrive.getCounterClockWise(), arrive, arrive.getClockWise()));
			Collections.shuffle(order, new java.util.Random(random.nextLong()));
			boolean made = false;
			for (Direction d : order) {
				RuinPiece.Kind kind = ROOMS[random.nextInt(ROOMS.length)];
				BoundingBox room = beyond(current.box, d, RuinPiece.TUNNEL, RuinPiece.ROOM, 0, RuinPiece.ROOM, minY, height(kind));
				BoundingBox tunnel = beyond(current.box, d, 0, RuinPiece.TUNNEL, 10, RuinPiece.TUNNEL, minY, RuinPiece.TUNNEL_H);
				if (!reaches(room, cx, cz) || !clear(placed, room, 1, null) || !clear(placed, tunnel, 0, current) || !terrain.buried(room)) {
					continue;
				}
				current.doors |= RuinPiece.doorBit(d);
				placed.add(new Draft(RuinPiece.Kind.TUNNEL, tunnel, d, 0));
				current = new Draft(kind, room, Direction.from2DDataValue(random.nextInt(4)), RuinPiece.doorBit(d.getOpposite()));
				placed.add(current);
				arrive = d;
				made = true;
				break;
			}
			if (!made) {
				break;
			}
		}
		// the lobby, and the way up
		List<Direction> order = new ArrayList<>(List.of(arrive.getCounterClockWise(), arrive, arrive.getClockWise(), arrive.getOpposite()));
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (Direction d : order) {
			if ((current.doors & RuinPiece.doorBit(d)) != 0) {
				continue;
			}
			BoundingBox lobby = beyond(current.box, d, RuinPiece.TUNNEL, RuinPiece.LOBBY_L, 0, RuinPiece.ROOM, minY, RuinPiece.LOBBY_H);
			BoundingBox tunnel = beyond(current.box, d, 0, RuinPiece.TUNNEL, 10, RuinPiece.TUNNEL, minY, RuinPiece.TUNNEL_H);
			if (!reaches(lobby, cx, cz) || !clear(placed, lobby, 1, null) || !clear(placed, tunnel, 0, current) || !terrain.buried(lobby)) {
				continue;
			}
			List<Draft> climb = climb(terrain, placed, lobby, d, floor, cx, cz);
			if (climb == null) {
				continue;
			}
			current.doors |= RuinPiece.doorBit(d);
			placed.add(new Draft(RuinPiece.Kind.TUNNEL, tunnel, d, 0));
			placed.add(new Draft(RuinPiece.Kind.LOBBY, lobby, d, RuinPiece.doorBit(d.getOpposite()) | RuinPiece.doorBit(d)));
			placed.addAll(climb);
			List<RuinPiece> pieces = new ArrayList<>();
			for (Draft draft : placed) {
				pieces.add(new RuinPiece(draft.kind, draft.box, draft.facing, random.nextLong(), draft.doors));
			}
			return pieces;
		}
		return null;
	}

	/** The flights of stairs from the lobby's far end, and the ruin they come up in: as many flights as bring the ruin's floor to just under the ground. */
	private static @Nullable List<Draft> climb(Terrain terrain, List<Draft> placed, BoundingBox lobby, Direction d, int floor, int cx, int cz) {
		for (int n = MIN_FLIGHTS; n <= MAX_FLIGHTS; n++) {
			int standEnd = floor + RuinPiece.RISE * n;
			BoundingBox ruin = beyond(lobby, d, RuinPiece.STAIR_L * n, RuinPiece.RUIN, 8, RuinPiece.RUIN, standEnd - 1, RuinPiece.RUIN_H);
			int ground = terrain.top((ruin.minX() + ruin.maxX()) / 2, (ruin.minZ() + ruin.maxZ()) / 2);
			if (Math.abs(ground - 4 - standEnd) > RUIN_TOLERANCE) {
				continue;
			}
			List<Draft> drafts = new ArrayList<>();
			boolean ok = reaches(ruin, cx, cz) && clear(placed, ruin, 1, null);
			for (int i = 0; i < n && ok; i++) {
				BoundingBox flight = beyond(lobby, d, RuinPiece.STAIR_L * i, RuinPiece.STAIR_L, 10, RuinPiece.STAIR_W, floor + RuinPiece.RISE * i - 1, RuinPiece.STAIR_H);
				ok = reaches(flight, cx, cz) && clear(placed, flight, 0, null);
				drafts.add(new Draft(RuinPiece.Kind.STAIRS, flight, d, 0));
			}
			if (ok) {
				drafts.add(new Draft(RuinPiece.Kind.RUIN, ruin, d, 0));
				return drafts;
			}
		}
		return null;
	}
}
