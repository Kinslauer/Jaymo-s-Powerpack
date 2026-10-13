package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.58) Groundskeeping: one of every settlement's builders walks its ground and levels what folk get stuck on. He
 * fills pits (up to {@link #HOLE_MAX} deep and three wide) with the ground round them, ramps ledges two to
 * {@link #CLIFF_MAX} high (a one-wide earth ramp every {@link #RAMP_SPACING} blocks along the ledge; stairs in the
 * settlement's stone where it's a road), and lays plank bridges in the settlement's wood where a road runs into a gap
 * up to {@link #BRIDGE_MAX} wide. Only open ground and roads are touched (never a building's piece, never water), over
 * the settlement's village (or round its seat, with none). The ground is read a few passes a day; a builder with
 * groundwork does it before anything else, the rest of the crew go on building. Wraps the builders' brain
 * ({@link Builders#brain}).
 */
final class Groundwork {
	/** How often the ground is read, and how many columns at a time. */
	static final int SCAN_TICKS = 20, SCAN_COLUMNS = 192;
	/** With no structure at the seat: how far round it the ground is kept; with one, how far past its pieces, and at most how far from the seat. */
	static final int AREA_RADIUS = 48, AREA_MARGIN = 6, AREA_MAX = 96;
	/** Pits: the deepest filled, and how far (each way) the rim is looked for (so pits up to three wide). */
	static final int HOLE_MAX = 3, PIT_REACH = 2;
	/** Ledges: the tallest given a ramp, and a ramp every this many blocks along one. */
	static final int CLIFF_MAX = 5, RAMP_SPACING = 6;
	/** Bridges: how far below the road a gap must drop, and the widest bridged. */
	static final int BRIDGE_DROP = 3, BRIDGE_MAX = 5;
	/** Fixes queued per settlement at most; passes over the ground a day. */
	static final int QUEUE_MAX = 200, PASSES_PER_DAY = 2;
	/** Ticks between a groundskeeper's fixes (and a random extra); how often the crew's groundskeeper is chosen. */
	static final int ACT_TICKS = 24, ACT_JITTER = 12, KEEPER_TICKS = 200;
	/** Walking: speed, how close counts as there (across and up), path renewal, no progress this long means a puff. */
	static final double WALK_SPEED = 0.7, REACH = 4.0, VERTICAL = 4.0;
	static final int REPATH_TICKS = 20, STUCK_TICKS = 200, LEG = 20;
	/** A builder hurt this recently is left to run (ticks). */
	static final int FLEE_TICKS = 100;

	/** One fix: blocks set upward from {@code at} (a pit or ramp column from its old surface up, or a bridge's plank). */
	record Fix(BlockPos at, BlockState[] column, boolean bridge) {
	}

	/** A settlement's ground: where it is, its buildings (left alone), how far the reading has got, what's queued. */
	private static final class Work {
		final BoundingBox area;
		final List<BoundingBox> buildings;
		int column, passes;
		long day;
		boolean scanning = true;
		final Map<BlockPos, Fix> fixes = new LinkedHashMap<>();
		/** Columns already raised (packed x/z): a ramp isn't a ledge to ramp again. */
		final Set<Long> raised = new HashSet<>();

		Work(BoundingBox area, List<BoundingBox> buildings, long day) {
			this.area = area;
			this.buildings = buildings;
			this.day = day;
		}
	}

	/** One groundskeeper's errand. */
	private static final class Job {
		@Nullable Fix target;
		double best = Double.MAX_VALUE;
		long improvedAt, repathAt, actAt;
	}

	private static final Map<Integer, Work> WORK = new HashMap<>();
	private static final Map<Integer, UUID> KEEPERS = new HashMap<>();
	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Groundwork() {
	}

	/** Wraps the builders' brain and reads the settlements' ground. */
	static void register() {
		Builders.Brain next = Builders.brain();
		Builders.setBrain((level, builder) -> {
			if (!tick(level, builder)) {
				next.tick(level, builder);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Groundwork::scan);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WORK.clear();
			KEEPERS.clear();
			JOBS.clear();
		});
	}

	// ---- reading the ground ------------------------------------------------------------------------------------------

	private static void scan(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % SCAN_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || Builders.crewCount(f) == 0 || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (now % KEEPER_TICKS == 0) {
				List<Worker> crew = Builders.crew(level, f);
				if (crew.size() >= 2) {
					KEEPERS.put(f.id(), crew.getLast().getUUID());
				} else {
					KEEPERS.remove(f.id());
				}
			}
			Work w = WORK.get(f.id());
			if (w == null) {
				w = area(level, f, now / 24000L);
				if (w == null) {
					continue;
				}
				WORK.put(f.id(), w);
			}
			if (w.scanning) {
				read(level, f, w);
			} else if (w.fixes.isEmpty()) {
				long day = now / 24000L;
				if (day != w.day) {
					w.day = day;
					w.passes = 0;
				}
				if (w.passes < PASSES_PER_DAY) {
					w.scanning = true;
					w.column = 0;
				}
			}
		}
	}

	/** Where {@code f}'s ground is: its village and a margin (its buildings left alone), or round its seat; null until the village can be read. */
	private static @Nullable Work area(ServerLevel level, Faction f, long day) {
		BlockPos seat = f.seat();
		Seats.Found found = Seats.structureAt(level, seat, Seats::village);
		if (found.status() == Seats.Status.UNREADY) {
			return null;
		}
		BoundingBox cap = new BoundingBox(seat).inflatedBy(AREA_MAX);
		if (found.status() == Seats.Status.FOUND && found.start() != null) {
			BoundingBox box = found.start().getBoundingBox().inflatedBy(AREA_MARGIN);
			BoundingBox area = new BoundingBox(Math.max(box.minX(), cap.minX()), cap.minY(), Math.max(box.minZ(), cap.minZ()),
				Math.min(box.maxX(), cap.maxX()), cap.maxY(), Math.min(box.maxZ(), cap.maxZ()));
			List<BoundingBox> buildings = new ArrayList<>();
			for (Seats.Building b : Seats.buildings(found.start())) {
				buildings.add(b.box().inflatedBy(1));
			}
			return new Work(area, buildings, day);
		}
		return new Work(new BoundingBox(seat).inflatedBy(AREA_RADIUS), List.of(), day);
	}

	/** Reads the next {@link #SCAN_COLUMNS} columns, queueing fixes; at the end, the pass is over. */
	private static void read(ServerLevel level, Faction f, Work w) {
		BoundingBox a = w.area;
		int columns = a.getXSpan() * a.getZSpan();
		Palette palette = Palette.at(level, f.seat());
		for (int done = 0; done < SCAN_COLUMNS && w.column < columns; done++, w.column++) {
			if (w.fixes.size() >= QUEUE_MAX) {
				break;
			}
			int x = a.minX() + w.column % a.getXSpan(), z = a.minZ() + w.column / a.getXSpan();
			if (!walkable(level, w, x, z)) {
				continue;
			}
			if (!pit(level, w, x, z)) {
				ledge(level, w, palette, x, z);
			}
			bridge(level, w, palette, x, z);
		}
		if (w.column >= columns) {
			w.scanning = false;
			w.passes++;
		}
	}

	/** The height folk stand at on column x/z (the first air over its ground), or {@link Integer#MIN_VALUE} if its chunk isn't loaded. */
	private static int height(ServerLevel level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return Integer.MIN_VALUE;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static BlockState surface(ServerLevel level, int x, int z) {
		return level.getBlockState(new BlockPos(x, height(level, x, z) - 1, z));
	}

	/** Open ground or road (not water, ice or a field), loaded, in the area and off every building. */
	private static boolean walkable(ServerLevel level, Work w, int x, int z) {
		if (!w.area.isInside(x, w.area.minY(), z) || height(level, x, z) == Integer.MIN_VALUE) {
			return false;
		}
		for (BoundingBox b : w.buildings) {
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) {
				return false;
			}
		}
		BlockState s = surface(level, x, z);
		if (!s.getFluidState().isEmpty() || s.is(Blocks.FARMLAND) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)) {
			return false;
		}
		return s.is(Blocks.DIRT_PATH) || Storehouse.ground(s);
	}

	/** Nothing in the way between heights {@code from} (inclusive) and {@code to} (exclusive) on column x/z: air or plants. */
	private static boolean clear(ServerLevel level, int x, int from, int to, int z) {
		for (int y = from; y < to; y++) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (!(s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty()))) {
				return false;
			}
		}
		return true;
	}

	/** What goes under a raised surface: dirt under grass, paths and the like; the same block under sand, gravel or stone. */
	private static BlockState under(BlockState top) {
		return top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT_PATH) || top.is(Blocks.PODZOL) || top.is(Blocks.MYCELIUM) || top.is(Blocks.SNOW_BLOCK)
			? Blocks.DIRT.defaultBlockState() : top;
	}

	/** A column raised from height {@code h} to {@code top}: its old surface becomes fill, the new surface {@code crown}. */
	private static Fix raise(int x, int h, int top, int z, BlockState old, BlockState crown) {
		BlockState[] column = new BlockState[top - h + 1];
		for (int i = 0; i < column.length - 1; i++) {
			column[i] = under(old);
		}
		column[column.length - 1] = crown;
		return new Fix(new BlockPos(x, h - 1, z), column, false);
	}

	/** A pit: ground higher on every side within {@link #PIT_REACH} (not more than {@link #HOLE_MAX} higher), filled to the lowest rim. */
	private static boolean pit(ServerLevel level, Work w, int x, int z) {
		int h = height(level, x, z), rim = Integer.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int found = Integer.MIN_VALUE;
			for (int k = 1; k <= PIT_REACH; k++) {
				int nx = x + d.getStepX() * k, nz = z + d.getStepZ() * k;
				if (!walkable(level, w, nx, nz)) {
					return false;
				}
				int nh = height(level, nx, nz);
				if (nh > h) {
					found = nh;
					break;
				}
			}
			if (found == Integer.MIN_VALUE) {
				return false;
			}
			rim = Math.min(rim, found);
		}
		if (rim - h > HOLE_MAX || !clear(level, x, h, rim + 1, z)) {
			return false;
		}
		BlockState s = surface(level, x, z);
		queue(w, raise(x, h, rim, z, s, s));
		return true;
	}

	/**
	 * A ledge: a neighbour two to {@link #CLIFF_MAX} higher (not one already raised). Every {@link #RAMP_SPACING} blocks
	 * along it, a ramp runs down from it a block a step, until it meets the ground.
	 */
	private static void ledge(ServerLevel level, Work w, Palette palette, int x, int z) {
		int h = height(level, x, z);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int along = d.getAxis() == Direction.Axis.X ? z : x;
			if (Math.floorMod(along, RAMP_SPACING) != 0) {
				continue;
			}
			int nx = x + d.getStepX(), nz = z + d.getStepZ();
			if (!walkable(level, w, nx, nz) || w.raised.contains(pack(nx, nz))) {
				continue;
			}
			int high = height(level, nx, nz), rise = high - h;
			if (rise < 2 || rise > CLIFF_MAX) {
				continue;
			}
			List<Fix> ramp = new ArrayList<>();
			for (int k = 0; ; k++) {
				int cx = x - d.getStepX() * k, cz = z - d.getStepZ() * k, target = high - 1 - k;
				if (!walkable(level, w, cx, cz)) {
					ramp.clear();
					break;
				}
				int ch = height(level, cx, cz);
				if (ch >= target) {
					break;
				}
				if (target - ch > CLIFF_MAX || !clear(level, cx, ch, target + 1, cz)) {
					ramp.clear();
					break;
				}
				BlockState s = surface(level, cx, cz);
				BlockState crown = Planner.road(s, palette) && palette.stairs().hasProperty(StairBlock.FACING)
					? palette.stairs().setValue(StairBlock.FACING, d) : s;
				ramp.add(raise(cx, ch, target, cz, s, crown));
			}
			for (Fix fix : ramp) {
				queue(w, fix);
			}
			if (!ramp.isEmpty()) {
				return;
			}
		}
	}

	/** A road running into a gap: across it (dropping {@link #BRIDGE_DROP} or more, up to {@link #BRIDGE_MAX} wide) to ground at the road's height, a plank bridge. */
	private static void bridge(ServerLevel level, Work w, Palette palette, int x, int z) {
		if (!Planner.road(surface(level, x, z), palette)) {
			return;
		}
		int h = height(level, x, z);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			List<BlockPos> deck = new ArrayList<>();
			for (int k = 1; k <= BRIDGE_MAX + 1; k++) {
				int cx = x + d.getStepX() * k, cz = z + d.getStepZ() * k;
				int ch = height(level, cx, cz);
				if (ch == Integer.MIN_VALUE || !w.area.isInside(cx, w.area.minY(), cz)) {
					deck.clear();
					break;
				}
				if (ch <= h - BRIDGE_DROP) {
					if (k > BRIDGE_MAX || !clear(level, cx, h - 1, h + 2, cz)) {
						deck.clear();
						break;
					}
					deck.add(new BlockPos(cx, h - 1, cz));
					continue;
				}
				if (Math.abs(ch - h) > 1 || !walkable(level, w, cx, cz)) {
					deck.clear();
				}
				break;
			}
			for (BlockPos at : deck) {
				queue(w, new Fix(at, new BlockState[] {palette.planks()}, true));
			}
		}
	}

	private static void queue(Work w, Fix fix) {
		if (w.fixes.size() < QUEUE_MAX) {
			w.fixes.putIfAbsent(fix.at(), fix);
		}
	}

	private static long pack(int x, int z) {
		return ((long)x << 32) | (z & 0xFFFFFFFFL);
	}

	// ---- the groundskeeper -------------------------------------------------------------------------------------------

	/** One builder's tick: true if he's the crew's groundskeeper and has groundwork (or is running), false to leave him to the rest of the brain. */
	static boolean tick(ServerLevel level, Worker b) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, b.factionId());
		Work w = f == null ? null : WORK.get(f.id());
		if (f == null || f.fallen() || w == null || w.fixes.isEmpty() || !b.getUUID().equals(KEEPERS.get(f.id())) || b.isCarrying()
			|| !Economy.atPeace(server, f)) {
			JOBS.remove(b.getUUID());
			return false;
		}
		if (b.getLastHurtByMob() != null && b.ticksAlive() - b.getLastHurtByMobTimestamp() < FLEE_TICKS) {
			return true;
		}
		long now = level.getGameTime();
		Job job = JOBS.computeIfAbsent(b.getUUID(), u -> new Job());
		if (job.target == null) {
			job.target = next(w, b.blockPosition());
			if (job.target == null) {
				return false;
			}
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			job.repathAt = 0;
			b.setHomeTo(Builders.depot(f), Construction.WORK_RADIUS);
			b.setTask(job.target.bridge() ? "bridging a gap" : "levelling the ground");
		}
		Fix fix = job.target;
		b.setSite(fix.at());
		if (!approach(level, b, job, fix.at(), now) || now < job.actAt) {
			return true;
		}
		job.actAt = now + ACT_TICKS + level.getRandom().nextInt(ACT_JITTER);
		w.fixes.remove(fix.at());
		job.target = null;
		if (!still(level, fix)) {
			return true;
		}
		b.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (int i = 0; i < fix.column().length; i++) {
			level.setBlock(fix.at().above(i), fix.column()[i], Block.UPDATE_ALL);
		}
		BlockState top = fix.column()[fix.column().length - 1];
		BlockPos at = fix.at().above(fix.column().length - 1);
		SoundType sound = top.getSoundType();
		level.playSound(null, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.05);
		if (!fix.bridge()) {
			w.raised.add(pack(at.getX(), at.getZ()));
		}
		return true;
	}

	/** The fix is still wanted: a bridge's spot still open; a column's old surface still there, and nothing built over it since. */
	private static boolean still(ServerLevel level, Fix fix) {
		BlockPos at = fix.at();
		if (fix.bridge()) {
			BlockState s = level.getBlockState(at);
			return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
		}
		BlockState old = level.getBlockState(at);
		return (old.is(Blocks.DIRT_PATH) || Storehouse.ground(old)) && clear(level, at.getX(), at.getY() + 1, at.getY() + fix.column().length, at.getZ());
	}

	/** The queued fix nearest {@code from} that no other groundskeeper has. */
	private static @Nullable Fix next(Work w, BlockPos from) {
		Fix best = null;
		double bestDist = Double.MAX_VALUE;
		for (Fix fix : w.fixes.values()) {
			double d = fix.at().distSqr(from);
			if (d < bestDist && JOBS.values().stream().noneMatch(j -> j.target == fix)) {
				bestDist = d;
				best = fix;
			}
		}
		return best;
	}

	/** Walks toward {@code goal}; true once within reach. Stuck too long: a puff of smoke, and he's there. */
	private static boolean approach(ServerLevel level, Worker b, Job job, BlockPos goal, long now) {
		double dx = goal.getX() + 0.5 - b.getX(), dz = goal.getZ() + 0.5 - b.getZ(), dy = goal.getY() - b.getY();
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= REACH && Math.abs(dy) <= VERTICAL) {
			b.getNavigation().stop();
			b.getLookControl().setLookAt(goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5);
			return true;
		}
		double dist = across + Math.max(0.0, Math.abs(dy) - VERTICAL);
		if (dist < job.best - 0.5) {
			job.best = dist;
			job.improvedAt = now;
		}
		if (now - job.improvedAt > STUCK_TICKS) {
			BlockPos to = Construction.standable(level, goal, 4);
			level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY() + 1.0, b.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			b.getNavigation().stop();
			b.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, to.getX() + 0.5, to.getY() + 1.0, to.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			return false;
		}
		if (now >= job.repathAt || b.getNavigation().isDone()) {
			job.repathAt = now + REPATH_TICKS;
			BlockPos leg = goal;
			if (across > LEG) {
				leg = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					BlockPos.containing(b.getX() + dx / across * LEG, b.getY(), b.getZ() + dz / across * LEG));
			}
			b.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, WALK_SPEED);
		}
		return false;
	}
}
