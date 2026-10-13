package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebAnchors;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The local player's web movement: swinging on up to two webs at once (one from each hand, each on its own key and
 * rope, as a rope constraint on top of vanilla physics), keeping momentum after a release, and being reeled in
 * (Zip / Yank-to). Runs at the end of each client tick, after vanilla has moved the player, and sets the velocity
 * they'll move with next tick. Player motion is client-authoritative, so this is the real movement; the server only
 * checks the anchors and waives fall damage and the flying kick.
 */
public final class WebSwing {
	// ===== swing feel: the knobs to tweak after playtesting =========================================================
	/** The rope starts at this fraction of the distance to the anchor: 1 = no tug when it sticks (Jump reels you in). */
	static final double ATTACH_SLACK = 1.0;
	/** Shortest the rope can be reeled to. */
	static final double ROPE_MIN = 3.0;
	/** Pull-back speed per block the rope is over-stretched (rope elasticity), and the most it pulls back per tick. */
	static final double ROPE_SPRING = 0.3;
	static final double ROPE_MAX_PULL = 0.7;
	/** Extra gravity while swinging (vanilla is 0.08/tick): heavier, snappier arcs that build speed at the bottom. */
	static final double SWING_GRAVITY = 0.025;
	/** Velocity kept per tick while on the web (replaces vanilla's 0.91 horizontal / 0.98 vertical air drag). */
	static final double SWING_DRAG = 0.993;
	/** Holding forward on the downswing pumps this much extra speed along the swing, per tick. */
	static final double PUMP = 0.015;
	/** Holding Jump on a web also thrusts you along the swing this much per tick (on top of reeling in): it rockets
	 * you round the arc, and a release flings you out of it with the extra speed. */
	static final double JUMP_THRUST = 0.035;
	/**
	 * Let go of Jump on a web (held at least {@code JUMP_OFF_MIN_HOLD} ticks) and you let go of the webs and spring up:
	 * {@code JUMP_OFF_BASE} plus {@code JUMP_OFF_PER_SPEED} per block/tick you were moving, up to {@code JUMP_OFF_MAX}.
	 * The faster the swing, the higher you go. (Holding Jump on a web also keeps you from taking fall damage; the
	 * server checks that.) Held web keys must be pressed again after, and no web for {@code JUMP_OFF_LOCKOUT} ticks.
	 */
	static final double JUMP_OFF_BASE = 0.2, JUMP_OFF_PER_SPEED = 0.45, JUMP_OFF_MAX = 1.4;
	static final int JUMP_OFF_MIN_HOLD = 3, JUMP_OFF_LOCKOUT = 8;
	/** Jump reels in, sneak lets out (blocks per tick), every web that's out. */
	static final double REEL_IN = 0.45;
	static final double LET_OUT = 0.35;
	/** Speed cap while swinging or flying from a swing (blocks/tick; sprinting is ~0.28). */
	static final double MAX_SPEED = 2.6;
	/**
	 * Momentum has to be built: a swing chain starts capped at {@code SWING_START_SPEED}, and the cap only climbs
	 * ({@code SWING_RAMP} a tick) while you're on a web actually moving at {@code RAMP_AT} of it or more, so it takes
	 * about half a minute of good, unbroken swinging to reach {@code MAX_SPEED}. A clean release (quality over
	 * {@code RELEASE_GAIN_QUALITY}) adds up to {@code RELEASE_GAIN}. It holds through releases and new webs in the air,
	 * and starts over once you're on the ground off the web.
	 */
	static final double SWING_START_SPEED = 0.55;
	static final double SWING_RAMP = 0.003;
	static final double RAMP_AT = 0.85;
	static final double RELEASE_GAIN = 0.08, RELEASE_GAIN_QUALITY = 0.6;
	/** Release timing: letting go while travelling this many degrees above level is perfect; the window either side. */
	static final double RELEASE_SWEET_ANGLE = 35.0;
	static final double RELEASE_WINDOW = 35.0;
	/** The boost a perfect release gives (scaled down linearly to 0 at the window's edges). */
	static final double RELEASE_FORWARD = 0.35;
	static final double RELEASE_UP = 0.5;
	/** Horizontal velocity kept per tick in the air after a release, until you land (vanilla air is 0.91). */
	static final double FLIGHT_DRAG = 0.985;
	/**
	 * Pendulum: while a rope is taut the swing keeps its energy (speed² / 2 + gravity × height), so it speeds up
	 * through the bottom of the arc and slows to a stall at the top, as a real pendulum does. {@code PENDULUM_DRAG}
	 * is the share of the moving energy kept each tick; Jump and the pump add to it.
	 */
	static final double PENDULUM_DRAG = 0.996;
	/** A rope counts as taut within this much of its length. */
	static final double TAUT_SLACK = 0.15;
	/** Holding a swing key in the air with nothing to stick to retries every this many ticks. */
	static final int RETRY_TICKS = 3;
	// ===== slingshot (two webs out: hold Sneak to brace, hold Jump to draw back, let Jump go to fire) ================
	/** Ticks of Jump to a full draw (you stay where you braced: the draw grinds into what's under and behind you instead). */
	static final int SLING_DRAW_TICKS = 30;
	/** Living Armor drawing this long (5 s) breaks what it's ground into and fires you off as if you'd let go. */
	static final int SLING_ARMOR_BREAK_TICKS = 100;
	/** Launch speed at no draw and at a full draw (blocks/tick; the swing cap is lifted for the flight). */
	static final double SLING_MIN_SPEED = 0.9;
	static final double SLING_MAX_SPEED = 3.4;
	/** How much the launch follows where you look, versus straight between the webs (0 = all webs). */
	static final double SLING_AIM = 0.8;
	/** After a launch, no web can be shot for this long (and held web keys must be pressed again), so a held key can't
	 * snag a web the instant you fire and kill the launch. */
	static final int SLING_LOCKOUT = 12;
	/** The least draw that fires (a tap of Jump just reels in). */
	static final float SLING_MIN_DRAW = 0.12F;
	// ===== web wings (hold Jump in mid-air) =========================================================================
	/** Ticks Jump must be held in mid-air (while falling) before the wings spread. */
	static final int WINGS_HOLD_TICKS = 6;
	/** The wings only open once you've fallen more than this far from your highest point. Each time they fold, Jump
	 * must be let go and you must fall this far again before they open again (there's no cooldown). */
	static final double WINGS_MIN_DROP = 2.0;
	/**
	 * The wings fly like an elytra (vanilla's own glide): pitch down to dive and gain speed, pull up to trade it for
	 * height, level out to glide. {@code WINGS_GRAVITY} is the pull they work against; {@code WINGS_MAX_SPEED} caps it.
	 */
	static final double WINGS_GRAVITY = 0.08;
	static final double WINGS_MAX_SPEED = 2.0;
	/**
	 * Airbrake: hold S (or Sneak) while gliding to flare the wings. The glide stops carrying you: forward speed is kept
	 * per tick ({@code BRAKE_DRAG}, so it bleeds off over a few seconds), and the fall eases toward {@code BRAKE_SINK}
	 * (blocks/tick) at {@code BRAKE_CATCH} a tick, like a parachute catching.
	 */
	static final double BRAKE_DRAG = 0.94;
	static final double BRAKE_SINK = 0.2;
	static final double BRAKE_CATCH = 0.3;
	/**
	 * Braked to a stop (forward speed under {@code DIVE_AT}), still holding S: the wings stay out but stop flying, and you
	 * drop head-first into a freedive, like Wind's, gaining speed down to {@code DIVE_MAX_SPEED}. Let go of S to pull out
	 * into the glide again (pitch up and the dive's speed turns forward, as an elytra's does).
	 */
	static final double DIVE_AT = 0.06;
	static final double DIVE_MAX_SPEED = 2.5;
	// ===== zip / yank-to ===========================================================================================
	/** How quickly the reel-in steers your velocity onto the line (0..1 per tick). */
	static final double ZIP_STEER = 0.55;
	/**
	 * A block zip lets go this far short of the anchor and keeps its speed, with a kick upward, so it flings you up and
	 * over (gaining height) instead of stopping dead. Hold Sneak to ride it all the way in and perch as before.
	 */
	static final double ZIP_RELEASE_DISTANCE = 4.0;
	static final double ZIP_RELEASE_LIFT = 0.55;
	/** At the end of a zip onto a wall: forward push onto the ledge. */
	static final double PERCH_FORWARD = 0.22;
	// ===============================================================================================================

	/** Vanilla's air drag, which the swing and flight undo before applying their own. */
	private static final double VANILLA_AIR_DRAG = 0.91;
	private static final double VANILLA_AIR_DRAG_Y = 0.98;
	/** The rope pulls on the body at about hand height. */
	private static final double ROPE_HEIGHT = 1.2;

	/** One hand's swing web ({@link WebNet#MAIN} or {@link WebNet#OFF}). */
	static final class Strand {
		final int hand;
		boolean attached;
		Vec3 anchor = Vec3.ZERO;
		double rope;
		int swingTicks;
		boolean keyHeld;
		int retryIn;
		/** Ticks until the web reaches its anchor (it shoots out at WebGrowth's speeds); no pull till then. */
		int travel;

		Strand(int hand) {
			this.hand = hand;
		}
	}

	final Strand[] strands = {new Strand(WebNet.MAIN), new Strand(WebNet.OFF)};
	/** The swing's energy per unit mass (see {@link #PENDULUM_DRAG}); NaN until the swing has measured it. */
	private double swingEnergy = Double.NaN;
	// after a release
	boolean flying;
	/** Speed cap for the current free flight (a slingshot launch may exceed the swing cap). */
	private double flightCap = SWING_START_SPEED;
	/** The swing chain's current speed cap (see {@link #SWING_START_SPEED}). */
	private double momentum = SWING_START_SPEED;
	// slingshot
	boolean slinging;
	float slingDraw;
	private Vec3 braced = Vec3.ZERO;
	private boolean slingJumpHeld;
	/** Ticks Jump has been held while on a web (0 = not held). */
	private int swingJumpTicks;
	/** Drawn back into a wall: ticks of strain against it so far, and the last strain sent. */
	private int strainTicks;
	private float sentStrain;
	/** Ticks of grinding into a wall before it gives way (Living Armor tears through faster). */
	static final int STRAIN_TICKS = 60, STRAIN_TICKS_ARMOR = 60;
	private int swingLockout;
	// wings
	int wingsTicks;
	/** Sneak is held while gliding: the wings are flared to airbrake. */
	boolean braking;
	/** Braked to a stop and dropping head-first (wings still out). */
	boolean diving;
	/** Jump has been let go since the wings spread (the next press folds them). */
	private boolean wingsJumpReleased;
	private int jumpHeldAir;
	private boolean wingsNeedRelease;
	/** The highest point since you last stood, rose, or folded the wings: falls for the wings count from here. */
	private double apexY;
	// zip / yank-to
	WebNet.@Nullable Pull pull;
	private int pullTicks;
	private int stuckTicks;
	private Vec3 lastPos = Vec3.ZERO;
	/** Flips and twirls off the webs, on them, and on landing. */
	private final WebTrickClient tricks = new WebTrickClient();

	void reset() {
		for (Strand strand : strands) {
			strand.attached = false;
			strand.keyHeld = false;
		}
		flying = false;
		pull = null;
		slinging = false;
		slingDraw = 0.0F;
		if (wingsTicks > 0) {
			ClientPlayNetworking.send(new WebNet.Wings(false));
		}
		wingsTicks = 0;
		braking = false;
		wingsNeedRelease = false;
		swingLockout = 0;
		tricks.reset();
	}

	private static LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	/** Both webs stuck: the slingshot is possible. */
	boolean bothAttached() {
		return strands[WebNet.MAIN].attached && strands[WebNet.OFF].attached;
	}

	/** Any web stuck right now. */
	boolean attached() {
		return strands[WebNet.MAIN].attached || strands[WebNet.OFF].attached;
	}

	// ---- input ----------------------------------------------------------------------------------------------------

	void press(LocalPlayer player, int hand) {
		Strand strand = strands[hand];
		if (swingLockout > 0) {
			// just launched from the slingshot: no web yet (press again once the lockout's over)
			return;
		}
		strand.keyHeld = true;
		if (pull != null) {
			return;
		}
		if (!tryAttach(player, strand)) {
			play(player, SoundEvents.FISHING_BOBBER_THROW, 0.4F, 2.0F);
			strand.retryIn = RETRY_TICKS;
		}
	}

	void releaseKey(LocalPlayer player, int hand) {
		Strand strand = strands[hand];
		strand.keyHeld = false;
		if (strand.attached) {
			detach(player, strand, true);
		}
	}

	private boolean tryAttach(LocalPlayer player, Strand strand) {
		if (player.getAbilities().flying || player.isInWater() || player.isInLava()) {
			return false;
		}
		if (strand.hand == WebNet.OFF && !WebPower.offHandFree(player)) {
			return false;
		}
		WebAnchors.Anchor found = WebAnchors.find(player);
		if (found == null) {
			return false;
		}
		boolean midAir = !player.onGround() && (flying || attached() || wingsTicks > 0);
		strand.attached = true;
		swingJumpTicks = 0;
		flying = false;
		swingEnergy = Double.NaN;
		foldWings();
		strand.anchor = found.pos();
		strand.swingTicks = 0;
		double distance = strand.anchor.distanceTo(player.position().add(0.0, ROPE_HEIGHT, 0.0));
		strand.rope = Math.max(ROPE_MIN, distance * ATTACH_SLACK);
		strand.travel = (int)(distance / (com.jaymospowers.power.web.WebSymbiote.suitShown(player) ? WebGrowth.ROPE_SPEED : WebGrowth.WEB_SPEED));
		ClientPlayNetworking.send(new WebNet.Swing(strand.hand, true, strand.anchor));
		play(player, SoundEvents.FISHING_BOBBER_THROW, 0.9F, 1.7F + player.getRandom().nextFloat() * 0.2F);
		puff(player, strand.anchor, 5);
		if (midAir) {
			tricks.caught(player);
		}
		return true;
	}

	/** Let go of one web. {@code timed}: a deliberate release, which may earn the upswing boost (if it was the last web). */
	void detach(LocalPlayer player, Strand strand, boolean timed) {
		if (!strand.attached) {
			return;
		}
		strand.attached = false;
		ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
		slinging = false;
		if (attached()) {
			// still hanging from the other hand: no release yet
			return;
		}
		flying = !player.onGround();
		flightCap = momentum;
		if (!timed || player.onGround()) {
			return;
		}
		tricks.released(player);
		Vec3 v = player.getDeltaMovement();
		double speed = v.length();
		if (speed < 0.4 || v.y <= 0.0) {
			return;
		}
		double angle = Math.toDegrees(Math.asin(v.y / speed));
		double quality = 1.0 - Math.abs(angle - RELEASE_SWEET_ANGLE) / RELEASE_WINDOW;
		if (quality <= 0.0) {
			return;
		}
		Vec3 forward = v.horizontal().normalize();
		player.setDeltaMovement(v.add(forward.scale(RELEASE_FORWARD * quality)).add(0.0, RELEASE_UP * quality, 0.0));
		if (quality > RELEASE_GAIN_QUALITY) {
			// a well-timed release is how you build speed fastest
			momentum = Math.min(MAX_SPEED, momentum + RELEASE_GAIN * quality);
			flightCap = momentum;
		}
		if (quality > 0.7) {
			play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.6F);
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			for (int i = 0; i < 6; i++) {
				player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, -v.x * 0.1 + rnd(player) * 0.1, -0.05, -v.z * 0.1 + rnd(player) * 0.1);
			}
		} else {
			play(player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.5F, 1.4F);
		}
	}

	private void detachAll(LocalPlayer player, boolean timed) {
		for (Strand strand : strands) {
			detach(player, strand, timed);
		}
	}

	/** The server refused that hand's web, or its anchor broke. */
	void rejected(LocalPlayer player, int hand) {
		if (hand < 0 || hand >= strands.length) {
			return;
		}
		Strand strand = strands[hand];
		if (strand.attached) {
			strand.attached = false;
			if (!attached()) {
				flying = !player.onGround();
			}
			play(player, SoundEvents.LEAD_BREAK, 0.6F, 1.6F);
		}
	}

	void startPull(LocalPlayer player, WebNet.Pull pull) {
		for (Strand strand : strands) {
			if (strand.attached) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
			}
		}
		flying = false;
		this.pull = pull;
		pullTicks = 0;
		stuckTicks = 0;
		lastPos = player.position();
	}

	// ---- per tick -------------------------------------------------------------------------------------------------

	void tick(Minecraft minecraft, LocalPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
			detachAll(player, false);
			flying = false;
			pull = null;
			return;
		}
		if (swingLockout > 0) {
			swingLockout--;
		}
		tricks.tick(player, attached(), flying, wingsTicks > 0, apexY);
		// the swing's momentum builds while on a web, and is lost on the ground
		if (attached()) {
			if (player.getDeltaMovement().length() >= momentum * RAMP_AT) {
				momentum = Math.min(MAX_SPEED, momentum + SWING_RAMP);
			}
		} else if (player.onGround() || player.isInWater() || player.isInLava()) {
			momentum = SWING_START_SPEED;
		}
		// the highest point of the current fall (resets on the ground, while rising, on a web or in water)
		if (player.onGround() || player.getDeltaMovement().y >= 0.0 || attached() || pull != null || player.isInWater() || player.onClimbable()) {
			apexY = player.getY();
		}
		if (pull != null) {
			// (a Zip while gliding keeps the wings spread: when the pull lets go, the glide carries on with its speed)
			tickPull(player);
			return;
		}
		if (com.jaymospowers.power.web.WebSymbiote.suitShown(player)) {
			// the suit has no wings: Jump is a charged leap instead
			foldWings();
			tickChargeJump(player);
		} else {
			if (chargeTicks > 0 || chargeSent) {
				endCharge();
			}
			tickWingsInput(player);
			if (wingsTicks > 0) {
				tickWings(player);
			}
		}
		Strand off = strands[WebNet.OFF];
		if (off.attached && !WebPower.offHandFree(player)) {
			// something was put in the off hand: that web lets go
			detach(player, off, false);
		}
		if (attached()) {
			tickSwing(player);
		} else if (flying && wingsTicks == 0) {
			tickFlight(player);
		}
		tickApexHang(player);
		for (Strand strand : strands) {
			if (!strand.attached && strand.keyHeld && !player.onGround() && --strand.retryIn <= 0) {
				strand.retryIn = RETRY_TICKS;
				tryAttach(player, strand);
			}
		}
	}

	private void tickSwing(LocalPlayer player) {
		if (player.isInWater() || player.isInLava()) {
			detachAll(player, false);
			return;
		}
		for (Strand strand : strands) {
			if (strand.attached && strand.travel > 0) {
				strand.travel--;
			}
			if (strand.attached && ++strand.swingTicks % 5 == 0 && !WebAnchors.nearSolid(player.level(), strand.anchor)) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
				play(player, SoundEvents.LEAD_BREAK, 0.6F, 1.6F);
			}
		}
		if (!attached()) {
			flying = !player.onGround();
			return;
		}
		Input input = player.input.keyPresses;
		if (bothAttached() && input.shift()) {
			swingJumpTicks = 0;
			tickSling(player, input);
			return;
		}
		if (input.jump()) {
			swingJumpTicks++;
		} else if (swingJumpTicks > 0) {
			boolean jumpOff = swingJumpTicks >= JUMP_OFF_MIN_HOLD && !player.onGround();
			swingJumpTicks = 0;
			if (jumpOff) {
				jumpOff(player);
				return;
			}
		}
		if (slinging) {
			endStrain();
			// let go of Sneak without firing: back to swinging
			slinging = false;
			slingDraw = 0.0F;
		}
		boolean air = !player.onGround();
		// a symbiote's webs pull harder
		double boost = com.jaymospowers.power.web.WebSymbiote.pullScale(player);
		Vec3 v = player.getDeltaMovement();
		if (air && !player.isFallFlying()) {
			v = new Vec3(v.x / VANILLA_AIR_DRAG, v.y / VANILLA_AIR_DRAG_Y, v.z / VANILLA_AIR_DRAG).scale(SWING_DRAG).add(0.0, -SWING_GRAVITY, 0.0);
		}
		Vec3 body = player.position().add(0.0, ROPE_HEIGHT, 0.0);
		double gravity = player.getGravity() + SWING_GRAVITY;
		if (Double.isNaN(swingEnergy)) {
			swingEnergy = 0.5 * v.lengthSqr() + gravity * body.y;
		}
		double before = v.lengthSqr();
		Vec3 pullDir = Vec3.ZERO;
		for (Strand strand : strands) {
			if (!strand.attached || strand.travel > 0) {
				continue;
			}
			if (input.jump()) {
				strand.rope = Math.max(ROPE_MIN, strand.rope - REEL_IN * boost);
			} else if (input.shift()) {
				strand.rope = Math.min(WebAnchors.SWING_RANGE * com.jaymospowers.power.web.WebSymbiote.swingScale(player), strand.rope + LET_OUT);
			}
			Vec3 to = strand.anchor.subtract(body);
			if (to.lengthSqr() > 1.0E-8) {
				pullDir = pullDir.add(to.normalize());
			}
		}
		// Jump: rocket along the arc (the swing's own direction, square to the webs)
		if (air && input.jump() && pullDir.lengthSqr() > 1.0E-6 && v.lengthSqr() > 0.04) {
			Vec3 dir = pullDir.normalize();
			Vec3 tangent = v.subtract(dir.scale(v.dot(dir)));
			if (tangent.lengthSqr() > 1.0E-4) {
				v = v.add(tangent.normalize().scale(JUMP_THRUST * boost));
			}
		}
		// pump: forward on the downswing adds speed along the arc (across the webs' combined pull)
		if (air && input.forward() && v.y < 0.0 && pullDir.lengthSqr() > 1.0E-6) {
			Vec3 dir = pullDir.normalize();
			Vec3 tangent = v.subtract(dir.scale(v.dot(dir)));
			if (tangent.lengthSqr() > 1.0E-4) {
				v = v.add(tangent.normalize().scale(PUMP * boost));
			}
		}
		// what Jump and the pump put in stays in the swing
		swingEnergy += 0.5 * (v.lengthSqr() - before);
		// each rope: no moving further away than its length, and a springy pull back when stretched. Two passes, so
		// two webs settle against each other instead of fighting
		for (int pass = 0; pass < 2; pass++) {
			for (Strand strand : strands) {
				if (!strand.attached || strand.travel > 0) {
					continue;
				}
				Vec3 toAnchor = strand.anchor.subtract(body);
				double distance = toAnchor.length();
				if (distance < 1.0E-4 || distance <= strand.rope) {
					continue;
				}
				Vec3 dir = toAnchor.scale(1.0 / distance);
				double inward = v.dot(dir);
				double want = Math.min((distance - strand.rope) * ROPE_SPRING * boost, ROPE_MAX_PULL * boost);
				if (inward < want) {
					v = v.add(dir.scale(want - inward));
				}
			}
		}
		v = pendulum(v, body, gravity, air);
		player.setDeltaMovement(cap(v));
		int ticks = Math.max(strands[0].swingTicks, strands[1].swingTicks);
		if (air && v.lengthSqr() > 1.2 * 1.2 && ticks % 12 == 0) {
			play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25F, 0.6F + (float)Math.min(0.6, v.length() * 0.2));
		}
	}

	/**
	 * On a taut rope the speed along the arc is whatever the swing's energy leaves after its height: fast through the
	 * bottom, stalling at the top. Slack (or on the ground), the energy is just measured again from the motion.
	 */
	private Vec3 pendulum(Vec3 v, Vec3 body, double gravity, boolean air) {
		Vec3 radial = Vec3.ZERO;
		boolean taut = false;
		for (Strand strand : strands) {
			if (!strand.attached || strand.travel > 0) {
				continue;
			}
			Vec3 toAnchor = strand.anchor.subtract(body);
			double distance = toAnchor.length();
			if (distance > 1.0E-4 && distance >= strand.rope - TAUT_SLACK) {
				taut = true;
				radial = radial.add(toAnchor.scale(1.0 / distance));
			}
		}
		if (!air || !taut || radial.lengthSqr() < 1.0E-6) {
			swingEnergy = 0.5 * v.lengthSqr() + gravity * body.y;
			return v;
		}
		radial = radial.normalize();
		// the rope's pull (and any spring back) stays as it is; only the speed along the arc is set from the energy
		Vec3 along = radial.scale(v.dot(radial));
		Vec3 tangent = v.subtract(along);
		double moving = Math.max(0.0, swingEnergy - gravity * body.y);
		swingEnergy = gravity * body.y + moving * PENDULUM_DRAG;
		// never more than the speed cap's worth
		swingEnergy = Math.min(swingEnergy, gravity * body.y + 0.5 * momentum * momentum);
		double speed = Math.sqrt(2.0 * Math.max(0.0, swingEnergy - gravity * body.y));
		if (tangent.lengthSqr() < 1.0E-6) {
			// dead still at the top of the arc: gravity starts it back down next tick
			return v;
		}
		return along.add(tangent.normalize().scale(speed));
	}

	/** No longer drawing into a wall: its cracks fade. */
	private void endStrain() {
		if (strainTicks > 0 || sentStrain > 0.0F) {
			strainTicks = 0;
			sentStrain = 0.0F;
			ClientPlayNetworking.send(new com.jaymospowers.power.web.WebStrain.Strain(0.0F, Vec3.ZERO));
		}
	}

	/** Braced between two webs: hold still; Jump draws back (storing energy), letting Jump go fires. */
	private void tickSling(LocalPlayer player, Input input) {
		Vec3 body = player.position().add(0.0, ROPE_HEIGHT, 0.0);
		Vec3 mid = strands[0].anchor.add(strands[1].anchor).scale(0.5);
		Vec3 toMid = mid.subtract(body);
		Vec3 forward = toMid.lengthSqr() > 1.0E-6 ? toMid.normalize() : player.getLookAngle();
		if (!slinging) {
			slinging = true;
			slingDraw = 0.0F;
			slingJumpHeld = false;
			braced = player.position();
			play(player, SoundEvents.LEAD_TIED, 0.6F, 1.3F);
		}
		boolean jump = input.jump();
		if (jump) {
			slingJumpHeld = true;
			float before = slingDraw;
			slingDraw = Math.min(1.0F, slingDraw + 1.0F / SLING_DRAW_TICKS);
			if ((int)(before * 8) != (int)(slingDraw * 8)) {
				// the webs creak tighter as the draw builds
				play(player, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.5F, 0.8F + slingDraw * 0.9F);
			}
		} else if (slingJumpHeld) {
			slingJumpHeld = false;
			if (slingDraw >= SLING_MIN_DRAW) {
				fireSling(player, forward);
				return;
			}
			slingDraw = 0.0F;
		}
		// held where you braced (no drawing back): the draw grinds into what's under and behind you instead
		Vec3 v = braced.subtract(player.position()).scale(0.4);
		Vec3 back = new Vec3(-forward.x, 0.0, -forward.z);
		if (jump && player.onGround()) {
			// the floor under you (and the wall behind, if there's one) cracks more and more, to all but breaking
			strainTicks++;
			boolean armored = com.jaymospowers.power.web.WebSymbiote.armored(player);
			int needed = armored ? STRAIN_TICKS_ARMOR : STRAIN_TICKS;
			float progress = Math.min(com.jaymospowers.power.web.WebStrain.ALMOST, strainTicks / (float)needed);
			if (armored && strainTicks >= SLING_ARMOR_BREAK_TICKS) {
				// Living Armor: it gives way, and you go as if you'd let go
				ClientPlayNetworking.send(new com.jaymospowers.power.web.WebStrain.Strain(1.0F, back));
				strainTicks = 0;
				sentStrain = 0.0F;
				WebShake.start(0.9F);
				slingJumpHeld = false;
				fireSling(player, forward);
				return;
			}
			if (strainTicks % 10 == 0) {
				WebShake.start(0.15F + 0.35F * progress);
			}
			if (progress - sentStrain >= 0.1F) {
				ClientPlayNetworking.send(new com.jaymospowers.power.web.WebStrain.Strain(progress, back));
				sentStrain = progress;
			}
		} else if (strainTicks > 0) {
			strainTicks = 0;
			sentStrain = 0.0F;
			ClientPlayNetworking.send(new com.jaymospowers.power.web.WebStrain.Strain(0.0F, back));
		}
		player.setDeltaMovement(v);
		if (slingDraw > 0.0F && player.tickCount % 3 == 0) {
			// tension shivers along the webs
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, rnd(player) * 0.05, 0.02, rnd(player) * 0.05);
		}
	}

	/** Jump let go on a web: let go of the webs and spring up, higher the faster you were swinging. */
	private void jumpOff(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		double lift = Math.min(JUMP_OFF_MAX, JUMP_OFF_BASE + v.length() * JUMP_OFF_PER_SPEED);
		for (Strand strand : strands) {
			if (strand.attached) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
			}
			// held web keys don't snag a new web straight away
			strand.keyHeld = false;
		}
		swingLockout = JUMP_OFF_LOCKOUT;
		flying = true;
		Vec3 out = new Vec3(v.x, Math.max(v.y, 0.0) + lift, v.z);
		flightCap = Math.max(momentum, out.length());
		player.setDeltaMovement(out);
		tricks.released(player);
		play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.3F + (float)Math.min(0.5, lift * 0.3));
	}

	private void fireSling(LocalPlayer player, Vec3 forward) {
		endStrain();
		Vec3 dir = forward.scale(1.0 - SLING_AIM).add(player.getLookAngle().scale(SLING_AIM)).normalize();
		double speed = SLING_MIN_SPEED + (SLING_MAX_SPEED - SLING_MIN_SPEED) * slingDraw;
		for (Strand strand : strands) {
			if (strand.attached) {
				strand.attached = false;
				ClientPlayNetworking.send(new WebNet.Swing(strand.hand, false, Vec3.ZERO));
			}
		}
		slinging = false;
		slingDraw = 0.0F;
		flying = true;
		flightCap = Math.max(MAX_SPEED, speed);
		// failsafe: held web keys don't re-fire, and no new web for a moment, so the launch can't be snagged
		for (Strand strand : strands) {
			strand.keyHeld = false;
		}
		swingLockout = SLING_LOCKOUT;
		player.setDeltaMovement(dir.scale(speed));
		play(player, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.7F);
		play(player, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.8F, 1.3F);
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		for (int i = 0; i < 12; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, -dir.x * 0.3 + rnd(player) * 0.2, -dir.y * 0.3 + rnd(player) * 0.2,
				-dir.z * 0.3 + rnd(player) * 0.2);
		}
	}

	// ===== the hang at the top of an arc ============================================================================
	/**
	 * Off the web and near the top of an arc (rising or falling slower than {@code HANG_SPEED}), most of gravity's pull
	 * is held off for up to {@code HANG_TICKS}: a moment's stall at the apex to line up the next swing.
	 */
	static final double HANG_SPEED = 0.12;
	static final double HANG_HOLD = 0.75;
	static final int HANG_TICKS = 8;
	private int hangTicks;

	private void tickApexHang(LocalPlayer player) {
		boolean air = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isFallFlying()
			&& !attached() && pull == null && wingsTicks == 0;
		if (!air) {
			hangTicks = 0;
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (Math.abs(v.y) < HANG_SPEED && hangTicks < HANG_TICKS) {
			hangTicks++;
			// hand back most of this tick's gravity
			double g = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
			player.setDeltaMovement(v.x, v.y + g * HANG_HOLD, v.z);
		}
	}

	// ===== the suit's charged jump ===================================================================================
	/** Ticks to a full charge; the leap from a tap to a full charge (up, and forward along the look). */
	static final int CHARGE_FULL = com.jaymospowers.power.web.WebLeap.CHARGE_FULL;
	static final double CHARGE_FORWARD_MAX = com.jaymospowers.power.web.WebLeap.CHARGE_FORWARD_MAX;
	/** Charge held (ticks); whether the server's been told it's held. */
	int chargeTicks;
	private boolean chargeSent;

	/** True while Jump is being held to charge a leap on the ground (the plain jump is held back; see the mixin). */
	public static boolean holdingCharge(LocalPlayer player) {
		// (not while a takeover has the legs: its steps are plain hops, its leaps its own)
		return com.jaymospowers.power.web.WebSymbiote.suitShown(player) && WebPowerClient.isActive(player)
			&& Minecraft.getInstance().options.keyJump.isDown() && !WebSymbioteClient.driving();
	}

	/**
	 * Wearing the suit: hold Jump to coil. The charge builds while it's held (up to {@link #CHARGE_FULL}), on the ground
	 * or in the air, and keeps for as long as it's held: landing doesn't spend it (and doesn't hurt). Let go on the
	 * ground to leap, as high and far as the charge; let go in the air and the charge is lost.
	 */
	private void tickChargeJump(LocalPlayer player) {
		if (WebSymbioteClient.driving()) {
			// a takeover has the legs (it coils its own leaps)
			if (chargeTicks > 0 || chargeSent) {
				endCharge();
			}
			return;
		}
		boolean jump = player.input.keyPresses.jump();
		boolean ground = player.onGround();
		if (jump && !attached()) {
			if (chargeTicks < CHARGE_FULL) {
				chargeTicks++;
				if (chargeTicks % 6 == 0) {
					play(player, SoundEvents.SLIME_SQUISH_SMALL, 0.4F, 0.6F + chargeTicks / (float)CHARGE_FULL);
				}
			}
			if (!chargeSent) {
				chargeSent = true;
				ClientPlayNetworking.send(new WebNet.ChargeHold(true));
			}
			return;
		}
		if (chargeTicks > 0 && ground && !jump) {
			float c = chargeTicks / (float)CHARGE_FULL;
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(CHARGE_FORWARD_MAX * c);
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x + flat.x, com.jaymospowers.power.web.WebLeap.speed(c), v.z + flat.z);
			play(player, SoundEvents.SLIME_JUMP, 1.0F, 0.6F);
			if (c > 0.5F) {
				play(player, SoundEvents.WIND_CHARGE_BURST.value(), 0.6F, 0.6F);
			}
		}
		endCharge();
	}

	private void endCharge() {
		chargeTicks = 0;
		if (chargeSent) {
			chargeSent = false;
			ClientPlayNetworking.send(new WebNet.ChargeHold(false));
		}
	}

	/**
	 * Holding Jump in mid-air (falling, no web out) spreads the wings. Spread, they stay until you land (or hit water),
	 * or you fold them: let go of Jump and tap it again.
	 */
	private void tickWingsInput(LocalPlayer player) {
		boolean jump = player.input.keyPresses.jump();
		if (wingsTicks > 0) {
			if (!jump) {
				wingsJumpReleased = true;
			} else if (wingsJumpReleased) {
				foldWings();
				play(player, SoundEvents.PHANTOM_FLAP, 0.6F, 0.8F);
			}
			return;
		}
		if (!jump) {
			wingsNeedRelease = false;
		}
		boolean air = !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isFallFlying();
		if (!jump || !air || attached() || wingsTicks > 0 || wingsNeedRelease) {
			jumpHeldAir = 0;
			return;
		}
		boolean fallenFarEnough = apexY - player.getY() > WINGS_MIN_DROP;
		if (++jumpHeldAir >= WINGS_HOLD_TICKS && fallenFarEnough) {
			wingsTicks = WebPower.WINGS_TICKS;
			wingsJumpReleased = false;
			jumpHeldAir = 0;
			flying = false;
			ClientPlayNetworking.send(new WebNet.Wings(true));
			play(player, SoundEvents.PHANTOM_FLAP, 0.8F, 1.5F);
			play(player, SoundEvents.FISHING_BOBBER_THROW, 0.7F, 1.2F);
		}
	}

	/** Gliding like an elytra: from how you actually moved last tick, vanilla's glide step, so walls and pitch play fair. */
	private void tickWings(LocalPlayer player) {
		// (wingsTicks only marks them open now: they never run out)
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			foldWings();
			return;
		}
		Vec3 m = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
		Vec3 look = player.getLookAngle();
		float lean = player.getXRot() * Mth.DEG_TO_RAD;
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double moveHorizontal = m.horizontalDistance();
		double lift = Mth.square(Math.cos(lean));
		Input input = player.input.keyPresses;
		boolean brake = input.backward() || input.shift();
		if (brake != braking) {
			braking = brake;
			ClientPlayNetworking.send(new WebNet.WingsBrake(brake));
			if (brake) {
				play(player, SoundEvents.PHANTOM_FLAP, 0.7F, 0.9F);
			}
		}
		if (!brake) {
			diving = false;
		}
		if (brake) {
			// flared: the wings stop flying you along and catch the air, bleeding the speed off and slowing the fall
			m = m.multiply(BRAKE_DRAG, 1.0, BRAKE_DRAG);
			if (!diving && m.horizontalDistance() < DIVE_AT) {
				diving = true;
				play(player, SoundEvents.PHANTOM_FLAP, 0.6F, 0.6F);
			}
			if (diving) {
				// stopped dead: tip over and drop, head-first
				m = new Vec3(m.x * 0.5, Math.max(-DIVE_MAX_SPEED, (m.y - WINGS_GRAVITY) * 0.98), m.z * 0.5);
			} else {
				m = m.add(0.0, -WINGS_GRAVITY, 0.0);
				if (m.y < -BRAKE_SINK) {
					m = new Vec3(m.x, Mth.lerp(BRAKE_CATCH, m.y, -BRAKE_SINK), m.z);
				}
			}
		} else {
			m = m.add(0.0, WINGS_GRAVITY * (-1.0 + lift * 0.75), 0.0);
			if (m.y < 0.0 && lookHorizontal > 0.0) {
				// falling turns into forward speed
				double convert = m.y * -0.1 * lift;
				m = m.add(look.x * convert / lookHorizontal, convert, look.z * convert / lookHorizontal);
			}
			if (lean < 0.0F && lookHorizontal > 0.0) {
				// pulling up turns speed into height
				double convert = moveHorizontal * -Mth.sin(lean) * 0.04;
				m = m.add(-look.x * convert / lookHorizontal, convert * 3.2, -look.z * convert / lookHorizontal);
			}
			if (lookHorizontal > 0.0) {
				m = m.add((look.x / lookHorizontal * moveHorizontal - m.x) * 0.1, 0.0, (look.z / lookHorizontal * moveHorizontal - m.z) * 0.1);
			}
			m = m.multiply(0.99, 0.98, 0.99);
		}
		if (diving) {
			if (m.length() > DIVE_MAX_SPEED) {
				m = m.normalize().scale(DIVE_MAX_SPEED);
			}
			player.setDeltaMovement(m);
			if (player.tickCount % 3 == 0) {
				play(player, SoundEvents.ELYTRA_FLYING, (float)Math.min(0.5, 0.1 - m.y * 0.15), 1.0F);
			}
			return;
		}
		if (m.length() > WINGS_MAX_SPEED) {
			m = m.normalize().scale(WINGS_MAX_SPEED);
		}
		player.setDeltaMovement(m);
		if (player.tickCount % 4 == 0) {
			play(player, SoundEvents.ELYTRA_FLYING, 0.08F, 1.6F);
		}
	}

	private void foldWings() {
		if (wingsTicks > 0) {
			wingsTicks = 0;
			braking = false;
			diving = false;
			// no cooldown, but let go of Jump and fall another couple of blocks before they open again
			wingsNeedRelease = true;
			apexY = player().getY();
			flying = true;
			ClientPlayNetworking.send(new WebNet.Wings(false));
		}
	}

	private void tickFlight(LocalPlayer player) {
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.onClimbable()) {
			flying = false;
			return;
		}
		Vec3 v = player.getDeltaMovement();
		Vec3 next = new Vec3(v.x / VANILLA_AIR_DRAG * FLIGHT_DRAG, v.y, v.z / VANILLA_AIR_DRAG * FLIGHT_DRAG);
		double speed = next.length();
		player.setDeltaMovement(speed > flightCap ? next.scale(flightCap / speed) : next);
		// a slingshot's extra speed bleeds back down to the normal cap
		flightCap = Math.max(momentum, flightCap * 0.99);
	}

	private void tickPull(LocalPlayer player) {
		WebNet.Pull pull = this.pull;
		pullTicks++;
		Vec3 dest = pull.target();
		double arrive = 0.9;
		if (pull.entityId() >= 0) {
			Entity entity = player.level().getEntity(pull.entityId());
			if (entity == null || !entity.isAlive()) {
				finishPull(player, pull);
				return;
			}
			dest = entity.position();
			arrive = entity.getBbWidth() * 0.5 + 1.2;
		}
		Vec3 to = dest.subtract(player.position());
		double distance = to.length();
		if (player.position().distanceToSqr(lastPos) < 0.05 * 0.05 && pullTicks > 3) {
			stuckTicks++;
		} else {
			stuckTicks = 0;
		}
		lastPos = player.position();
		// gliding, a block zip is a boost: when its time is up (WebPower.GLIDE_ZIP_TICKS) it lets go with the kick up, wings out
		boolean flingOut = pull.entityId() < 0 && wingsTicks > 0 && pullTicks > pull.maxTicks() && !player.isInWater();
		if (!flingOut && (distance < arrive || pullTicks > pull.maxTicks() || stuckTicks >= 3 || player.isInWater())) {
			finishPull(player, pull);
			return;
		}
		if (flingOut || pull.entityId() < 0 && distance < ZIP_RELEASE_DISTANCE && pullTicks > 2 && !player.isShiftKeyDown()) {
			// let go early: momentum carries on, with a kick up
			this.pull = null;
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.max(v.y, 0.0) + ZIP_RELEASE_LIFT, v.z);
			play(player, SoundEvents.SLIME_SQUISH_SMALL, 0.5F, 1.8F);
			return;
		}
		double speed = Math.min(WebPower.ZIP_SPEED, 0.35 + distance * 0.45);
		Vec3 v = player.getDeltaMovement().lerp(to.scale(speed / distance), ZIP_STEER);
		player.setDeltaMovement(v);
	}

	private void finishPull(LocalPlayer player, WebNet.Pull pull) {
		this.pull = null;
		Direction face = pull.face() >= 0 && pull.face() < Direction.values().length ? Direction.values()[pull.face()] : null;
		Vec3 v = player.getDeltaMovement();
		if (face != null && face.getAxis().isHorizontal()) {
			// perch: hop up and onto the ledge above the hit point, if there's one within reach
			BlockPos wall = BlockPos.containing(pull.anchor().subtract(face.getUnitVec3().scale(0.05)));
			double hop = 0.42;
			for (int k = 1; k <= 3; k++) {
				BlockPos top = wall.above(k);
				if (free(player, top) && free(player, top.above())) {
					double height = top.getY() - player.getY() + 0.2;
					hop = Math.max(0.42, Math.min(0.95, Math.sqrt(2.0 * 0.08 * Math.max(0.0, height)) + 0.08));
					break;
				}
			}
			Vec3 into = face.getUnitVec3().scale(-PERCH_FORWARD);
			player.setDeltaMovement(into.x, hop, into.z);
		} else if (face == Direction.UP) {
			player.setDeltaMovement(v.x * 0.3, 0.25, v.z * 0.3);
		} else {
			player.setDeltaMovement(v.scale(0.4).add(0.0, 0.2, 0.0));
		}
		play(player, SoundEvents.SLIME_SQUISH_SMALL, 0.5F, 1.5F);
	}

	private static boolean free(LocalPlayer player, BlockPos pos) {
		return player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty();
	}

	private Vec3 cap(Vec3 v) {
		double speed = v.length();
		return speed > momentum ? v.scale(momentum / speed) : v;
	}

	private static double rnd(LocalPlayer player) {
		return player.getRandom().nextDouble() - 0.5;
	}

	static void play(LocalPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	private static void puff(LocalPlayer player, Vec3 at, int count) {
		for (int i = 0; i < count; i++) {
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, rnd(player) * 0.05, rnd(player) * 0.05, rnd(player) * 0.05);
		}
	}

	/** Where the local player's own web from {@code hand} currently ends, or null. Zips and yanks come from the main hand. */
	@Nullable Vec3 lineEnd(int hand, float partialTick) {
		if (strands[hand].attached) {
			return strands[hand].anchor;
		}
		WebNet.Pull pull = this.pull;
		if (pull != null && hand == WebNet.MAIN) {
			if (pull.entityId() >= 0) {
				Minecraft minecraft = Minecraft.getInstance();
				Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(pull.entityId());
				if (entity != null) {
					return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
				}
			}
			return pull.anchor();
		}
		return null;
	}
}
