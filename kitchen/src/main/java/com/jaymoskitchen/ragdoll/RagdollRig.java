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
 * <li>{@link Shape#CHAIN}: (0.5.47) a small rigid head and a long body of many short links behind it, each joint
 * bending freely (snakes). It drapes over whatever it lies on like a rope.</li>
 * </ul>
 * Rest positions are in body space: feet on y = 0, facing +Z (south, as a mob with yaw 0), its right toward -X.
 * Nothing here touches Minecraft, so the simulation can be checked on its own (tools/ragdoll_sim).
 */
public final class RagdollRig {
	public enum Shape {
		// new shapes go on the end: saved corpses and the network keep the number
		HUMANOID, QUADRUPED, BOX, CHAIN;

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
	/**
	 * (0.5.47) Chain point indices: the head's corners are the torso's (snout "top", back of the head "bottom"), then
	 * the body's joints run back from the head, the last one the tail's tip.
	 */
	public static final int CHAIN_FIRST = 4;
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
	/**
	 * (0.5.47) A chain body: how many joints behind the head, its length (a share of its width), how thick it is, and
	 * how far each joint may bend (radians from straight). Andy: "a snake should go all noodle like when they ragdoll".
	 */
	private static final int CHAIN_JOINTS = 10;
	private static final float CHAIN_LENGTH = 3.5F, CHAIN_THICK = 0.16F, CHAIN_HEAD = 0.14F, CHAIN_BEND = 1.3F;
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
	/**
	 * (0.5.47) Struts: points {@code strutA[i]} and {@code strutB[i]} are pushed apart when nearer than
	 * {@code strutMin[i]}, never pulled together: how far a chain's joints can bend.
	 */
	public final int[] strutA, strutB;
	public final float[] strutMin;

	private RagdollRig(Shape shape, float width, float height, float[][] points, float[] radius, int[][] sticks,
		float[][] cones, int pivot) {
		this(shape, width, height, points, radius, sticks, cones, pivot, new int[0][], new float[0]);
	}

	private RagdollRig(Shape shape, float width, float height, float[][] points, float[] radius, int[][] sticks,
		float[][] cones, int pivot, int[][] struts, float[] strutShare) {
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
		this.strutA = new int[struts.length];
		this.strutB = new int[struts.length];
		this.strutMin = new float[struts.length];
		for (int i = 0; i < struts.length; i++) {
			int a = struts[i][0], b = struts[i][1];
			strutA[i] = a;
			strutB[i] = b;
			float dx = restX[a] - restX[b], dy = restY[a] - restY[b], dz = restZ[a] - restZ[b];
			strutMin[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * strutShare[i];
		}
	}

	/** (0.5.47) True for a chain body (a snake): its joints run from {@link #CHAIN_FIRST} to the last point. */
	public boolean chain() {
		return shape == Shape.CHAIN;
	}

	/** (0.5.47) The tail's tip of a chain body (its last point). */
	public int tailTip() {
		return count - 1;
	}

	public static RagdollRig of(Shape shape, float width, float height) {
		width = Math.max(width, 0.1F);
		height = Math.max(height, 0.1F);
		return switch (shape) {
			case HUMANOID -> humanoid(width, height);
			case QUADRUPED -> quadruped(width, height);
			case BOX -> box(width, height);
			case CHAIN -> chain(width, height);
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

	/**
	 * (0.5.47) A snake: lying along +Z (head forward), a small rigid head (four corners, as a torso's) and
	 * {@link #CHAIN_JOINTS} joints behind it, each held to the next by a stick and free to bend every way up to
	 * {@link #CHAIN_BEND} (struts two joints apart stop it folding flat on itself). Nothing stiffens it, so it sags
	 * over edges, drapes over bumps and flops when shoved.
	 */
	private static RagdollRig chain(float width, float height) {
		float length = Math.max(width * CHAIN_LENGTH, 0.6F);
		float thick = Math.max(Math.min(width * CHAIN_THICK, height * 0.25F), MIN_RADIUS);
		float head = Math.max(length * CHAIN_HEAD, thick * 2.0F);
		float halfW = thick * 0.7F;
		float front = length * 0.5F, back = front - head;
		float seg = (length - head) / CHAIN_JOINTS;
		int n = CHAIN_FIRST + CHAIN_JOINTS;
		float[][] points = new float[n][];
		points[TOP_RIGHT] = new float[] {-halfW, thick, front};
		points[TOP_LEFT] = new float[] {halfW, thick, front};
		points[BOTTOM_RIGHT] = new float[] {-halfW, thick, back};
		points[BOTTOM_LEFT] = new float[] {halfW, thick, back};
		float[] radius = new float[n];
		for (int k = 0; k < CHAIN_JOINTS; k++) {
			points[CHAIN_FIRST + k] = new float[] {0, thick, back - seg * (k + 1)};
			// tapering to the tail
			radius[CHAIN_FIRST + k] = Math.max(thick * (1.0F - 0.5F * k / (CHAIN_JOINTS - 1)), MIN_RADIUS);
		}
		for (int i = 0; i < CHAIN_FIRST; i++) {
			radius[i] = Math.max(thick * 0.8F, MIN_RADIUS);
		}
		// the head rigid, hinged to the first joint by both its back corners; then joint to joint
		int[][] extra = new int[2 + CHAIN_JOINTS - 1][];
		extra[0] = new int[] {BOTTOM_RIGHT, CHAIN_FIRST};
		extra[1] = new int[] {BOTTOM_LEFT, CHAIN_FIRST};
		for (int k = 0; k + 1 < CHAIN_JOINTS; k++) {
			extra[2 + k] = new int[] {CHAIN_FIRST + k, CHAIN_FIRST + k + 1};
		}
		int[][] sticks = join(new int[] {TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT}, extra);
		// bending: two links meeting at angle a (from straight) leave their far ends 2 cos(a / 2) of a link apart
		float bend = (float) Math.cos(CHAIN_BEND * 0.5F);
		int[][] struts = new int[3 + CHAIN_JOINTS - 2][];
		float[] share = new float[struts.length];
		// the head nods on its hinge no further than a joint bends
		struts[0] = new int[] {TOP_RIGHT, CHAIN_FIRST};
		struts[1] = new int[] {TOP_LEFT, CHAIN_FIRST};
		float nod = (float) Math.sqrt(head * head + seg * seg + 2.0F * head * seg * Math.cos(CHAIN_BEND)) / (head + seg);
		share[0] = share[1] = Math.min(1.0F, nod);
		// the back of the head to the second joint
		struts[2] = new int[] {BOTTOM_RIGHT, CHAIN_FIRST + 1};
		share[2] = bend;
		for (int k = 0; k + 2 < CHAIN_JOINTS; k++) {
			struts[3 + k] = new int[] {CHAIN_FIRST + k, CHAIN_FIRST + k + 2};
			share[3 + k] = bend;
		}
		return new RagdollRig(Shape.CHAIN, width, height, points, radius, sticks, new float[0][], CHAIN_FIRST, struts,
			share);
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
