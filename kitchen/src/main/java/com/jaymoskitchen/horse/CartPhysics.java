package com.jaymoskitchen.horse;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a pulled cart rolls: it has a weight of its own, so it doesn't snap round behind its horse. The drive model is
 * after the Cars mod (MIT, by Pokato; see licenses/cars): a tyre's side force rises with slip then saturates
 * (sin(C * atan(B * slip))), grip scales with the road surface (ice barely holds), the heading turns at a limited
 * rate, and a slope adds or takes away speed. Written for a cart on a pole, in blocks and ticks, and with no
 * Minecraft types but the vector, so it can be checked on its own. Not the cars (the cars are their own patch).
 */
final class CartPhysics {
	/** Blocks/tick^2 the pole pulls per block it is stretched (a spring), up to {@link #MAX_PULL}. */
	static final double PULL_STIFFNESS = 0.12;
	/** The most the puller can accelerate the cart (blocks/tick^2): a horse's strength; a loaded cart is slow to get going. */
	static final double MAX_PULL = 0.035;
	/** Slack in the pole before it pulls (blocks): a cart coasts a little. */
	static final double POLE_SLACK = 0.15;
	/** Past this much stretch the pole is a hard leash, so a cart never falls far behind (blocks). */
	static final double MAX_STRETCH = 1.6;
	/** Blocks/tick^2 the wheels' rolling resistance takes off, whatever the speed. */
	static final double ROLLING = 0.003;
	/** Fraction of speed lost each tick to air and axles. */
	static final double DRAG = 0.015;
	/** Blocks/tick^2 gravity pulls along a slope (a slope is rise over run); the cars' hills, scaled for a cart. */
	static final double SLOPE_PULL = 0.08;
	/** The greatest slope counted (rise over run). */
	static final double MAX_SLOPE = 0.7;
	/** How fast the slope reading follows the ground (0..1 per tick), so a step doesn't jerk it. */
	static final double SLOPE_SMOOTH = 0.15;
	/** Blocks/tick^2 of side force a tyre gives at full grip on good ground (the tyre curve's top). */
	static final double SIDE_GRIP = 0.09;
	/** The tyre curve: slip (side speed over forward speed) where it peaks, and how hard it saturates (Cars' pacB, pacC). */
	static final double PEAK_SLIP = 0.18;
	static final double CURVE_C = 1.4;
	/** Most the heading turns each tick on full grip (degrees). */
	static final double MAX_TURN = 6.0;
	/** The fastest speed a cart still turns at full rate; faster, it turns less (blocks/tick), as the cars' steer falls off with speed. */
	static final double TURN_FALLOFF_SPEED = 0.3;
	/** The share of its turning a cart keeps even on ice, so it isn't a rock. */
	static final double MIN_TURN_GRIP = 0.12;
	/** The share of the pole's pull the puller feels as a pull back (a heavy cart drags at its horse). */
	static final double REACTION = 0.6;
	/** The most the puller is pulled back each tick (blocks/tick). */
	static final double MAX_REACTION = 0.03;
	/** A block's slipperiness (Block#getFriction) for plain ground, where grip is 1; ice is 0.98 and holds hardly at all. */
	static final float NORMAL_FRICTION = 0.6F;
	static final float SLIPPERY_FRICTION = 0.98F;

	/** World velocity on the ground plane (blocks/tick). */
	Vec3 velocity = Vec3.ZERO;
	/** Slope under the wheels, rise over run, smoothed. Positive is uphill in the direction it travels. */
	double slope;
	/** What the last step pulled on the puller (blocks/tick, toward the cart), 0 when the pole was slack. */
	double lastPull;

	void reset() {
		velocity = Vec3.ZERO;
		slope = 0.0;
		lastPull = 0.0;
	}

	/** Grip of a surface from its block friction: 1 on plain ground, near 0 on ice. */
	static double grip(float friction) {
		return Mth.clamp((SLIPPERY_FRICTION - friction) / (SLIPPERY_FRICTION - NORMAL_FRICTION), 0.04, 1.0);
	}

	/** The tyre's side force share for a slip (Cars: sin(C * atan(B * slip)), B from the peak): 0..1, rising then flattening. */
	static double tyre(double slip) {
		double b = Math.tan(Math.PI / (2.0 * CURVE_C)) / PEAK_SLIP;
		return Math.sin(CURVE_C * Math.atan(b * slip));
	}

	/**
	 * One tick. {@code yaw} is the cart's heading in radians (forward is (-sin, cos), as the cart's yRot has it),
	 * {@code toPuller} the flat vector from the cart to the pole's far end, {@code pole} the pole's rest length,
	 * {@code surfaceGrip} from {@link #grip}, {@code rise} the height gained this tick and {@code ran} the
	 * ground covered. Returns the new heading in radians.
	 */
	double step(double yaw, Vec3 toPuller, double pole, double surfaceGrip, double rise, double ran) {
		double gap = Math.sqrt(toPuller.x * toPuller.x + toPuller.z * toPuller.z);
		double px = gap > 1.0E-6 ? toPuller.x / gap : 0.0;
		double pz = gap > 1.0E-6 ? toPuller.z / gap : 0.0;
		double vx = velocity.x, vz = velocity.z;

		// pull along the pole once it's taut; the puller can only give so much
		double stretch = gap - pole - POLE_SLACK;
		lastPull = 0.0;
		if (stretch > 0.0) {
			double pull = Math.min(stretch * PULL_STIFFNESS, MAX_PULL);
			vx += px * pull;
			vz += pz * pull;
			lastPull = pull;
		}

		// split into along and across the wheels
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double forward = vx * fx + vz * fz;
		double side = -vx * fz + vz * fx;

		// slope: a slow, smoothed reading of the ground; uphill takes speed off, downhill gives it
		if (ran > 1.0E-4) {
			double raw = Mth.clamp(rise / ran, -MAX_SLOPE, MAX_SLOPE);
			slope += (raw - slope) * SLOPE_SMOOTH;
		} else {
			slope *= 1.0 - SLOPE_SMOOTH;
		}
		double before = forward;
		forward -= Math.signum(forward) * slope * SLOPE_PULL;
		if (slope > 0.0 && before * forward < 0.0) {
			forward = 0.0;
		}

		// rolling resistance and drag along the wheels
		double rolled = Math.min(Math.abs(forward), ROLLING);
		forward -= Math.signum(forward) * rolled;
		forward *= 1.0 - DRAG;

		// tyre grip: side speed is scrubbed off by a force that saturates, and on ice there's little to scrub it
		double speed = Math.abs(forward);
		double slip = Math.abs(side) / (speed + 0.05);
		double scrub = tyre(slip) * SIDE_GRIP * surfaceGrip;
		side -= Math.signum(side) * Math.min(Math.abs(side), scrub);

		vx = fx * forward - fz * side;
		vz = fz * forward + fx * side;
		velocity = new Vec3(vx, 0.0, vz);

		// heading turns toward the pole, at a limited rate that falls with speed and with how little the road holds
		if (gap > 1.0E-6) {
			double want = Math.atan2(-px, pz);
			double error = Mth.wrapDegrees((float) Math.toDegrees(want - yaw)) * Mth.DEG_TO_RAD;
			double rate = Math.toRadians(MAX_TURN) * Math.max(surfaceGrip, MIN_TURN_GRIP)
				/ (1.0 + Math.hypot(vx, vz) / TURN_FALLOFF_SPEED);
			yaw += Mth.clamp(error, -rate, rate);
		}
		return yaw;
	}
}
