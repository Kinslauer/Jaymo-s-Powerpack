package com.jaymospowers.aether.structure;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * What the ground is like where a structure may stand, read from the world generator's base columns (before caves and features).
 * "Ground" is the top of the highest run of rock at least {@link #ISLAND_THICK} thick, since thin floating bits sit above the islands.
 */
final class Ground {
	// ---- tuning ----
	/** A run of rock must be this thick to count as ground (the Aether islands are far thicker). */
	static final int ISLAND_THICK = 16;
	/** Rock that must stand over a buried box, and the number of its five sample columns that must be solid at floor and ceiling. */
	static final int COVER = 3, SOLID_SAMPLES = 4;
	static final int NONE = Integer.MIN_VALUE / 2;

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
		BlockState state = this.column(x, z).getBlock(y);
		return !state.isAir() && state.getFluidState().isEmpty();
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
