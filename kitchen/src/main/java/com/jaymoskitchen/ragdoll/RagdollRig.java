package com.jaymoskitchen.ragdoll;

/**
 * (0.5.1) The skeleton a ragdoll is built from: point masses (joints, hands, feet, the head) held together by rigid
 * sticks, with each limb kept inside a cone of the ways it can really swing. Worked out from the body's standing size
 * only, so the server needs no model: every body is one of three shapes.
 * <ul>
 * <li>{@link Shape#HUMANOID}: an upright torso (shoulders and hips), a head on the neck, two arms, two legs.
 * Players, zombies, skeletons, villagers, illagers, piglins, endermen...</li>
 * <li>{@link Shape#QUADRUPED}: a level torso (front and back corners), a head out in front, four legs.</li>
 * <li>{@link Shape#BOX}: a rigid block the body's size (fish, chickens, slimes, spiders, anything odd).</li>
 * </ul>
 * Rest positions are in body space: feet on y = 0, facing +Z (south, as a mob with yaw 0), its right toward -X.
 * Nothing here touches Minecraft, so the simulation can be checked on its own (tools/ragdoll_sim).
 */
public final class RagdollRig {
	public enum Shape {
		HUMANOID, QUADRUPED, BOX;

		public static Shape byId(int id) {
			Shape[] all = values();
			return id >= 0 && id < all.length ? all[id] : BOX;
		}
	}

	/** Humanoid point indices. The first four are the torso corners every shape has (see {@link #TOP_RIGHT}). */
	public static final int R_SHOULDER = 0, L_SHOULDER = 1, R_HIP = 2, L_HIP = 3, NECK = 4, HEAD = 5, R_HAND = 6,
		L_HAND = 7, R_FOOT = 8, L_FOOT = 9, CHEST = 10;
	/** Quadruped and box point indices (the torso corners again: front is "top", toward the head). */
	public static final int FRONT_RIGHT = 0, FRONT_LEFT = 1, BACK_RIGHT = 2, BACK_LEFT = 3, SNOUT = 4, MUZZLE = 5,
		FR_FOOT = 6, FL_FOOT = 7, BR_FOOT = 8, BL_FOOT = 9, MIDDLE = 10;
	/** A box body's middle (where it's drawn from). */
	public static final int BOX_MIDDLE = 8;
	/** The torso's four corners, by role: the two toward the head ("top") and the two away from it. */
	public static final int TOP_RIGHT = 0, TOP_LEFT = 1, BOTTOM_RIGHT = 2, BOTTOM_LEFT = 3;

	/** A humanoid in 32 "pixels" from feet to crown, the way the game's player model is drawn. */
	private static final float PX = 1.0F / 32.0F;
	/** How far each humanoid limb can swing from hanging straight (radians): arms nearly anywhere, legs less. */
	private static final float ARM_CONE = 2.8F, LEG_CONE = 1.45F, HEAD_CONE = 0.9F;
	/** Legs swing forward (sitting up, curling) much further than back: their cone leans this far forward. */
	private static final float LEG_LEAN = 0.55F;
	/** Quadruped legs, and how far their heads nod. */
	private static final float HOOF_CONE = 1.2F, MUZZLE_CONE = 1.0F;
	/** Smallest a point can be (blocks), so tiny bodies still touch the ground. */
	private static final float MIN_RADIUS = 0.04F;

	public final Shape shape;
	/** The body's standing size, kept for rebuilding and saving. */
	public final float width, height;
	public final int count;
	public final float[] restX, restY, restZ, radius;
	/** Sticks between points, each held at its rest length. */
	public final int[] stickA, stickB;
	public final float[] stickLength;
	/**
	 * Cones: point {@code coneTip[i]} swings from {@code coneRoot[i]}, and stays within {@code coneAngle[i]} of the
	 * axis {@code coneAxis*[i]}, given in the torso's own frame (x right, y "up" toward the head, z forward).
	 */
	public final int[] coneRoot, coneTip;
	public final float[] coneAxisX, coneAxisY, coneAxisZ, coneAngle;
	/** The point the body's height above the ground is measured at, and where it's drawn from (the neck). */
	public final int pivot;

	private RagdollRig(Shape shape, float width, float height, float[][] points, float[] radius, int[][] sticks,
		float[][] cones, int pivot) {
		this.shape = shape;
		this.width = width;
		this.height = height;
		this.count = points.length;
		this.restX = new float[count];
		this.restY = new float[count];
		this.restZ = new float[count];
		for (int i = 0; i < count; i++) {
			restX[i] = points[i][0];
			restY[i] = points[i][1];
			restZ[i] = points[i][2];
		}
		this.radius = radius;
		this.stickA = new int[sticks.length];
		this.stickB = new int[sticks.length];
		this.stickLength = new float[sticks.length];
		for (int i = 0; i < sticks.length; i++) {
			int a = sticks[i][0], b = sticks[i][1];
			stickA[i] = a;
			stickB[i] = b;
			float dx = restX[a] - restX[b], dy = restY[a] - restY[b], dz = restZ[a] - restZ[b];
			stickLength[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		int n = cones.length;
		this.coneRoot = new int[n];
		this.coneTip = new int[n];
		this.coneAxisX = new float[n];
		this.coneAxisY = new float[n];
		this.coneAxisZ = new float[n];
		this.coneAngle = new float[n];
		for (int i = 0; i < n; i++) {
			coneRoot[i] = (int) cones[i][0];
			coneTip[i] = (int) cones[i][1];
			float ax = cones[i][2], ay = cones[i][3], az = cones[i][4];
			float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
			coneAxisX[i] = ax / len;
			coneAxisY[i] = ay / len;
			coneAxisZ[i] = az / len;
			coneAngle[i] = cones[i][5];
		}
		this.pivot = pivot;
	}

	public static RagdollRig of(Shape shape, float width, float height) {
		width = Math.max(width, 0.1F);
		height = Math.max(height, 0.1F);
		return switch (shape) {
			case HUMANOID -> humanoid(width, height);
			case QUADRUPED -> quadruped(width, height);
			case BOX -> box(width, height);
		};
	}

	/** Guesses a body's shape from its standing size alone, for mobs no tag names. */
	public static Shape guess(float width, float height) {
		if (height >= width * 1.6F && height >= 0.9F) {
			return Shape.HUMANOID;
		}
		if (width >= 0.6F && height >= 0.6F && height <= width * 1.6F) {
			return Shape.QUADRUPED;
		}
		return Shape.BOX;
	}

	private static RagdollRig humanoid(float width, float height) {
		// the player model in pixels: legs 12 tall, torso 12, head 8; torso 8 wide and 4 deep, limbs 4 by 4
		float u = height * PX;
		// thin bodies (skeletons, endermen) keep thin limbs; the torso is never wider than the body's box
		float half = Math.min(4.0F * u, width * 0.5F);
		float hip = half * 0.5F;
		float limb = Math.max(2.0F * u * Math.min(1.0F, width / 0.6F), MIN_RADIUS);
		float[][] points = {
			{-half, 22 * u, 0}, {half, 22 * u, 0}, {-hip, 12 * u, 0}, {hip, 12 * u, 0},
			{0, 24 * u, 0}, {0, 28 * u, 0},
			{-half, 10 * u, 0}, {half, 10 * u, 0}, {-hip, 0, 0}, {hip, 0, 0},
			{0, 17 * u, 0}
		};
		float[] radius = new float[points.length];
		java.util.Arrays.fill(radius, limb);
		radius[HEAD] = Math.max(4.0F * u, MIN_RADIUS);
		radius[NECK] = limb;
		radius[CHEST] = limb;
		int[][] sticks = join(new int[] {R_SHOULDER, L_SHOULDER, R_HIP, L_HIP, NECK, CHEST},
			new int[][] {{NECK, HEAD}, {R_SHOULDER, R_HAND}, {L_SHOULDER, L_HAND}, {R_HIP, R_FOOT}, {L_HIP, L_FOOT}});
		float lean = (float) Math.sin(LEG_LEAN), drop = (float) Math.cos(LEG_LEAN);
		float[][] cones = {
			{NECK, HEAD, 0, 1, 0, HEAD_CONE},
			{R_SHOULDER, R_HAND, 0, -1, 0, ARM_CONE},
			{L_SHOULDER, L_HAND, 0, -1, 0, ARM_CONE},
			{R_HIP, R_FOOT, 0, -drop, lean, LEG_CONE},
			{L_HIP, L_FOOT, 0, -drop, lean, LEG_CONE}
		};
		return new RagdollRig(Shape.HUMANOID, width, height, points, radius, sticks, cones, NECK);
	}

	private static RagdollRig quadruped(float width, float height) {
		// legs take the lower 40%, the torso sits on them; a body as long as it is wide plus a bit
		float legs = height * 0.4F;
		float thick = Math.max(height * 0.22F, MIN_RADIUS);
		float y = legs + thick;
		float halfW = Math.max(width * 0.5F - thick * 0.6F, 0.05F);
		float halfL = Math.max(width * 0.75F - thick, 0.1F);
		float head = Math.max(Math.min(height, width) * 0.22F, MIN_RADIUS);
		float[][] points = {
			{-halfW, y, halfL}, {halfW, y, halfL}, {-halfW, y, -halfL}, {halfW, y, -halfL},
			{0, y + thick * 0.6F, halfL + thick * 0.4F}, {0, y + thick * 0.8F, halfL + thick * 0.4F + head * 2.2F},
			{-halfW, 0, halfL}, {halfW, 0, halfL}, {-halfW, 0, -halfL}, {halfW, 0, -halfL},
			{0, y, 0}
		};
		float[] radius = new float[points.length];
		float leg = Math.max(thick * 0.45F, MIN_RADIUS);
		java.util.Arrays.fill(radius, leg);
		for (int corner : new int[] {FRONT_RIGHT, FRONT_LEFT, BACK_RIGHT, BACK_LEFT, MIDDLE}) {
			radius[corner] = thick;
		}
		radius[SNOUT] = thick * 0.8F;
		radius[MUZZLE] = head;
		int[][] sticks = join(new int[] {FRONT_RIGHT, FRONT_LEFT, BACK_RIGHT, BACK_LEFT, SNOUT, MIDDLE},
			new int[][] {{SNOUT, MUZZLE}, {FRONT_RIGHT, FR_FOOT}, {FRONT_LEFT, FL_FOOT}, {BACK_RIGHT, BR_FOOT},
				{BACK_LEFT, BL_FOOT}});
		// in a level torso's frame "up" (y) runs to the head and "forward" (z) out of the belly: legs hang along +z,
		// the head reaches on along +y, lifted a little (-z)
		float[][] cones = {
			{SNOUT, MUZZLE, 0, 0.95F, -0.3F, MUZZLE_CONE},
			{FRONT_RIGHT, FR_FOOT, 0, 0, 1, HOOF_CONE},
			{FRONT_LEFT, FL_FOOT, 0, 0, 1, HOOF_CONE},
			{BACK_RIGHT, BR_FOOT, 0, 0, 1, HOOF_CONE},
			{BACK_LEFT, BL_FOOT, 0, 0, 1, HOOF_CONE}
		};
		return new RagdollRig(Shape.QUADRUPED, width, height, points, radius, sticks, cones, SNOUT);
	}

	private static RagdollRig box(float width, float height) {
		// a block the size of the body, rigid: eight corners and a middle. It tumbles, rolls onto a side, and lies there
		float r = Math.max(Math.min(width, height) * 0.3F, MIN_RADIUS);
		float halfW = Math.max(width * 0.5F - r, r * 0.5F);
		float halfL = Math.max(width * 0.6F - r, r * 0.5F);
		float low = r, high = Math.max(height - r, r * 1.5F);
		float[][] points = {
			{-halfW, low, halfL}, {halfW, low, halfL}, {-halfW, low, -halfL}, {halfW, low, -halfL},
			{-halfW, high, halfL}, {halfW, high, halfL}, {-halfW, high, -halfL}, {halfW, high, -halfL},
			{0, (low + high) * 0.5F, 0}
		};
		float[] radius = new float[points.length];
		java.util.Arrays.fill(radius, r);
		int[][] sticks = join(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, new int[0][]);
		return new RagdollRig(Shape.BOX, width, height, points, radius, sticks, new float[0][], BOX_MIDDLE);
	}

	/** Every pair of {@code rigid} points held fast (a rigid body), plus the {@code extra} sticks. */
	private static int[][] join(int[] rigid, int[][] extra) {
		int pairs = rigid.length * (rigid.length - 1) / 2;
		int[][] out = new int[pairs + extra.length][];
		int k = 0;
		for (int i = 0; i < rigid.length; i++) {
			for (int j = i + 1; j < rigid.length; j++) {
				out[k++] = new int[] {rigid[i], rigid[j]};
			}
		}
		for (int[] e : extra) {
			out[k++] = e;
		}
		return out;
	}

	/** The torso's frame at rest, as the renderer needs it: right, up (toward the head), forward. */
	public void restFrame(float[] out9) {
		frame(restX, restY, restZ, out9);
	}

	/**
	 * The torso's frame from four corner positions: x = right, y = toward the head, z = forward (out of the chest or
	 * the back, for a humanoid, out of the belly for a quadruped). Rows of a 3x3 matrix, orthonormal.
	 */
	public static void frame(float[] x, float[] y, float[] z, float[] out9) {
		float rx = (x[TOP_RIGHT] + x[BOTTOM_RIGHT]) - (x[TOP_LEFT] + x[BOTTOM_LEFT]);
		float ry = (y[TOP_RIGHT] + y[BOTTOM_RIGHT]) - (y[TOP_LEFT] + y[BOTTOM_LEFT]);
		float rz = (z[TOP_RIGHT] + z[BOTTOM_RIGHT]) - (z[TOP_LEFT] + z[BOTTOM_LEFT]);
		float ux = (x[TOP_RIGHT] + x[TOP_LEFT]) - (x[BOTTOM_RIGHT] + x[BOTTOM_LEFT]);
		float uy = (y[TOP_RIGHT] + y[TOP_LEFT]) - (y[BOTTOM_RIGHT] + y[BOTTOM_LEFT]);
		float uz = (z[TOP_RIGHT] + z[TOP_LEFT]) - (z[BOTTOM_RIGHT] + z[BOTTOM_LEFT]);
		frameOf(rx, ry, rz, ux, uy, uz, out9);
	}

	static void frame(double[] x, double[] y, double[] z, float[] out9) {
		float rx = (float) ((x[TOP_RIGHT] + x[BOTTOM_RIGHT]) - (x[TOP_LEFT] + x[BOTTOM_LEFT]));
		float ry = (float) ((y[TOP_RIGHT] + y[BOTTOM_RIGHT]) - (y[TOP_LEFT] + y[BOTTOM_LEFT]));
		float rz = (float) ((z[TOP_RIGHT] + z[BOTTOM_RIGHT]) - (z[TOP_LEFT] + z[BOTTOM_LEFT]));
		float ux = (float) ((x[TOP_RIGHT] + x[TOP_LEFT]) - (x[BOTTOM_RIGHT] + x[BOTTOM_LEFT]));
		float uy = (float) ((y[TOP_RIGHT] + y[TOP_LEFT]) - (y[BOTTOM_RIGHT] + y[BOTTOM_LEFT]));
		float uz = (float) ((z[TOP_RIGHT] + z[TOP_LEFT]) - (z[BOTTOM_RIGHT] + z[BOTTOM_LEFT]));
		frameOf(rx, ry, rz, ux, uy, uz, out9);
	}

	private static void frameOf(float rx, float ry, float rz, float ux, float uy, float uz, float[] out9) {
		// up first (the torso's long axis), then right made square to it, forward = up x right
		float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
		if (ul < 1.0E-6F) {
			ux = 0;
			uy = 1;
			uz = 0;
			ul = 1;
		}
		ux /= ul;
		uy /= ul;
		uz /= ul;
		float d = rx * ux + ry * uy + rz * uz;
		rx -= d * ux;
		ry -= d * uy;
		rz -= d * uz;
		float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (rl < 1.0E-6F) {
			// right lost (folded flat): any square direction will do
			if (Math.abs(ux) < 0.9F) {
				rx = 0;
				ry = -uz;
				rz = uy;
			} else {
				rx = uz;
				ry = 0;
				rz = -ux;
			}
			rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
		}
		rx /= rl;
		ry /= rl;
		rz /= rl;
		// rest: right = -X, up = +Y gives forward = up x right = +Z (south), as the body space says
		float fx = uy * rz - uz * ry, fy = uz * rx - ux * rz, fz = ux * ry - uy * rx;
		out9[0] = rx;
		out9[1] = ry;
		out9[2] = rz;
		out9[3] = ux;
		out9[4] = uy;
		out9[5] = uz;
		out9[6] = fx;
		out9[7] = fy;
		out9[8] = fz;
	}
}
