package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.9) King's Roads: real roads between settlements, our own take on the idea behind Countered's Settlement Roads
 * (which is MIT but 1.21.10-only and knows nothing of realms) and the road half of Via Romana (whose licence bars ports).
 * Nothing of either is copied.
 *
 * <p><b>Planned.</b> Every so often each kingdom, fiefdom and village is linked to its {@link #LINKS} nearest neighbours
 * within {@link #LINK_RANGE} (and a fiefdom to its overlord). The route is found off the server thread on the world
 * generator's own terrain heights (so it can be planned through land nobody has seen yet): an A* search over a grid of
 * {@link #CELL}-block cells that dislikes climbing, cliffs and water, then smoothed and graded. Roads are saved on the
 * Overworld.
 *
 * <p><b>Laid.</b> A road is paved a chunk at a time as its chunks load: a path three wide in the ground's own style (dirt
 * path and gravel in the green, sandstone in the desert, cobble on rock and near towns), graded up to {@link #GRADE}
 * blocks to keep it even, trees in its way felled, plank bridges over water, a lantern post every so often and a
 * milestone at each end saying where it goes and how far. It leaves the settlements' own streets alone and only ever
 * touches natural ground, never anything built. A chunk is paved once; what players do to it after is theirs.
 *
 * <p><b>Followed.</b> {@link #ahead} is how a traveller keeps to a road: cabs, rickshaws, rulers on their rounds
 * (all through {@link Construction#leg}) and caravans make for a point further along the road that leads to where they're
 * going, instead of cutting straight across country.
 */
public final class Roads {
	/** Settlements this far apart (blocks) may be linked; each to its nearest this many (and a fiefdom to its overlord). */
	static final double LINK_RANGE = 700.0;
	static final int LINKS = 2;
	/** How often (ticks) the links are looked over (one new road planned at a time). */
	static final int PLAN_TICKS = 600;
	/** The planning grid (blocks a cell), how far beyond the two ends it may stray, and the most cells it may look at. */
	static final int CELL = 8, MARGIN = 192, MOST_CELLS = 40000;
	/** A cell's cost on top of its length: climbing (per block of rise, squared), a cliff, water (and per block deep). */
	static final double CLIMB = 0.8, CLIFF = 400.0, WET = 12.0, DEPTH = 8.0;
	static final int CLIFF_RISE = 7, DEEP = 6;
	/** Route points this far apart (blocks), their heights averaged over this many either side. */
	static final int STEP = 4, SMOOTH = 4;
	/** Left alone this near a settlement's seat (its own streets); paved in stone this near. */
	static final double TOWN_CLEAR = 56.0, PAVED_NEAR = 120.0;
	/** Half its width on land and on a bridge. */
	static final int HALF = 1, BRIDGE_HALF = 2;
	/** Graded up or down at most this many blocks to keep it even (more than that, it follows the ground). */
	static final int GRADE = 3;
	/** A lantern post every this many route points; trees felled up to this many logs. */
	static final int LAMP_EVERY = 12, TREE_MOST = 160;
	/** Chunks paved a tick, at most. */
	static final int CHUNKS_PER_TICK = 2;
	/** Kept to: the road ending within this of the goal, by a traveller this near it, on trips longer than this. */
	static final double END_NEAR = 64.0, JOIN = 96.0, LEAST = 48.0;
	/** A road is only followed if what's left of it is at most this times the straight distance (plus a little). */
	static final double DETOUR = 2.0;

	/** One road: between factions {@code a} and {@code b}, its route (packed block positions), the chunks it's paved in. */
	public record Road(int a, int b, List<Long> points, List<Long> laid) {
		static final Codec<Road> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("a").forGetter(Road::a),
				Codec.INT.fieldOf("b").forGetter(Road::b),
				Codec.LONG.listOf().fieldOf("points").forGetter(Road::points),
				Codec.LONG.listOf().optionalFieldOf("laid", List.of()).forGetter(Road::laid))
			.apply(i, Road::new));

		String key() {
			return Roads.key(this.a, this.b);
		}
	}

	public record Network(List<Road> roads) {
		static final Network EMPTY = new Network(List.of());
		static final Codec<Network> CODEC = RecordCodecBuilder.create(i -> i.group(
				Road.CODEC.listOf().optionalFieldOf("roads", List.of()).forGetter(Network::roads))
			.apply(i, Network::new));
	}

	public static final AttachmentType<Network> ROADS = AttachmentRegistry.create(JaymosKingdoms.id("roads"),
		builder -> builder.persistent(Network.CODEC));

	/** A road as a line to follow: its points, and how far along it each one is. */
	private static final class Line {
		final double[] x, y, z, along;
		final double minX, maxX, minZ, maxZ;

		Line(List<Long> points) {
			int n = points.size();
			this.x = new double[n];
			this.y = new double[n];
			this.z = new double[n];
			this.along = new double[n];
			double lx = Double.MAX_VALUE, hx = -Double.MAX_VALUE, lz = Double.MAX_VALUE, hz = -Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				long p = points.get(i);
				this.x[i] = BlockPos.getX(p) + 0.5;
				this.y[i] = BlockPos.getY(p) + 1.0;
				this.z[i] = BlockPos.getZ(p) + 0.5;
				this.along[i] = i == 0 ? 0.0 : this.along[i - 1] + Math.hypot(this.x[i] - this.x[i - 1], this.z[i] - this.z[i - 1]);
				lx = Math.min(lx, this.x[i]);
				hx = Math.max(hx, this.x[i]);
				lz = Math.min(lz, this.z[i]);
				hz = Math.max(hz, this.z[i]);
			}
			this.minX = lx;
			this.maxX = hx;
			this.minZ = lz;
			this.maxZ = hz;
		}

		double length() {
			return this.along[this.along.length - 1];
		}

		boolean near(double px, double pz, double reach) {
			return px >= this.minX - reach && px <= this.maxX + reach && pz >= this.minZ - reach && pz <= this.maxZ + reach;
		}

		/** How far along the road the nearest point to (px, pz) is, and how far off the road (px, pz) is. */
		double[] project(double px, double pz) {
			double bestS = 0.0, best = Double.MAX_VALUE;
			for (int i = 0; i + 1 < this.x.length; i++) {
				double sx = this.x[i + 1] - this.x[i], sz = this.z[i + 1] - this.z[i];
				double len2 = sx * sx + sz * sz;
				double t = len2 < 1.0E-6 ? 0.0 : Mth.clamp(((px - this.x[i]) * sx + (pz - this.z[i]) * sz) / len2, 0.0, 1.0);
				double cx = this.x[i] + sx * t, cz = this.z[i] + sz * t;
				double d = Math.hypot(px - cx, pz - cz);
				if (d < best) {
					best = d;
					bestS = this.along[i] + (this.along[i + 1] - this.along[i]) * t;
				}
			}
			return new double[] {bestS, best};
		}

		/** The point this far along the road. */
		Vec3 at(double s) {
			s = Mth.clamp(s, 0.0, this.length());
			int i = 0;
			while (i + 2 < this.along.length && this.along[i + 1] < s) {
				i++;
			}
			double seg = this.along[i + 1] - this.along[i];
			double t = seg < 1.0E-6 ? 0.0 : (s - this.along[i]) / seg;
			return new Vec3(Mth.lerp(t, this.x[i], this.x[i + 1]), Mth.lerp(t, this.y[i], this.y[i + 1]), Mth.lerp(t, this.z[i], this.z[i + 1]));
		}
	}

	private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Kingdoms road planner");
		t.setDaemon(true);
		return t;
	});
	private static @Nullable CompletableFuture<Road> planning;
	/** Links found unplannable this session (not tried again till a restart). */
	private static final Set<String> GIVEN_UP = new HashSet<>();
	private static final Map<Road, Line> LINES = new IdentityHashMap<>();
	private static @Nullable Network indexed;
	private static Map<Long, List<Integer>> index = Map.of();
	private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private Roads() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			finish(server, level);
			if (level.getGameTime() % PLAN_TICKS == 0) {
				plan(server, level, false);
			}
			pave(level);
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level == Factions.level(level.getServer())) {
				long key = chunk.getPos().pack();
				if (index(level.getServer()).containsKey(key)) {
					queue(key);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (planning != null) {
				planning.cancel(true);
			}
			planning = null;
			GIVEN_UP.clear();
			LINES.clear();
			indexed = null;
			index = Map.of();
			QUEUE.clear();
			QUEUED.clear();
		});
	}

	static String key(int a, int b) {
		return Math.min(a, b) + "-" + Math.max(a, b);
	}

	public static Network network(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(ROADS, Network.EMPTY);
	}

	private static void save(MinecraftServer server, Network network) {
		Factions.level(server).setAttached(ROADS, network);
	}

	private static Line line(Road road) {
		return LINES.computeIfAbsent(road, r -> new Line(r.points()));
	}

	/** Which roads pass through each chunk (rebuilt when the roads change). */
	private static Map<Long, List<Integer>> index(MinecraftServer server) {
		Network net = network(server);
		if (net != indexed) {
			Map<Long, List<Integer>> built = new HashMap<>();
			for (int r = 0; r < net.roads().size(); r++) {
				Road road = net.roads().get(r);
				Set<Long> laid = new HashSet<>(road.laid());
				for (long chunk : chunks(road)) {
					if (!laid.contains(chunk)) {
						built.computeIfAbsent(chunk, k -> new ArrayList<>()).add(r);
					}
				}
			}
			index = built;
			indexed = net;
			LINES.keySet().retainAll(net.roads());
		}
		return index;
	}

	/** Every chunk a road (and its bridges, lamps and milestones) touches. */
	private static Set<Long> chunks(Road road) {
		Set<Long> out = new HashSet<>();
		int reach = BRIDGE_HALF + 3;
		for (long p : road.points()) {
			int x = BlockPos.getX(p), z = BlockPos.getZ(p);
			for (int dx = -reach; dx <= reach; dx += reach) {
				for (int dz = -reach; dz <= reach; dz += reach) {
					out.add(ChunkPos.pack((x + dx) >> 4, (z + dz) >> 4));
				}
			}
		}
		return out;
	}

	private static void queue(long chunk) {
		if (QUEUED.add(chunk)) {
			QUEUE.add(chunk);
		}
	}

	// ---- planning ----------------------------------------------------------------------------------------------------

	/** The settlements roads join: kingdoms, fiefdoms and villages still standing. */
	private static List<Faction> towns(MinecraftServer server) {
		return Factions.all(server).stream().filter(f -> !f.fallen() && f.type() != FactionType.BARBARIAN).toList();
	}

	/** The links that should have roads, nearest first. */
	static List<Faction[]> wanted(MinecraftServer server) {
		List<Faction> towns = towns(server);
		Map<String, Faction[]> links = new HashMap<>();
		for (Faction f : towns) {
			towns.stream().filter(g -> g.id() != f.id() && g.seat().closerThan(f.seat(), LINK_RANGE))
				.sorted(Comparator.comparingDouble(g -> g.seat().distSqr(f.seat())))
				.limit(LINKS)
				.forEach(g -> links.putIfAbsent(key(f.id(), g.id()), new Faction[] {f, g}));
			if (f.overlord() >= 0) {
				Faction lord = Factions.get(server, f.overlord());
				if (lord != null && !lord.fallen() && lord.seat().closerThan(f.seat(), LINK_RANGE * 1.5)) {
					links.putIfAbsent(key(f.id(), lord.id()), new Faction[] {f, lord});
				}
			}
		}
		List<Faction[]> out = new ArrayList<>(links.values());
		out.sort(Comparator.comparingDouble(l -> l[0].seat().distSqr(l[1].seat())));
		return out;
	}

	/** Starts planning the next missing road (one at a time). {@code all}: every missing one is waited for in turn. */
	private static int plan(MinecraftServer server, ServerLevel level, boolean all) {
		Set<String> have = new HashSet<>();
		for (Road r : network(server).roads()) {
			have.add(r.key());
		}
		int missing = 0;
		for (Faction[] link : wanted(server)) {
			String key = key(link[0].id(), link[1].id());
			if (have.contains(key) || GIVEN_UP.contains(key)) {
				continue;
			}
			missing++;
			if (planning == null) {
				start(level, link[0], link[1]);
			}
			if (!all) {
				break;
			}
		}
		return missing;
	}

	private static void start(ServerLevel level, Faction a, Faction b) {
		BlockPos from = Caravans.start(a), to = Caravans.start(b);
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int ida = a.id(), idb = b.id();
		LevelHeightAccessor heights = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
		planning = CompletableFuture.supplyAsync(() -> new Road(ida, idb, route(gen, random, heights, from, to), List.of()), PLANNER);
		JaymosKingdoms.LOGGER.debug("planning a road between {} and {}", a.name(), b.name());
	}

	/** A finished plan saved, its loaded chunks set to be paved. */
	private static void finish(MinecraftServer server, ServerLevel level) {
		CompletableFuture<Road> done = planning;
		if (done == null || !done.isDone()) {
			return;
		}
		planning = null;
		Road road;
		try {
			road = done.join();
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("a road couldn't be planned", e);
			return;
		}
		if (road.points().size() < 2) {
			GIVEN_UP.add(road.key());
			return;
		}
		Network net = network(server);
		List<Road> roads = new ArrayList<>(net.roads());
		roads.removeIf(r -> r.key().equals(road.key()));
		roads.add(road);
		save(server, new Network(roads));
		for (long chunk : chunks(road)) {
			if (level.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
				queue(chunk);
			}
		}
	}

	/** Terrain at a column: its surface (top block + 1, water counted) and how deep the water over the floor is. */
	private record Ground(int surface, int depth) {
	}

	/** The route from {@code from} to {@code to} as packed positions {@link #STEP} apart, each at its graded height. */
	static List<Long> route(ChunkGenerator gen, RandomState random, LevelHeightAccessor heights, BlockPos from, BlockPos to) {
		Map<Long, Ground> seen = new HashMap<>();
		java.util.function.LongFunction<Ground> ground = key -> seen.computeIfAbsent(key, k -> {
			int x = (int)(k >> 32), z = (int)(long)k;
			int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
			int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
			return new Ground(surface, Math.max(0, surface - floor));
		});
		List<int[]> cells = search(ground, from, to);
		// the cells as a line (from its true ends), its corners rounded twice
		List<double[]> line = new ArrayList<>();
		line.add(new double[] {from.getX() + 0.5, from.getZ() + 0.5});
		for (int i = 1; i + 1 < cells.size(); i++) {
			line.add(new double[] {cells.get(i)[0] * CELL + CELL / 2.0, cells.get(i)[1] * CELL + CELL / 2.0});
		}
		line.add(new double[] {to.getX() + 0.5, to.getZ() + 0.5});
		for (int pass = 0; pass < 2; pass++) {
			List<double[]> round = new ArrayList<>();
			round.add(line.getFirst());
			for (int i = 0; i + 1 < line.size(); i++) {
				double[] p = line.get(i), q = line.get(i + 1);
				round.add(new double[] {p[0] * 0.75 + q[0] * 0.25, p[1] * 0.75 + q[1] * 0.25});
				round.add(new double[] {p[0] * 0.25 + q[0] * 0.75, p[1] * 0.25 + q[1] * 0.75});
			}
			round.add(line.getLast());
			line = round;
		}
		// walked out a step at a time
		List<double[]> steps = new ArrayList<>();
		steps.add(line.getFirst());
		double carry = 0.0;
		for (int i = 0; i + 1 < line.size(); i++) {
			double[] p = line.get(i), q = line.get(i + 1);
			double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
			double s = STEP - carry;
			while (s <= len) {
				double t = s / len;
				steps.add(new double[] {Mth.lerp(t, p[0], q[0]), Mth.lerp(t, p[1], q[1])});
				s += STEP;
			}
			carry = len - (s - STEP);
		}
		steps.add(line.getLast());
		// heights: the ground's, evened out; over water, the water's top
		int n = steps.size();
		int[] raw = new int[n];
		boolean[] wet = new boolean[n];
		for (int i = 0; i < n; i++) {
			Ground g = ground.apply(pack(Mth.floor(steps.get(i)[0]), Mth.floor(steps.get(i)[1])));
			raw[i] = g.surface() - 1;
			wet[i] = g.depth() > 0;
		}
		List<Long> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int y = raw[i];
			if (!wet[i]) {
				double sum = 0.0;
				int count = 0;
				for (int j = Math.max(0, i - SMOOTH); j <= Math.min(n - 1, i + SMOOTH); j++) {
					if (!wet[j]) {
						sum += raw[j];
						count++;
					}
				}
				y = (int)Math.round(sum / count);
			}
			out.add(BlockPos.asLong(Mth.floor(steps.get(i)[0]), y, Mth.floor(steps.get(i)[1])));
		}
		return out;
	}

	private static long pack(int x, int z) {
		return ((long)x << 32) | (z & 0xFFFFFFFFL);
	}

	/** A* over the cells between the two ends; straight across if it can't be found within {@link #MOST_CELLS}. */
	private static List<int[]> search(java.util.function.LongFunction<Ground> ground, BlockPos from, BlockPos to) {
		int sx = Math.floorDiv(from.getX(), CELL), sz = Math.floorDiv(from.getZ(), CELL);
		int ex = Math.floorDiv(to.getX(), CELL), ez = Math.floorDiv(to.getZ(), CELL);
		int margin = MARGIN / CELL;
		int lx = Math.min(sx, ex) - margin, hx = Math.max(sx, ex) + margin, lz = Math.min(sz, ez) - margin, hz = Math.max(sz, ez) + margin;
		java.util.function.LongFunction<Ground> cell = k -> ground.apply(pack((int)(k >> 32) * CELL + CELL / 2, (int)k * CELL + CELL / 2));
		Map<Long, Double> cost = new HashMap<>();
		Map<Long, Long> came = new HashMap<>();
		PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
		long start = pack(sx, sz), end = pack(ex, ez);
		cost.put(start, 0.0);
		open.add(new double[] {0.0, sx, sz});
		int looked = 0;
		boolean found = false;
		while (!open.isEmpty() && looked < MOST_CELLS) {
			double[] e = open.poll();
			int cx = (int)e[1], cz = (int)e[2];
			long here = pack(cx, cz);
			double g = cost.get(here);
			if (e[0] > g + Math.hypot(cx - ex, cz - ez) * CELL + 1.0E-6) {
				continue;
			}
			if (here == end) {
				found = true;
				break;
			}
			looked++;
			Ground at = cell.apply(here);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int nx = cx + dx, nz = cz + dz;
					if (nx < lx || nx > hx || nz < lz || nz > hz) {
						continue;
					}
					long next = pack(nx, nz);
					Ground there = cell.apply(next);
					double step = (dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0) * CELL;
					int rise = Math.abs(there.surface() - at.surface());
					double c = step + CLIMB * rise * rise;
					if (rise > CLIFF_RISE) {
						c += CLIFF;
					}
					if (there.depth() > 0) {
						c += WET + DEPTH * Math.min(there.depth(), 20);
						if (there.depth() > DEEP) {
							c += CLIFF;
						}
					}
					double ng = g + c;
					Double old = cost.get(next);
					if (old == null || ng < old) {
						cost.put(next, ng);
						came.put(next, here);
						open.add(new double[] {ng + Math.hypot(nx - ex, nz - ez) * CELL, nx, nz});
					}
				}
			}
		}
		List<int[]> cells = new ArrayList<>();
		if (found) {
			for (Long k = end; k != null; k = came.get(k)) {
				cells.add(new int[] {(int)(k >> 32), (int)(long)k});
			}
			java.util.Collections.reverse(cells);
		} else {
			// (too far round, or no way at all: straight across, bridges and all)
			int steps = Math.max(Math.abs(ex - sx), Math.abs(ez - sz));
			for (int i = 0; i <= steps; i++) {
				double t = steps == 0 ? 0.0 : (double)i / steps;
				cells.add(new int[] {(int)Math.round(Mth.lerp(t, sx, ex)), (int)Math.round(Mth.lerp(t, sz, ez))});
			}
		}
		return cells;
	}

	// ---- paving ------------------------------------------------------------------------------------------------------

	/** Paves a few of the queued chunks that are loaded. */
	private static void pave(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (int done = 0; done < CHUNKS_PER_TICK && !QUEUE.isEmpty(); done++) {
			long chunk = QUEUE.poll();
			QUEUED.remove(chunk);
			int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
			if (!level.hasChunk(cx, cz)) {
				continue;
			}
			List<Integer> which = index(server).get(chunk);
			if (which == null) {
				continue;
			}
			Network net = network(server);
			List<Road> roads = new ArrayList<>(net.roads());
			List<BlockPos> seats = towns(server).stream().map(Faction::seat).toList();
			for (int r : which) {
				if (r >= roads.size()) {
					continue;
				}
				Road road = roads.get(r);
				pave(level, road, cx, cz, seats);
				List<Long> laid = new ArrayList<>(road.laid());
				laid.add(chunk);
				roads.set(r, new Road(road.a(), road.b(), road.points(), laid));
			}
			save(server, new Network(roads));
		}
	}

	/** What one road lays in one chunk. */
	private static void pave(ServerLevel level, Road road, int cx, int cz, List<BlockPos> seats) {
		int x0 = cx << 4, z0 = cz << 4, x1 = x0 + 15, z1 = z0 + 15;
		List<Long> pts = road.points();
		RandomSource random = RandomSource.create(ChunkPos.pack(cx, cz) * 31L + road.key().hashCode());
		// the columns it covers here, each with the height it's graded to
		Map<Long, Integer> columns = new HashMap<>();
		Set<Long> wide = new HashSet<>();
		for (int i = 0; i + 1 < pts.size(); i++) {
			long p = pts.get(i), q = pts.get(i + 1);
			int px = BlockPos.getX(p), pz = BlockPos.getZ(p), qx = BlockPos.getX(q), qz = BlockPos.getZ(q);
			int reach = BRIDGE_HALF + 1;
			if (Math.max(px, qx) < x0 - reach || Math.min(px, qx) > x1 + reach || Math.max(pz, qz) < z0 - reach || Math.min(pz, qz) > z1 + reach) {
				continue;
			}
			double dx = qx - px, dz = qz - pz;
			double len = Math.max(1.0E-3, Math.hypot(dx, dz));
			double ox = -dz / len, oz = dx / len;
			int n = (int)Math.ceil(len * 2.0);
			for (int s = 0; s <= n; s++) {
				double t = (double)s / n;
				double x = Mth.lerp(t, px + 0.5, qx + 0.5), z = Mth.lerp(t, pz + 0.5, qz + 0.5);
				int y = (int)Math.round(Mth.lerp(t, BlockPos.getY(p), BlockPos.getY(q)));
				for (int w = -BRIDGE_HALF; w <= BRIDGE_HALF; w++) {
					int bx = Mth.floor(x + ox * w), bz = Mth.floor(z + oz * w);
					if (bx < x0 || bx > x1 || bz < z0 || bz > z1) {
						continue;
					}
					long key = pack(bx, bz);
					if (Math.abs(w) <= HALF) {
						columns.putIfAbsent(key, y);
						wide.remove(key);
					} else if (!columns.containsKey(key)) {
						columns.put(key, y);
						wide.add(key);
					}
				}
			}
		}
		for (Map.Entry<Long, Integer> e : columns.entrySet()) {
			int x = (int)(e.getKey() >> 32), z = (int)(long)e.getKey();
			double town = nearest(seats, x, z);
			if (town < TOWN_CLEAR) {
				continue;
			}
			column(level, x, z, e.getValue(), town < PAVED_NEAR, wide.contains(e.getKey()), random);
		}
		// lantern posts along it, and a milestone at each end
		Line line = line(road);
		for (int i = LAMP_EVERY / 2; i < pts.size(); i += LAMP_EVERY) {
			int side = (i / LAMP_EVERY) % 2 == 0 ? 1 : -1;
			BlockPos at = beside(pts, i, side * (HALF + 2));
			if (inside(at, x0, z0) && nearest(seats, at.getX(), at.getZ()) >= TOWN_CLEAR) {
				lamp(level, at);
			}
		}
		int first = -1, last = -1;
		for (int i = 0; i < pts.size(); i++) {
			long p = pts.get(i);
			if (nearest(seats, BlockPos.getX(p), BlockPos.getZ(p)) >= TOWN_CLEAR + 4) {
				if (first < 0) {
					first = i;
				}
				last = i;
			}
		}
		if (first >= 0 && last > first) {
			MinecraftServer server = level.getServer();
			Faction a = Factions.get(server, road.a()), b = Factions.get(server, road.b());
			BlockPos atA = beside(pts, first, HALF + 2), atB = beside(pts, last, -(HALF + 2));
			if (a != null && b != null) {
				if (inside(atA, x0, z0)) {
					milestone(level, atA, pts, first, first + 1, b, a, (int)(line.length() - line.along[first]));
				}
				if (inside(atB, x0, z0)) {
					milestone(level, atB, pts, last, last - 1, a, b, (int)line.along[last]);
				}
			}
		}
	}

	private static boolean inside(BlockPos at, int x0, int z0) {
		return at.getX() >= x0 && at.getX() < x0 + 16 && at.getZ() >= z0 && at.getZ() < z0 + 16;
	}

	private static double nearest(List<BlockPos> seats, int x, int z) {
		double best = Double.MAX_VALUE;
		for (BlockPos s : seats) {
			best = Math.min(best, Math.hypot(s.getX() - x, s.getZ() - z));
		}
		return best;
	}

	/** The column {@code off} blocks to the side of route point {@code i} (left of the way it runs for positive). */
	private static BlockPos beside(List<Long> pts, int i, int off) {
		long p = pts.get(i), q = pts.get(Math.min(pts.size() - 1, i + 1)), o = pts.get(Math.max(0, i - 1));
		double dx = BlockPos.getX(q) - BlockPos.getX(o), dz = BlockPos.getZ(q) - BlockPos.getZ(o);
		double len = Math.max(1.0E-3, Math.hypot(dx, dz));
		return new BlockPos(Mth.floor(BlockPos.getX(p) + 0.5 - dz / len * off), BlockPos.getY(p), Mth.floor(BlockPos.getZ(p) + 0.5 + dx / len * off));
	}

	/** Ground nobody built: what a road may be cut through or laid on. */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SNOW)
			|| s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.MUD) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE)
			|| s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE);
	}

	/** What may be cleared off a road: plants, snow, air. */
	private static boolean clearable(ServerLevel level, BlockPos pos, BlockState s) {
		return s.isAir() || (s.canBeReplaced() && level.getFluidState(pos).isEmpty());
	}

	/** One column of road: graded, surfaced, its way cleared; or a bridge plank over water. */
	private static void column(ServerLevel level, int x, int z, int graded, boolean paved, boolean edge, RandomSource random) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
		for (int i = 0; i < 12 && (level.getBlockState(top).is(BlockTags.LOGS) || clearable(level, top, level.getBlockState(top))); i++) {
			BlockState s = level.getBlockState(top);
			if (s.is(BlockTags.LOGS) && !fell(level, top)) {
				return;
			}
			top = top.below();
		}
		BlockState ground = level.getBlockState(top);
		if (!level.getFluidState(top).isEmpty()) {
			if (!level.getFluidState(top).is(net.minecraft.tags.FluidTags.WATER) || !level.getFluidState(top).isSource()) {
				return;
			}
			// a bridge: planks on the water, a rail along its edges
			level.setBlock(top, Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
			if (edge && clearable(level, top.above(), level.getBlockState(top.above()))) {
				level.setBlock(top.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
			}
			return;
		}
		if (edge || !natural(ground)) {
			return;
		}
		int y = Math.abs(top.getY() - graded) <= GRADE ? graded : top.getY();
		BlockState fill = ground.is(BlockTags.SAND) ? (ground.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE).defaultBlockState()
			: Blocks.DIRT.defaultBlockState();
		for (int fy = top.getY() + 1; fy < y; fy++) {
			BlockPos at = new BlockPos(x, fy, z);
			if (clearable(level, at, level.getBlockState(at))) {
				level.setBlock(at, fill, Block.UPDATE_CLIENTS);
			}
		}
		for (int fy = top.getY(); fy > y; fy--) {
			BlockPos at = new BlockPos(x, fy, z);
			if (natural(level.getBlockState(at))) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		BlockPos road = new BlockPos(x, y, z);
		BlockState under = level.getBlockState(road);
		if (!natural(under) && !clearable(level, road, under)) {
			return;
		}
		for (int up = 1; up <= 3; up++) {
			BlockPos at = road.above(up);
			BlockState s = level.getBlockState(at);
			if (s.is(BlockTags.LOGS)) {
				fell(level, at);
			} else if (clearable(level, at, s) && !s.isAir()) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(road, surface(ground, paved, random), Block.UPDATE_CLIENTS);
	}

	/** The road's top: in the style of the ground it crosses (stone near towns). */
	private static BlockState surface(BlockState ground, boolean paved, RandomSource random) {
		int roll = random.nextInt(100);
		Block b;
		if (ground.is(BlockTags.SAND)) {
			boolean red = ground.is(Blocks.RED_SAND);
			b = roll < 70 ? (red ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE) : roll < 90 ? (red ? Blocks.CUT_RED_SANDSTONE : Blocks.CUT_SANDSTONE)
				: (red ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE);
		} else if (paved) {
			b = roll < 60 ? Blocks.COBBLESTONE : roll < 80 ? Blocks.MOSSY_COBBLESTONE : roll < 92 ? Blocks.STONE_BRICKS : Blocks.GRAVEL;
		} else if (ground.is(BlockTags.BASE_STONE_OVERWORLD)) {
			b = roll < 50 ? Blocks.COBBLESTONE : roll < 75 ? Blocks.ANDESITE : Blocks.GRAVEL;
		} else if (ground.is(BlockTags.MUD)) {
			b = roll < 60 ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT;
		} else {
			b = roll < 78 ? Blocks.DIRT_PATH : roll < 90 ? Blocks.COARSE_DIRT : Blocks.GRAVEL;
		}
		return b.defaultBlockState();
	}

	/**
	 * A tree in the road's way taken down whole (its logs, its leaves left to fall away), if it is one: logs with
	 * natural leaves on them, not a log wall.
	 */
	private static boolean fell(ServerLevel level, BlockPos log) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(log);
		seen.add(log);
		boolean leafy = false;
		while (!open.isEmpty() && logs.size() < TREE_MOST) {
			BlockPos p = open.poll();
			logs.add(p);
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				BlockState s = level.getBlockState(n);
				if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
					leafy = true;
				} else if (s.is(BlockTags.LOGS) && seen.add(n.immutable())) {
					open.add(n.immutable());
				}
			}
		}
		if (!leafy || !open.isEmpty()) {
			return false;
		}
		for (BlockPos p : logs) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		return true;
	}

	/** A lantern on a post beside the road. */
	private static void lamp(ServerLevel level, BlockPos near) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near).below();
		if (!natural(level.getBlockState(top)) || !level.getFluidState(top).isEmpty()) {
			return;
		}
		for (int up = 1; up <= 3; up++) {
			if (!clearable(level, top.above(up), level.getBlockState(top.above(up)))) {
				return;
			}
		}
		level.setBlock(top.above(1), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(top.above(2), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(top.above(3), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
	}

	/**
	 * A milestone at a road's end: a post and a sign that reads, to those setting out, where the road goes and how far;
	 * on its back, to those arriving, where they've come to.
	 */
	private static void milestone(ServerLevel level, BlockPos near, List<Long> pts, int i, int toward, Faction there, Faction here, int blocks) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near).below();
		if (!natural(level.getBlockState(top)) || !level.getFluidState(top).isEmpty()) {
			return;
		}
		for (int up = 1; up <= 2; up++) {
			if (!clearable(level, top.above(up), level.getBlockState(top.above(up)))) {
				return;
			}
		}
		long p = pts.get(i), q = pts.get(Math.max(0, Math.min(pts.size() - 1, toward)));
		double dx = BlockPos.getX(q) - BlockPos.getX(p), dz = BlockPos.getZ(q) - BlockPos.getZ(p);
		float yaw = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		level.setBlock(top.above(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos at = top.above(2);
		level.setBlock(at, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180.0F)),
			Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
			List<Component> front = List.of(
				Component.translatable("sign.jaymoskingdoms.road.title").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("sign.jaymoskingdoms.road.to", there.name()),
				Component.translatable("sign.jaymoskingdoms.road.blocks", blocks),
				Component.empty());
			List<Component> back = List.of(
				Component.empty(),
				Component.translatable("sign.jaymoskingdoms.road.welcome"),
				Component.literal(here.name()),
				Component.empty());
			sign.setText(new SignText(front, front, DyeColor.BLACK, false), SignTextSlot.FRONT);
			sign.setText(new SignText(back, back, DyeColor.BLACK, false), SignTextSlot.BACK);
			sign.setWaxed(true);
			sign.setChanged();
			BlockState state = level.getBlockState(at);
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}

	// ---- following -----------------------------------------------------------------------------------------------------

	/**
	 * Where someone at {@code here} going to {@code goal} should make for next to keep to the road: a point {@code step}
	 * blocks further along it, or (off it) {@code step} blocks toward it. Null when there's no road that way (or the trip is
	 * short, or the road's end is near): go straight.
	 */
	public static @Nullable Vec3 ahead(ServerLevel level, Vec3 here, BlockPos goal, double step) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return null;
		}
		double gx = goal.getX() + 0.5, gz = goal.getZ() + 0.5;
		double straight = Math.hypot(here.x - gx, here.z - gz);
		if (straight < LEAST) {
			return null;
		}
		Line best = null;
		double bestS = 0.0, bestD = Double.MAX_VALUE;
		boolean bestForward = true;
		for (Road road : network(server).roads()) {
			Line l = line(road);
			int n = l.x.length - 1;
			double dA = Math.hypot(l.x[0] - gx, l.z[0] - gz), dB = Math.hypot(l.x[n] - gx, l.z[n] - gz);
			if (Math.min(dA, dB) > END_NEAR || !l.near(here.x, here.z, JOIN)) {
				continue;
			}
			boolean forward = dB <= dA;
			double[] p = l.project(here.x, here.z);
			double s = p[0], d = p[1];
			double left = forward ? l.length() - s : s;
			if (d > JOIN || left < step || left + d > straight * DETOUR + 32.0) {
				continue;
			}
			if (d < bestD) {
				best = l;
				bestS = s;
				bestD = d;
				bestForward = forward;
			}
		}
		if (best == null) {
			return null;
		}
		if (bestD > step) {
			// off the road: toward it
			Vec3 on = best.at(bestS);
			Vec3 flat = new Vec3(on.x - here.x, 0.0, on.z - here.z);
			return here.add(flat.scale(step / flat.length()));
		}
		return best.at(bestForward ? bestS + step : bestS - step);
	}

	// ---- commands -----------------------------------------------------------------------------------------------------

	/** {@code /kingdom roads}: the roads and how far laid; {@code plan}: every missing one planned now. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("roads").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(Roads::list)
			.then(Commands.literal("plan").executes(c -> {
				MinecraftServer server = c.getSource().getServer();
				int missing = plan(server, Factions.level(server), false);
				c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roads.plan", missing), false);
				return missing;
			}));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		List<Road> roads = network(server).roads();
		c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roads.count", roads.size()), false);
		for (Road r : roads) {
			Faction a = Factions.get(server, r.a()), b = Factions.get(server, r.b());
			int length = (int)line(r).length();
			int chunks = chunks(r).size();
			c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roads.road", a == null ? "?" : a.name(),
				b == null ? "?" : b.name(), length, r.laid().size(), chunks), false);
		}
		return roads.size();
	}
}
