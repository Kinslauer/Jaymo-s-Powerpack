package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * The loop over the anchor. Andy's pick from Legends: Superheroes, "look up while holding forward on a swing and you
 * climb right over the top of the anchor in a full loop, then come down the far side still swinging". The swing's rope
 * ({@link WebSwing}) already carries a fast swing up the arc, but a true rope goes slack at the top and the swing stalls
 * and falls back; this keeps a swing that is fast enough going round instead, by never letting its speed along the arc
 * fall under what keeps the rope taut at that height ({@link #minSpeed}), and plays the whole-body loop pose
 * ({@link WebPose#LOOP_FLIP}) for everyone. Once the body has come down past the anchor's level on the far side the
 * assist lets go and it is an ordinary swing again.
 */
final class WebLoop {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** Moving at least this fast (blocks/tick) on the upswing, looking up at least this many degrees, to start a loop. */
	static final double ENTER_SPEED = 0.85, LOOK_UP_DEGREES = 25.0;
	/** The upswing counts from this far below the anchor's level (a share of the rope) up to just short of it. */
	static final double ENTER_LOW = 0.6, ENTER_HIGH = 0.1;
	/** The rope's length may be up to this long (blocks): the longer the rope, the faster the top of the loop must be. */
	static final double ROPE_MAX = 36.0;
	/** The speed at the top is this share over the least that keeps the rope taut. */
	static final double TAUT_MARGIN = 1.15;
	/** The least speed (blocks/tick) a loop in progress ever has, and the most ticks one may take. */
	static final double FLOOR = 0.35;
	static final int MAX_TICKS = 140;
	/** The body counts as over the top once this far above the anchor (a share of the rope), and as down the far side this far below. */
	static final double TOP_SHARE = 0.25, DOWN_SHARE = 0.1;
	// ================================================================================================================

	private boolean active, passedTop;
	private int ticks;

	boolean active() {
		return active;
	}

	void reset() {
		active = false;
		passedTop = false;
		ticks = 0;
	}

	/**
	 * Each tick on a swing, before the pendulum sets its speed: starts a loop when the swing is fast enough and the
	 * slinger holds forward looking up, and ends it once it has come down the far side (or was given up before the top).
	 */
	void tick(LocalPlayer player, WebSwing.Strand[] strands, Vec3 body, Vec3 v, boolean taut, WebTrickClient tricks) {
		Vec3 anchor = Vec3.ZERO;
		double rope = 0.0;
		int count = 0;
		for (WebSwing.Strand strand : strands) {
			if (strand.attached && strand.travel <= 0) {
				anchor = anchor.add(strand.anchor);
				rope += strand.rope;
				count++;
			}
		}
		if (count == 0 || !taut && !active) {
			if (count == 0) {
				reset();
			}
			return;
		}
		anchor = anchor.scale(1.0 / count);
		rope /= count;
		Input input = player.input.keyPresses;
		if (!active) {
			double level = (body.y - anchor.y) / rope;
			if (input.forward() && player.getXRot() <= -LOOK_UP_DEGREES && v.length() >= ENTER_SPEED && v.y > 0.05 && rope <= ROPE_MAX
				&& level > -ENTER_LOW && level < ENTER_HIGH && !player.onGround() && tricks.idle(player)) {
				active = true;
				passedTop = false;
				ticks = 0;
				tricks.sendMove(WebPose.LOOP_FLIP, player.getRandom().nextBoolean() ? 1 : -1);
				tricks.busy(player, WebPose.ticks(WebPose.LOOP_FLIP));
				WebSwing.play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.8F);
				WebSwing.play(player, SoundEvents.WIND_CHARGE_BURST.value(), 0.4F, 1.3F);
			}
			return;
		}
		ticks++;
		double level = (body.y - anchor.y) / rope;
		if (level > TOP_SHARE) {
			passedTop = true;
		}
		if (ticks > MAX_TICKS || passedTop && level < -DOWN_SHARE || !passedTop && !input.forward()) {
			reset();
		}
	}

	/**
	 * The least speed along the arc (blocks/tick) that keeps the swing going round at this point: nothing while the body is
	 * level with the anchor or below it, and at the top the speed at which gravity alone just keeps the rope taut
	 * ({@code sqrt(gravity x rope)}, with a margin), so the rope never goes slack over the top.
	 */
	double minSpeed(WebSwing.Strand[] strands, Vec3 body, double gravity) {
		Vec3 anchor = Vec3.ZERO;
		int count = 0;
		for (WebSwing.Strand strand : strands) {
			if (strand.attached && strand.travel <= 0) {
				anchor = anchor.add(strand.anchor);
				count++;
			}
		}
		if (count == 0) {
			return 0.0;
		}
		anchor = anchor.scale(1.0 / count);
		double radius = body.distanceTo(anchor);
		if (radius < 1.0E-3) {
			return 0.0;
		}
		double above = Math.max(0.0, (body.y - anchor.y) / radius);
		return Math.max(FLOOR, Math.sqrt(above * gravity * radius * TAUT_MARGIN));
	}
}
