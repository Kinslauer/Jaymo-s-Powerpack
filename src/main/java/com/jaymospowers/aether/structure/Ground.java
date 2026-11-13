package com.jaymospowers.aether.structure;

import com.jaymospowers.aether.AercloudBlock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * What the ground is like where a structure may stand, read from the world generator's base columns (before caves and features).
 * "Ground" is the top of the highest run of rock at least {@link #ISLAND_THICK} thick, since thin floating bits sit above the islands.
 * Only island counts as rock ({@link #land}): holystone and the other stones, Aether dirt and grass, never an aercloud.
 * <p>
 * The settlements (sky towns, sky villages, the sky castle) ask more of it than one spot: that nearly all of their footprint is
 * ground at their own floor's height ({@link #cover}), and that the island they stand on is a great landmass, not an islet or a
 * shelf at the edge of one ({@link #island}).
 */
final class Ground {
	// ---- tuning ----
	/** A run of rock must be this thick to count as ground (the Aether islands are far thicker). */
	static final int ISLAND_THICK = 16;
	/** Rock that must stand over a buried box, and the number of its five sample columns that must be solid at floor and ceiling. */
	static final int COVER = 3, SOLID_SAMPLES = 4;
	static final int NONE = Integer.MIN_VALUE / 2;
	/** Two neighbouring columns of an island (one {@link #island} step apart) may differ this much in height and still be one land. */
	static final int ISLAND_CLIMB = 12;

	private final Structure.GenerationContext context;
	private final Map<Long, Integer> tops = new HashMap<>();
	private final Map<Long, NoiseColumn> columns = new HashMap<>();

	Ground(Structure.GenerationContext context) {
		this.context = context;
	}

	private static long key(int x, int z) {
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** The y of the highest block of the ground at a column, or {@link #NONE}. */
	int top(int x, int z) {
		return this.tops.computeIfAbsent(key(x, z), k -> {
			int run = 0;
			for (int y = this.context.heightAccessor().getMaxY(); y >= this.context.heightAccessor().getMinY(); y--) {
				run = this.solid(x, y, z) ? run + 1 : 0;
				if (run >= ISLAND_THICK) {
					return y + run - 1;
				}
			}
			return NONE;
		});
	}

	boolean solid(int x, int y, int z) {
		return land(this.column(x, z).getBlock(y));
	}

	/**
	 * Whether a block is island to stand a structure on: holystone (and the Aether's other stones), Aether dirt and grass.
	 * Air, water and every aercloud are not, so a bank of cloud is never taken for ground.
	 */
	static boolean land(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof AercloudBlock);
	}

	/** Whether the column's ground is dry and lies within {@code off} blocks of {@code y}. */
	boolean near(int x, int z, int y, int off) {
		int top = this.top(x, z);
		return top != NONE && Math.abs(top - y) <= off && this.column(x, z).getBlock(top + 1).getFluidState().isEmpty();
	}

	/**
	 * The share (0 to 1) of the columns on a {@code step} grid over the rectangle {@code halfW} by {@code halfL} around
	 * (cx, cz), edges included, whose ground is dry and within {@code off} of {@code y}: how much of a footprint is real
	 * island at the floor's height, not open sky between a few lucky sample columns.
	 */
	float cover(int cx, int cz, int halfW, int halfL, int y, int off, int step) {
		int on = 0, all = 0;
		for (int dx = -halfW; dx <= halfW; dx = next(dx, halfW, step)) {
			for (int dz = -halfL; dz <= halfL; dz = next(dz, halfL, step)) {
				all++;
				on += this.near(cx + dx, cz + dz, y, off) ? 1 : 0;
			}
		}
		return all == 0 ? 0 : (float)on / all;
	}

	/** The next offset on a grid from {@code -half} to {@code half}: {@code step} on, but never past {@code half}, which is always sampled. */
	private static int next(int d, int half, int step) {
		return d >= half ? half + 1 : Math.min(d + step, half);
	}

	/**
	 * How much island (in blocks, each grid column counting {@code step} squared) joins (cx, cz) at about height {@code y}:
	 * a flood over a {@code step} grid from there, a column joining when its ground is dry, within {@code off} of {@code y}, and
	 * within {@link #ISLAND_CLIMB} of the neighbour it was reached from (so an islet, or a shelf far below, does not join), out to
	 * {@code reach} blocks. It stops as soon as {@code enough} is found and answers that.
	 */
	int island(int cx, int cz, int y, int off, int step, int reach, int enough) {
		if (!this.near(cx, cz, y, off)) {
			return 0;
		}
		int cells = reach / step;
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> open = new ArrayDeque<>();
		seen.add(key(0, 0));
		open.add(new int[] {0, 0, this.top(cx, cz)});
		int area = 0;
		while (!open.isEmpty()) {
			int[] at = open.poll();
			area += step * step;
			if (area >= enough) {
				return enough;
			}
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int i = at[0] + d[0], j = at[1] + d[1];
				if (Math.abs(i) > cells || Math.abs(j) > cells || !seen.add(key(i, j))) {
					continue;
				}
				int x = cx + i * step, z = cz + j * step;
				if (this.near(x, z, y, off) && Math.abs(this.top(x, z) - at[2]) <= ISLAND_CLIMB) {
					open.add(new int[] {i, j, this.top(x, z)});
				}
			}
		}
		return area;
	}

	private NoiseColumn column(int x, int z) {
		return this.columns.computeIfAbsent(key(x, z), k -> this.context.chunkGenerator().getBaseColumn(x, z, this.context.heightAccessor(),
			this.context.randomState()));
	}

	/**
	 * The y of the ground for a surface structure over the rectangle {@code halfW} by {@code halfL} around (cx, cz), or {@link #NONE} if
	 * it is not ground everywhere, is under water, or is not flat enough (the highest and lowest of nine columns must be within {@code tolerance}).
	 */
	int flat(int cx, int cz, int halfW, int halfL, int tolerance) {
		return this.flat(cx, cz, halfW, halfL, tolerance, false);
	}

	/** As {@link #flat(int, int, int, int, int)}; with {@code highest} the answer is the highest column instead of the middle one. */
	int flat(int cx, int cz, int halfW, int halfL, int tolerance, boolean highest) {
		int[] ys = new int[9];
		int n = 0;
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				int x = cx + i * halfW, z = cz + j * halfL;
				int top = this.top(x, z);
				if (top == NONE || this.column(x, z).getBlock(top + 1).getFluidState().isEmpty() == false) {
					return NONE;
				}
				ys[n++] = top;
			}
		}
		java.util.Arrays.sort(ys);
		return ys[8] - ys[0] > tolerance ? NONE : ys[highest ? 8 : 4];
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
