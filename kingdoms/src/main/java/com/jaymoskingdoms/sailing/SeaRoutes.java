package com.jaymoskingdoms.sailing;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.285) A crewed ship's way round the land: her captain's course, worked out over a chart of the water about her in
 * squares {@link #CELL} blocks on a side, each one she'd have water enough in, her beam's width clear of the shore
 * (Andy: "maybe the captain's AI isnt navigating to the ocean? it keeps getting stuck"). Before, she only ever steered
 * straight for where she was going, looking {@code Fleet.LOOK} blocks ahead, and a headland or a bay between her and the
 * open sea held her for good. The course goes to where she's bound, or (out of the chart's reach, or with no way
 * there) as near to it as the water goes.
 */
public final class SeaRoutes {
	/** A square of her chart (blocks), how far round her it reaches, and the most squares looked at for one course. */
	static final int CELL = 4, REACH = 224, MOST = 6000;
	/** Within this many squares of where she lies, a square need only have water in it (she's in close already). */
	static final int NEAR = 3;
	/** A course is worked out again this often (ticks), or when where she's bound moves this far (blocks). */
	static final long FRESH = 200L;
	static final double MOVED = 24.0;
	/** She's passed a turn of her course this near it (blocks), and steers for the turn this many ahead. */
	static final double PASSED = 8.0;
	static final int LEAD = 2;

	private SeaRoutes() {
	}

	/**
	 * The bearing she should steer to make for {@code goal} round the land, by her course (worked out again when it's
	 * stale); null: no course (she steers straight for it).
	 */
	public static @Nullable Float steer(ServerLevel level, Ship ship, Crew crew, BlockPos goal) {
		long now = level.getGameTime();
		if (crew.route == null || crew.routeTo == null || now - crew.routeAt > FRESH
			|| Math.sqrt(horizontal(goal, crew.routeTo)) > MOVED) {
			crew.route = route(level, ship, goal);
			crew.routeTo = goal;
			crew.routeAt = now;
		}
		List<BlockPos> way = crew.route;
		if (way == null) {
			return null;
		}
		BlockPos here = ship.blockPosition();
		while (!way.isEmpty() && Math.sqrt(horizontal(here, way.get(0))) < PASSED) {
			way.remove(0);
		}
		if (way.isEmpty()) {
			return null;
		}
		BlockPos next = way.get(Math.min(LEAD, way.size() - 1));
		return Fleet.bearing(ship, next.getX() + 0.5, next.getZ() + 0.5);
	}

	/** Forgets her course (she's stuck: work it out again). */
	public static void forget(Crew crew) {
		crew.route = null;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** Her course from where she lies to {@code goal}: the turns to make, in order (null: none, or she's there). */
	static @Nullable List<BlockPos> route(ServerLevel level, Ship ship, BlockPos goal) {
		Hull h = ship.hull();
		if (h == null) {
			return null;
		}
		int beam = Mth.ceil((h.maxX - h.minX) / 2.0 + 1.0);
		int sx = Math.floorDiv(Mth.floor(ship.getX()), CELL), sz = Math.floorDiv(Mth.floor(ship.getZ()), CELL);
		int gx = Math.floorDiv(goal.getX(), CELL), gz = Math.floorDiv(goal.getZ(), CELL);
		int reach = REACH / CELL;
		// (out of the chart's reach: as near it as the chart goes)
		int dx = gx - sx, dz = gz - sz;
		double far = Math.sqrt((double)dx * dx + (double)dz * dz);
		if (far > reach) {
			gx = sx + (int)Math.round(dx * reach / far);
			gz = sz + (int)Math.round(dz * reach / far);
		}
		long start = key(sx, sz), end = key(gx, gz);
		if (start == end) {
			return null;
		}
		Long2ByteOpenHashMap water = new Long2ByteOpenHashMap();
		Long2DoubleOpenHashMap cost = new Long2DoubleOpenHashMap();
		cost.defaultReturnValue(Double.MAX_VALUE);
		Long2LongOpenHashMap came = new Long2LongOpenHashMap();
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		cost.put(start, 0.0);
		open.add(new double[] {dist(sx, sz, gx, gz), sx, sz});
		long best = start;
		double bestLeft = dist(sx, sz, gx, gz);
		int looked = 0;
		while (!open.isEmpty() && looked++ < MOST) {
			double[] o = open.poll();
			int x = (int)o[1], z = (int)o[2];
			long k = key(x, z);
			double g = cost.get(k);
			if (o[0] > g + dist(x, z, gx, gz) + 1.0E-6) {
				continue;
			}
			double left = dist(x, z, gx, gz);
			if (left < bestLeft) {
				bestLeft = left;
				best = k;
			}
			if (k == end) {
				break;
			}
			for (int nx = x - 1; nx <= x + 1; nx++) {
				for (int nz = z - 1; nz <= z + 1; nz++) {
					if (nx == x && nz == z || Math.abs(nx - sx) > reach || Math.abs(nz - sz) > reach) {
						continue;
					}
					long n = key(nx, nz);
					boolean near = Math.max(Math.abs(nx - sx), Math.abs(nz - sz)) <= NEAR;
					if (!navigable(level, ship, water, nx, nz, near ? 0 : beam)) {
						continue;
					}
					// (no cutting a corner of the land)
					if (nx != x && nz != z && (!navigable(level, ship, water, nx, z, near ? 0 : beam) || !navigable(level, ship, water, x, nz, near ? 0 : beam))) {
						continue;
					}
					double step = g + (nx != x && nz != z ? 1.4142 : 1.0);
					if (step < cost.get(n)) {
						cost.put(n, step);
						came.put(n, k);
						open.add(new double[] {step + dist(nx, nz, gx, gz), nx, nz});
					}
				}
			}
		}
		if (best == start) {
			return null;
		}
		List<BlockPos> way = new ArrayList<>();
		for (long k = best; k != start; k = came.get(k)) {
			int x = (int)(k >> 32), z = (int)k;
			way.add(0, new BlockPos(x * CELL + CELL / 2, ship.blockPosition().getY(), z * CELL + CELL / 2));
		}
		return way;
	}

	private static double dist(int x, int z, int gx, int gz) {
		double dx = x - gx, dz = z - gz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static long key(int x, int z) {
		return (long)x << 32 | z & 0xFFFFFFFFL;
	}

	/** Whether the square at (x, z) has water enough for her, and (if {@code clearance}) that far round it too. */
	private static boolean navigable(ServerLevel level, Ship ship, Long2ByteOpenHashMap water, int x, int z, int clearance) {
		double cx = x * CELL + CELL / 2.0, cz = z * CELL + CELL / 2.0;
		if (!wet(level, ship, water, cx, cz)) {
			return false;
		}
		if (clearance <= 0) {
			return true;
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			if (!wet(level, ship, water, cx + Math.cos(a) * clearance, cz + Math.sin(a) * clearance)) {
				return false;
			}
		}
		return true;
	}

	/** Whether she'd float at (x, z) (worked out once a course, by the block). */
	private static boolean wet(ServerLevel level, Ship ship, Long2ByteOpenHashMap water, double x, double z) {
		long k = BlockPos.asLong(Mth.floor(x), 0, Mth.floor(z));
		byte known = water.getOrDefault(k, (byte)-1);
		if (known < 0) {
			known = (byte)(Fleet.open(level, ship, x, z) ? 1 : 0);
			water.put(k, known);
		}
		return known == 1;
	}
}
