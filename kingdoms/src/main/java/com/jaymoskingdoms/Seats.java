package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

/**
 * Seats in buildings the world generated (0.5): a kingdom rises only inside a village (vanilla's, or Epic Structures'
 * towns, which use the same structure ids), and takes the village's church for its seat (or, without one, its largest
 * house): a throne at the back away from the door, its banner on the wall behind, a panic room dug under the floor
 * beside it, guards at the door and archers on the rooftops round it. Another of the village's buildings becomes its
 * storehouse ({@link Storehouse#adopt}). The same reading of a structure's pieces seats fiefdoms and camps in Grim
 * Kingdoms' ruins ({@link GrimSites}). Nothing a villager lives or works by is moved: the throne, chests and barrels
 * only ever go where there's air.
 */
final class Seats {
	/** A building's rooms: open cells under a roof at most this far above; fewer cells than this isn't a building. */
	static final int ROOF_SCAN = 12, MIN_CELLS = 6;
	/** How many candidate buildings are looked over (each is scanned block by block). */
	static final int MAX_CANDIDATES = 8;
	/** Archer posts: how many, how high above the seat's floor a rooftop must be, how far apart, the ring round the bell. */
	static final int POSTS = 3, POST_HEIGHT = 4, POST_SPACING = 6, POST_RING_MIN = 5, POST_RING_MAX = 17, POST_STEP = 3;
	/** The panic room: its inner half-width and height, and how far under the floor its ceiling is. */
	static final int CELLAR_HALF = 1, CELLAR_HIGH = 3;
	/** A bell this close (any way) to one of a village's pieces is in the village. */
	static final int BELL_SLACK = 2;

	/** Pieces that aren't buildings (by template path): streets, plazas, farms, pens, decorations, trees, terrain. */
	private static final String[] NOT_BUILDINGS = {"/streets/", "/town_centers/", "terminator", "farm", "animal_pen", "meeting_point", "decor",
		"/villagers/", "/trees/", "lamp", "/terrain", "path", "well", "fountain"};
	/** A storehouse is best in a building nobody sleeps in (or a big house). */
	private static final String[] STORE_WORDS = {"library", "mason", "armorer", "armourer", "toolsmith", "tool_smith", "weaponsmith",
		"weapon_smith", "tannery", "stable", "fletcher", "cartographer", "butcher", "shepherd", "fisher", "storage", "barn", "large", "big",
		"medium"};

	private Seats() {
	}

	// ---- reading structures ---------------------------------------------------------------------------------------

	/** Whether a structure stands at a spot: none, one whose pieces can't be read yet (its start chunk isn't loaded), or one. */
	enum Status { NONE, UNREADY, FOUND }

	/** What {@link #structureAt} found: its start and id when {@link Status#FOUND}. */
	record Found(Status status, @Nullable StructureStart start, String id) {
		static final Found NONE = new Found(Status.NONE, null, "");
		static final Found UNREADY = new Found(Status.UNREADY, null, "");
	}

	/** A village structure (by tag, or a {@code minecraft:village_*} id). */
	static boolean village(Holder<Structure> holder) {
		return holder.is(StructureTags.VILLAGE)
			|| holder.unwrapKey().map(k -> k.identifier().getNamespace().equals("minecraft") && k.identifier().getPath().startsWith("village_")).orElse(false);
	}

	/**
	 * The structure {@code which} accepts with a piece at {@code pos} (within {@link #BELL_SLACK}), read only from loaded
	 * chunks: {@link Status#UNREADY} if one might be there but its start chunk isn't loaded.
	 */
	static Found structureAt(ServerLevel level, BlockPos pos, Predicate<Holder<Structure>> which) {
		int cx = SectionPos.blockToSectionCoord(pos.getX()), cz = SectionPos.blockToSectionCoord(pos.getZ());
		if (!level.hasChunk(cx, cz)) {
			return Found.UNREADY;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkAccess chunk = level.getChunk(cx, cz);
		boolean pending = false;
		for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
			Holder<Structure> holder = registry.wrapAsHolder(e.getKey());
			if (!which.test(holder)) {
				continue;
			}
			for (long ref : e.getValue()) {
				ChunkPos from = ChunkPos.unpack(ref);
				if (!level.hasChunk(from.x(), from.z())) {
					pending = true;
					continue;
				}
				StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(e.getKey());
				if (start == null || !start.isValid()) {
					continue;
				}
				for (StructurePiece piece : start.getPieces()) {
					if (piece.getBoundingBox().inflatedBy(BELL_SLACK).isInside(pos)) {
						Identifier id = registry.getKey(e.getKey());
						return new Found(Status.FOUND, start, id == null ? "" : id.toString());
					}
				}
			}
		}
		return pending ? Found.UNREADY : Found.NONE;
	}

	/** A piece's name: its template's path (lower case), or what the piece calls itself. */
	static String name(StructurePiece piece) {
		String s = piece instanceof PoolElementStructurePiece pool ? pool.getElement().toString() : piece.getClass().getSimpleName();
		return s.toLowerCase(Locale.ROOT);
	}

	/** One of a structure's pieces that may be a building: its box and its template's path. */
	record Building(BoundingBox box, String name) {
		long volume() {
			return (long)this.box.getXSpan() * this.box.getYSpan() * this.box.getZSpan();
		}

		boolean church() {
			return this.name.contains("temple") || this.name.contains("church") || this.name.contains("chapel");
		}

		boolean store() {
			for (String w : STORE_WORDS) {
				if (this.name.contains(w)) {
					return true;
				}
			}
			return false;
		}
	}

	/** A structure's pieces that may be buildings (no streets, farms, decorations...), largest first. */
	static List<Building> buildings(StructureStart start) {
		List<Building> out = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			String name = name(piece);
			boolean building = true;
			for (String no : NOT_BUILDINGS) {
				if (name.contains(no)) {
					building = false;
					break;
				}
			}
			BoundingBox box = piece.getBoundingBox();
			if (building && box.getXSpan() >= 4 && box.getZSpan() >= 4 && box.getYSpan() >= 3) {
				out.add(new Building(box, name));
			}
		}
		out.sort(Comparator.comparingLong(Building::volume).reversed());
		return out;
	}

	/** (0.5.1) A village's farms: its crop fields, then its animal pens, each kind nearest {@code toward} first. */
	static List<BoundingBox> farms(StructureStart start, BlockPos toward) {
		List<BoundingBox> fields = new ArrayList<>(), pens = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			String name = name(piece);
			if (name.contains("animal_pen")) {
				pens.add(piece.getBoundingBox());
			} else if (name.contains("farm")) {
				fields.add(piece.getBoundingBox());
			}
		}
		Comparator<BoundingBox> nearest = Comparator.comparingDouble(b -> b.getCenter().distSqr(toward));
		fields.sort(nearest);
		pens.sort(nearest);
		fields.addAll(pens);
		return fields;
	}

	/** Every piece's box (for a hold's guarded ground). */
	static List<BoundingBox> boxes(StructureStart start) {
		List<BoundingBox> out = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			out.add(piece.getBoundingBox());
		}
		return out;
	}

	// ---- rooms --------------------------------------------------------------------------------------------------------

	/**
	 * A building's main floor: its box, name, the floor's height (where one stands), its open cells under the roof (two
	 * high, a sturdy floor), its door's inside and outside cells (or, without a door, the ground outside it toward the
	 * town), and the way out.
	 */
	record Room(BoundingBox box, String name, int floor, List<BlockPos> cells, @Nullable BlockPos inside, BlockPos outside) {
		boolean has(BlockPos pos) {
			return this.cells.contains(pos);
		}

		BlockPos middle() {
			BlockPos c = this.box.getCenter();
			return this.cells.stream().min(Comparator.comparingDouble(p -> p.distSqr(c.atY(p.getY())))).orElse(c);
		}
	}

	/** Its rooms, if {@code b} is a building with a floor under a roof (and all its chunks are loaded); null if not. */
	static @Nullable Room room(ServerLevel level, Building b, BlockPos toward) {
		BoundingBox box = b.box();
		if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
			return null;
		}
		Map<Integer, List<BlockPos>> byFloor = new HashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY() + 1; y <= box.maxY() - 1; y++) {
					at.set(x, y, z);
					if (cell(level, at)) {
						byFloor.computeIfAbsent(y, k -> new ArrayList<>()).add(at.immutable());
					}
				}
			}
		}
		int floor = Integer.MIN_VALUE, most = 0;
		for (var e : byFloor.entrySet()) {
			if (e.getValue().size() > most || (e.getValue().size() == most && e.getKey() < floor)) {
				most = e.getValue().size();
				floor = e.getKey();
			}
		}
		if (most < MIN_CELLS) {
			return null;
		}
		List<BlockPos> cells = byFloor.get(floor);
		Set<BlockPos> set = new HashSet<>(cells);
		// the door nearest the town: its lower half on the floor, open floor on one side and the open air on the other
		BlockPos inside = null, outside = null;
		double best = Double.MAX_VALUE;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = floor - 1; y <= floor + 1; y++) {
					BlockPos door = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(door);
					if (!state.is(BlockTags.DOORS) || !state.hasProperty(DoorBlock.HALF) || !state.hasProperty(DoorBlock.FACING)
						|| state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
						continue;
					}
					Direction facing = state.getValue(DoorBlock.FACING);
					for (Direction out : new Direction[] {facing, facing.getOpposite()}) {
						BlockPos o = door.relative(out), i = door.relative(out.getOpposite());
						if (open(level, o) && open(level, o.above()) && !roofed(level, o) && (set.contains(i) || cell(level, i))) {
							double d = door.distSqr(toward);
							if (d < best) {
								best = d;
								inside = i.immutable();
								outside = o.immutable();
							}
						}
					}
				}
			}
		}
		if (outside == null) {
			// no door: the ground outside the building on the side toward the town
			BlockPos c = box.getCenter();
			Direction way = Direction.getApproximateNearest(toward.getX() - c.getX(), 0, toward.getZ() - c.getZ());
			int reach = (way.getAxis() == Direction.Axis.X ? box.getXSpan() : box.getZSpan()) / 2 + 2;
			BlockPos edge = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.relative(way, reach));
			outside = Construction.standable(level, edge, 4);
			inside = cells.stream().min(Comparator.comparingDouble(p -> p.distSqr(edge.atY(p.getY())))).orElse(null);
		}
		return new Room(box, b.name(), floor, List.copyOf(cells), inside, outside);
	}

	/** Two blocks of room, a sturdy floor, dry, and a roof over it. */
	private static boolean cell(ServerLevel level, BlockPos at) {
		return open(level, at) && open(level, at.above()) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
			&& level.getFluidState(at).isEmpty() && roofed(level, at);
	}

	private static boolean open(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	/** Something solid overhead, within {@link #ROOF_SCAN}. */
	static boolean roofed(ServerLevel level, BlockPos at) {
		for (int dy = 2; dy <= ROOF_SCAN; dy++) {
			BlockPos up = at.above(dy);
			BlockState state = level.getBlockState(up);
			if (!state.isAir() && !state.getCollisionShape(level, up).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** A full block (a wall to put things against). */
	static boolean wall(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).isCollisionShapeFullBlock(level, at) && level.getBlockEntity(at) == null;
	}

	/** The seat and the storehouse for a settlement in these buildings ({@code store} null: the seat's own building keeps the stores). */
	record Pick(Room seat, @Nullable Room store) {
	}

	/**
	 * Chooses among {@code buildings} (largest first): the seat (a church first, if {@code church}; else the largest with a
	 * floor under a roof) and the storehouse (another building, one nobody sleeps in first). Null if no building has a
	 * room.
	 */
	static @Nullable Pick pick(ServerLevel level, List<Building> buildings, BlockPos toward, boolean church) {
		List<Building> order = new ArrayList<>(buildings);
		if (church) {
			order.sort(Comparator.comparing((Building b) -> !b.church()).thenComparing(Comparator.comparingLong(Building::volume).reversed()));
		}
		Room seat = null;
		int looked = 0;
		for (Building b : order) {
			if (looked++ >= MAX_CANDIDATES) {
				break;
			}
			seat = room(level, b, toward);
			if (seat != null) {
				break;
			}
		}
		if (seat == null) {
			return null;
		}
		List<Building> rest = new ArrayList<>();
		for (Building b : buildings) {
			if (!b.box().intersects(seat.box())) {
				rest.add(b);
			}
		}
		rest.sort(Comparator.comparing((Building b) -> !b.store()).thenComparing(Comparator.comparingLong(Building::volume).reversed()));
		looked = 0;
		for (Building b : rest) {
			if (looked++ >= MAX_CANDIDATES) {
				break;
			}
			Room store = room(level, b, toward);
			if (store != null) {
				return new Pick(seat, store);
			}
		}
		return new Pick(seat, null);
	}

	// ---- the seat -----------------------------------------------------------------------------------------------------

	/**
	 * Makes {@code room} a faction's seat: where its ruler sits (a throne in its materials at the back, away from the door,
	 * and its banner behind, if {@code throne}), its hall, its gate, a panic room dug under the floor beside the throne
	 * (if {@code cellar}: only through the land's own ground, never into anything built), and archers' posts on the
	 * rooftops round it (or about {@code town}). Only air is ever replaced in the building itself.
	 */
	static Castle.Keep keep(ServerLevel level, Faction faction, Room room, BlockPos town, boolean throne, boolean cellar) {
		BlockPos gate = room.outside();
		BlockPos door = room.inside() != null ? room.inside() : gate;
		// the throne: the open cell furthest from the door with a wall behind it and floor in front
		BlockPos seat = null, front = null;
		Direction back = Direction.NORTH;
		double far = -1;
		Set<BlockPos> cells = new HashSet<>(room.cells());
		for (BlockPos c : room.cells()) {
			if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir()) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos f = c.relative(d.getOpposite());
				if (wall(level, c.relative(d)) && cells.contains(f) && !f.equals(door)) {
					double dist = c.distSqr(door);
					if (dist > far) {
						far = dist;
						seat = c;
						front = f;
						back = d;
					}
				}
			}
		}
		BlockPos hall = room.middle();
		Palette p = Palette.at(level, room.box().getCenter());
		BlockPos king;
		if (seat == null) {
			king = room.cells().stream().max(Comparator.comparingDouble(c -> c.distSqr(door))).orElse(hall);
		} else {
			king = front;
			if (throne) {
				level.setBlock(seat, p.stairs().setValue(StairBlock.FACING, back), Block.UPDATE_ALL);
				BlockPos bannerAt = seat.above(2);
				BlockState banner = Castle.byName(faction.styleColor(level.getServer()).getName() + "_wall_banner", Blocks.AIR);
				if (!banner.isAir() && level.getBlockState(bannerAt).isAir() && wall(level, bannerAt.relative(back))) {
					level.setBlock(bannerAt, banner.setValue(WallBannerBlock.FACING, back.getOpposite()), Block.UPDATE_ALL);
				}
			}
		}
		if (king.equals(hall)) {
			hall = room.cells().stream().filter(c -> !c.equals(king)).min(Comparator.comparingDouble(c -> c.distSqr(room.box().getCenter()))).orElse(hall);
		}
		BlockPos panic = cellar ? cellar(level, room, king, seat, p) : null;
		List<BlockPos> posts = posts(level, room, town);
		return new Castle.Keep(king.immutable(), panic != null ? panic : king.immutable(), gate.immutable(), hall.immutable(), posts);
	}

	/**
	 * A panic room under the floor beside {@code king}: a cell of fortified stone ({@link Palette#fortified}) reached by a
	 * ladder under a trapdoor laid flush in the floor, the treasury chest inside. Dug only through the land's own ground
	 * (no block entity, nothing wet). Returns where one stands in it, or null if there was nowhere to dig.
	 */
	private static @Nullable BlockPos cellar(ServerLevel level, Room room, BlockPos king, @Nullable BlockPos seat, Palette p) {
		RandomSource random = level.getRandom();
		List<BlockPos> hatches = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos h = king.relative(d);
			if (room.has(h) && !h.equals(seat) && level.getBlockState(h).isAir()) {
				hatches.add(h);
			}
		}
		hatches.add(king);
		int r = CELLAR_HALF + 1;
		for (BlockPos hatch : hatches) {
			BlockPos floorBlock = hatch.below();
			if (level.getBlockEntity(floorBlock) != null || !wall(level, floorBlock)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				// the hatch sits over the room's inner edge; the room's middle is a step on from it
				BlockPos center = hatch.relative(d, CELLAR_HALF);
				int top = hatch.getY() - 2, bottom = top - CELLAR_HIGH - 1;
				if (!diggable(level, center, r, bottom, top)) {
					continue;
				}
				for (int x = -r; x <= r; x++) {
					for (int z = -r; z <= r; z++) {
						for (int y = bottom; y <= top; y++) {
							boolean shell = Math.abs(x) == r || Math.abs(z) == r || y == bottom || y == top;
							level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), shell ? p.fortified() : Blocks.AIR.defaultBlockState(),
								Block.UPDATE_CLIENTS);
						}
					}
				}
				for (int y = bottom + 1; y <= top; y++) {
					level.setBlock(hatch.atY(y), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d), Block.UPDATE_CLIENTS);
				}
				level.setBlock(floorBlock, p.trapdoor().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.FACING, d), Block.UPDATE_ALL);
				Direction side = d.getClockWise();
				BlockPos far = center.relative(d, CELLAR_HALF).atY(bottom + 1);
				BlockPos chest = far.relative(side, CELLAR_HALF);
				level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, d.getOpposite()), Block.UPDATE_ALL);
				if (level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
					box.setLootTable(BuiltInLootTables.VILLAGE_WEAPONSMITH, random.nextLong());
				}
				level.setBlock(far.relative(side.getOpposite(), CELLAR_HALF), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
				return center.atY(bottom + 1);
			}
		}
		return null;
	}

	/** The block of ground round {@code center} ({@code r} across, {@code bottom..top}) is the land's own: nothing built, nothing wet. */
	private static boolean diggable(ServerLevel level, BlockPos center, int r, int bottom, int top) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				for (int y = bottom; y <= top; y++) {
					BlockPos at = new BlockPos(center.getX() + x, y, center.getZ() + z);
					BlockState state = level.getBlockState(at);
					if (level.getBlockEntity(at) != null || !level.getFluidState(at).isEmpty() || state.getDestroySpeed(level, at) < 0
						|| !(state.isAir() || Castle.natural(state))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Archers' posts: rooftops round the seat's building and about {@code town} at least {@link #POST_HEIGHT} above its
	 * floor, {@link #POST_SPACING} apart; topped up with open ground about {@code town}.
	 */
	static List<BlockPos> posts(ServerLevel level, Room room, BlockPos town) {
		List<BlockPos> roofs = new ArrayList<>(), ground = new ArrayList<>();
		BoundingBox box = room.box();
		List<BlockPos> columns = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x += 2) {
			columns.add(new BlockPos(x, 0, box.minZ()));
			columns.add(new BlockPos(x, 0, box.maxZ()));
		}
		for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
			columns.add(new BlockPos(box.minX(), 0, z));
			columns.add(new BlockPos(box.maxX(), 0, z));
		}
		for (int r = POST_RING_MIN; r <= POST_RING_MAX; r += POST_STEP) {
			for (int i = 0; i < 12; i++) {
				double a = i / 12.0 * Math.PI * 2.0;
				columns.add(town.offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r)));
			}
		}
		for (BlockPos column : columns) {
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			BlockPos under = top.below();
			if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) || level.getBlockState(under).is(BlockTags.LEAVES)
				|| !level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir() || !level.getFluidState(under).isEmpty()) {
				continue;
			}
			(top.getY() >= room.floor() + POST_HEIGHT ? roofs : ground).add(top.immutable());
		}
		roofs.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).reversed());
		List<BlockPos> posts = new ArrayList<>();
		for (List<BlockPos> list : List.of(roofs, ground)) {
			for (BlockPos p : list) {
				if (posts.size() >= POSTS) {
					return posts;
				}
				if (posts.stream().allMatch(q -> q.distSqr(p) >= POST_SPACING * POST_SPACING)) {
					posts.add(p);
				}
			}
		}
		return posts;
	}
}
