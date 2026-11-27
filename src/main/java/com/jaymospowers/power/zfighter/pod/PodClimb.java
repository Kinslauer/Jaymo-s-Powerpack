package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The climb (Patch 9, tr_cockpit): after the lift the pilot flies the pod up through the sky bands of the world they leave, in
 * the real world, until it clears the top and the world falls away below. The server moves the pod (collisions and all: a floating
 * isle is something to steer round); the pilot's look is the nose, forward is cruise, sprint is boost, back is brake. With
 * nothing held the pod drifts on slowly. The autopilot climbs straight up and sidesteps what is in the way. Aerclouds and
 * Cloudbank let a pod through ({@code AercloudBlock}), so the Cloud Sea's overcast and the cloudbed under the Aether are no wall.
 */
public final class PodClimb {
	// ---- tuning ----
	/** How fast the climb gains speed (blocks a tick, a tick), and the share of cruise it drifts at with no key held. */
	static final double ACCEL = 0.04, IDLE_SHARE = 0.35;
	/** How far ahead (ticks of travel) the autopilot looks for something in the way, how long a sidestep holds (ticks), how far round it tries (steps of 30 degrees). */
	static final double LOOK_AHEAD = 8.0;
	static final int SIDESTEP_TICKS = 30, SIDESTEP_TRIES = 12;
	/** How fast the pod turns to face where the pilot looks (share of the way a tick). */
	static final float TURN_EASE = 0.2F;

	/** One climb in progress. */
	public static final class Flight {
		final double startY;
		double speed;
		public boolean auto;
		int sidestep;
		Vec3 side = Vec3.ZERO;
		int bumps;

		public Flight(double startY, boolean auto) {
			this.startY = startY;
			this.auto = auto;
		}
	}

	private PodClimb() {
	}

	/** One tick of the climb; true once the pod is above {@code top}. */
	public static boolean step(ServerPlayer player, ServerLevel level, PodEntity pod, Flight f, double top, float flightShare, @Nullable ServerPlayer guest) {
		PodFlight.Input in = PodFlight.input(player);
		if (PodFlight.toggled(player)) {
			f.auto = !f.auto;
		}
		Input keys = player.getLastClientInput();
		double scale = PodFlight.classSpeed(flightShare);
		double cruise = PodFlight.CLIMB_CRUISE * scale, boost = PodFlight.CLIMB_BOOST * scale;
		boolean boosting = !f.auto && keys.forward() && keys.sprint();
		double target;
		if (f.auto) {
			target = cruise;
		} else if (keys.forward()) {
			target = boosting ? boost : cruise;
		} else if (keys.backward()) {
			target = 0.0;
		} else {
			target = cruise * IDLE_SHARE;
		}
		f.speed += Mth.clamp(target - f.speed, -ACCEL * 2.0, ACCEL);
		Vec3 dir = f.auto ? upward(level, pod, f) : player.getLookAngle();
		if (f.speed > 0.01) {
			Vec3 before = pod.position();
			Vec3 step = dir.scale(f.speed);
			pod.move(MoverType.SELF, step);
			pod.setDeltaMovement(Vec3.ZERO);
			double moved = pod.position().subtract(before).length();
			if (moved < f.speed * 0.5) {
				// scraped against an isle or a wall: slows to a crawl and rattles
				f.speed *= 0.3;
				if (f.bumps++ % 10 == 0) {
					Shakes.send(player, 0.4F, pod.position());
				}
			}
		}
		if (!f.auto) {
			pod.setYRot(Mth.rotLerp(TURN_EASE, pod.getYRot(), player.getYRot()));
		}
		if (boosting && pod.tickCount % 3 == 0) {
			MagicFx.burst(level, pod.getX(), pod.getY() - 0.3, pod.getZ(), MagicFx.EMBER_COLOR, 0.5F, 4, 0.3, 0.1, 0.3);
		}
		if (pod.tickCount % 2 == 0) {
			double span = Math.max(1.0, top - f.startY);
			double left = Math.max(0.0, top - pod.getY());
			int eta = (int)Math.round(left / Math.max(0.3, cruise));
			int flags = (f.auto ? PodFlight.F_AUTO : 0) | (boosting ? PodFlight.F_BOOST : 0);
			PodFlight.State state = new PodFlight.State(PodFlight.PHASE_CLIMB, flags, (float)f.speed, (float)Mth.clamp((pod.getY() - f.startY) / span, 0.0, 1.0),
				eta, 1.0F, 0.0F, 1.0F, (float)pod.getX(), (float)pod.getY(), (float)pod.getZ(), pod.getYRot(), player.getXRot(), -1, -1, 0, 0, 0, 0, -1, -1, 0.0F,
				0.0F, List.of());
			ServerPlayNetworking.send(player, state);
			if (guest != null) {
				ServerPlayNetworking.send(guest, state);
			}
		}
		return pod.getY() >= top;
	}

	/** Straight up, unless something is in the way a few ticks on: then to the side a while, round until there is room. */
	private static Vec3 upward(ServerLevel level, PodEntity pod, Flight f) {
		if (f.sidestep > 0) {
			f.sidestep--;
			return f.side;
		}
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		if (free(level, pod, up)) {
			return up;
		}
		for (int k = 0; k < SIDESTEP_TRIES; k++) {
			double angle = Math.toRadians(30.0 * k);
			Vec3 round = new Vec3(Math.cos(angle) * 0.9, 0.45, Math.sin(angle) * 0.9).normalize();
			if (free(level, pod, round)) {
				f.side = round;
				f.sidestep = SIDESTEP_TICKS;
				return round;
			}
		}
		return up;
	}

	private static boolean free(ServerLevel level, PodEntity pod, Vec3 dir) {
		AABB ahead = pod.getBoundingBox().move(dir.scale(LOOK_AHEAD));
		return level.noCollision(pod, ahead);
	}
}
