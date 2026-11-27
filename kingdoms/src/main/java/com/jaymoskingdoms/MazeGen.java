package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The maze both of the mazes' designs are drawn from ({@link HedgeMaze}, {@link CryptMaze}): a perfect maze made by a
 * seeded recursive backtracker. It keeps its own splitmix64 stream, not the game's random, so the same seed draws the same
 * maze on every machine, and the Python that draws the pictures of it (tools/small_designs/mazes.py) gives the same layout.
 */
final class MazeGen {
	/** Neighbouring cells in the order they are tried: north, east, south, west (z grows southward). */
	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private MazeGen() {
	}

	/** A block of cells: its first cell and its side in cells. */
	record Room(int cx, int cz, int size) {
		boolean has(int x, int z) {
			return x >= this.cx && x < this.cx + this.size && z >= this.cz && z < this.cz + this.size;
		}
	}

	private static long next(long[] state) {
		state[0] += 0x9E3779B97F4A7C15L;
		long z = state[0];
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Blocks along a side of a maze of {@code n} cells: a cell at each odd block, a wall or a passage between two cells. */
	static int size(int n) {
		return 2 * n + 1;
	}

	/**
	 * Which blocks of the maze are open ({@code [x][z]}). A recursive backtracker from cell ({@code rootX}, {@code rootZ})
	 * carves a perfect maze over every cell outside the rooms. {@code heart} is an open room of cells, reached by one door
	 * set where the walk from the root is longest; {@code sealed} (or null) is a block of cells left closed for the caller
	 * to fill. Every cell outside the sealed room can be reached from the root, and from the heart.
	 */
	static boolean[][] layout(int n, Room heart, Room sealed, long seed, int rootX, int rootZ) {
		boolean[][] open = new boolean[size(n)][size(n)];
		long[] rng = {seed};
		Map<Long, Integer> depth = new HashMap<>();
		List<int[]> stack = new ArrayList<>();
		depth.put(key(rootX, rootZ), 0);
		open[2 * rootX + 1][2 * rootZ + 1] = true;
		stack.add(new int[] {rootX, rootZ});
		while (!stack.isEmpty()) {
			int[] at = stack.get(stack.size() - 1);
			int[][] options = new int[4][];
			int count = 0;
			for (int[] d : DIRS) {
				int nx = at[0] + d[0], nz = at[1] + d[1];
				if (nx >= 0 && nx < n && nz >= 0 && nz < n && !inRoom(heart, sealed, nx, nz) && !depth.containsKey(key(nx, nz))) {
					options[count++] = d;
				}
			}
			if (count == 0) {
				stack.remove(stack.size() - 1);
				continue;
			}
			int[] d = options[(int)Long.remainderUnsigned(next(rng), count)];
			int nx = at[0] + d[0], nz = at[1] + d[1];
			open[2 * at[0] + 1 + d[0]][2 * at[1] + 1 + d[1]] = true;
			open[2 * nx + 1][2 * nz + 1] = true;
			depth.put(key(nx, nz), depth.get(key(at[0], at[1])) + 1);
			stack.add(new int[] {nx, nz});
		}
		for (int x = 2 * heart.cx() + 1; x <= 2 * (heart.cx() + heart.size() - 1) + 1; x++) {
			for (int z = 2 * heart.cz() + 1; z <= 2 * (heart.cz() + heart.size() - 1) + 1; z++) {
				open[x][z] = true;
			}
		}
		int best = -1, bx = 0, bz = 0;
		int[] bd = DIRS[0];
		for (int cx = 0; cx < n; cx++) {
			for (int cz = 0; cz < n; cz++) {
				Integer here = depth.get(key(cx, cz));
				if (here == null) {
					continue;
				}
				for (int[] d : DIRS) {
					if (heart.has(cx + d[0], cz + d[1]) && here > best) {
						best = here;
						bx = cx;
						bz = cz;
						bd = d;
					}
				}
			}
		}
		if (best >= 0) {
			open[2 * bx + 1 + bd[0]][2 * bz + 1 + bd[1]] = true;
		}
		return open;
	}

	private static boolean inRoom(Room heart, Room sealed, int x, int z) {
		return heart.has(x, z) || sealed != null && sealed.has(x, z);
	}

	private static long key(int x, int z) {
		return (long)x << 32 ^ (z & 0xFFFFFFFFL);
	}
}
