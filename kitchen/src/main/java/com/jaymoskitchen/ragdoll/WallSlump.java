package com.jaymoskitchen.ragdoll;

import java.util.Random;

/**
 * Andy: "can enemies who ragdoll into walls pose similarly? as if they were knocked into the wall and slipped down onto
 * the floor? basically so they dont all default to lying flat." A humanoid body flung hard into a wall slides down it
 * and ends sitting on the floor with its back to the wall: legs out in front, arms fallen to its sides, slumped a
 * little to one side with its head lolled.
 * <p>
 * Like {@link KeelOver}, it steers the ragdoll's points through the physics (so blocks still hold them): over
 * {@link #SLIDE_TICKS} (a man's) each point is moved along from where it was when the body struck to where it sits. Once down,
 * the body is left resting there; the owner steps it without gravity while it rests (a body propped on a wall is held
 * up by friction the point physics doesn't have), and lets it fall free once anything disturbs it. Nothing here
 * touches Minecraft, so it can be checked on its own (tools/ragdoll_sim).
 */
public final class WallSlump {
	/**
	 * Struck a wall losing at least this much of its sideways speed in one step (blocks a tick: a body killed beside a
	 * wall by a hard blow loses some 0.15 to 0.2 against it, one thrown far more), in the air or still more upright than {@link #UPRIGHT} (the torso's up,
	 * straight up being 1), it slides down. A body lying on the floor shoved into a wall stays lying.
	 */
	static final double HIT = 0.12;
	static final float UPRIGHT = 0.5F;
	/** Ticks to slide from where it struck down to sitting, for a body a man's height; taller ones slide slower (as the root of their height). */
	static final int SLIDE_TICKS = 14;
	private static final float MAN_HEIGHT = 1.8F;
	/** How far the torso leans back against the wall from upright (radians), at least and at most. */
	private static final float LEAN_MIN = 0.25F, LEAN_MAX = 0.45F;
	/** How far it slumps over to one side (radians), at least and at most. */
	private static final float ROLL_MIN = 0.08F, ROLL_MAX = 0.3F;
	/** The head: dropped forward toward the chest, and tipped toward the side it slumps to (radians). */
	private static final float HEAD_DROOP = 0.6F, HEAD_TILT = 0.45F;
	/** Each leg turns out from straight ahead by this much (radians), at least and at most. */
	private static final float SPLAY_MIN = 0.08F, SPLAY_MAX = 0.4F;
	/** Each hand lands this far round from straight out to the side toward the front (radians), at least and at most. */
	private static final float ARM_FORWARD_MIN = 0.4F, ARM_FORWARD_MAX = 1.2F;
	/** Passes putting the bones' lengths back in each tick's steered pose. */
	private static final int LENGTH_PASSES = 4;
	/** Kept off the wall and the floor by this much (blocks), so it starts touching neither. */
	private static final float GAP = 0.01F;

	private final RagdollRig rig;
	private final double[] tx, ty, tz;
	/** Set as the slide starts: each pose's points about its torso's middle in the torso's frame, its turn, its middle. */
	private double[] localS, localT, turnS, turnT, startMiddle, endMiddle;
	private final int slide;
	/** The wall's face (out of it), and how far the body bounces off it as it turns (blocks). */
	private final double nx, nz, bounce;
	private int tick;

	/**
	 * A slump for {@code body}, which struck a wall: {@code (nx, nz)} is the wall's face, pointing out toward the body
	 * (level, length 1); {@code wall} is where its face is along that line (the face's points p have p.n = wall);
	 * {@code floorY} the floor at its foot. The body sits where its middle was, slid along the wall.
	 */
	public WallSlump(RagdollBody body, double nx, double nz, double wall, double floorY, long seed) {
		this.rig = body.rig;
		this.nx = nx;
		this.nz = nz;
		this.bounce = Math.abs(rig.restX[RagdollRig.L_SHOULDER]) + rig.radius[RagdollRig.L_SHOULDER];
		this.slide = Math.max(SLIDE_TICKS, Math.round(SLIDE_TICKS * (float) Math.sqrt(rig.height / MAN_HEIGHT)));
		int n = rig.count;
		Random random = new Random(seed);
		float lean = LEAN_MIN + random.nextFloat() * (LEAN_MAX - LEAN_MIN);
		float side = random.nextBoolean() ? 1.0F : -1.0F;
		float roll = side * (ROLL_MIN + random.nextFloat() * (ROLL_MAX - ROLL_MIN));
		// body space: x its left, y up, z away from the wall (forward); the hips' middle at x = 0
		float[] bx = new float[n], by = new float[n], bz = new float[n];
		float hipY = rig.restY[RagdollRig.R_HIP];
		int[] torso = {RagdollRig.R_SHOULDER, RagdollRig.L_SHOULDER, RagdollRig.R_HIP, RagdollRig.L_HIP, RagdollRig.NECK,
			RagdollRig.CHEST};
		for (int i : torso) {
			place(rig.restX[i], rig.restY[i] - hipY, rig.restZ[i], lean, roll, i, bx, by, bz);
		}
		// the head: dropped forward and tipped to the slumped side about the neck, in the torso's own frame
		{
			float hx = rig.restX[RagdollRig.HEAD] - rig.restX[RagdollRig.NECK];
			float hy = rig.restY[RagdollRig.HEAD] - rig.restY[RagdollRig.NECK];
			float tilt = Math.signum(roll) * HEAD_TILT * (0.6F + random.nextFloat() * 0.4F);
			float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
			float rx = hx * ct - hy * st, ry = hx * st + hy * ct;
			float cd = (float) Math.cos(HEAD_DROOP), sd = (float) Math.sin(HEAD_DROOP);
			float ox = rx, oy = ry * cd, oz = ry * sd;
			// the neck's own place in the torso, plus the head's turned reach from it
			place(rig.restX[RagdollRig.NECK] + ox, rig.restY[RagdollRig.NECK] - hipY + oy, rig.restZ[RagdollRig.NECK] + oz,
				lean, roll, RagdollRig.HEAD, bx, by, bz);
		}
		// sat on the floor: the lowest of the torso and head just on it
		float low = Float.MAX_VALUE, back = Float.MAX_VALUE;
		for (int i : new int[] {RagdollRig.R_SHOULDER, RagdollRig.L_SHOULDER, RagdollRig.R_HIP, RagdollRig.L_HIP,
			RagdollRig.NECK, RagdollRig.CHEST, RagdollRig.HEAD}) {
			low = Math.min(low, by[i] - rig.radius[i]);
			back = Math.min(back, bz[i] - rig.radius[i]);
		}
		for (int i : new int[] {RagdollRig.R_SHOULDER, RagdollRig.L_SHOULDER, RagdollRig.R_HIP, RagdollRig.L_HIP,
			RagdollRig.NECK, RagdollRig.CHEST, RagdollRig.HEAD}) {
			by[i] += GAP - low;
			bz[i] += GAP - back;
		}
		// legs straight out along the floor, each turned out its own way
		limbOnFloor(RagdollRig.R_HIP, RagdollRig.R_FOOT, -1.0F, (float) Math.PI * 0.5F
			- (SPLAY_MIN + random.nextFloat() * (SPLAY_MAX - SPLAY_MIN)), bx, by, bz);
		limbOnFloor(RagdollRig.L_HIP, RagdollRig.L_FOOT, 1.0F, (float) Math.PI * 0.5F
			- (SPLAY_MIN + random.nextFloat() * (SPLAY_MAX - SPLAY_MIN)), bx, by, bz);
		// arms fallen to its sides, hands on the floor a little in front
		limbOnFloor(RagdollRig.R_SHOULDER, RagdollRig.R_HAND, -1.0F,
			ARM_FORWARD_MIN + random.nextFloat() * (ARM_FORWARD_MAX - ARM_FORWARD_MIN), bx, by, bz);
		limbOnFloor(RagdollRig.L_SHOULDER, RagdollRig.L_HAND, 1.0F,
			ARM_FORWARD_MIN + random.nextFloat() * (ARM_FORWARD_MAX - ARM_FORWARD_MIN), bx, by, bz);
		// into the world: the wall's foot under where the body's middle is, x along the wall, z out of it
		double[] m = body.middle();
		double along = m[0] * nx + m[2] * nz - wall;
		double ox = m[0] - nx * along, oz = m[2] - nz * along;
		// its left, facing out of the wall (as RagdollBody#standing turns a body facing +z)
		double lx = nz, lz = -nx;
		tx = new double[n];
		ty = new double[n];
		tz = new double[n];
		for (int i = 0; i < n; i++) {
			tx[i] = ox + bx[i] * lx + bz[i] * nx;
			ty[i] = floorY + by[i];
			tz[i] = oz + bx[i] * lz + bz[i] * nz;
		}
	}

	/**
	 * Whether {@code body}, which lost {@code (lx, lz)} of its sideways speed this step, struck a wall hard enough to
	 * slump against it: a humanoid (only they sit), thrown or still on its feet.
	 */
	public static boolean struck(RagdollBody body, double lx, double lz) {
		return body.rig.shape == RagdollRig.Shape.HUMANOID && lx * lx + lz * lz >= HIT * HIT
			&& (!body.grounded() || body.frame()[4] > UPRIGHT);
	}

	/** Puts a torso point (relative to the hips' middle, standing) leaned back by {@code lean} and slumped by {@code roll}. */
	private static void place(float x, float y, float z, float lean, float roll, int i, float[] bx, float[] by, float[] bz) {
		// slumped over about its own front-back line, then leaned back (its top toward the wall, -z)
		float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
		float rx = x * cr - y * sr, ry = x * sr + y * cr;
		float cl = (float) Math.cos(lean), sl = (float) Math.sin(lean);
		bx[i] = rx;
		by[i] = ry * cl - z * sl;
		bz[i] = -ry * sl + z * cl;
	}

	/**
	 * A limb from {@code root} lying with its tip on the floor, its length kept: reaching out level toward {@code angle}
	 * (radians from straight out to its {@code side}, round toward the front). Too short to reach the floor, it hangs
	 * straight down.
	 */
	private void limbOnFloor(int root, int tip, float side, float angle, float[] bx, float[] by, float[] bz) {
		float length = (float) Math.sqrt(sq(rig.restX[tip] - rig.restX[root]) + sq(rig.restY[tip] - rig.restY[root])
			+ sq(rig.restZ[tip] - rig.restZ[root]));
		float drop = by[root] - (rig.radius[tip] + GAP);
		if (drop >= length) {
			bx[tip] = bx[root];
			by[tip] = by[root] - length;
			bz[tip] = bz[root];
			return;
		}
		float reach = (float) Math.sqrt(length * length - Math.max(drop, 0.0F) * Math.max(drop, 0.0F));
		bx[tip] = bx[root] + side * reach * (float) Math.cos(angle);
		by[tip] = rig.radius[tip] + GAP;
		bz[tip] = Math.max(bz[root] + reach * (float) Math.sin(angle), rig.radius[tip] + GAP);
	}

	private static float sq(float v) {
		return v * v;
	}

	/**
	 * The torso's turn in the points given (a quaternion, x y z w, of its frame: right, up toward the head, and right x
	 * up), each point's place about the torso's middle in that frame into {@code local}, and the middle into {@code middle}.
	 */
	private double[] turn(double[] x, double[] y, double[] z, double[] local, double[] middle) {
		float[] f = new float[9];
		RagdollRig.frame(x, y, z, f);
		// (RagdollRig's forward is up x right; this frame turns properly, so the third row is right x up)
		double[] m = {f[0], f[1], f[2], f[3], f[4], f[5], -f[6], -f[7], -f[8]};
		middle[0] = (x[0] + x[1] + x[2] + x[3]) * 0.25;
		middle[1] = (y[0] + y[1] + y[2] + y[3]) * 0.25;
		middle[2] = (z[0] + z[1] + z[2] + z[3]) * 0.25;
		for (int i = 0; i < rig.count; i++) {
			double dx = x[i] - middle[0], dy = y[i] - middle[1], dz = z[i] - middle[2];
			local[i * 3] = dx * m[0] + dy * m[1] + dz * m[2];
			local[i * 3 + 1] = dx * m[3] + dy * m[4] + dz * m[5];
			local[i * 3 + 2] = dx * m[6] + dy * m[7] + dz * m[8];
		}
		return quaternion(m);
	}

	/** A turn's quaternion (x y z w) from its rows. */
	private static double[] quaternion(double[] m) {
		double trace = m[0] + m[4] + m[8];
		double x, y, z, w;
		if (trace > 0.0) {
			double s = Math.sqrt(trace + 1.0) * 2.0;
			w = 0.25 * s;
			x = (m[5] - m[7]) / s;
			y = (m[6] - m[2]) / s;
			z = (m[1] - m[3]) / s;
		} else if (m[0] > m[4] && m[0] > m[8]) {
			double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2.0;
			w = (m[5] - m[7]) / s;
			x = 0.25 * s;
			y = (m[3] + m[1]) / s;
			z = (m[6] + m[2]) / s;
		} else if (m[4] > m[8]) {
			double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2.0;
			w = (m[6] - m[2]) / s;
			x = (m[3] + m[1]) / s;
			y = 0.25 * s;
			z = (m[7] + m[5]) / s;
		} else {
			double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2.0;
			w = (m[1] - m[3]) / s;
			x = (m[6] + m[2]) / s;
			y = (m[7] + m[5]) / s;
			z = 0.25 * s;
		}
		return new double[] {x, y, z, w};
	}

	/** A quaternion's (x y z w) turn as its rows, the inverse of {@link #quaternion}. */
	private static double[] rows(double[] q) {
		double x = q[0], y = q[1], z = q[2], w = q[3];
		return new double[] {
			1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w),
			2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w),
			2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y)};
	}

	/** Part way ({@code k}) from turn {@code a} to {@code b}, at an even pace. */
	private static double[] slerp(double[] a, double[] b, double k) {
		double dot = Math.min(1.0, a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]);
		double wa, wb;
		if (dot > 0.9995) {
			wa = 1.0 - k;
			wb = k;
		} else {
			double angle = Math.acos(dot), sin = Math.sin(angle);
			wa = Math.sin((1.0 - k) * angle) / sin;
			wb = Math.sin(k * angle) / sin;
		}
		double[] q = new double[4];
		double length = 0.0;
		for (int i = 0; i < 4; i++) {
			q[i] = a[i] * wa + b[i] * wb;
			length += q[i] * q[i];
		}
		length = Math.sqrt(length);
		for (int i = 0; i < 4; i++) {
			q[i] /= length;
		}
		return q;
	}

	/** Still sliding down the wall. */
	public boolean sliding() {
		return tick < slide;
	}

	/**
	 * One tick of the slide: sets {@code body} moving to where it should be at the end of this tick (its physics step
	 * then takes it there). On the last, it comes to rest there. False once it's sitting.
	 */
	public boolean steer(RagdollBody body) {
		if (!sliding()) {
			return false;
		}
		int n = rig.count;
		if (localS == null) {
			// each pose as its torso's turn and its shape about its middle, so the slide turns it round as one body
			// (thrown at the wall face first, it comes round to sit with its back to it), not drawing every point straight
			// across, which crushes it flat halfway
			localS = new double[n * 3];
			localT = new double[n * 3];
			startMiddle = new double[3];
			endMiddle = new double[3];
			turnS = turn(body.x, body.y, body.z, localS, startMiddle);
			turnT = turn(tx, ty, tz, localT, endMiddle);
			if (turnS[0] * turnT[0] + turnS[1] * turnT[1] + turnS[2] * turnT[2] + turnS[3] * turnT[3] < 0.0) {
				// the short way round
				for (int i = 0; i < 4; i++) {
					turnT[i] = -turnT[i];
				}
			}
		}
		tick++;
		float u = (float) tick / slide;
		// slow off the wall, then down, easing in to sit
		float k = u * u * (3.0F - 2.0F * u);
		double[] m = rows(slerp(turnS, turnT, k));
		// it bounces a little off the wall as it turns, so its shoulders swing clear of it
		double off = bounce * Math.sin(Math.PI * u);
		double cx = startMiddle[0] + (endMiddle[0] - startMiddle[0]) * k + nx * off;
		double cy = startMiddle[1] + (endMiddle[1] - startMiddle[1]) * k;
		double cz = startMiddle[2] + (endMiddle[2] - startMiddle[2]) * k + nz * off;
		double[] gx = new double[n], gy = new double[n], gz = new double[n];
		for (int i = 0; i < n; i++) {
			double a = localS[i * 3] + (localT[i * 3] - localS[i * 3]) * k;
			double b = localS[i * 3 + 1] + (localT[i * 3 + 1] - localS[i * 3 + 1]) * k;
			double c = localS[i * 3 + 2] + (localT[i * 3 + 2] - localS[i * 3 + 2]) * k;
			gx[i] = cx + a * m[0] + b * m[3] + c * m[6];
			gy[i] = cy + a * m[1] + b * m[4] + c * m[7];
			gz[i] = cz + a * m[2] + b * m[5] + c * m[8];
		}
		// part way between two shapes, a limb drawn straight across is a little short: put the bones' lengths back
		for (int pass = 0; pass < LENGTH_PASSES; pass++) {
			for (int s = 0; s < rig.stickA.length; s++) {
				int a = rig.stickA[s], b = rig.stickB[s];
				double dx = gx[b] - gx[a], dy = gy[b] - gy[a], dz = gz[b] - gz[a];
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d > 1.0E-6) {
					double f = (d - rig.stickLength[s]) / d * 0.5;
					gx[a] += dx * f;
					gy[a] += dy * f;
					gz[a] += dz * f;
					gx[b] -= dx * f;
					gy[b] -= dy * f;
					gz[b] -= dz * f;
				}
			}
		}
		for (int i = 0; i < n; i++) {
			body.px[i] = body.x[i] - (gx[i] - body.x[i]);
			body.py[i] = body.y[i] - (gy[i] - body.y[i]);
			body.pz[i] = body.z[i] - (gz[i] - body.z[i]);
		}
		body.wake();
		return true;
	}
}
