package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The foreman's planning: while a faction's economy is healthy, it has the people it needs and it's at peace
 * ({@link Economy}), it picks its next building from its type's growth order ({@link Blueprint#catalogue}) and a site
 * for it, and starts a project ({@link Works.Project}). Plots its lords marked come first. Otherwise the site is open
 * natural ground inside its land, near its roads, never on anything built (only the land's own soil and stone, plants
 * and air; no logs, no paths, nothing placed), not over water or lava, level within {@link #LEVEL_TOLERANCE}, and clear
 * of every other building, project and plot. Sites are looked for ring by ring outward from the seat, so the town grows
 * outward; an entry may have its own rings ({@link #setRings}: walls go up at the edge of town).
 * <p>
 * Since 0.4.1 a site may also stand on an old structure the world generated ({@link Ruins}: a village's empty houses,
 * ATi's ruins): the builders tear it down as they go. Natural ground is preferred ({@link #RUINS_PENALTY}); old
 * structures win when the natural ground is much further out, so the town grows through them instead of round them.
 */
public final class Planner {
	/** (0.5.7) How many variants of a building are tried for a site before the next building in line. */
	static final int VARIANT_TRIES = 3;
	/** Projects a faction runs at once. */
	public static final int PROJECTS_AT_ONCE = 1;
	/** How far the ground under a site may rise or fall from its floor. */
	static final int LEVEL_TOLERANCE = 2;
	/** Untouched ground kept round a site (it must be natural too), and the gap kept to other buildings. */
	static final int MARGIN = 1, GAP = 2;
	/** Open air needed above a site's tallest part. */
	static final int CLEARANCE = 2;
	/** Rings: the first (blocks from the seat), the step between them, the spacing of candidates along a ring. */
	static final int RING_MIN = 12, RING_STEP = 6, ANGLE_SPACING = 7, MIN_ANGLES = 12;
	/** Rings looked at past the first that had a site (a site by a road a little further out may be better). */
	static final int RING_LOOKAHEAD = 2;
	/** The most candidates looked at in one go. */
	static final int MAX_CANDIDATES = 700;
	/** The bell (or fire) keeps this much open ground round it. */
	static final int SEAT_CLEAR = 6;
	/** Land's reach when a faction holds no chunks (a village inside a realm's land): this far round its seat. */
	static final int NO_LAND_RADIUS = 44;
	/** The furthest out it ever builds. */
	static final int MAX_RADIUS = 120;
	/** A road this close to a site; how far it looks for one; the penalty for none; a ring's weight against roads. */
	static final int ROAD_REACH = 10, ROAD_STEP = 2, NO_ROAD_PENALTY = 30, RING_WEIGHT = 3;
	/** A site on an old structure scores this much worse than natural ground (four rings' worth). */
	static final double RUINS_PENALTY = 12.0;
	/** How long a faction's next needs are remembered (ticks). */
	static final long NEEDS_CACHE_TICKS = 100L;

	/** One block of a layout: where it goes, what, and what it costs. */
	public record Op(BlockPos pos, BlockState state, Materials.@Nullable Cost cost) {
	}

	/**
	 * A blueprint set down at a site and turned: its footprint ({@code min} to {@code max}, x and z), its height, its
	 * blocks in the order they're laid (bottom up, like materials together), and the spots it fills.
	 */
	public record Layout(Blueprint blueprint, String entry, BlockPos min, BlockPos max, int height, List<Op> ops, Set<BlockPos> filled, boolean plot,
		boolean ruins) {
		public int width() {
			return this.max.getX() - this.min.getX() + 1;
		}

		public int depth() {
			return this.max.getZ() - this.min.getZ() + 1;
		}

		public int columns() {
			return this.width() * this.depth();
		}

		public BlockPos center() {
			return new BlockPos((this.min.getX() + this.max.getX()) / 2, this.min.getY(), (this.min.getZ() + this.max.getZ()) / 2);
		}

		/** Column {@code i} (prep goes column by column, row by row). */
		public BlockPos column(int i) {
			return new BlockPos(this.min.getX() + i % this.width(), this.min.getY(), this.min.getZ() + i / this.width());
		}
	}

	/** Where a building goes: its corner, how it's turned, and whether it's on an old structure to be torn down. */
	record Placing(BlockPos origin, Rotation rotation, double score, boolean ruins) {
	}

	/** A site's floor, and whether it stands on an old structure. */
	record Footing(int floor, boolean ruins) {
	}

	private static final Map<String, int[]> RINGS = new HashMap<>();
	private static final Map<Integer, Map<Item, Integer>> NEEDS = new HashMap<>();
	private static final Map<Integer, Long> NEEDS_AT = new HashMap<>();

	private Planner() {
	}

	/** Builds of {@code entry} (any faction type) go up between {@code min} and {@code max} blocks from the seat. */
	public static void setRings(String entry, int min, int max) {
		RINGS.put(entry, new int[] {min, max});
	}

	/** The settlement's size, for the catalogue's gates: its villagers and its folk. */
	public static int size(Faction faction) {
		return faction.holdings().population() + faction.holdings().staff();
	}

	/**
	 * It may start a project now: healthy, staffed, at peace, room for another project, no rubble left to clear
	 * ({@link Debris}), and no mine being dug unless its crew can spare the hands ({@link Mines#mayBuild}).
	 */
	static boolean ready(ServerLevel level, Faction f) {
		return Works.of(level.getServer(), f.id()).projects().size() < PROJECTS_AT_ONCE && !Debris.busy(level.getServer(), f.id())
			&& Mines.mayBuild(level.getServer(), f)
			&& Economy.adequatePersonnel(f)
			&& Economy.atPeace(level.getServer(), f) && Economy.healthy(level, f);
	}

	/** The catalogue entries it may build next (in growth order), whatever the site. */
	static List<Blueprint.Entry> eligible(MinecraftServer server, Faction f) {
		Works.Site works = Works.of(server, f.id());
		int size = size(f);
		List<Blueprint.Entry> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Blueprint.Entry e : Blueprint.catalogue(f.type())) {
			// (0.5.11) the wall goes up as a whole, round everything, once the rest is built ({@link Enclosure})
			if (Enclosure.piece(e.id())) {
				continue;
			}
			if (size >= e.minPopulation() && works.count(e.id()) < e.max() && seen.add(e.id())) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * (0.5.11) Every building in {@code f}'s growth order but houses and the wall's own pieces is up to the most of it the
	 * order ever asks for: time for the wall ({@link Enclosure}).
	 */
	static boolean utilitiesDone(MinecraftServer server, Faction f) {
		Works.Site works = Works.of(server, f.id());
		Map<String, Integer> most = new HashMap<>();
		for (Blueprint.Entry e : Blueprint.catalogue(f.type())) {
			if (!e.id().equals("house") && !Enclosure.piece(e.id()) && e.minPopulation() < Integer.MAX_VALUE) {
				most.merge(e.id(), e.max(), Math::max);
			}
		}
		for (Map.Entry<String, Integer> e : most.entrySet()) {
			if (e.getValue() > 0 && works.count(e.getKey()) < e.getValue()) {
				return false;
			}
		}
		return !most.isEmpty();
	}

	/** The catalogue entry {@code id} for this faction's type (or any type, if its own has none). */
	static Blueprint.@Nullable Entry entry(Faction f, String id) {
		for (Blueprint.Entry e : Blueprint.catalogue(f.type())) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		for (FactionType type : FactionType.values()) {
			for (Blueprint.Entry e : Blueprint.catalogue(type)) {
				if (e.id().equals(id)) {
					return e;
				}
			}
		}
		return null;
	}

	/**
	 * Chooses the next building and its site and starts the project (plots first). {@code force} skips the economy's
	 * say (operators). Returns the project, or null if there's nothing to build or nowhere to put it.
	 */
	static Works.@Nullable Project plan(ServerLevel level, Faction f, boolean force, @Nullable String only) {
		MinecraftServer server = level.getServer();
		if (!force && !ready(level, f)) {
			return null;
		}
		Works.Site works = Works.of(server, f.id());
		// a lord's plot first
		for (Works.Plot plot : works.plots()) {
			if (plot.done() || works.projects().stream().anyMatch(p -> p.plot() == plot.id())) {
				continue;
			}
			List<Blueprint.Entry> choices = new ArrayList<>();
			if (plot.building().isPresent()) {
				Blueprint.Entry chosen = entry(f, plot.building().get());
				if (chosen != null) {
					choices.add(chosen);
				}
			} else {
				choices.addAll(eligible(server, f));
			}
			for (Blueprint.Entry e : choices) {
				if (only != null && !only.equals(e.id())) {
					continue;
				}
				// a few of its variants, then the plain design (the one a plot was measured for)
				for (int attempt = 0; attempt <= VARIANT_TRIES; attempt++) {
					long variant = attempt < VARIANT_TRIES ? variant(level, f, e.id(), attempt) : 0L;
					Placing at = onPlot(f, plot, e.maker().make(level, f, variant));
					if (at != null) {
						return start(level, f, e.id(), at, plot.id(), variant);
					}
				}
			}
		}
		List<Blueprint.Entry> choices = new ArrayList<>();
		if (only == null) {
			choices.addAll(eligible(server, f));
		} else if (entry(f, only) != null && !Enclosure.piece(only)) {
			choices.add(entry(f, only));
		}
		// (0.5.11) once every other kind of building is up to its full count, the builders wall the settlement in
		if (only == null && utilitiesDone(server, f) || only != null && Enclosure.piece(only)) {
			Enclosure.Piece piece = Enclosure.next(level, f);
			Blueprint.Entry wallEntry = piece == null ? null : entry(f, piece.entry());
			if (piece != null && wallEntry != null) {
				return start(level, f, piece.entry(), new Placing(piece.origin(), piece.rotation(), 0.0, true), -1,
					variant(level, f, piece.entry(), 0));
			}
		}
		for (Blueprint.Entry e : choices) {
			for (int attempt = 0; attempt < VARIANT_TRIES; attempt++) {
				long variant = variant(level, f, e.id(), attempt);
				Placing at = site(level, f, e.id(), e.maker().make(level, f, variant));
				if (at != null) {
					return start(level, f, e.id(), at, -1, variant);
				}
			}
		}
		return null;
	}

	/**
	 * (0.5.7) The variant of {@code f}'s next {@code entry}: different for every building it puts up (and every world),
	 * never 0 (the plain design). {@code attempt} gives the alternatives tried when one doesn't fit anywhere.
	 */
	static long variant(ServerLevel level, Faction f, String entry, int attempt) {
		Works.Site works = Works.of(level.getServer(), f.id());
		long v = level.getSeed() * 0x9E3779B97F4A7C15L + f.id() * 0xC2B2AE3D27D4EB4FL + entry.hashCode() * 0x165667B19E3779F9L
			+ (works.built().size() + works.projects().size()) * 0x27D4EB2F165667C5L + attempt * 0x94D049BB133111EBL;
		v ^= v >>> 31;
		v *= 0xBF58476D1CE4E5B9L;
		v ^= v >>> 29;
		return v == 0L ? 1L : v;
	}

	private static Works.Project start(ServerLevel level, Faction f, String entry, Placing at, int plot, long variant) {
		Works.Project project = new Works.Project(entry, at.origin(), at.rotation(), Works.STAKE, 0, Map.of(), List.of(), plot, level.getGameTime(),
			at.ruins(), variant);
		Works.update(level.getServer(), f.id(), s -> {
			List<Works.Project> list = new ArrayList<>(s.projects());
			list.add(project);
			return s.withProjects(list);
		});
		NEEDS.remove(f.id());
		JaymosKingdoms.LOGGER.info("{} begins a {} at {} ({})", f.name(), entry, at.origin(),
			plot >= 0 ? "on plot " + plot : at.ruins() ? "over an old structure" : "open ground");
		return project;
	}

	// ---- layouts --------------------------------------------------------------------------------------------------

	/** The footprint's size turned by {@code rotation}: {width, depth}. */
	static int[] turned(Blueprint bp, Rotation rotation) {
		return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? new int[] {bp.depth(), bp.width()}
			: new int[] {bp.width(), bp.depth()};
	}

	/** Where a blueprint's offset lands, turned by {@code rotation} inside its footprint (its door, drawn north, faces the rotation's way). */
	static BlockPos turn(Blueprint bp, BlockPos offset, Rotation rotation) {
		int x = offset.getX(), z = offset.getZ(), w = bp.width(), d = bp.depth();
		return switch (rotation) {
			case CLOCKWISE_90 -> new BlockPos(d - 1 - z, offset.getY(), x);
			case CLOCKWISE_180 -> new BlockPos(w - 1 - x, offset.getY(), d - 1 - z);
			case COUNTERCLOCKWISE_90 -> new BlockPos(z, offset.getY(), w - 1 - x);
			default -> offset;
		};
	}

	/** The rotation that turns a door drawn facing north to face {@code way}. */
	static Rotation facing(Direction way) {
		return switch (way) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Lays {@code bp} out at {@code origin}, turned: its blocks bottom up, each layer's blocks of one material together. */
	static Layout layout(ServerLevel level, Faction f, String entry, Blueprint bp, BlockPos origin, Rotation rotation, boolean plot, boolean ruins) {
		int[] size = turned(bp, rotation);
		List<Op> ops = new ArrayList<>();
		Set<BlockPos> filled = new HashSet<>();
		for (Blueprint.Placement pl : bp.blocks()) {
			BlockPos at = origin.offset(turn(bp, pl.offset(), rotation));
			BlockState state = pl.state().rotate(rotation);
			ops.add(new Op(at, state, Materials.cost(level, f, state)));
			filled.add(at);
		}
		// bottom up; within a layer, one material after another (a load goes further)
		ops.sort((a, b) -> {
			int y = Integer.compare(a.pos().getY(), b.pos().getY());
			if (y != 0) {
				return y;
			}
			String ka = a.cost() == null ? "" : Materials.key(a.cost().item());
			String kb = b.cost() == null ? "" : Materials.key(b.cost().item());
			return ka.compareTo(kb);
		});
		BlockPos max = origin.offset(size[0] - 1, 0, size[1] - 1);
		return new Layout(bp, entry, origin, max, bp.height(), List.copyOf(ops), filled, plot, ruins && !plot);
	}

	// ---- sites ----------------------------------------------------------------------------------------------------

	/** Fits {@code bp} on a lord's plot, centred, its door to the seat if it fits that way. Null if it doesn't fit. */
	static @Nullable Placing onPlot(Faction f, Works.Plot plot, Blueprint bp) {
		BlockPos mid = new BlockPos((plot.min().getX() + plot.max().getX()) / 2, plot.min().getY(), (plot.min().getZ() + plot.max().getZ()) / 2);
		Rotation best = facing(Direction.getApproximateNearest(f.seat().getX() - mid.getX(), 0, f.seat().getZ() - mid.getZ()));
		List<Rotation> order = new ArrayList<>(List.of(best, best.getRotated(Rotation.CLOCKWISE_90), best.getRotated(Rotation.CLOCKWISE_180),
			best.getRotated(Rotation.COUNTERCLOCKWISE_90)));
		for (Rotation r : order) {
			int[] size = turned(bp, r);
			if (size[0] <= plot.width() && size[1] <= plot.depth()) {
				BlockPos origin = new BlockPos(plot.min().getX() + (plot.width() - size[0]) / 2, plot.min().getY(),
					plot.min().getZ() + (plot.depth() - size[1]) / 2);
				return new Placing(origin, r, 0, false);
			}
		}
		return null;
	}

	/** How far out its land reaches from the seat. */
	static int landRadius(Faction f) {
		if (f.claims().isEmpty()) {
			return NO_LAND_RADIUS;
		}
		double far = 0;
		for (long packed : f.claims()) {
			ChunkPos c = ChunkPos.unpack(packed);
			double dx = c.getMiddleBlockX() - f.seat().getX();
			double dz = c.getMiddleBlockZ() - f.seat().getZ();
			far = Math.max(far, Math.sqrt(dx * dx + dz * dz));
		}
		return (int)Math.min(MAX_RADIUS, far);
	}

	/** The best open site for {@code bp} in {@code f}'s land, ring by ring out from the seat; null if none. */
	static @Nullable Placing site(ServerLevel level, Faction f, String entry, Blueprint bp) {
		int[] rings = RINGS.getOrDefault(entry, new int[] {RING_MIN, MAX_RADIUS});
		int reach = Math.min(rings[1], landRadius(f));
		Palette palette = Palette.at(level, f.seat());
		List<int[]> taken = taken(level, f);
		Placing best = null;
		int firstRing = -1, candidates = 0, ringIndex = 0;
		double start = (f.id() * 0.618) % 1.0 * Math.PI * 2.0;
		Works.Site works = Works.of(level.getServer(), f.id());
		for (int r = Math.max(rings[0], SEAT_CLEAR + Math.max(bp.width(), bp.depth())); r <= reach; r += RING_STEP, ringIndex++) {
			if (firstRing >= 0 && ringIndex > firstRing + RING_LOOKAHEAD) {
				break;
			}
			int angles = Math.max(MIN_ANGLES, (int)(Math.PI * 2.0 * r / ANGLE_SPACING));
			for (int i = 0; i < angles && candidates < MAX_CANDIDATES; i++) {
				double a = start + (i + (ringIndex % 2) * 0.5) / angles * Math.PI * 2.0;
				BlockPos center = f.seat().offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r));
				if (!level.isLoaded(center)) {
					continue;
				}
				candidates++;
				Rotation rotation = facing(Direction.getApproximateNearest(f.seat().getX() - center.getX(), 0, f.seat().getZ() - center.getZ()));
				int[] size = turned(bp, rotation);
				BlockPos corner = center.offset(-size[0] / 2, 0, -size[1] / 2);
				if (!Enclosure.inside(works, corner.getX() - GAP, corner.getZ() - GAP, corner.getX() + size[0] + GAP,
					corner.getZ() + size[1] + GAP)) {
					continue;
				}
				Footing footing = check(level, f, corner, size[0], size[1], bp.height(), taken);
				if (footing == null) {
					continue;
				}
				int floor = footing.floor();
				double score = ringIndex * RING_WEIGHT + road(level, palette, corner.atY(floor), size[0], size[1])
					+ (footing.ruins() ? RUINS_PENALTY : 0.0);
				if (best == null || score < best.score()) {
					best = new Placing(corner.atY(floor), rotation, score, footing.ruins());
				}
				// (only natural ground ends the search: old structures nearer in are weighed against it)
				if (firstRing < 0 && !footing.ruins()) {
					firstRing = ringIndex;
				}
			}
		}
		return best;
	}

	/** Every footprint already spoken for (all factions' buildings, projects and plots), as {minX, minZ, maxX, maxZ}. */
	private static List<int[]> taken(ServerLevel level, Faction f) {
		List<int[]> out = new ArrayList<>();
		MinecraftServer server = level.getServer();
		for (var e : Works.all(server).entrySet()) {
			Faction owner = Factions.get(server, e.getKey());
			for (Works.Built b : e.getValue().built()) {
				out.add(new int[] {b.min().getX(), b.min().getZ(), b.max().getX(), b.max().getZ()});
			}
			for (Works.Plot p : e.getValue().plots()) {
				out.add(new int[] {p.min().getX(), p.min().getZ(), p.max().getX(), p.max().getZ()});
			}
			if (owner == null) {
				continue;
			}
			for (Works.Project p : e.getValue().projects()) {
				Layout l = Construction.layout(level, owner, p);
				if (l != null) {
					out.add(new int[] {l.min().getX(), l.min().getZ(), l.max().getX(), l.max().getZ()});
				}
			}
		}
		return out;
	}

	/**
	 * Whether a footprint ({@code w} x {@code d} from {@code corner}) can be built on: in the faction's land, clear of
	 * the seat and of everything taken, every column (and the margin round it) natural ground, dry, level within
	 * {@link #LEVEL_TOLERANCE}, and open air above. A column may instead stand on an old structure ({@link Ruins}): its
	 * ground is then the land under the structure, and the structure's blocks above count as open (they'll be torn
	 * down), unless the site is {@link Ruins#guarded} or has a bell. Returns the floor height (the median ground block)
	 * and whether it's on an old structure, or null.
	 */
	static @Nullable Footing check(ServerLevel level, Faction f, BlockPos corner, int w, int d, int height, List<int[]> taken) {
		int x0 = corner.getX(), z0 = corner.getZ(), x1 = x0 + w - 1, z1 = z0 + d - 1;
		// in its land
		for (int[] c : new int[][] {{x0 - MARGIN, z0 - MARGIN}, {x1 + MARGIN, z0 - MARGIN}, {x0 - MARGIN, z1 + MARGIN}, {x1 + MARGIN, z1 + MARGIN}}) {
			BlockPos at = new BlockPos(c[0], 0, c[1]);
			if (f.claims().isEmpty() ? f.seat().atY(0).distSqr(at) > (double)NO_LAND_RADIUS * NO_LAND_RADIUS : !f.claims(ChunkPos.containing(at))) {
				return null;
			}
		}
		// clear of the seat
		int sx = f.seat().getX(), sz = f.seat().getZ();
		if (sx >= x0 - SEAT_CLEAR && sx <= x1 + SEAT_CLEAR && sz >= z0 - SEAT_CLEAR && sz <= z1 + SEAT_CLEAR) {
			return null;
		}
		// clear of everything taken
		for (int[] t : taken) {
			if (x0 - GAP <= t[2] && x1 + GAP >= t[0] && z0 - GAP <= t[3] && z1 + GAP >= t[1]) {
				return null;
			}
		}
		// the ground (a margin column over an old structure is left as it stands, and not looked over)
		int[] grounds = new int[(w + 2 * MARGIN) * (d + 2 * MARGIN)];
		List<Integer> inside = new ArrayList<>();
		boolean ruins = false;
		int n = 0;
		for (int x = x0 - MARGIN; x <= x1 + MARGIN; x++) {
			for (int z = z0 - MARGIN; z <= z1 + MARGIN; z++) {
				BlockPos column = new BlockPos(x, 0, z);
				if (!level.isLoaded(column)) {
					return null;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockState ground = level.getBlockState(top.below());
				boolean margin = x < x0 || x > x1 || z < z0 || z > z1;
				if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) {
					return null;
				}
				int at;
				if (Storehouse.ground(ground) || (margin && road(ground, null))) {
					at = top.getY() - 1;
				} else if (Ruins.inPiece(level, top.below())) {
					if (margin) {
						at = Integer.MIN_VALUE;
					} else {
						Integer under = underRuins(level, top.below());
						if (under == null) {
							return null;
						}
						at = under;
						ruins = true;
					}
				} else {
					return null;
				}
				grounds[n++] = at;
				if (!margin) {
					inside.add(at);
				}
			}
		}
		inside.sort(Integer::compare);
		int floor = inside.get(inside.size() / 2);
		for (int g : inside) {
			if (Math.abs(g - floor) > LEVEL_TOLERANCE) {
				return null;
			}
		}
		// open air above (no trees, nothing built): plants, snow and air only; or an old structure's blocks to tear down
		n = 0;
		for (int x = x0 - MARGIN; x <= x1 + MARGIN; x++) {
			for (int z = z0 - MARGIN; z <= z1 + MARGIN; z++) {
				int ground = grounds[n++];
				if (ground == Integer.MIN_VALUE) {
					continue;
				}
				boolean margin = x < x0 || x > x1 || z < z0 || z > z1;
				for (int y = ground + 1; y <= floor + height + CLEARANCE; y++) {
					BlockPos at = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(at);
					if (state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty())) {
						continue;
					}
					if (!state.is(Blocks.BELL) && Ruins.inPiece(level, at) && (margin || Ruins.tearable(level, at, state))) {
						ruins |= !margin;
						continue;
					}
					return null;
				}
			}
		}
		if (ruins && Ruins.guarded(level, x0, z0, x1, z1, floor - LEVEL_TOLERANCE, floor + height + CLEARANCE)) {
			return null;
		}
		return new Footing(floor, ruins);
	}

	/**
	 * The land's own ground under an old structure's block at {@code from} (looking down through the structure, at most
	 * {@link Ruins#GROUND_SCAN}); null if there's none, or a street or something not the structure's is in the way.
	 */
	private static @Nullable Integer underRuins(ServerLevel level, BlockPos from) {
		for (int dy = 0; dy <= Ruins.GROUND_SCAN; dy++) {
			BlockPos at = from.below(dy);
			BlockState state = level.getBlockState(at);
			if (state.is(Blocks.DIRT_PATH)) {
				return null;
			}
			if (Storehouse.ground(state)) {
				return at.getY();
			}
			if (!(state.isAir() || state.canBeReplaced() || Ruins.inPiece(level, at))) {
				return null;
			}
		}
		return null;
	}

	/** A road or path block (a village's dirt path, or the realm's own road stone). */
	static boolean road(BlockState state, @Nullable Palette palette) {
		return state.is(Blocks.DIRT_PATH) || (palette != null && state.is(palette.path().getBlock()));
	}

	/** How far the nearest road is from the footprint's edge (a penalty if there's none within {@link #ROAD_REACH}). */
	private static double road(ServerLevel level, Palette palette, BlockPos corner, int w, int d) {
		int x0 = corner.getX(), z0 = corner.getZ(), x1 = x0 + w - 1, z1 = z0 + d - 1;
		int best = Integer.MAX_VALUE;
		for (int x = x0 - ROAD_REACH; x <= x1 + ROAD_REACH; x += ROAD_STEP) {
			for (int z = z0 - ROAD_REACH; z <= z1 + ROAD_REACH; z += ROAD_STEP) {
				int dx = Math.max(0, Math.max(x0 - x, x - x1));
				int dz = Math.max(0, Math.max(z0 - z, z - z1));
				int dist = Math.max(dx, dz);
				if (dist == 0 || dist >= best) {
					continue;
				}
				BlockPos column = new BlockPos(x, 0, z);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (road(level.getBlockState(top.below()), palette)) {
					best = dist;
				}
			}
		}
		return best == Integer.MAX_VALUE ? NO_ROAD_PENALTY : best;
	}

	// ---- the economy's view -------------------------------------------------------------------------------------

	/**
	 * What {@code f}'s next building needs ({@link Economy#nextNeeds}): what's left of the project under way (less what's
	 * already on site), or all of the next building in its growth order. Empty when it has nothing left to build.
	 */
	static Map<Item, Integer> needs(ServerLevel level, Faction f) {
		long now = level.getGameTime();
		Long at = NEEDS_AT.get(f.id());
		if (at != null && now - at < NEEDS_CACHE_TICKS && NEEDS.containsKey(f.id())) {
			return NEEDS.get(f.id());
		}
		Map<Item, Integer> needs = compute(level, f);
		NEEDS.put(f.id(), needs);
		NEEDS_AT.put(f.id(), now);
		return needs;
	}

	private static Map<Item, Integer> compute(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		List<Works.Project> projects = Works.of(server, f.id()).projects();
		if (!projects.isEmpty()) {
			return Construction.remaining(level, f, projects.getFirst());
		}
		List<Blueprint.Entry> next = eligible(server, f);
		if (next.isEmpty()) {
			return Map.of();
		}
		Blueprint bp = next.getFirst().maker().make(level, f, variant(level, f, next.getFirst().id(), 0));
		List<BlockState> states = new ArrayList<>();
		for (Blueprint.Placement pl : bp.blocks()) {
			states.add(pl.state());
		}
		return new LinkedHashMap<>(Materials.total(level, f, states));
	}

	static void forget(int faction) {
		NEEDS.remove(faction);
		NEEDS_AT.remove(faction);
	}

	static void clear() {
		NEEDS.clear();
		NEEDS_AT.clear();
	}
}
