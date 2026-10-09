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
final class WebSwing {
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
	static final double PUMP = 0.03;
	/** Holding Jump on a web also thrusts you along the swing this much per tick (on top of reeling in): it rockets
	 * you round the arc, and a release flings you out of it with the extra speed. */
	static final double JUMP_THRUST = 0.07;
	/** Jump reels in, sneak lets out (blocks per tick), every web that's out. */
	static final double REEL_IN = 0.45;
	static final double LET_OUT = 0.35;
	/** Speed cap while swinging or flying from a swing (blocks/tick; sprinting is ~0.28). */
	static final double MAX_SPEED = 2.6;
	/** Release timing: letting go while travelling this many degrees above level is perfect; the window either side. */
	static final double RELEASE_SWEET_ANGLE = 35.0;
	static final double RELEASE_WINDOW = 35.0;
	/** The boost a perfect release gives (scaled down linearly to 0 at the window's edges). */
	static final double RELEASE_FORWARD = 0.35;
	static final double RELEASE_UP = 0.5;
	/** Horizontal velocity kept per tick in the air after a release, until you land (vanilla air is 0.91). */
	static final double FLIGHT_DRAG = 0.985;
	/** Holding a swing key in the air with nothing to stick to retries every this many ticks. */
	static final int RETRY_TICKS = 3;
	// ===== slingshot (two webs out: hold Sneak to brace, hold Jump to draw back, let Jump go to fire) ================
	/** Ticks of Jump to a full draw, and how far the full draw pulls you back from where you braced. */
	static final int SLING_DRAW_TICKS = 30;
	static final double SLING_PULLBACK = 2.5;
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
	/** Glide: steady sink rate, extra sink per unit of looking down, and how fast the path swings to the look. */
	static final double WINGS_SINK = 0.07;
	static final double WINGS_DIVE = 0.4;
	static final double WINGS_STEER = 0.12;
	/** Glide speed: the slowest it settles to, the fastest it builds to, speed gained per block/tick of sink. */
	static final double WINGS_MIN_SPEED = 0.45;
	static final double WINGS_MAX_SPEED = 1.8;
	static final double WINGS_DIVE_GAIN = 0.05;
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

		Strand(int hand) {
			this.hand = hand;
		}
	}

	final Strand[] strands = {new Strand(WebNet.MAIN), new Strand(WebNet.OFF)};
	// after a release
	boolean flying;
	/** Speed cap for the current free flight (a slingshot launch may exceed the swing cap). */
	private double flightCap = MAX_SPEED;
	// slingshot
	boolean slinging;
	float slingDraw;
	private Vec3 braced = Vec3.ZERO;
	private boolean slingJumpHeld;
	private int swingLockout;
	// wings
	int wingsTicks;
	private int jumpHeldAir;
	private boolean wingsNeedRelease;
	/** The highest point since you last stood, rose, or folded the wings: falls for the wings count from here. */
	private double apexY;
	// zip / yank-to
	WebNet.@Nullable Pull pull;
	private int pullTicks;
	private int stuckTicks;
	private Vec3 lastPos = Vec3.ZERO;

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
		wingsNeedRelease = false;
		swingLockout = 0;
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
		strand.attached = true;
		flying = false;
		foldWings();
		strand.anchor = found.pos();
		strand.swingTicks = 0;
		double distance = strand.anchor.distanceTo(player.position().add(0.0, ROPE_HEIGHT, 0.0));
		strand.rope = Math.max(ROPE_MIN, distance * ATTACH_SLACK);
		ClientPlayNetworking.send(new WebNet.Swing(strand.hand, true, strand.anchor));
		play(player, SoundEvents.FISHING_BOBBER_THROW, 0.9F, 1.7F + player.getRandom().nextFloat() * 0.2F);
		puff(player, strand.anchor, 5);
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
		flightCap = MAX_SPEED;
		if (!timed || player.onGround()) {
			return;
		}
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
		// the highest point of the current fall (resets on the ground, while rising, on a web or in water)
		if (player.onGround() || player.getDeltaMovement().y >= 0.0 || attached() || pull != null || player.isInWater() || player.onClimbable()) {
			apexY = player.getY();
		}
		if (pull != null) {
			foldWings();
			tickPull(player);
			return;
		}
		tickWingsInput(player);
		if (wingsTicks > 0) {
			tickWings(player);
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
			tickSling(player, input);
			return;
		}
		if (slinging) {
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
		Vec3 pullDir = Vec3.ZERO;
		for (Strand strand : strands) {
			if (!strand.attached) {
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
		// each rope: no moving further away than its length, and a springy pull back when stretched. Two passes, so
		// two webs settle against each other instead of fighting
		for (int pass = 0; pass < 2; pass++) {
			for (Strand strand : strands) {
				if (!strand.attached) {
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
		player.setDeltaMovement(cap(v));
		int ticks = Math.max(strands[0].swingTicks, strands[1].swingTicks);
		if (air && v.lengthSqr() > 1.2 * 1.2 && ticks % 12 == 0) {
			play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.25F, 0.6F + (float)Math.min(0.6, v.length() * 0.2));
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
		// held in place, drawn back away from the webs by the draw
		Vec3 target = braced.subtract(forward.scale(SLING_PULLBACK * slingDraw));
		Vec3 v = target.subtract(player.position()).scale(0.4);
		player.setDeltaMovement(v);
		if (slingDraw > 0.0F && player.tickCount % 3 == 0) {
			// tension shivers along the webs
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, rnd(player) * 0.05, 0.02, rnd(player) * 0.05);
		}
	}

	private void fireSling(LocalPlayer player, Vec3 forward) {
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

	/** Holding Jump in mid-air (falling, no web out) spreads the wings, if they're off cooldown. */
	private void tickWingsInput(LocalPlayer player) {
		boolean jump = player.input.keyPresses.jump();
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
			jumpHeldAir = 0;
			flying = false;
			ClientPlayNetworking.send(new WebNet.Wings(true));
			play(player, SoundEvents.PHANTOM_FLAP, 0.8F, 1.5F);
			play(player, SoundEvents.FISHING_BOBBER_THROW, 0.7F, 1.2F);
		}
	}

	/** Gliding: a steady sink, diving trades height for speed, the path follows the look. */
	private void tickWings(LocalPlayer player) {
		if (--wingsTicks <= 0 || player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			foldWings();
			return;
		}
		Vec3 v = player.getDeltaMovement();
		Vec3 look = player.getLookAngle();
		double h = Math.sqrt(v.x * v.x + v.z * v.z) / VANILLA_AIR_DRAG;
		double vy = v.y;
		double sink = -WINGS_SINK + Math.min(0.0, look.y) * WINGS_DIVE;
		if (vy < sink) {
			vy = Mth.lerp(0.35, vy, sink);
		} else {
			vy = Math.max(sink, vy - 0.04);
		}
		h = Mth.clamp((h + Math.max(0.0, -vy) * WINGS_DIVE_GAIN) * 0.995, WINGS_MIN_SPEED, WINGS_MAX_SPEED);
		Vec3 heading = look.horizontal().lengthSqr() > 1.0E-4 ? look.horizontal().normalize() : new Vec3(v.x, 0.0, v.z).normalize();
		Vec3 current = v.horizontal().lengthSqr() > 1.0E-6 ? v.horizontal().normalize() : heading;
		Vec3 dir = current.lerp(heading, WINGS_STEER).normalize();
		player.setDeltaMovement(dir.x * h, vy, dir.z * h);
		if (wingsTicks % 4 == 0) {
			play(player, SoundEvents.ELYTRA_FLYING, 0.08F, 1.6F);
		}
	}

	private void foldWings() {
		if (wingsTicks > 0) {
			wingsTicks = 0;
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
		flightCap = Math.max(MAX_SPEED, flightCap * 0.99);
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
		if (distance < arrive || pullTicks > pull.maxTicks() || stuckTicks >= 3 || player.isInWater()) {
			finishPull(player, pull);
			return;
		}
		if (pull.entityId() < 0 && distance < ZIP_RELEASE_DISTANCE && pullTicks > 2 && !player.isShiftKeyDown()) {
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

	private static Vec3 cap(Vec3 v) {
		double speed = v.length();
		return speed > MAX_SPEED ? v.scale(MAX_SPEED / speed) : v;
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
