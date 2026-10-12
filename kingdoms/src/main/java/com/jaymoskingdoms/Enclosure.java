package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.11) The wall round a whole settlement. Once a settlement has put up every other kind of building in its growth
 * order to its full count (only more houses are left), its builders wall it in: a rectangle round everything it has, the village it grew from and
 * a margin, squared to whole wall sections. A kingdom or fiefdom builds stone wall sections, a tower on each corner and
 * a gatehouse in the middle of each side; a village a palisade with a gap in the middle of each side. The gatehouses
 * go up first, then the towers, then the wall round from the north-west. Once the ground is walled, later buildings
 * stay inside it ({@link Planner#site}). Each piece stands at the ground's own height (its builders level the ground).
 */
final class Enclosure {
	/** The pieces of a wall: built only here, never as ordinary buildings ({@link Planner#eligible}). */
	static final Set<String> PIECES = Set.of("wall", "tower", "gatehouse", "palisade");
	/**
	 * Ground kept between the outermost building and the wall (room for the houses still to come inside it); the wall's
	 * least and greatest reach from the seat.
	 */
	static final int MARGIN = 12, LEAST = 24, MOST = 110;
	/** A wall section's length, a tower's side, a gatehouse's depth, a stone section's depth, a palisade's depth. */
	static final int SECTION = 9, TOWER = 7, GATE_DEPTH = 5, WALL_DEPTH = 3, PALISADE_DEPTH = 2;

	/** One piece of the wall: what, its corner (its y the ground's), how it's turned. */
	record Piece(String entry, BlockPos origin, Rotation rotation) {
	}

	private Enclosure() {
	}

	/** Whether {@code entry} is a piece of the wall. */
	static boolean piece(String entry) {
		return PIECES.contains(entry);
	}

	/** The next piece of {@code f}'s wall to put up (laying the wall out first), or null if it's all up (or can't be yet). */
	static @Nullable Piece next(ServerLevel level, Faction f) {
		Works.Site works = Works.of(level.getServer(), f.id());
		List<Integer> wall = works.wall();
		if (wall.size() != 4) {
			wall = layOut(level, f, works);
			List<Integer> chosen = wall;
			Works.update(level.getServer(), f.id(), s -> s.withWall(chosen));
			JaymosKingdoms.LOGGER.info("{} lays out its wall round {},{} to {},{}", f.name(), wall.get(0), wall.get(1), wall.get(2), wall.get(3));
		}
		for (Piece p : pieces(f.type(), wall)) {
			if (standing(works, p)) {
				continue;
			}
			BlockPos middle = p.origin().offset(2, 0, 2);
			if (!level.isLoaded(middle)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ()) - 1;
			return new Piece(p.entry(), p.origin().atY(ground), p.rotation());
		}
		return null;
	}

	/** It's built or being built (by its corner's x and z: its height is the ground's). */
	private static boolean standing(Works.Site works, Piece p) {
		for (Works.Built b : works.built()) {
			if (b.entry().equals(p.entry()) && b.min().getX() == p.origin().getX() && b.min().getZ() == p.origin().getZ()) {
				return true;
			}
		}
		for (Works.Project q : works.projects()) {
			if (q.entry().equals(p.entry()) && q.origin().getX() == p.origin().getX() && q.origin().getZ() == p.origin().getZ()) {
				return true;
			}
		}
		return false;
	}

	/** Whether the ground from {@code min} to {@code max} (x and z) lies inside {@code f}'s wall (true if it has none yet). */
	static boolean inside(Works.Site works, int minX, int minZ, int maxX, int maxZ) {
		List<Integer> wall = works.wall();
		return wall.size() != 4 || minX >= wall.get(0) && minZ >= wall.get(1) && maxX <= wall.get(2) && maxZ <= wall.get(3);
	}

	/**
	 * The ground the wall goes round: the seat, every building and project (not the wall's own), and the structure the
	 * settlement grew from, with a margin, squared off to whole sections.
	 */
	private static List<Integer> layOut(ServerLevel level, Faction f, Works.Site works) {
		BlockPos seat = f.seat();
		int minX = seat.getX() - LEAST, minZ = seat.getZ() - LEAST, maxX = seat.getX() + LEAST, maxZ = seat.getZ() + LEAST;
		List<int[]> boxes = new ArrayList<>();
		for (Works.Built b : works.built()) {
			if (!piece(b.entry())) {
				boxes.add(new int[] {b.min().getX(), b.min().getZ(), b.max().getX(), b.max().getZ()});
			}
		}
		for (Works.Project p : works.projects()) {
			if (!piece(p.entry())) {
				boxes.add(new int[] {p.origin().getX(), p.origin().getZ(), p.origin().getX() + SECTION, p.origin().getZ() + SECTION});
			}
		}
		Seats.Found found = Seats.structureAt(level, seat, holder -> true);
		if (found.status() == Seats.Status.FOUND && found.start() != null) {
			for (BoundingBox box : Seats.boxes(found.start())) {
				boxes.add(new int[] {box.minX(), box.minZ(), box.maxX(), box.maxZ()});
			}
		}
		for (int[] b : boxes) {
			// (only what's near enough: a far-flung piece of a sprawling town would stretch the wall past all reason)
			if (Math.abs(b[0] - seat.getX()) > MOST || Math.abs(b[2] - seat.getX()) > MOST || Math.abs(b[1] - seat.getZ()) > MOST
				|| Math.abs(b[3] - seat.getZ()) > MOST) {
				continue;
			}
			minX = Math.min(minX, b[0] - MARGIN);
			minZ = Math.min(minZ, b[1] - MARGIN);
			maxX = Math.max(maxX, b[2] + MARGIN);
			maxZ = Math.max(maxZ, b[3] + MARGIN);
		}
		// whole sections along each side
		int w = maxX - minX + 1, d = maxZ - minZ + 1;
		int grow = Math.floorMod(-w, SECTION);
		minX -= grow / 2;
		maxX += grow - grow / 2;
		grow = Math.floorMod(-d, SECTION);
		minZ -= grow / 2;
		maxZ += grow - grow / 2;
		return List.of(minX, minZ, maxX, maxZ);
	}

	/** Every piece of the wall round {@code wall}, in the order they go up (their y left at 0). */
	static List<Piece> pieces(FactionType type, List<Integer> wall) {
		int minX = wall.get(0), minZ = wall.get(1), maxX = wall.get(2), maxZ = wall.get(3);
		List<Piece> gates = new ArrayList<>(), towers = new ArrayList<>(), sections = new ArrayList<>();
		boolean village = type == FactionType.VILLAGE;
		String section = village ? "palisade" : "wall";
		int depth = village ? PALISADE_DEPTH : WALL_DEPTH;
		int reach = village ? PALISADE_DEPTH : 0;
		// north and south: along x
		int across = (maxX + reach - (minX - reach) + 1 + SECTION - 1) / SECTION;
		for (int i = 0; i < across; i++) {
			int x = Math.min(minX - reach + i * SECTION, maxX + reach - SECTION + 1);
			boolean middle = i == across / 2;
			// north: its inside faces south
			if (middle && !village) {
				gates.add(new Piece("gatehouse", new BlockPos(x, 0, minZ - GATE_DEPTH), Planner.facing(Direction.SOUTH)));
				gates.add(new Piece("gatehouse", new BlockPos(x, 0, maxZ + 1), Planner.facing(Direction.NORTH)));
			} else if (!middle) {
				sections.add(new Piece(section, new BlockPos(x, 0, minZ - depth), Planner.facing(Direction.SOUTH)));
				sections.add(new Piece(section, new BlockPos(x, 0, maxZ + 1), Planner.facing(Direction.NORTH)));
			}
		}
		// west and east: along z
		int down = (maxZ - minZ + 1 + SECTION - 1) / SECTION;
		for (int i = 0; i < down; i++) {
			int z = Math.min(minZ + i * SECTION, maxZ - SECTION + 1);
			boolean middle = i == down / 2;
			if (middle && !village) {
				gates.add(new Piece("gatehouse", new BlockPos(minX - GATE_DEPTH, 0, z), Planner.facing(Direction.EAST)));
				gates.add(new Piece("gatehouse", new BlockPos(maxX + 1, 0, z), Planner.facing(Direction.WEST)));
			} else if (!middle) {
				sections.add(new Piece(section, new BlockPos(minX - depth, 0, z), Planner.facing(Direction.EAST)));
				sections.add(new Piece(section, new BlockPos(maxX + 1, 0, z), Planner.facing(Direction.WEST)));
			}
		}
		if (!village) {
			// a tower on each corner, over the wall's ends
			int half = TOWER / 2;
			towers.add(new Piece("tower", new BlockPos(minX - WALL_DEPTH + 1 - half, 0, minZ - WALL_DEPTH + 1 - half), Planner.facing(Direction.SOUTH)));
			towers.add(new Piece("tower", new BlockPos(maxX + WALL_DEPTH - 1 - half, 0, minZ - WALL_DEPTH + 1 - half), Planner.facing(Direction.SOUTH)));
			towers.add(new Piece("tower", new BlockPos(minX - WALL_DEPTH + 1 - half, 0, maxZ + WALL_DEPTH - 1 - half), Planner.facing(Direction.NORTH)));
			towers.add(new Piece("tower", new BlockPos(maxX + WALL_DEPTH - 1 - half, 0, maxZ + WALL_DEPTH - 1 - half), Planner.facing(Direction.NORTH)));
		}
		List<Piece> out = new ArrayList<>(gates);
		out.addAll(towers);
		out.addAll(sections);
		return out;
	}
}
