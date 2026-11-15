package com.jaymoskitchen.ragdoll;

import java.util.Random;

/**
 * A dying mob's last moments before it goes limp: it sways, its legs give and splay out under it, its head drops, then
 * it tips over onto its side (a humanoid: the way the killing blow pushed it) and falls the rest of the way as a
 * ragdoll. Andy wanted every mob to die the way an animal drifts off to sleep in Promenade (the capybara's slow sway,
 * splay and tip onto its side); this is our own animation of that look, worked on the ragdoll's points.
 * <p>
 * Each tick it works out where every point of the body should be and steers the body there through its physics (so
 * the ground and walls still hold it). A humanoid is let go of partway over, still falling, to crumple as a ragdoll;
 * anything else lands on its side, bounces a little and is let go of lying still there. Nothing here
 * touches Minecraft, so it can be checked on its own (tools/ragdoll_sim).
 */
public final class KeelOver {
	/** The sway (ticks): leaning away from the fall, back through upright, to leaning toward it. */
	private static final int SWAY_TICKS = 16;
	/** How far it sways (radians), at most. */
	private static final float SWAY = 0.17F;
	/** The tip onto its side (ticks) for a body a block tall; taller bodies fall slower (as the root of their height). */
	private static final float TIP_TICKS = 13.0F;
	private static final int TIP_MIN = 9, TIP_MAX = 24;
	/** Each body's sway and tip run this much longer or shorter (a share), so a herd shot down doesn't fall as one. */
	private static final float JITTER = 0.2F;
	/**
	 * How far over the tip takes it (radians): an animal right onto its side, a humanoid partway (the physics lets it
	 * crumple the rest of the way, limbs flopping).
	 */
	private static final float END_SIDE = (float) (Math.PI / 2.0), END_HUMANOID = 1.05F;
	/**
	 * An animal (or any body that isn't a humanoid) landing on its side: it bounces back this far (radians) and settles
	 * over this many ticks, still, before the physics has it. Let go of still rolling, it rolled right on over onto its back.
	 */
	private static final float LAND_BOUNCE = 0.16F;
	private static final int LAND_TICKS = 7;
	/** How much its legs give (a share of their length the body sinks) as they splay out: four legs, two. */
	private static final float SQUASH_QUADRUPED = 0.2F, SQUASH_HUMANOID = 0.1F;
	/** A humanoid's feet slide this share forward (the rest out to the sides) as its knees give. */
	private static final float HUMANOID_FEET_FORWARD = 0.75F;
	/** How far the head drops (radians): a humanoid's chin to its chest, an animal's muzzle to the ground. */
	private static final float DROOP_HUMANOID = 0.55F, DROOP_QUADRUPED = 0.45F;

	private final RagdollRig rig;
	/** The standing feet, and the way it faced (sin, cos of its yaw). */
	private final double fx, fy, fz;
	private final float sin, cos;
	/** The way it falls, in its own body space (x its left, z its front), level and of length 1. */
	private final float dirX, dirZ;
	private final int sway, tip, land;
	private final float end;
	private final float[] restX, restY, restZ;
	/** Where it rocks about, falling its way and leaning the other (body space, on the ground). */
	private final float pivotX, pivotZ, backX, backZ;
	private int tick;

	/**
	 * A keel-over for a body standing on its feet at {@code (fx, fy, fz)} facing {@code yaw} (degrees, as Minecraft's).
	 * {@code fallX, fallZ}: the way it should fall in the world (a humanoid along the killing blow); zero for any way.
	 * Animals and other bodies always go over onto a side, the side nearer that way.
	 */
	public KeelOver(RagdollRig rig, double fx, double fy, double fz, float yaw, double fallX, double fallZ, long seed) {
		this.rig = rig;
		this.fx = fx;
		this.fy = fy;
		this.fz = fz;
		this.sin = (float) Math.sin(Math.toRadians(yaw));
		this.cos = (float) Math.cos(Math.toRadians(yaw));
		Random random = new Random(seed);
		// the fall's way, into the body's own space (the inverse of the turn in RagdollBody#standing)
		double bx = fallX * cos + fallZ * sin, bz = -fallX * sin + fallZ * cos;
		double length = Math.sqrt(bx * bx + bz * bz);
		if (length < 1.0E-3) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			bx = Math.cos(angle);
			bz = Math.sin(angle);
			length = 1.0;
		}
		boolean humanoid = rig.shape == RagdollRig.Shape.HUMANOID;
		if (humanoid) {
			dirX = (float) (bx / length);
			dirZ = (float) (bz / length);
		} else {
			// onto a side, never over its nose or tail
			dirX = bx >= 0.0 ? 1.0F : -1.0F;
			dirZ = 0.0F;
		}
		float jitter = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * JITTER;
		this.sway = Math.round(SWAY_TICKS * jitter);
		float tall = (float) Math.sqrt(Math.max(rig.height, 0.1F));
		this.tip = Math.max(TIP_MIN, Math.min(TIP_MAX, Math.round(TIP_TICKS * tall * jitter)));
		this.end = humanoid ? END_HUMANOID : END_SIDE;
		this.land = humanoid ? 0 : LAND_TICKS;
		this.restX = rig.restX.clone();
		this.restY = rig.restY.clone();
		this.restZ = rig.restZ.clone();
		// standing on its feet, not sunk into the ground to their middles
		float lift = 0.0F;
		for (int i : feet()) {
			lift = Math.max(lift, rig.radius[i] - restY[i]);
		}
		for (int i = 0; i < restY.length; i++) {
			restY[i] += lift;
		}
		// it rocks about the outer edge of its feet (as they stand once its legs have given), the way it leans
		float[] x = new float[rig.count], y = new float[rig.count], z = new float[rig.count];
		shape(1.0F, 0.0F, x, y, z);
		float reach = 0.0F, back = 0.0F;
		for (int i : feet()) {
			float along = x[i] * dirX + z[i] * dirZ;
			reach = Math.max(reach, along + rig.radius[i]);
			back = Math.max(back, -along + rig.radius[i]);
		}
		if (feet().length == 0) {
			// a box: its own edge
			for (int i = 0; i < rig.count; i++) {
				float along = x[i] * dirX + z[i] * dirZ;
				reach = Math.max(reach, along + rig.radius[i]);
				back = Math.max(back, -along + rig.radius[i]);
			}
		}
		this.pivotX = dirX * reach;
		this.pivotZ = dirZ * reach;
		this.backX = -dirX * back;
		this.backZ = -dirZ * back;
	}

	/** Ticks from the first sway to the physics taking over. */
	public int ticks() {
		return sway + tip + land;
	}

	public boolean done() {
		return tick >= ticks();
	}

	/**
	 * One tick: sets {@code body} moving toward where it should be at the end of this tick (its physics step then takes
	 * it there, held off the ground and out of walls). False once it's over (the body is left as it is, falling).
	 */
	public boolean steer(RagdollBody body) {
		if (done()) {
			return false;
		}
		tick++;
		float squash, droop, roll;
		if (tick <= sway) {
			float u = (float) tick / sway;
			squash = smooth(u);
			droop = smooth(u);
			// away from the fall, back through upright, to leaning toward it (and still there for a moment)
			roll = -SWAY * (float) Math.sin(1.5 * Math.PI * u);
		} else if (tick <= sway + tip) {
			float u = (float) (tick - sway) / tip;
			squash = 1.0F;
			droop = 1.0F;
			// gathering speed as it goes, as anything toppling does
			roll = SWAY + (end - SWAY) * u * u;
		} else {
			// down with a thump: it bounces back a little off its side and comes to rest on it
			float v = (float) (tick - sway - tip) / land;
			squash = 1.0F;
			droop = 1.0F;
			roll = end - LAND_BOUNCE * (float) Math.sin(Math.PI * v) * (1.0F - v);
		}
		int n = rig.count;
		float[] x = new float[n], y = new float[n], z = new float[n];
		shape(squash, droop, x, y, z);
		// rolled about a level line square to the fall, through the edge it leans over
		float px = roll >= 0.0F ? pivotX : backX, pz = roll >= 0.0F ? pivotZ : backZ;
		// the axis is up x the fall's way: (dirZ, 0, -dirX)
		float kx = dirZ, kz = -dirX;
		float c = (float) Math.cos(roll), s = (float) Math.sin(roll);
		for (int i = 0; i < n; i++) {
			float vx = x[i] - px, vy = y[i], vz = z[i] - pz;
			// Rodrigues: v cos + (k x v) sin + k (k . v)(1 - cos), with k level
			float kdot = kx * vx + kz * vz;
			float cx = -kz * vy, cy = kz * vx - kx * vz, cz = kx * vy;
			float rx = vx * c + cx * s + kx * kdot * (1 - c);
			float ry = vy * c + cy * s;
			float rz = vz * c + cz * s + kz * kdot * (1 - c);
			float wx = px + rx, wy = ry, wz = pz + rz;
			double tx = fx + wx * cos - wz * sin, ty = fy + wy, tz = fz + wx * sin + wz * cos;
			// moving there over this tick
			body.px[i] = body.x[i] - (tx - body.x[i]);
			body.py[i] = body.y[i] - (ty - body.y[i]);
			body.pz[i] = body.z[i] - (tz - body.z[i]);
		}
		body.wake();
		return true;
	}

	/** The body standing with its legs given ({@code squash} of the way) and its head dropped ({@code droop}). */
	private void shape(float squash, float droop, float[] x, float[] y, float[] z) {
		System.arraycopy(restX, 0, x, 0, x.length);
		System.arraycopy(restY, 0, y, 0, y.length);
		System.arraycopy(restZ, 0, z, 0, z.length);
		int[][] legs = legs();
		if (legs.length > 0) {
			int root = legs[0][0], foot = legs[0][1];
			float legLength = restY[root] - restY[foot];
			float sink = legLength * squash * (rig.shape == RagdollRig.Shape.HUMANOID ? SQUASH_HUMANOID : SQUASH_QUADRUPED);
			// everything but the feet sinks; the feet stay on the ground and slide out, keeping each leg its length
			boolean[] isFoot = new boolean[x.length];
			for (int[] leg : legs) {
				isFoot[leg[1]] = true;
			}
			for (int i = 0; i < x.length; i++) {
				if (!isFoot[i]) {
					y[i] -= sink;
				}
			}
			float drop = legLength - sink;
			float out = (float) Math.sqrt(Math.max(0.0F, legLength * legLength - drop * drop));
			for (int[] leg : legs) {
				float side = Math.signum(restX[leg[1]]);
				if (rig.shape == RagdollRig.Shape.HUMANOID) {
					x[leg[1]] = x[leg[0]] + side * out * (float) Math.sqrt(1.0F - HUMANOID_FEET_FORWARD * HUMANOID_FEET_FORWARD);
					z[leg[1]] = z[leg[0]] + out * HUMANOID_FEET_FORWARD;
				} else {
					x[leg[1]] = x[leg[0]] + side * out;
				}
			}
		}
		if (rig.shape == RagdollRig.Shape.HUMANOID) {
			// chin to the chest: the head tips forward about the neck
			nod(x, y, z, RagdollRig.NECK, RagdollRig.HEAD, DROOP_HUMANOID * droop);
		} else if (rig.shape == RagdollRig.Shape.QUADRUPED) {
			// the muzzle sinks toward the ground
			nod(x, y, z, RagdollRig.SNOUT, RagdollRig.MUZZLE, DROOP_QUADRUPED * droop);
		}
	}

	/** Turns {@code tip} about {@code root}, about the body's left-right line, front going down by {@code angle}. */
	private static void nod(float[] x, float[] y, float[] z, int root, int tip, float angle) {
		float dy = y[tip] - y[root], dz = z[tip] - z[root];
		float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
		y[tip] = y[root] + dy * c - dz * s;
		z[tip] = z[root] + dy * s + dz * c;
	}

	/** Each leg's root and foot, or none for a body without legs (a box). */
	private int[][] legs() {
		return switch (rig.shape) {
			case HUMANOID -> new int[][] {{RagdollRig.R_HIP, RagdollRig.R_FOOT}, {RagdollRig.L_HIP, RagdollRig.L_FOOT}};
			case QUADRUPED -> new int[][] {{RagdollRig.FRONT_RIGHT, RagdollRig.FR_FOOT}, {RagdollRig.FRONT_LEFT, RagdollRig.FL_FOOT},
				{RagdollRig.BACK_RIGHT, RagdollRig.BR_FOOT}, {RagdollRig.BACK_LEFT, RagdollRig.BL_FOOT}};
			case BOX, CHAIN -> new int[0][];
		};
	}

	private int[] feet() {
		int[][] legs = legs();
		int[] out = new int[legs.length];
		for (int i = 0; i < legs.length; i++) {
			out[i] = legs[i][1];
		}
		return out;
	}

	private static float smooth(float u) {
		return u * u * (3.0F - 2.0F * u);
	}
}
