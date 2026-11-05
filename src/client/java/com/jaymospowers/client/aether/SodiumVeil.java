package com.jaymospowers.client.aether;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;

/**
 * (0.20.52) {@link AetherVeil} for Sodium, which draws the pack's chunks: every frustum it culls with is wrapped so that boxes
 * at or above the veil's ceiling are outside it. Loaded only when Sodium is.
 */
public final class SodiumVeil {
	private SodiumVeil() {
	}

	/** Have Sodium cull again (the ceiling moved). */
	static void refresh() {
		SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
		if (renderer != null) {
			renderer.scheduleTerrainUpdate();
		}
	}

	/** This frustum, with the veil's ceiling as one more side; {@code cameraY} is the height its camera-relative boxes are measured from. */
	public static Frustum wrap(Frustum frustum, double cameraY) {
		return frustum instanceof Veiled ? frustum : new Veiled(frustum, cameraY);
	}

	/** Sodium's frustum with a ceiling (read live, so a viewport kept a little while still follows the veil). */
	private record Veiled(Frustum inner, double cameraY) implements Frustum {
		/** Half a chunk section, around the centres Sodium tests sections by. */
		private static final float HALF = 8.0F;
		/** JOML's FrustumIntersection answers, which Sodium's trees read. */
		private static final int INSIDE = -2, INTERSECT = -1, OUTSIDE = -3;

		/** The ceiling relative to the camera, or +infinity. */
		private float ceiling() {
			double world = AetherVeil.ceiling();
			return world == AetherVeil.NONE ? Float.POSITIVE_INFINITY : (float) (world - this.cameraY);
		}

		@Override
		public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
			return minY < this.ceiling() && this.inner.testAab(minX, minY, minZ, maxX, maxY, maxZ);
		}

		@Override
		public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
			float ceiling = this.ceiling();
			if (minY >= ceiling) {
				return OUTSIDE;
			}
			int answer = this.inner.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
			// a box reaching through the ceiling is only partly in: its parts get tested one by one
			return answer == INSIDE && maxY > ceiling ? INTERSECT : answer;
		}

		@Override
		public boolean testSection(float x, float y, float z) {
			return y - HALF < this.ceiling() && this.inner.testSection(x, y, z);
		}

		@Override
		public boolean testSectionExpanded(float x, float y, float z, float expand) {
			return y - HALF - expand < this.ceiling() && this.inner.testSectionExpanded(x, y, z, expand);
		}
	}
}
