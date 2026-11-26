package com.jaymospowers.client.power.web;

import com.jaymospowers.freerun.moves.ChargeJump;
import com.jaymospowers.power.web.WebBridges;
import com.jaymospowers.power.web.WebLineRide;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Riding a webline, and bending it to fling off. Andy's picks from Legends: Superheroes. {@code ws_ride}: "sneak and use
 * on a webline to hang from it or stand on it; forward and back slide you along it, turn onto a crossing line where two
 * meet, and sneak again drops you". {@code ws_springline}: "standing on a webline, crouch to bend it down, then let go to be
 * flung where you look; a twang tells you it's at full bend", with the charge bar the slingshot uses.
 *
 * <p>Sneak and the use key on a webline ({@link WebBridges}) in reach starts a ride: standing on it if you are at or above
 * its height, hanging under it by the hands if you are below it (the line carries you to it). Forward slides you along it
 * the way you are looking, back the other way; at a place where another line crosses, A or D turns you onto it. Standing:
 * a tap of Sneak lets go, but hold it and the line bends under you, the jump bar filling as it does ({@link #draw}); a
 * twang sounds at full bend, and letting go flings you the way you look, faster the nearer the bar's orange peak you let
 * go. Hanging: Sneak lets go and you fall. Jump hops off either. The server is told ({@link WebLineRide}) so everyone
 * sees the pose; the sliding is the slinger's own client's, as the swing's is.
 */
final class WebLineRideClient {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** The line you aim at must pass within this far (blocks) of the crosshair's ray, out to this reach. */
	static final double AIM_TOLERANCE = 1.1, REACH = 7.0;
	/** Sliding speed along the line (blocks/tick), and the most the body is carried toward its place on the line each tick. */
	static final double SLIDE_SPEED = 0.22, CATCH = 0.8;
	/** Hanging, the feet hang this far (blocks) under the line (the hands reach up to it); standing, they sit this far above it. */
	static final double HANG_DROP = 1.75, STAND_RISE = 0.07;
	/** Another line this near (blocks) is a crossing, where A or D turns you onto it. */
	static final double CROSSING = 0.9;
	/** Sneak held this many ticks starts to bend the line (a shorter hold is a tap, which lets go). */
	static final int BEND_AFTER = 6;
	/** Ticks of bending to a full bend, and how deep (blocks) the line is drawn bent at a full one. */
	static final int DRAW_TICKS = 24;
	static final double BEND_DEPTH = 1.4;
	/** The least bend that flings, and the fling's speed (blocks/tick) at none and at a full bend let go in the orange peak. */
	static final float MIN_DRAW = 0.12F;
	static final double FLING_MIN = 1.0, FLING_MAX = 3.0;
	/** Jumping off: up, and along the line's way. */
	static final double HOP_UP = 0.55, HOP_ALONG = 0.3;
	// ================================================================================================================

	private WebBridges.@Nullable Span span;
	private double t;
	private int mode;
	/** Which way along the line (+1 toward its end, -1 toward its start) forward goes. */
	private int facing = 1;
	private boolean useWas, sneakFree, sneakWas, leftWas, rightWas;
	/** How long Sneak has been held while standing (ticks, 0 not held), and how far the bend is drawn (0-1). */
	private int sneakTicks;
	private float draw, peak;
	private boolean twanged;
	private int ticks;

	void reset() {
		if (span != null) {
			stop();
		}
		useWas = false;
	}

	boolean active() {
		return span != null;
	}

	/** Standing on a line with Sneak held long enough that it is bending (the jump bar shows its draw). */
	boolean bending() {
		return span != null && draw > 0.0F;
	}

	float draw() {
		return draw;
	}

	float peak() {
		return peak;
	}

	/** Where {@code line} is bent to this frame (the point on it pushed down by the bend), or null when it isn't the one being bent. */
	@Nullable Vec3 dip(WebBridges.Span line) {
		if (span == null || draw <= 0.0F || !span.equals(line)) {
			return null;
		}
		Vec3 a = span.from(), b = span.to();
		double len = a.distanceTo(b);
		Vec3 at = a.lerp(b, len < 1.0E-6 ? 0.0 : Mth.clamp(t / len, 0.0, 1.0));
		return at.add(0.0, -draw * BEND_DEPTH, 0.0);
	}

	void tick(Minecraft minecraft, LocalPlayer player, WebSwing swing) {
		Input input = player.input.keyPresses;
		boolean use = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
		boolean usePressed = use && !useWas;
		useWas = use;
		boolean free = player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()
			&& !player.isInWater() && !player.isInLava() && !swing.attached() && swing.pull == null && swing.wingsTicks == 0
			&& !WebSymbioteClient.driving() && !com.jaymospowers.power.web.WebPower.ragdolled(player);
		if (span == null) {
			if (usePressed && input.shift() && free) {
				start(player);
			}
			return;
		}
		List<WebBridges.Span> spans = player.level().getAttached(WebBridges.SPANS);
		if (!free || spans == null || !spans.contains(span)) {
			// the line frayed or was cut, or something else took you
			stop();
			return;
		}
		ticks++;
		boolean sneak = input.shift();
		boolean sneakPressed = sneak && !sneakWas;
		boolean sneakReleased = !sneak && sneakWas;
		sneakWas = sneak;
		// (the Sneak that started the ride must be let go of first: only a press after that is "sneak again")
		boolean armed = sneakFree;
		if (!sneak) {
			sneakFree = true;
		}
		if (input.jump() && ticks > 2) {
			hop(player);
			return;
		}
		Vec3 a = span.from(), b = span.to();
		double len = a.distanceTo(b);
		Vec3 dir = len < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : b.subtract(a).scale(1.0 / len);
		if (mode == WebLineRide.HANG) {
			if (armed && sneakPressed) {
				// sneak again: let go
				stop();
				return;
			}
		} else if (armed) {
			if (sneak) {
				sneakTicks++;
				if (sneakTicks >= BEND_AFTER) {
					tickBend(player);
				}
			}
			if (sneakReleased) {
				boolean flung = draw >= MIN_DRAW;
				int held = sneakTicks;
				sneakTicks = 0;
				if (flung) {
					fling(player);
					return;
				}
				draw = 0.0F;
				twanged = false;
				if (held < BEND_AFTER) {
					// a tap: let go
					stop();
					return;
				}
			}
		}
		// sliding: forward goes the way you are looking along the line, back the other way
		double move = (input.forward() ? 1.0 : 0.0) - (input.backward() ? 1.0 : 0.0);
		if (move != 0.0 && draw <= 0.0F) {
			Vec3 look = player.getLookAngle();
			double along = look.x * dir.x + look.z * dir.z;
			double flat = Math.hypot(dir.x, dir.z);
			if (flat < 0.15) {
				// a line nearly straight up or down: forward is up it
				facing = dir.y >= 0.0 ? 1 : -1;
			} else if (Math.abs(along) > 0.5 * flat * Math.hypot(look.x, look.z)) {
				facing = along >= 0.0 ? 1 : -1;
			}
			t = Mth.clamp(t + move * facing * SLIDE_SPEED, 0.0, len);
			if (ticks % 4 == 0) {
				WebSwing.play(player, SoundEvents.WOOL_STEP, 0.25F, 1.6F);
			}
		}
		Vec3 at = a.add(dir.scale(t));
		// a crossing: another line through this one, where A or D turns you onto it
		BestCross cross = crossing(spans, at);
		boolean left = input.left() && !leftWas, right = input.right() && !rightWas;
		leftWas = input.left();
		rightWas = input.right();
		if (cross != null) {
			if (ticks % 6 == 0) {
				player.level().addParticle(ParticleTypes.ITEM_COBWEB, cross.point.x, cross.point.y, cross.point.z, 0.0, 0.03, 0.0);
			}
			if (left != right && draw <= 0.0F) {
				turnOnto(player, cross, dir, left);
				return;
			}
		}
		Vec3 target = mode == WebLineRide.HANG ? at.add(0.0, -HANG_DROP, 0.0) : at.add(0.0, STAND_RISE, 0.0);
		Vec3 carry = target.subtract(player.position());
		if (carry.length() > CATCH) {
			carry = carry.normalize().scale(CATCH);
		}
		player.setDeltaMovement(carry);
		player.resetFallDistance();
	}

	/** The line aimed at (or the nearest to the crosshair, in reach), if any: you are on it now. */
	private void start(LocalPlayer player) {
		List<WebBridges.Span> spans = player.level().getAttached(WebBridges.SPANS);
		if (spans == null) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		WebBridges.Span best = null;
		double bestT = 0.0, bestMiss = AIM_TOLERANCE;
		for (WebBridges.Span candidate : spans) {
			double[] hit = nearestToRay(eye, look, REACH, candidate.from(), candidate.to());
			if (hit[2] < bestMiss) {
				bestMiss = hit[2];
				best = candidate;
				bestT = hit[1] * candidate.from().distanceTo(candidate.to());
			}
		}
		if (best == null) {
			return;
		}
		span = best;
		t = bestT;
		Vec3 point = best.from().lerp(best.to(), best.from().distanceTo(best.to()) < 1.0E-6 ? 0.0 : t / best.from().distanceTo(best.to()));
		mode = player.getY() >= point.y - 0.6 ? WebLineRide.STAND : WebLineRide.HANG;
		facing = 1;
		sneakFree = false;
		sneakWas = true;
		sneakTicks = 0;
		draw = 0.0F;
		twanged = false;
		ticks = 0;
		leftWas = player.input.keyPresses.left();
		rightWas = player.input.keyPresses.right();
		ClientPlayNetworking.send(new WebLineRide.Ride(mode));
		WebSwing.play(player, SoundEvents.LEAD_TIED, 0.7F, 1.5F);
		player.level().addParticle(ParticleTypes.ITEM_COBWEB, point.x, point.y, point.z, 0.0, 0.02, 0.0);
	}

	/** The line is let go of (fell off it, tapped Sneak, frayed away, or something else took you). */
	private void stop() {
		if (span != null && ClientPlayNetworking.canSend(WebLineRide.Ride.TYPE)) {
			ClientPlayNetworking.send(new WebLineRide.Ride(0));
		}
		span = null;
		mode = 0;
		draw = 0.0F;
		sneakTicks = 0;
		twanged = false;
	}

	/** Jump: hop off the line, up and on along the way you were sliding. */
	private void hop(LocalPlayer player) {
		Vec3 a = span.from(), b = span.to();
		Vec3 dir = b.subtract(a).normalize().scale(facing);
		stop();
		player.setDeltaMovement(dir.x * HOP_ALONG, HOP_UP, dir.z * HOP_ALONG);
		WebSwing.play(player, SoundEvents.SLIME_JUMP, 0.5F, 1.5F);
	}

	/** Sneak held on a line you stand on: the bend builds, creaking, and twangs once at full. */
	private void tickBend(LocalPlayer player) {
		if (draw <= 0.0F) {
			peak = ChargeJump.peak(player, player.getRandom());
			WebSwing.play(player, SoundEvents.LEAD_TIED, 0.6F, 0.8F);
		}
		float before = draw;
		draw = Math.min(1.0F, (sneakTicks - BEND_AFTER + 1) / (float)DRAW_TICKS);
		if ((int)(before * 8) != (int)(draw * 8)) {
			WebSwing.play(player, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.5F, 0.7F + draw * 0.9F);
		}
		if (draw >= 1.0F && !twanged) {
			twanged = true;
			// the twang: the line is at full bend
			WebSwing.play(player, SoundEvents.NOTE_BLOCK_BANJO.value(), 0.9F, 0.6F);
			WebSwing.play(player, SoundEvents.TRIPWIRE_CLICK_ON, 0.6F, 1.8F);
		}
		if (player.tickCount % 3 == 0) {
			Vec3 at = player.position().add(0.0, 0.1, 0.0);
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, (player.getRandom().nextDouble() - 0.5) * 0.05, 0.02,
				(player.getRandom().nextDouble() - 0.5) * 0.05);
		}
	}

	/** Let go of a bent line: flung the way you look, faster the nearer the bar's orange peak you let go. */
	private void fling(LocalPlayer player) {
		double speed = FLING_MIN + (FLING_MAX - FLING_MIN) * ChargeJump.share(draw, peak);
		Vec3 look = player.getLookAngle();
		Vec3 dir = look.y < 0.15 ? new Vec3(look.x, 0.15, look.z).normalize() : look;
		stop();
		swingFling(player, dir.scale(speed));
		WebSwing.play(player, SoundEvents.CROSSBOW_SHOOT, 1.0F, 0.8F);
		WebSwing.play(player, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.7F, 1.4F);
		Vec3 at = player.position();
		for (int i = 0; i < 10; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, -dir.x * 0.25 + (player.getRandom().nextDouble() - 0.5) * 0.2,
				0.05, -dir.z * 0.25 + (player.getRandom().nextDouble() - 0.5) * 0.2);
		}
	}

	private static void swingFling(LocalPlayer player, Vec3 velocity) {
		WebPowerClient.INSTANCE.swing.flung(player, velocity);
	}

	private record BestCross(WebBridges.Span other, double along, Vec3 point) {
	}

	/** The other line (not the one you are on) that passes within {@link #CROSSING} of {@code at}, the nearest, if any. */
	private @Nullable BestCross crossing(List<WebBridges.Span> spans, Vec3 at) {
		BestCross best = null;
		double bestDistance = CROSSING;
		for (WebBridges.Span other : spans) {
			if (other.equals(span)) {
				continue;
			}
			Vec3 a = other.from(), b = other.to();
			Vec3 w = b.subtract(a);
			double len2 = w.lengthSqr();
			if (len2 < 1.0E-6) {
				continue;
			}
			double u = Mth.clamp(at.subtract(a).dot(w) / len2, 0.0, 1.0);
			Vec3 point = a.add(w.scale(u));
			double distance = point.distanceTo(at);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = new BestCross(other, u * Math.sqrt(len2), point);
			}
		}
		return best;
	}

	/** A or D at a crossing: onto the other line, heading the way that turns toward the key pressed. */
	private void turnOnto(LocalPlayer player, BestCross cross, Vec3 dir, boolean left) {
		Vec3 heading = dir.scale(facing);
		double flat = Math.hypot(heading.x, heading.z);
		Vec3 here = flat < 0.2 ? player.getLookAngle() : heading;
		// the right of heading (x, z) is (-z, x); the left is (z, -x)
		Vec3 want = left ? new Vec3(here.z, 0.0, -here.x) : new Vec3(-here.z, 0.0, here.x);
		Vec3 other = cross.other.to().subtract(cross.other.from()).normalize();
		span = cross.other;
		t = cross.along;
		facing = other.x * want.x + other.z * want.z >= 0.0 ? 1 : -1;
		WebSwing.play(player, SoundEvents.LEAD_TIED, 0.5F, 1.8F);
		player.level().addParticle(ParticleTypes.ITEM_COBWEB, cross.point.x, cross.point.y, cross.point.z, 0.0, 0.03, 0.0);
	}

	/**
	 * Where along the ray {@code from} + s {@code look} (s up to {@code reach}) it passes nearest the segment
	 * {@code a}-{@code b}: {@code {s, t, miss}}, t as a share (0-1) along the segment, miss the gap between the two points.
	 */
	static double[] nearestToRay(Vec3 from, Vec3 look, double reach, Vec3 a, Vec3 b) {
		Vec3 w = b.subtract(a);
		double c = w.lengthSqr();
		if (c < 1.0E-9) {
			double s = Mth.clamp(a.subtract(from).dot(look), 0.0, reach);
			return new double[] {s, 0.0, from.add(look.scale(s)).distanceTo(a)};
		}
		Vec3 r = from.subtract(a);
		double bb = look.dot(w), d = look.dot(r), e = w.dot(r);
		double s = 0.0, tt = Mth.clamp(e / c, 0.0, 1.0);
		double den = c - bb * bb;
		if (den > 1.0E-9) {
			s = Mth.clamp((bb * e - c * d) / den, 0.0, reach);
		}
		for (int i = 0; i < 2; i++) {
			tt = Mth.clamp((e + s * bb) / c, 0.0, 1.0);
			s = Mth.clamp(tt * bb - d, 0.0, reach);
		}
		Vec3 onRay = from.add(look.scale(s));
		Vec3 onLine = a.add(w.scale(tt));
		return new double[] {s, tt, onRay.distanceTo(onLine)};
	}
}
