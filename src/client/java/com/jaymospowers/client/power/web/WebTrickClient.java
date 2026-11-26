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

	/**
	 * The nine flips off a released web: the five for a web let go going up, the six for one let go going out or down
	 * (the corkscrew and the front flip are in both). No flip is picked that is one of the last two done, so a long run
	 * of swings never repeats itself.
	 */
	private static final int[] RELEASE_UP = {WebPose.FRONT_FLIP, WebPose.BACK_FLIP, WebPose.CORKSCREW, WebPose.TWIST_FLIP, WebPose.PIKE_FLIP};
	private static final int[] RELEASE_OUT = {WebPose.CORKSCREW, WebPose.CARTWHEEL, WebPose.FRONT_FLIP, WebPose.SIDE_ROLL, WebPose.SWAN_DIVE,
		WebPose.HANDSPRING};

	private int busyUntil;
	private int beforeLastKind = -1;
	/** When (the player's tick count) the last web was let go of; a strike out of the swing needs it recent. */
	private int releasedAt = Integer.MIN_VALUE / 2;
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
		releasedAt = player.tickCount;
		Vec3 v = player.getDeltaMovement();
		if (v.length() < RELEASE_SPEED || !free(player)) {
			return;
		}
		int kind;
		if (v.y > 0.35) {
			// thrown upward: tuck and go over (forwards, backwards, folded at the hips, with a half twist) or lay out and twist
			kind = pick(player, RELEASE_UP);
		} else {
			// thrown out level or down: a corkscrew, a cartwheel, a roll, a swan dive, a handspring or a front flip
			kind = pick(player, RELEASE_OUT);
		}
		send(player, kind);
	}

	int releasedAt() {
		return releasedAt;
	}

	/** A strike out of the swing was thrown: no trick starts over it. */
	void strikeSent(LocalPlayer player) {
		busyUntil = player.tickCount + 20;
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

	/** One of the given tricks, at random, but not one of the last two done. */
	private int pick(LocalPlayer player, int... kinds) {
		for (int tries = 0; tries < 8; tries++) {
			int kind = kinds[player.getRandom().nextInt(kinds.length)];
			if (kind != lastKind && kind != beforeLastKind) {
				return kind;
			}
		}
		for (int kind : kinds) {
			if (kind != lastKind) {
				return kind;
			}
		}
		return kinds[0];
	}

	/** Nothing else has the body, so a move of the swing's own (the loop, the roll) may start. */
	boolean idle(LocalPlayer player) {
		return free(player);
	}

	/** Like {@link #idle}, but a flip still going may be cut into (a dodge, a ragdoll or a takeover still may not). */
	boolean mayInterrupt(LocalPlayer player) {
		return !blocked(player);
	}

	/** A move of the swing's own has the body for {@code ticks}: no trick starts over it. */
	void busy(LocalPlayer player, int ticks) {
		busyUntil = Math.max(busyUntil, player.tickCount + ticks);
	}

	/** The pose {@code kind} turning to {@code side}, asked for by a move of the swing's own (the loop, the barrel roll). */
	void sendMove(int kind, int side) {
		ClientPlayNetworking.send(new WebTricks.Trick(kind, side));
	}

	private void send(LocalPlayer player, int kind) {
		beforeLastKind = lastKind;
		lastKind = kind;
		busyUntil = player.tickCount + new WebPose(kind, 0L, 1).ticks();
		ClientPlayNetworking.send(new WebTricks.Trick(kind, player.getRandom().nextBoolean() ? 1 : -1));
	}
}
