package com.jaymospowers.water;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Which way a river runs (Andy's TFC Hardrock picks, the Dynamic Waters row; our own code from the idea alone: a river
 * is moving water from end to end, not only where the game happens to draw a falling edge). The game's rivers are
 * all still source blocks, so the game gives them no flow at all. This reads the river around a spot: the cells of river
 * biome within {@link #RADIUS} give the line the river lies along (the long axis of the cells), and the end of that line
 * where the land lies lower is downstream. Only loaded chunks are read. The answer is kept for each {@link #CELL}-block
 * square, so asking is cheap and both sides agree.
 */
public final class RiverRun {
	/** How far round a spot the river is read (blocks), the spacing of the samples, and the size of a remembered square. */
	static final int RADIUS = 20, STEP = 4, CELL = 8;
	/** How far along the line the land's height is compared each way, and the fewest river samples that count as a river. */
	static final int LOOK = 40, FEWEST = 6;
	private static final int KEEP = 4096;
	private static final Map<Long, Vec3> KNOWN = new HashMap<>();
	private static final Vec3 NONE = new Vec3(0.0, 0.0, 0.0);

	private RiverRun() {
	}

	/** Whether the biome at {@code pos} is a river. */
	public static boolean river(Level level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getBiome(pos).is(BiomeTags.IS_RIVER);
	}

	/** The unit heading downstream at {@code pos} (zero if there is no river there). */
	public static synchronized Vec3 downstream(Level level, BlockPos pos) {
		if (!river(level, pos)) {
			return NONE;
		}
		long key = (((long) level.dimension().identifier().hashCode()) << 40) ^ (((long) Math.floorDiv(pos.getX(), CELL) & 0xFFFFF) << 20)
			^ ((long) Math.floorDiv(pos.getZ(), CELL) & 0xFFFFF);
		Vec3 known = KNOWN.get(key);
		if (known != null) {
			return known;
		}
		Vec3 heading = read(level, pos);
		if (KNOWN.size() > KEEP) {
			KNOWN.clear();
		}
		KNOWN.put(key, heading);
		return heading;
	}

	private static Vec3 read(Level level, BlockPos pos) {
		double n = 0.0, mx = 0.0, mz = 0.0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
			for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
				at.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
				if (river(level, at)) {
					n++;
					mx += dx;
					mz += dz;
				}
			}
		}
		if (n < FEWEST) {
			return NONE;
		}
		mx /= n;
		mz /= n;
		double sxx = 0.0, szz = 0.0, sxz = 0.0;
		for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
			for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
				at.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
				if (river(level, at)) {
					double x = dx - mx, z = dz - mz;
					sxx += x * x;
					szz += z * z;
					sxz += x * z;
				}
			}
		}
		// the long axis of the river cells
		double angle = 0.5 * Math.atan2(2.0 * sxz, sxx - szz);
		Vec3 axis = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		// the river runs toward the lower land
		double ahead = height(level, pos, axis.scale(LOOK)), behind = height(level, pos, axis.scale(-LOOK));
		return behind < ahead ? axis.scale(-1.0) : axis;
	}

	/** The land's height where {@code pos} + {@code by} lies (very high if unloaded, so it never counts as downhill). */
	private static double height(Level level, BlockPos pos, Vec3 by) {
		BlockPos there = pos.offset((int) by.x, 0, (int) by.z);
		if (!level.hasChunkAt(there)) {
			return 1.0E6;
		}
		return level.getHeight(Heightmap.Types.OCEAN_FLOOR, there.getX(), there.getZ());
	}
}
