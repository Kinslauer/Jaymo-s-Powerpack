package com.jaymoskitchen.ragdoll;

/**
 * What a ragdoll can bump into, asked once a tick around the body (see {@link RagdollBody#step}). The game's side is
 * {@code LevelRagdollWorld}; the checks in tools/ragdoll_sim build their own.
 */
public interface RagdollWorld {
	/** Adds every solid box overlapping the given box to {@code out}. */
	void solids(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out);

	/** Adds every moving thing (a walker, a cart, a boat) overlapping the given box to {@code out}, with its speed. */
	default void movers(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Boxes out) {
	}

	/** What's at a point: {@link #AIR}, {@link #WATER} or {@link #LAVA}. */
	default int fluid(double x, double y, double z) {
		return AIR;
	}

	int AIR = 0, WATER = 1, LAVA = 2;

	/** A growable list of boxes, six numbers each (min x y z, max x y z), with an optional speed (three more). */
	final class Boxes {
		double[] data = new double[9 * 16];
		int size;

		public void clear() {
			size = 0;
		}

		public int size() {
			return size;
		}

		public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			add(minX, minY, minZ, maxX, maxY, maxZ, 0, 0, 0);
		}

		public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double vx,
			double vy, double vz) {
			if ((size + 1) * 9 > data.length) {
				data = java.util.Arrays.copyOf(data, data.length * 2);
			}
			int o = size++ * 9;
			data[o] = minX;
			data[o + 1] = minY;
			data[o + 2] = minZ;
			data[o + 3] = maxX;
			data[o + 4] = maxY;
			data[o + 5] = maxZ;
			data[o + 6] = vx;
			data[o + 7] = vy;
			data[o + 8] = vz;
		}
	}
}
