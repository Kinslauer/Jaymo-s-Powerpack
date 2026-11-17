package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The wood left in one chopped log: {@link #N} cells a side, carved away where the axe lands ({@link #carve}), and
 * the faces that draws it ({@link #faces}). Ported from Immersive Tree Chop by Litewer
 * ({@code dev.litewer.immersivetreechop.wood.WoodVolume}), MIT: Copyright (c) 2026 Litewer, licence text in
 * {@code docs/licenses/Immersive-Tree-Chop-MIT.txt}. Andy (2026-11-16): "i want the trunk to carve out, retaining the
 * interior texture and all". Only the carving is taken; the rest of that mod (its falling trees, animations and sounds)
 * is not.
 */
public final class WoodVolume {
	/** Cells along each side of the log, and in all of it. */
	public static final int N = 16;
	public static final int SIZE = N * N * N;

	// ---- tuning (Immersive Tree Chop's cut, in blocks) ----
	/** How deep one stroke bites, and the most a harder stroke adds. */
	static final double BITE_DEPTH = 0.13, BITE_DEPTH_MORE = 0.17, BITE_DEPTH_PER_POWER = 0.6;
	/** Half the cut's width across the trunk, and the most a harder stroke adds. */
	static final double BITE_WIDTH = 0.31, BITE_WIDTH_MORE = 0.09, BITE_WIDTH_PER_POWER = 0.2;
	/** Half the cut's height. */
	static final double BITE_HEIGHT = 0.095;
	/** The bite's reach inward, as a share of its depth. */
	static final double BITE_REACH = 0.69;
	/** Each stroke slants the other way by this much across the cut, so two strokes leave a notch. */
	static final double BITE_SLANT = 0.08;
	/** How ragged the cut's edge is (0 clean). */
	static final double BITE_RAGGED = 0.2;
	/** No bite starts deeper into the log than this (blocks), so strokes at one spot never saw clean through it (ours). */
	static final double CUT_LIMIT = 0.55;

	private final BitSet wood;
	private List<Face> mesh;

	/**
	 * One flat face of the carved wood, in cells: it lies across {@code axis} (0 x, 1 y, 2 z) at {@code plane}, looking
	 * the {@code positive} way or the other, from ({@code u0}, {@code v0}) to ({@code u1}, {@code v1}) ({@link #point}).
	 * {@code outside}: on the log's own surface (bark), not inside a cut.
	 */
	public record Face(int axis, boolean positive, int plane, int u0, int v0, int u1, int v1, boolean outside) {
	}

	/** A whole log. */
	public WoodVolume() {
		this.wood = new BitSet(SIZE);
		this.wood.set(0, SIZE);
	}

	/** A log as saved ({@link #data}). */
	public WoodVolume(long[] data) {
		this.wood = BitSet.valueOf(data);
		if (this.wood.length() > SIZE) {
			this.wood.clear(SIZE, this.wood.length());
		}
	}

	private static int index(int x, int y, int z) {
		return x + N * (z + N * y);
	}

	/** Whether there's wood in cell (x, y, z); none outside the log. */
	public boolean solid(int x, int y, int z) {
		return x >= 0 && x < N && y >= 0 && y < N && z >= 0 && z < N && this.wood.get(index(x, y, z));
	}

	/** The wood, to save or send. */
	public long[] data() {
		return this.wood.toLongArray();
	}

	/** How many cells of wood are left. */
	public int count() {
		return this.wood.cardinality();
	}

	/** Whether nothing has been cut from it. */
	public boolean whole() {
		return count() >= SIZE;
	}

	/**
	 * An axe stroke: cuts a bite out of the wood where the axe lands. {@code point} is where it struck, inside the block
	 * (0 to 1 each way); {@code face} the side it came in from; {@code power} how hard (0 an ordinary stroke); and
	 * {@code stroke} which stroke this is (odd and even slant opposite ways, and each is ragged its own way). The bite
	 * starts at the first wood inward from {@code point}, so each stroke goes deeper than the last. Returns how many
	 * cells it took.
	 */
	public int carve(Vec3 point, Direction face, float power, int stroke) {
		Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
		double depth = BITE_DEPTH + Math.min(BITE_DEPTH_MORE, power * BITE_DEPTH_PER_POWER);
		double width = BITE_WIDTH + Math.min(BITE_WIDTH_MORE, power * BITE_WIDTH_PER_POWER);
		Vec3 centre = surface(point, normal).subtract(normal.scale(depth * 0.5));
		Direction.Axis axis = face.getAxis();
		int removed = 0;
		for (int y = 0; y < N; y++) {
			for (int z = 0; z < N; z++) {
				for (int x = 0; x < N; x++) {
					int index = index(x, y, z);
					if (!this.wood.get(index)) {
						continue;
					}
					Vec3 d = new Vec3((x + 0.5) / N, (y + 0.5) / N, (z + 0.5) / N).subtract(centre);
					double in = d.dot(normal) / (depth * BITE_REACH);
					double across = axis == Direction.Axis.X ? d.z : d.x;
					double up = axis == Direction.Axis.Y ? d.z : d.y;
					up += across * ((stroke & 1) == 0 ? BITE_SLANT : -BITE_SLANT);
					double ragged = ((index * 73428767L + stroke * 912931L) >>> 4 & 31L) / 31.0 * BITE_RAGGED - BITE_RAGGED / 2.0;
					if (in * in + across / width * (across / width) + up / BITE_HEIGHT * (up / BITE_HEIGHT) <= 1.0 + ragged) {
						this.wood.clear(index);
						removed++;
					}
				}
			}
		}
		if (removed > 0) {
			this.mesh = null;
		}
		return removed;
	}

	/** The first wood straight in from {@code point} (the bottom of the cut already there), no deeper than {@link #CUT_LIMIT}. */
	private Vec3 surface(Vec3 point, Vec3 normal) {
		Vec3 step = normal.scale(-0.5 / N);
		// (just inside the face it struck)
		Vec3 at = point.add(step.scale(0.5));
		for (int i = 0; i * 0.5 / N < CUT_LIMIT; i++) {
			if (solid((int)Math.floor(at.x * N), (int)Math.floor(at.y * N), (int)Math.floor(at.z * N))) {
				break;
			}
			at = at.add(step);
		}
		return at;
	}

	/** Whether there's wood at ({@code u}, {@code v}) on {@code plane} across {@code axis}. */
	private boolean sample(int axis, int plane, int u, int v) {
		return switch (axis) {
			case 0 -> solid(plane, v, u);
			case 1 -> solid(u, plane, v);
			default -> solid(u, v, plane);
		};
	}

	/** A face's corner as a point in the block (0 to 1 each way): the reverse of how {@link #faces} walks each axis. */
	public static float[] point(int axis, float plane, float u, float v) {
		return switch (axis) {
			case 0 -> new float[] {plane, v, u};
			case 1 -> new float[] {u, plane, v};
			default -> new float[] {u, v, plane};
		};
	}

	/** The wood's surface as flat faces, neighbouring cells joined into the biggest rectangles (kept until the next cut). */
	public List<Face> faces() {
		if (this.mesh != null) {
			return this.mesh;
		}
		List<Face> faces = new ArrayList<>();
		for (int axis = 0; axis < 3; axis++) {
			for (boolean positive : new boolean[] {false, true}) {
				for (int plane = 0; plane <= N; plane++) {
					boolean[][] open = new boolean[N][N];
					for (int v = 0; v < N; v++) {
						for (int u = 0; u < N; u++) {
							open[v][u] = sample(axis, positive ? plane - 1 : plane, u, v) && !sample(axis, positive ? plane : plane - 1, u, v);
						}
					}
					for (int v = 0; v < N; v++) {
						for (int u = 0; u < N; u++) {
							if (!open[v][u]) {
								continue;
							}
							int w = 1;
							while (u + w < N && open[v][u + w]) {
								w++;
							}
							int h = 1;
							grow:
							while (v + h < N) {
								for (int i = 0; i < w; i++) {
									if (!open[v + h][u + i]) {
										break grow;
									}
								}
								h++;
							}
							for (int j = 0; j < h; j++) {
								for (int i = 0; i < w; i++) {
									open[v + j][u + i] = false;
								}
							}
							faces.add(new Face(axis, positive, plane, u, v, u + w, v + h, plane == 0 || plane == N));
						}
					}
				}
			}
		}
		this.mesh = List.copyOf(faces);
		return this.mesh;
	}
}
