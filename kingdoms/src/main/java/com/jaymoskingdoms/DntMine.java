package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.627) Andy (2026-11-06): "cant they just use the dungeon and taverns structure?" When Dungeons and Taverns (mod id
 * {@link #MOD}, All Rights Reserved) is installed, a settlement that isn't poor digs and builds D&T's own miner's pit
 * (its "remnant miner hut": a fenced pit with a cobblestone shed, rails and ladders down to a little rail tunnel), read at
 * run time from the copy the player installed: nothing of theirs is copied into ours. Its two pieces ({@link #TOP}, and
 * {@link #UNDER} {@link #DROP} below) are laid as D&T joins them. The builders dig out its open blocks a layer at a time,
 * top down (what they dig goes to storage); the blocks under the ground go in as each layer opens (ladders, rails, floor),
 * the ones above it (the fence, the shed) when the last is dug; then the plain tunnel ({@link Mines}) goes on into the rock
 * from its lowest floor. Built whole, without the weathering D&T gives the ruins it scatters.
 */
final class DntMine {
	static final String MOD = "mr_dungeons_andtaverns";
	static final Identifier TOP = Identifier.fromNamespaceAndPath("nova_structures", "remnant/wild_ruin_19"),
		UNDER = Identifier.fromNamespaceAndPath("nova_structures", "remnant/wild_ruin_19_b");
	/** The lower piece sits this far below the upper (D&T's jigsaws meet there). */
	static final int DROP = 10;
	/** The tunnel from the pit's floor runs level this many steps before it turns down. */
	static final int FLAT = 2;

	/**
	 * The pit, untouched by rotation: each block (air included; left-out blocks are D&T's structure voids, where the land
	 * stays as it is), its ground at y -1; its size; its highest and lowest open layers; where the tunnel starts and which way.
	 */
	record Shape(Map<BlockPos, BlockState> cells, int sx, int sz, int top, int bottom, BlockPos low, Direction way) {
		int layers() {
			return this.top - this.bottom + 1;
		}
	}

	private static @Nullable Optional<Shape> shape;

	private DntMine() {
	}

	static void clear() {
		shape = null;
	}

	/** D&T is installed and its miner's pit could be read. */
	static boolean available(MinecraftServer server) {
		return FabricLoader.getInstance().isModLoaded(MOD) && shape(server) != null;
	}

	static @Nullable Shape shape(MinecraftServer server) {
		if (shape == null) {
			shape = Optional.ofNullable(read(server));
			if (shape.isEmpty() && FabricLoader.getInstance().isModLoaded(MOD)) {
				JaymosKingdoms.LOGGER.warn("Dungeons and Taverns is installed but its miner's pit ({}) wasn't found; settlements dig our own", TOP);
			}
		}
		return shape.orElse(null);
	}

	private static @Nullable Shape read(MinecraftServer server) {
		Optional<StructureTemplate> top = server.getStructureTemplateManager().get(TOP);
		Optional<StructureTemplate> under = server.getStructureTemplateManager().get(UNDER);
		if (top.isEmpty() || under.isEmpty()) {
			return null;
		}
		Map<BlockPos, BlockState> cells = new HashMap<>();
		// (the upper piece's lowest layer is the ground)
		int sx = add(cells, top.get().save(new CompoundTag()), -1)[0];
		int[] u = add(cells, under.get().save(new CompoundTag()), -1 - DROP);
		int sz = u[1];
		sx = Math.max(sx, u[0]);
		int topY = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
		for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
			if (e.getValue().isAir()) {
				topY = Math.max(topY, e.getKey().getY());
				bottom = Math.min(bottom, e.getKey().getY());
			}
		}
		if (topY == Integer.MIN_VALUE) {
			return null;
		}
		// the tunnel: from the lowest open block it can start from, the way its first steps run through nothing of D&T's but
		// rock (so it digs no ladder or plank)
		List<BlockPos> open = cells.entrySet().stream().filter(e -> e.getValue().isAir()).map(Map.Entry::getKey)
			.sorted(Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ())).toList();
		BlockPos low = open.getFirst();
		Direction way = Direction.NORTH;
		search:
		for (BlockPos p : open) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (rock(cells, p, d)) {
					low = p;
					way = d;
					break search;
				}
			}
		}
		return new Shape(Map.copyOf(cells), sx, sz, topY, bottom, low, way);
	}

	/** The tunnel's first few steps from {@code p} going {@code d} (its width and height) hold only rock or the land's own. */
	private static boolean rock(Map<BlockPos, BlockState> cells, BlockPos p, Direction d) {
		Direction side = d.getClockWise();
		for (int i = 1; i <= 4; i++) {
			for (int w = -Mines.WIDE / 2; w <= Mines.WIDE / 2; w++) {
				for (int h = 0; h < Mines.HIGH; h++) {
					BlockState s = cells.get(p.relative(d, i).relative(side, w).above(h));
					if (s != null && (s.isAir() || !MinePit.natural(s))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** One piece's blocks into {@code cells}, raised {@code dy}; returns its size (x, z). */
	private static int[] add(Map<BlockPos, BlockState> cells, CompoundTag tag, int dy) {
		ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i)));
		}
		ListTag blocks = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			int s = b.getIntOr("state", -1);
			if (s < 0 || s >= states.size()) {
				continue;
			}
			BlockState state = states.get(s);
			if (state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK)) {
				continue;
			}
			if (state.is(Blocks.JIGSAW)) {
				// (what the joint turns into)
				Identifier id = Identifier.tryParse(b.getCompoundOrEmpty("nbt").getStringOr("final_state", "minecraft:dirt").split("\\[")[0]);
				state = id == null ? Blocks.DIRT.defaultBlockState() : BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
			}
			ListTag pos = b.getListOrEmpty("pos");
			cells.put(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0) + dy, pos.getIntOr(2, 0)), state);
		}
		ListTag size = tag.getListOrEmpty("size");
		return new int[] {size.getIntOr(0, 0), size.getIntOr(2, 0)};
	}

	// ---- placing ----------------------------------------------------------------------------------------------------

	private static Rotation rotation(int turn) {
		return Rotation.values()[Math.floorMod(turn, 4)];
	}

	/** Where the pit's block {@code rel} lands, laid at {@code origin} turned {@code turn} quarters. */
	static BlockPos world(BlockPos origin, int turn, BlockPos rel) {
		return origin.offset(StructureTemplate.transform(rel, Mirror.NONE, rotation(turn), BlockPos.ZERO));
	}

	static Mines.Mine plan(Shape s, BlockPos origin, int turn) {
		BlockPos entrance = world(origin, turn, s.low());
		Direction facing = rotation(turn).rotate(s.way());
		return new Mines.Mine(entrance, facing, FLAT, 0, Mines.DIGGING, -s.layers(), 0, origin, turn);
	}

	/** About the middle of the pit, at the ground. */
	static BlockPos center(Mines.Mine m) {
		return m.origin();
	}

	/** Layer {@code k}'s open blocks (from the top). Empty if D&T has gone. */
	static List<BlockPos> layer(MinecraftServer server, Mines.Mine m, int k) {
		Shape s = shape(server);
		List<BlockPos> out = new ArrayList<>();
		if (s == null) {
			return out;
		}
		int y = s.top() - k;
		for (Map.Entry<BlockPos, BlockState> e : s.cells().entrySet()) {
			if (e.getKey().getY() == y && e.getValue().isAir()) {
				out.add(world(m.origin(), m.turn(), e.getKey()));
			}
		}
		return out;
	}

	/** Every open block of the pit, as laid. */
	static java.util.Set<BlockPos> open(MinecraftServer server, Mines.Mine m) {
		Shape s = shape(server);
		java.util.Set<BlockPos> out = new java.util.HashSet<>();
		if (s != null) {
			for (Map.Entry<BlockPos, BlockState> e : s.cells().entrySet()) {
				if (e.getValue().isAir()) {
					out.add(world(m.origin(), m.turn(), e.getKey()));
				}
			}
		}
		return out;
	}

	/** Lays the pit's blocks of layer {@code y} (or every layer from 0 up, {@code above}): its own, and the ground only where the land has a hole. */
	private static void lay(ServerLevel level, Shape s, Mines.Mine m, int y, boolean above) {
		List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : s.cells().entrySet()) {
			int at = e.getKey().getY();
			if (!e.getValue().isAir() && (above ? at >= 0 : at == y)) {
				blocks.add(e);
			}
		}
		blocks.sort(Comparator.comparingInt(e -> e.getKey().getY()));
		Rotation r = rotation(m.turn());
		for (Map.Entry<BlockPos, BlockState> e : blocks) {
			BlockPos q = world(m.origin(), m.turn(), e.getKey());
			BlockState state = e.getValue().rotate(r);
			if (MinePit.natural(state) && !Mines.clear(level, q) && level.getFluidState(q).isEmpty()) {
				continue;
			}
			// (no neighbour updates: a ladder isn't knocked off before its wall goes in)
			level.setBlock(q, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}

	/** A layer is dug: what goes in it below the ground laid; after the last, the fence and shed, then on to the tunnel. */
	static Mines.Mine advance(ServerLevel level, Faction f, Mines.Mine m) {
		Shape s = shape(level.getServer());
		Mines.Mine after = m.withLayer(m.layer() + 1);
		if (s == null) {
			// (D&T taken out mid-dig: straight on to the tunnel)
			after = m.withLayer(-m.pit());
		} else {
			int y = s.top() - m.layer();
			if (y < 0) {
				lay(level, s, m, y, false);
			}
			if (!after.inPit()) {
				// (and everything below the open layers: the floor under the lowest, rock where there's a cave)
				for (int k = -1 - DROP; k < s.bottom(); k++) {
					lay(level, s, m, k, false);
				}
				lay(level, s, m, 0, true);
				JaymosKingdoms.LOGGER.info("{}'s mine pit (Dungeons and Taverns') is dug; on to the tunnel", f.name());
			}
		}
		Mines.put(level.getServer(), f.id(), after);
		return after;
	}

	/** The pit's corners, turned as laid. */
	static BlockPos[] footprint(Mines.Mine m) {
		Shape s = shape != null ? shape.orElse(null) : null;
		int sx = s == null ? 16 : s.sx(), sz = s == null ? 11 : s.sz(), lo = s == null ? -11 : s.bottom() - 1, hi = s == null ? 6 : s.top() + 1;
		BlockPos a = world(m.origin(), m.turn(), new BlockPos(0, lo, 0));
		BlockPos b = world(m.origin(), m.turn(), new BlockPos(sx - 1, hi, sz - 1));
		return new BlockPos[] {
			new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
			new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))};
	}

	// ---- the site ---------------------------------------------------------------------------------------------------

	/** The nearest level, dry, natural ground round the seat with room for the pit (nothing built there), or null. */
	static Mines.@Nullable Mine site(ServerLevel level, Faction f, List<int[]> taken, int least) {
		Shape s = shape(level.getServer());
		if (s == null) {
			return null;
		}
		BlockPos seat = f.seat();
		int reach = Mines.SITE_MAX;
		Mines.Mine best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx += MinePit.SITE_STEP) {
			for (int dz = -reach; dz <= reach; dz += MinePit.SITE_STEP) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < least + 8 || d > reach || d >= bestD) {
					continue;
				}
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				for (int turn = 0; turn < 4; turn++) {
					Mines.Mine m = plan(s, at, turn);
					if (fits(level, s, m) && !Mines.clash(level.getServer(), footprint(m), taken)) {
						best = m;
						bestD = d;
						break;
					}
				}
			}
		}
		return best;
	}

	/** Every column of it (and a block round) level, dry, natural ground; no water or lava where it digs. */
	private static boolean fits(ServerLevel level, Shape s, Mines.Mine m) {
		int y0 = m.origin().getY();
		for (int x = -1; x <= s.sx(); x++) {
			for (int z = -1; z <= s.sz(); z++) {
				BlockPos q = world(m.origin(), m.turn(), new BlockPos(x, 0, z));
				if (!MinePit.level(level, q.getX(), q.getZ(), y0)) {
					return false;
				}
			}
		}
		for (Map.Entry<BlockPos, BlockState> e : s.cells().entrySet()) {
			if (e.getValue().isAir() && e.getKey().getY() < 0) {
				BlockPos q = world(m.origin(), m.turn(), e.getKey());
				for (Direction d : Direction.values()) {
					if (!level.getFluidState(q.relative(d)).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
