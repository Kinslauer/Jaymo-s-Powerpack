package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebTricks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Picks the swing tricks ({@link WebTricks}) from how the swing actually goes: a flip, corkscrew or cartwheel off a
 * released web; a twirl or a giant swing on the web, at the bottom of a fast arc or when the next web catches; and on
 * touching down a superhero landing or a roll (the wings always come down in the superhero landing). {@link WebSwing} tells it what happened.
 */
final class WebTrickClient {
	/** A released web flips you only if you're moving at least this fast (blocks/tick). */
	static final double RELEASE_SPEED = 0.5;
	/** On the web: the bottom of an arc at this speed may turn into a trick, this often (0-1). */
	static final double ARC_SPEED = 0.75, ARC_CHANCE = 0.3;
	/** The next web catching mid-air turns you round it this often. */
	static final double CATCH_CHANCE = 0.4;
	/** Touching down: this fast across the ground (blocks/tick) rolls; slower, a fall this far (blocks) lands three-point. */
	static final double HERO_DROP = 2.5, ROLL_SPEED = 0.55;
	/**
	 * The wings' freedive coming down onto the ground at this speed (blocks/tick), its sideways drift under this much
	 * of the fall: a superhero landing that breaks the ground ({@link com.jaymospowers.power.web.WebSlam}).
	 */
	static final double DIVE_SLAM_SPEED = 1.0, DIVE_STRAIGHT = 0.5;
	/** Only a real trip through the air ends in a landing trick (ticks off the ground). */
	static final int MIN_AIR_TICKS = 8;

	private int busyUntil;
	private int lastKind = -1;
	private int airTicks;
	/** Off the ground on a web, a release or the wings: the next touchdown may get a landing. */
	private boolean armed;
	private boolean wings;
	/** In the wings' freedive on the last tick in the air. */
	private boolean dove;
	private double previousVy;
	private Vec3 airVelocity = Vec3.ZERO;

	void reset() {
		armed = false;
		wings = false;
		dove = false;
		airTicks = 0;
	}

	/** Every tick, before the swing moves you. {@code apexY}: the top of the current fall. */
	void tick(LocalPlayer player, boolean onWeb, boolean flying, boolean wingsOpen, boolean diving, double apexY) {
		if (player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger() || player.isFallFlying()) {
			reset();
			return;
		}
		if (!player.onGround()) {
			airTicks++;
			airVelocity = player.getDeltaMovement();
			if (onWeb || flying || wingsOpen) {
				armed = true;
			}
			if (wingsOpen) {
				wings = true;
			}
			dove = wingsOpen && diving;
			if (onWeb) {
				arc(player);
			}
			previousVy = player.getDeltaMovement().y;
			return;
		}
		// (feet down while still hanging on a web is a run along the ground, not a landing)
		if (armed && airTicks >= MIN_AIR_TICKS && !onWeb) {
			land(player, apexY - player.getY());
		}
		reset();
	}

	/** The last web let go of, in the air. */
	void released(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		if (v.length() < RELEASE_SPEED || !free(player)) {
			return;
		}
		int kind;
		if (v.y > 0.35) {
			// thrown upward: tuck and go over, or lay out and twist
			kind = pick(player, WebPose.FRONT_FLIP, WebPose.CORKSCREW, WebPose.FRONT_FLIP);
		} else {
			kind = pick(player, WebPose.CORKSCREW, WebPose.CARTWHEEL, WebPose.FRONT_FLIP);
		}
		send(player, kind);
	}

	/** A web just caught while you were already in the air. */
	void caught(LocalPlayer player) {
		if (!player.onGround() && free(player) && player.getRandom().nextDouble() < CATCH_CHANCE) {
			send(player, WebPose.TWIRL);
		}
	}

	/** On the web: through the bottom of a fast arc, now and then a twirl or a giant swing over the hand. */
	private void arc(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		if (previousVy < 0.0 && v.y >= 0.0 && v.horizontalDistance() > ARC_SPEED && free(player)
			&& player.getRandom().nextDouble() < ARC_CHANCE) {
			send(player, pick(player, WebPose.GIANT, WebPose.TWIRL, WebPose.GIANT));
		}
	}

	private void land(LocalPlayer player, double drop) {
		if (blocked(player)) {
			return;
		}
		double across = airVelocity.horizontalDistance();
		int kind;
		if (dove && -airVelocity.y >= DIVE_SLAM_SPEED && across < -airVelocity.y * DIVE_STRAIGHT) {
			// straight down out of the dive: a superhero landing that breaks the ground under it
			send(player, WebPose.HERO_LAND);
			ClientPlayNetworking.send(new com.jaymospowers.power.web.WebSlam.Dive());
			return;
		}
		if (wings) {
			// (Motion patch 0) Andy: the hero landing "over all web's glide landings too"
			kind = WebPose.HERO_LAND;
		} else if (across > ROLL_SPEED) {
			// coming in fast: carried on into a roll, however far the drop
			kind = WebPose.LAND_ROLL;
		} else if (drop >= HERO_DROP) {
			// slow across the ground: eased down into the three-point landing
			kind = WebPose.HERO_LAND;
		} else {
			return;
		}
		// a landing always shows, even over a flip that's still going
		send(player, kind);
	}

	/** Nothing else has the body: no trick still going, no dodge, no ragdoll, no takeover. */
	private boolean free(LocalPlayer player) {
		return player.tickCount >= busyUntil && !blocked(player);
	}

	private static boolean blocked(LocalPlayer player) {
		WebPose pose = player.getAttached(com.jaymospowers.power.web.WebPower.POSE);
		boolean posed = pose != null && !WebPose.trick(pose.kind()) && player.level().getGameTime() - pose.start() < pose.ticks();
		return posed || WebSymbioteClient.driving();
	}

	/** One of the given tricks, at random, but not the one just done. */
	private int pick(LocalPlayer player, int... kinds) {
		for (int tries = 0; tries < 4; tries++) {
			int kind = kinds[player.getRandom().nextInt(kinds.length)];
			if (kind != lastKind) {
				return kind;
			}
		}
		return kinds[0] == lastKind ? kinds[1] : kinds[0];
	}

	private void send(LocalPlayer player, int kind) {
		lastKind = kind;
		busyUntil = player.tickCount + new WebPose(kind, 0L, 1).ticks();
		ClientPlayNetworking.send(new WebTricks.Trick(kind, player.getRandom().nextBoolean() ? 1 : -1));
	}
}
