package com.jaymoskitchen.ragdoll;

import java.util.Random;

/**
 * (0.5.0) A ragdoll: the points of a {@link RagdollRig} moved by Verlet integration (each point remembers where it
 * was, and keeps going the way it was going), the sticks between them held at length, the limbs held inside their
 * cones, and every point pushed out of the blocks around it, with friction and a little bounce. Gravity pulls, the
 * body topples and folds over whatever it lands on, and lies still once it settles (it then sleeps until something
 * moves it). Positions are in world blocks.
 * <p>
 * One body is simulated in one place: a corpse's on the server (see {@code CorpseRagdolls}), a living player's on
 * their own client (see {@code LivingRagdolls}); everyone else is sent its points.
 */
public final class RagdollBody {
	/** Blocks a tick, a tick: about vanilla's falling bodies. */
	public static final float GRAVITY = 0.08F;
	/** Of a point's speed, how much survives a tick in the air, in water, in lava. */
	private static final float AIR_DRAG = 0.985F, WATER_DRAG = 0.8F, LAVA_DRAG = 0.5F;
	/** In water, a body floats up (gravity times this, the opposite way); in lava it sinks slowly. */
	private static final float WATER_LIFT = 0.25F, LAVA_SINK = 0.15F;
	/** Sliding along what it lies on: how much of the speed along the surface is lost each time a point touches. */
	private static final float FRICTION = 0.35F;
	/** How much of the speed into a surface comes back out of it (a bounce). Slow hits don't bounce. */
	private static final float BOUNCE = 0.25F, BOUNCE_MIN = 0.12F;
	/** A body lying on something loses this much of its speed each tick besides friction (limp bodies don't roll). */
	private static final float GROUND_DAMP = 0.82F;
	/** Pulling a limb back into its cone moves the torso as much the other way (so the body never creeps along). */
	private static final float CONE_RECOIL = 0.5F;
	/** Passes over the sticks, cones and blocks each step: more is stiffer. */
	private static final int ITERATIONS = 6;
	/** The furthest a point may move in one sub-step, as a share of its size, and the most sub-steps in a tick. */
	private static final float SUBSTEP_SHARE = 0.9F;
	private static final int MAX_SUBSTEPS = 6;
	/** No point moves faster than this (blocks a tick). */
	private static final float MAX_SPEED = 2.5F;
	/** Still below this speed (blocks a tick) for this many ticks, it falls asleep; asleep, it checks every so often. */
	private static final float SLEEP_SPEED = 0.004F;
	private static final int SLEEP_TICKS = 20, PROBE_EVERY = 40, FEEL_EVERY = 4;
	/** A mover (someone walking, a cart) at least this fast kicks the points it passes through, this hard. */
	private static final float KICK_SPEED = 0.03F, KICK_SHARE = 0.45F, KICK_LIFT = 0.04F;
	/** A point pressed into a block from inside it goes out of the top if that's within this much of the nearest way out. */
	private static final float PREFER_UP = 0.35F;

	public final RagdollRig rig;
	public final double[] x, y, z, px, py, pz;
	private final float[] normalY;
	private final boolean[] touching;
	private final float[] frame = new float[9];
	private final RagdollWorld.Boxes solids = new RagdollWorld.Boxes(), movers = new RagdollWorld.Boxes();
	private int quiet, sleepingFor;
	private boolean asleep, grounded;
	private int ticks;

	public RagdollBody(RagdollRig rig) {
		this.rig = rig;
		int n = rig.count;
		x = new double[n];
		y = new double[n];
		z = new double[n];
		px = new double[n];
		py = new double[n];
		pz = new double[n];
		normalY = new float[n];
		touching = new boolean[n];
	}

	// ---------------------------------------------------------------- building

	/** Standing as the living body stood: feet at the given spot, facing {@code yaw} (degrees, as Minecraft's). */
	public static RagdollBody standing(RagdollRig rig, double fx, double fy, double fz, float yaw) {
		RagdollBody body = new RagdollBody(rig);
		float s = (float) Math.sin(Math.toRadians(yaw)), c = (float) Math.cos(Math.toRadians(yaw));
		for (int i = 0; i < rig.count; i++) {
			float bx = rig.restX[i], bz = rig.restZ[i];
			body.x[i] = body.px[i] = fx + bx * c - bz * s;
			body.y[i] = body.py[i] = fy + rig.restY[i];
			body.z[i] = body.pz[i] = fz + bx * s + bz * c;
		}
		return body;
	}

	/**
	 * Lying on the ground at the given spot (its middle there, the ground at {@code fy}), along {@code yaw} (the way its
	 * head points), face up or down, limbs splayed a little: how a body lies after it's been set down or carried.
	 */
	public static RagdollBody lying(RagdollRig rig, double fx, double fy, double fz, float yaw, boolean faceUp, long seed) {
		RagdollBody body = new RagdollBody(rig);
		Random random = new Random(seed);
		int n = rig.count;
		float[] bx = new float[n], by = new float[n], bz = new float[n];
		for (int i = 0; i < n; i++) {
			bx[i] = rig.restX[i];
			by[i] = rig.restY[i];
			bz[i] = rig.restZ[i];
		}
		if (rig.shape == RagdollRig.Shape.HUMANOID) {
			// arms and legs out to the sides, each its own way (in the body's own plane, before it's laid down)
			splay(bx, by, RagdollRig.R_SHOULDER, RagdollRig.R_HAND, -(0.2F + random.nextFloat() * 1.3F));
			splay(bx, by, RagdollRig.L_SHOULDER, RagdollRig.L_HAND, 0.2F + random.nextFloat() * 1.3F);
			splay(bx, by, RagdollRig.R_HIP, RagdollRig.R_FOOT, -(0.05F + random.nextFloat() * 0.4F));
			splay(bx, by, RagdollRig.L_HIP, RagdollRig.L_FOOT, 0.05F + random.nextFloat() * 0.4F);
			// head lolled to one side
			splay(bx, by, RagdollRig.NECK, RagdollRig.HEAD, (random.nextFloat() - 0.5F) * 1.2F);
		}
		// a humanoid lies down (its up turns to the yaw, its front to the sky or the ground); a four-legged body rolls
		// onto its side; either way it's then turned to face along the yaw
		float minY = Float.MAX_VALUE;
		float[] wx = new float[n], wy = new float[n], wz = new float[n];
		boolean humanoid = rig.shape == RagdollRig.Shape.HUMANOID;
		boolean side = random.nextBoolean();
		for (int i = 0; i < n; i++) {
			float lx, ly, lz;
			if (humanoid) {
				// tipped onto its back or front: up (y) becomes the yaw direction (z), forward (z) up or down (a
				// quarter turn about x, and face up also a half turn about the new length)
				lx = faceUp ? -bx[i] : bx[i];
				ly = faceUp ? bz[i] : -bz[i];
				lz = by[i] - rig.height * 0.5F;
			} else if (rig.shape == RagdollRig.Shape.QUADRUPED) {
				// on its side: up (y) becomes sideways (x), right (-x) becomes up or down
				lx = side ? by[i] - rig.height * 0.3F : -(by[i] - rig.height * 0.3F);
				ly = side ? -bx[i] : bx[i];
				lz = bz[i];
			} else {
				lx = bx[i];
				ly = by[i];
				lz = bz[i];
			}
			wx[i] = lx;
			wy[i] = ly;
			wz[i] = lz;
			minY = Math.min(minY, ly - rig.radius[i]);
		}
		float s = (float) Math.sin(Math.toRadians(yaw)), c = (float) Math.cos(Math.toRadians(yaw));
		for (int i = 0; i < n; i++) {
			body.x[i] = body.px[i] = fx + wx[i] * c - wz[i] * s;
			body.y[i] = body.py[i] = fy + wy[i] - minY;
			body.z[i] = body.pz[i] = fz + wx[i] * s + wz[i] * c;
		}
		return body;
	}

	/** Swings a limb's tip round its root in the body's own plane (about its forward axis) by {@code angle}. */
	private static void splay(float[] bx, float[] by, int root, int tip, float angle) {
		float dx = bx[tip] - bx[root], dy = by[tip] - by[root];
		float s = (float) Math.sin(angle), c = (float) Math.cos(angle);
		bx[tip] = bx[root] + dx * c - dy * s;
		by[tip] = by[root] + dx * s + dy * c;
	}

	/** A copy of these points (same rig), as they are now, moving as they were. */
	public RagdollBody copy() {
		RagdollBody body = new RagdollBody(rig);
		System.arraycopy(x, 0, body.x, 0, x.length);
		System.arraycopy(y, 0, body.y, 0, y.length);
		System.arraycopy(z, 0, body.z, 0, z.length);
		System.arraycopy(px, 0, body.px, 0, px.length);
		System.arraycopy(py, 0, body.py, 0, py.length);
		System.arraycopy(pz, 0, body.pz, 0, pz.length);
		return body;
	}

	// ---------------------------------------------------------------- pushing it about

	/** Adds a speed (blocks a tick) to the whole body. */
	public void shove(double vx, double vy, double vz) {
		for (int i = 0; i < rig.count; i++) {
			px[i] -= vx;
			py[i] -= vy;
			pz[i] -= vz;
		}
		wake();
	}

	/** Sets the whole body's speed (blocks a tick), keeping how its parts move against each other. */
	public void setSpeed(double vx, double vy, double vz) {
		double[] v = speed();
		shove(vx - v[0], vy - v[1], vz - v[2]);
	}

	/**
	 * A blow at a point: the nearest part takes it all, the rest of the body some of it, so the body turns as it goes
	 * (a punch to the head snaps it back, a hit low sweeps the legs out).
	 */
	public void hit(double hx, double hy, double hz, double vx, double vy, double vz, float share) {
		int nearest = nearest(hx, hy, hz);
		for (int i = 0; i < rig.count; i++) {
			double k = i == nearest ? 1.0 : share;
			px[i] -= vx * k;
			py[i] -= vy * k;
			pz[i] -= vz * k;
		}
		wake();
	}

	/** Moves the whole body, as it lies, without changing its speed. */
	public void translate(double dx, double dy, double dz) {
		for (int i = 0; i < rig.count; i++) {
			x[i] += dx;
			y[i] += dy;
			z[i] += dz;
			px[i] += dx;
			py[i] += dy;
			pz[i] += dz;
		}
	}

	/** Every point at rest where it is now (no speed). */
	public void stop() {
		System.arraycopy(x, 0, px, 0, x.length);
		System.arraycopy(y, 0, py, 0, y.length);
		System.arraycopy(z, 0, pz, 0, z.length);
	}

	public void wake() {
		asleep = false;
		quiet = 0;
	}

	public boolean asleep() {
		return asleep;
	}

	/** Something of it touched the ground (anything it could lie on) last tick. */
	public boolean grounded() {
		return grounded;
	}

	public int nearest(double hx, double hy, double hz) {
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < rig.count; i++) {
			double dx = x[i] - hx, dy = y[i] - hy, dz = z[i] - hz;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- reading it

	/** The middle of the torso (the average of its four corners). */
	public double[] middle() {
		double mx = 0, my = 0, mz = 0;
		for (int i = 0; i < 4; i++) {
			mx += x[i];
			my += y[i];
			mz += z[i];
		}
		return new double[] {mx / 4, my / 4, mz / 4};
	}

	/** The whole body's speed (blocks a tick): the average of its points'. */
	public double[] speed() {
		double vx = 0, vy = 0, vz = 0;
		for (int i = 0; i < rig.count; i++) {
			vx += x[i] - px[i];
			vy += y[i] - py[i];
			vz += z[i] - pz[i];
		}
		int n = rig.count;
		return new double[] {vx / n, vy / n, vz / n};
	}

	/** The box the whole body fills: min x y z, max x y z. */
	public double[] bounds() {
		double[] out = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
			-Double.MAX_VALUE};
		for (int i = 0; i < rig.count; i++) {
			float r = rig.radius[i];
			out[0] = Math.min(out[0], x[i] - r);
			out[1] = Math.min(out[1], y[i] - r);
			out[2] = Math.min(out[2], z[i] - r);
			out[3] = Math.max(out[3], x[i] + r);
			out[4] = Math.max(out[4], y[i] + r);
			out[5] = Math.max(out[5], z[i] + r);
		}
		return out;
	}

	/** The lowest any part reaches (its underside). */
	public double bottom() {
		double low = Double.MAX_VALUE;
		for (int i = 0; i < rig.count; i++) {
			low = Math.min(low, y[i] - rig.radius[i]);
		}
		return low;
	}

	/** The torso's frame now (rows: right, up toward the head, forward), see {@link RagdollRig#frame}. */
	public float[] frame() {
		RagdollRig.frame(x, y, z, frame);
		return frame;
	}

	/** The way the body faces, as a Minecraft yaw (degrees): where its head points, lying, or its front, standing. */
	public float yaw() {
		float[] f = frame();
		// lying, the head shows the way; upright, the chest does
		double hx = f[3], hz = f[5];
		if (hx * hx + hz * hz < 0.25) {
			hx = f[6];
			hz = f[8];
		}
		return (float) Math.toDegrees(Math.atan2(-hx, hz));
	}

	/** True if every number is a number (a body that blew up is rebuilt by its owner). */
	public boolean sane() {
		for (int i = 0; i < rig.count; i++) {
			if (!Double.isFinite(x[i]) || !Double.isFinite(y[i]) || !Double.isFinite(z[i])
				|| !Double.isFinite(px[i]) || !Double.isFinite(py[i]) || !Double.isFinite(pz[i])) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- simulating

	/** One game tick. */
	public void step(RagdollWorld world) {
		step(world, GRAVITY);
	}

	public void step(RagdollWorld world, float gravity) {
		ticks++;
		int n = rig.count;
		if (asleep) {
			// asleep: wake if someone walks into it, and look again now and then in case the ground went (a block
			// broken under it)
			sleepingFor++;
			boolean probe = sleepingFor % PROBE_EVERY == 0;
			if (!probe && sleepingFor % FEEL_EVERY != 0) {
				return;
			}
			if (!probe) {
				double[] box = bounds();
				movers.clear();
				world.movers(box[0], box[1], box[2], box[3], box[4], box[5], movers);
				kick();
				if (asleep) {
					return;
				}
			}
		}
		// how far anything goes this tick, to size the sub-steps and the search for blocks
		double fastest = 0;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		float smallest = Float.MAX_VALUE, biggest = 0;
		for (int i = 0; i < n; i++) {
			double vx = x[i] - px[i], vy = y[i] - py[i], vz = z[i] - pz[i];
			double v2 = vx * vx + vy * vy + vz * vz;
			if (v2 > MAX_SPEED * MAX_SPEED) {
				double k = MAX_SPEED / Math.sqrt(v2);
				px[i] = x[i] - vx * k;
				py[i] = y[i] - vy * k;
				pz[i] = z[i] - vz * k;
				v2 = MAX_SPEED * MAX_SPEED;
			}
			fastest = Math.max(fastest, v2);
			minX = Math.min(minX, x[i]);
			minY = Math.min(minY, y[i]);
			minZ = Math.min(minZ, z[i]);
			maxX = Math.max(maxX, x[i]);
			maxY = Math.max(maxY, y[i]);
			maxZ = Math.max(maxZ, z[i]);
			smallest = Math.min(smallest, rig.radius[i]);
			biggest = Math.max(biggest, rig.radius[i]);
		}
		fastest = Math.sqrt(fastest) + gravity;
		double reach = fastest + biggest + 0.1;
		solids.clear();
		world.solids(minX - reach, minY - reach, minZ - reach, maxX + reach, maxY + reach, maxZ + reach, solids);
		movers.clear();
		world.movers(minX - biggest, minY - biggest, minZ - biggest, maxX + biggest, maxY + biggest, maxZ + biggest,
			movers);
		kick();

		int substeps = (int) Math.min(MAX_SUBSTEPS, Math.max(1, Math.ceil(fastest / (smallest * 2 * SUBSTEP_SHARE))));
		double g = gravity / ((double) substeps * substeps);
		// between ticks, where a point was is a tick behind; inside the tick, a sub-step behind
		rescale(1.0 / substeps);
		float drag = (float) Math.pow(AIR_DRAG, 1.0 / substeps);
		// fluids are checked once a tick per point
		int[] fluid = new int[n];
		for (int i = 0; i < n; i++) {
			fluid[i] = world.fluid(x[i], y[i], z[i]);
		}
		java.util.Arrays.fill(touching, false);
		for (int s = 0; s < substeps; s++) {
			for (int i = 0; i < n; i++) {
				double keep = drag;
				double pull = -g;
				if (fluid[i] == RagdollWorld.WATER) {
					keep = Math.pow(WATER_DRAG, 1.0 / substeps);
					pull = g * WATER_LIFT;
				} else if (fluid[i] == RagdollWorld.LAVA) {
					keep = Math.pow(LAVA_DRAG, 1.0 / substeps);
					pull = -g * LAVA_SINK;
				}
				double vx = (x[i] - px[i]) * keep, vy = (y[i] - py[i]) * keep, vz = (z[i] - pz[i]) * keep;
				px[i] = x[i];
				py[i] = y[i];
				pz[i] = z[i];
				x[i] += vx;
				y[i] += vy + pull;
				z[i] += vz;
			}
			for (int k = 0; k < ITERATIONS; k++) {
				sticks();
				cones();
				collide(k == ITERATIONS - 1);
			}
		}
		rescale(substeps);
		grounded = false;
		for (int i = 0; i < n; i++) {
			grounded |= touching[i] && normalY[i] > 0.5F;
		}
		if (grounded) {
			rescale(GROUND_DAMP);
		}
		// asleep or not?
		double moved = 0;
		for (int i = 0; i < n; i++) {
			double vx = x[i] - px[i], vy = y[i] - py[i], vz = z[i] - pz[i];
			moved = Math.max(moved, vx * vx + vy * vy + vz * vz);
		}
		moved = Math.sqrt(moved);
		if (asleep) {
			if (moved > SLEEP_SPEED * 3) {
				wake();
			} else {
				stop();
			}
		} else if (moved < SLEEP_SPEED && grounded) {
			if (++quiet >= SLEEP_TICKS) {
				asleep = true;
				sleepingFor = 0;
				stop();
			}
		} else {
			quiet = 0;
		}
	}

	/** Scales every point's speed (how far behind it its last place is). */
	private void rescale(double k) {
		for (int i = 0; i < rig.count; i++) {
			px[i] = x[i] - (x[i] - px[i]) * k;
			py[i] = y[i] - (y[i] - py[i]) * k;
			pz[i] = z[i] - (z[i] - pz[i]) * k;
		}
	}

	/** Things moving through the body kick the parts they pass through (once a tick). */
	private void kick() {
		if (movers.size == 0) {
			return;
		}
		double[] d = movers.data;
		for (int m = 0; m < movers.size; m++) {
			int o = m * 9;
			double vx = d[o + 6], vy = d[o + 7], vz = d[o + 8];
			if (vx * vx + vy * vy + vz * vz < KICK_SPEED * KICK_SPEED) {
				continue;
			}
			for (int i = 0; i < rig.count; i++) {
				float r = rig.radius[i];
				if (x[i] + r < d[o] || x[i] - r > d[o + 3] || y[i] + r < d[o + 1] || y[i] - r > d[o + 4]
					|| z[i] + r < d[o + 2] || z[i] - r > d[o + 5]) {
					continue;
				}
				double ox = x[i] - px[i], oz = z[i] - pz[i];
				px[i] -= (vx - ox) * KICK_SHARE;
				pz[i] -= (vz - oz) * KICK_SHARE;
				py[i] -= KICK_LIFT;
				wake();
			}
		}
	}

	private void sticks() {
		int[] a = rig.stickA, b = rig.stickB;
		float[] len = rig.stickLength;
		for (int s = 0; s < a.length; s++) {
			int i = a[s], j = b[s];
			double dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1.0E-9) {
				continue;
			}
			double k = (d - len[s]) / d * 0.5;
			x[i] += dx * k;
			y[i] += dy * k;
			z[i] += dz * k;
			x[j] -= dx * k;
			y[j] -= dy * k;
			z[j] -= dz * k;
		}
	}

	private void cones() {
		if (rig.coneRoot.length == 0) {
			return;
		}
		float[] f = frame();
		for (int c = 0; c < rig.coneRoot.length; c++) {
			int root = rig.coneRoot[c], tip = rig.coneTip[c];
			float axX = rig.coneAxisX[c], axY = rig.coneAxisY[c], axZ = rig.coneAxisZ[c];
			// the cone's axis in the world: the torso's right, up and forward, mixed
			double ax = axX * f[0] + axY * f[3] + axZ * f[6];
			double ay = axX * f[1] + axY * f[4] + axZ * f[7];
			double az = axX * f[2] + axY * f[5] + axZ * f[8];
			double dx = x[tip] - x[root], dy = y[tip] - y[root], dz = z[tip] - z[root];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0E-9) {
				continue;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			double cos = dx * ax + dy * ay + dz * az;
			double limit = Math.cos(rig.coneAngle[c]);
			if (cos >= limit) {
				continue;
			}
			// swing it back to the cone's edge, the short way round
			double ox = dx - ax * cos, oy = dy - ay * cos, oz = dz - az * cos;
			double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
			if (ol < 1.0E-6) {
				// straight against the axis: any side will do (the torso's right)
				ox = f[0];
				oy = f[1];
				oz = f[2];
				ol = 1;
			}
			ox /= ol;
			oy /= ol;
			oz /= ol;
			double sin = Math.sqrt(1 - limit * limit);
			double nx = ax * limit + ox * sin, ny = ay * limit + oy * sin, nz = az * limit + oz * sin;
			double cx = x[root] + nx * len - x[tip], cy = y[root] + ny * len - y[tip], cz = z[root] + nz * len - z[tip];
			x[tip] += cx * (1 - CONE_RECOIL);
			y[tip] += cy * (1 - CONE_RECOIL);
			z[tip] += cz * (1 - CONE_RECOIL);
			x[root] -= cx * CONE_RECOIL;
			y[root] -= cy * CONE_RECOIL;
			z[root] -= cz * CONE_RECOIL;
		}
	}

	/** Pushes every point out of the blocks it's in; on the last pass, also friction and bounce. */
	private void collide(boolean last) {
		double[] d = solids.data;
		int boxes = solids.size;
		for (int i = 0; i < rig.count; i++) {
			float r = rig.radius[i];
			double nxSum = 0, nySum = 0, nzSum = 0;
			boolean hit = false;
			for (int b = 0; b < boxes; b++) {
				int o = b * 9;
				double minX = d[o], minY = d[o + 1], minZ = d[o + 2], maxX = d[o + 3], maxY = d[o + 4], maxZ = d[o + 5];
				if (x[i] + r <= minX || x[i] - r >= maxX || y[i] + r <= minY || y[i] - r >= maxY || z[i] + r <= minZ
					|| z[i] - r >= maxZ) {
					continue;
				}
				double cx = clamp(x[i], minX, maxX), cy = clamp(y[i], minY, maxY), cz = clamp(z[i], minZ, maxZ);
				double ex = x[i] - cx, ey = y[i] - cy, ez = z[i] - cz;
				double dist2 = ex * ex + ey * ey + ez * ez;
				double nx, ny, nz, push;
				if (dist2 > 1.0E-12) {
					double dist = Math.sqrt(dist2);
					if (dist >= r) {
						continue;
					}
					nx = ex / dist;
					ny = ey / dist;
					nz = ez / dist;
					push = r - dist;
				} else {
					// the middle is inside the box: out by the nearest face, but rather the top if it's nearly as near
					double up = maxY - y[i], down = y[i] - minY;
					double east = maxX - x[i], west = x[i] - minX, south = maxZ - z[i], north = z[i] - minZ;
					double side = Math.min(Math.min(east, west), Math.min(south, north));
					double best = Math.min(side, Math.min(up, down));
					nx = ny = nz = 0;
					if (up <= best + PREFER_UP) {
						ny = 1;
						push = up + r;
					} else if (best == down) {
						ny = -1;
						push = down + r;
					} else if (best == east) {
						nx = 1;
						push = east + r;
					} else if (best == west) {
						nx = -1;
						push = west + r;
					} else if (best == south) {
						nz = 1;
						push = south + r;
					} else {
						nz = -1;
						push = north + r;
					}
				}
				x[i] += nx * push;
				y[i] += ny * push;
				z[i] += nz * push;
				nxSum += nx;
				nySum += ny;
				nzSum += nz;
				hit = true;
			}
			if (!hit) {
				continue;
			}
			double nl = Math.sqrt(nxSum * nxSum + nySum * nySum + nzSum * nzSum);
			if (nl < 1.0E-9) {
				continue;
			}
			double nx = nxSum / nl, ny = nySum / nl, nz = nzSum / nl;
			touching[i] = true;
			normalY[i] = (float) ny;
			if (!last) {
				continue;
			}
			// friction and bounce: split the speed into along the surface and into it
			double vx = x[i] - px[i], vy = y[i] - py[i], vz = z[i] - pz[i];
			double vn = vx * nx + vy * ny + vz * nz;
			double tx = vx - vn * nx, ty = vy - vn * ny, tz = vz - vn * nz;
			double keep = 1 - FRICTION;
			double out = vn < -BOUNCE_MIN ? -vn * BOUNCE : Math.max(vn, 0);
			vx = tx * keep + nx * out;
			vy = ty * keep + ny * out;
			vz = tz * keep + nz * out;
			px[i] = x[i] - vx;
			py[i] = y[i] - vy;
			pz[i] = z[i] - vz;
		}
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	// ---------------------------------------------------------------- saving and sending

	/** The points, relative to {@code (ox, oy, oz)}, three numbers each. */
	public float[] relative(double ox, double oy, double oz) {
		float[] out = new float[rig.count * 3];
		for (int i = 0; i < rig.count; i++) {
			out[i * 3] = (float) (x[i] - ox);
			out[i * 3 + 1] = (float) (y[i] - oy);
			out[i * 3 + 2] = (float) (z[i] - oz);
		}
		return out;
	}

	/** Puts the points back from {@link #relative}, at rest; false if they don't fit this rig. */
	public boolean restore(float[] points, double ox, double oy, double oz) {
		if (points == null || points.length != rig.count * 3) {
			return false;
		}
		for (int i = 0; i < rig.count; i++) {
			x[i] = px[i] = ox + points[i * 3];
			y[i] = py[i] = oy + points[i * 3 + 1];
			z[i] = pz[i] = oz + points[i * 3 + 2];
		}
		if (!sane()) {
			return false;
		}
		asleep = true;
		sleepingFor = 0;
		return true;
	}

	public int ticks() {
		return ticks;
	}
}
