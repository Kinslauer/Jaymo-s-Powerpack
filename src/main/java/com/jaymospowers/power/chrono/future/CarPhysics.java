package com.jaymospowers.power.chrono.future;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a car rolls: the tyre model of the Cars mod (MIT, by Pokato; see licenses/cars), the same Jaymo's Kitchen's
 * {@code CartPhysics} uses for a pulled cart (that class is private to the Kitchen jar, so the curve is written out here):
 * a tyre's side force rises with slip then saturates (sin(C * atan(B * slip))), grip scales with the road (ice barely holds),
 * and the heading turns at a limited rate that falls with speed. Here the engine, not a pole, pushes it. In blocks and ticks,
 * with no Minecraft types but the vector, so it can be checked on its own.
 */
final class CarPhysics {
	/** Blocks/tick^2 of side force a tyre gives at full grip on good ground (the curve's top). */
	static final double SIDE_GRIP = 0.14;
	/** The curve: slip (side speed over forward speed) where it peaks, and how hard it saturates (Cars' pacB, pacC). */
	static final double PEAK_SLIP = 0.18, CURVE_C = 1.4;
	/** Braking and reversing: blocks/tick^2 the brake takes off, how hard reverse pulls, and the fastest reverse. */
	static final double BRAKE = 0.05, REVERSE = 0.02, REVERSE_TOP = 0.22;
	/** Blocks/tick^2 the wheels' rolling resistance takes off, whatever the speed. */
	static final double ROLLING = 0.002;
	/** The handbrake leaves the tyres this share of their side grip (the rear lets go and it slides), and slows it this much a tick. */
	static final double HANDBRAKE_GRIP = 0.22, HANDBRAKE_DRAG = 0.025;
	/** Most the heading turns each tick on full steer at speed (degrees), the speed it turns at full rate up to, and how fast the turn falls past it. */
	static final double MAX_TURN = 5.5, TURN_FULL_AT = 0.12, TURN_FALLOFF = 0.9;
	/** The share of its turning a car keeps even on ice. */
	static final double MIN_TURN_GRIP = 0.15;
	/** A block's slipperiness for plain ground (grip 1) and ice (almost none). */
	static final float NORMAL_FRICTION = 0.6F, SLIPPERY_FRICTION = 0.98F;

	/** World velocity on the ground plane (blocks/tick). */
	Vec3 velocity = Vec3.ZERO;

	/** Grip of a surface from its block friction: 1 on plain ground, near 0 on ice. */
	static double grip(float friction) {
		return Mth.clamp((SLIPPERY_FRICTION - friction) / (SLIPPERY_FRICTION - NORMAL_FRICTION), 0.04, 1.0);
	}

	/** The tyre's side force share for a slip: 0..1, rising then flattening. */
	static double tyre(double slip) {
		double b = Math.tan(Math.PI / (2.0 * CURVE_C)) / PEAK_SLIP;
		return Math.sin(CURVE_C * Math.atan(b * slip));
	}

	/**
	 * One tick. {@code yaw} is the heading in radians (forward is (-sin, cos), as the entity's yRot has it); {@code throttle}
	 * is -1 (reverse or brake) to 1 and counts only if {@code engine} runs; {@code steer} is -1 (left) to 1 (right, the way yaw grows);
	 * {@code surfaceGrip} is from {@link #grip}. Returns the new heading in radians.
	 */
	double step(double yaw, CarKind kind, boolean engine, double throttle, double steer, boolean handbrake, double surfaceGrip) {
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double forward = velocity.x * fx + velocity.z * fz;
		double side = -velocity.x * fz + velocity.z * fx;
		if (throttle > 0.0 && engine) {
			// the drive can only use the grip there is
			forward += kind.accel * throttle * (0.4 + 0.6 * surfaceGrip);
		} else if (throttle < 0.0) {
			if (forward > 0.02) {
				forward = Math.max(0.0, forward - BRAKE * (0.3 + 0.7 * surfaceGrip));
			} else if (engine) {
				forward = Math.max(-REVERSE_TOP, forward + throttle * REVERSE);
			}
		}
		forward -= Math.signum(forward) * Math.min(Math.abs(forward), ROLLING);
		// air and axles: the top speed is where the drive and the drag meet
		forward *= 1.0 - kind.accel / kind.top;
		if (handbrake) {
			forward *= 1.0 - HANDBRAKE_DRAG;
		}
		double slip = Math.abs(side) / (Math.abs(forward) + 0.05);
		double scrub = tyre(slip) * SIDE_GRIP * surfaceGrip * (handbrake ? HANDBRAKE_GRIP : 1.0);
		side -= Math.signum(side) * Math.min(Math.abs(side), scrub);
		velocity = new Vec3(fx * forward - fz * side, 0.0, fz * forward + fx * side);
		double rate = Math.toRadians(MAX_TURN) * steer * Math.min(1.0, Math.abs(forward) / TURN_FULL_AT) * Math.signum(forward)
			* Math.max(surfaceGrip, MIN_TURN_GRIP) / (1.0 + Math.abs(forward) * TURN_FALLOFF);
		return yaw + rate;
	}

	/** Forward speed along {@code yaw} (blocks/tick, negative backing up). */
	double forwardSpeed(double yaw) {
		return velocity.x * -Math.sin(yaw) + velocity.z * Math.cos(yaw);
	}
}
