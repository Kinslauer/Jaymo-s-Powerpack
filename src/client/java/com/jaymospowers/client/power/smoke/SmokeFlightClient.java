package com.jaymospowers.client.power.smoke;

import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.client.sound.Accents;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.power.smoke.SmokeVapour;
import com.jaymospowers.power.wind.WindFlight;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vapour Form's flight, run on the client like Wind's (player motion is client-authoritative): its own velocity, steered
 * toward what the controls ask, handed to the player every tick. The server says when the form starts and ends
 * ({@link SmokeVapour#FORM}); the take-off is the moment it starts.
 * <ul>
 * <li>Take-off: Vapour Form switched on, a sprint starts it, straight into a cruise.</li>
 * <li>Cruise: along your look, picking up speed to the top and holding it with or without Sprint (a dive adds more, which
 * eases off again); S brakes, and slowed right down you hover. Turning is slower the faster you go.</li>
 * <li>Hover: WASD along your look (look down to come down), Jump rises; Sprint cruises again.</li>
 * <li>Shift, fully mastered: the whole body to smoke, or back (Shift no longer sinks).</li>
 * </ul>
 */
public final class SmokeFlightClient {
	// ---- tuning (blocks per tick) ----
	static final double HOVER_SPEED = 0.5;
	static final double HOVER_VERTICAL = 0.42;
	static final double HOVER_RESPONSE = 0.16;
	static final double HOVER_BOB = 0.012;
	/** The take-off: thrown up this fast on a column of smoke. */
	static final double TAKEOFF_BOOST = 0.8;
	/** A sprinting take-off goes straight into a cruise, lifted at least this steeply off the ground (look's y). */
	static final double SPRINT_TAKEOFF_LIFT = 0.2;
	/** Cruise: speed on entering, acceleration toward the top (tapering), easing back after a dive, and S's brake. */
	static final double CRUISE_ENTER_SPEED = 0.8;
	static final double CRUISE_ACCEL = 0.06;
	static final double CRUISE_ACCEL_TAPER = 0.6;
	static final double CRUISE_DECEL = 0.012;
	static final double CRUISE_BRAKE = 0.14;
	/** Top cruise speed, and a fresh awakening's (only Vapour Form unlocked). */
	static final double TOP_SPEED = 2.2;
	static final double STARTER_TOP_SPEED = 1.3;
	/** Below this a coasting cruise settles back into a hover. */
	static final double CRUISE_EXIT_SPEED = 0.35;
	/** Turning rate (degrees a tick), from just-cruising to top speed. */
	static final double TURN_RATE_SLOW = 7.5;
	static final double TURN_RATE_FAST = 0.9;
	/** Pointing down picks up speed (blocks/tick a tick straight down), up to this share over the top speed. */
	static final double DIVE_GRAVITY = 0.07;
	static final double DIVE_OVERSPEED = 1.25;

	/** Breaking into a cruise: a burst of air, now and then a crackle of embers. */
	static final Soundscape.Layer LAUNCH = Soundscape.Layer.of(0.35F, 0.5F, 0.7F, 0.95F, SoundEvents.BREEZE_SHOOT, SoundEvents.BREEZE_JUMP);

	private static boolean flying;
	private static boolean cruising;
	/** Ascended (Cloudshaper): top speed in the rain. */
	static final double CLOUD_RAIN_SPEED = 1.15;
	/** Whether the server was last told this flier is wholly smoke. */
	private static boolean wholeSent;
	private static boolean formWas;
	/** Shift last tick, and whether the flier has asked (Shift) to be wholly smoke. */
	private static boolean shiftWas;
	private static boolean wholeWanted;
	private static Vec3 velocity = Vec3.ZERO;
	private static double speed;
	private static long ticks;
	private static @Nullable LocalPlayer owner;

	private SmokeFlightClient() {
	}

	/** Whether the local player is flying in Vapour Form right now. */
	public static boolean flying() {
		return flying;
	}

	/** Whether the local player is cruising (laid out flat in Wind's pose). */
	public static boolean cruising() {
		return flying && cruising;
	}

	/** 0..1 of the top speed while cruising, for the HUD. */
	public static double speedFraction(LocalPlayer player) {
		return cruising ? Mth.clamp(speed / top(player), 0.0, 1.0) : 0.0;
	}

	/** The flight's velocity this tick (blocks a tick), for the sound. */
	static Vec3 velocity() {
		return velocity;
	}

	/** Speed over the top speed (past 1 in a dive), cruising or not, for the sound. */
	static double speedOverTop(LocalPlayer player) {
		return flying ? velocity.length() / top(player) : 0.0;
	}

	/** Whether the flier asked to be wholly smoke. */
	static boolean wholly() {
		return flying && wholeWanted;
	}

	private static double top(LocalPlayer player) {
		double top = Mastery.starterOnly(player) ? STARTER_TOP_SPEED : TOP_SPEED;
		// a cloudshaper (ascended, not smog) flies faster in the rain
		if (Fate.isAscended(player) && !Fate.isCorrupted(player) && player.level().isRainingAt(player.blockPosition().above())) {
			top *= CLOUD_RAIN_SPEED;
		}
		return top;
	}

	/** Every client tick, from the power's own tick. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		fly(player);
		SmokeFlightSound.tick(player);
		// fully mastered, Shift turns the whole body to smoke: the server is told when that starts and stops
		boolean whole = flying && wholeWanted && Mastery.unlocked(player) >= Mastery.ALL;
		if (whole != wholeSent && ClientPlayNetworking.canSend(SmokeVapour.Whole.TYPE)) {
			wholeSent = whole;
			ClientPlayNetworking.send(new SmokeVapour.Whole(whole));
		}
	}

	private static void fly(LocalPlayer player) {
		ticks++;
		boolean form = SmokeVapour.active(player);
		if (form && !formWas) {
			takeOff(player);
		}
		formWas = form;
		if (!flying) {
			return;
		}
		if (!form || player != owner || !WindFlight.canFly(player) || player.isFallFlying()) {
			// the form ended (R, hunger, landing), or a respawn or dimension change: on foot again
			land();
			return;
		}
		Input input = player.input.keyPresses;
		if (input.shift() && !shiftWas) {
			if (Mastery.unlocked(player) >= Mastery.ALL) {
				wholeWanted = !wholeWanted;
			} else {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.smoke_wholly_locked")
					.withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
		shiftWas = input.shift();
		if (player.horizontalCollision && cruising && speed > 0.9) {
			// flew into something: the cruise is over
			ScreenShake.add((float)Math.min(2.0, speed));
			Accents.play(player, SmokeFlightSound.STEAM.louder(2.0F), SmokeFlightSound.CRACKLE);
		}
		if (player.horizontalCollision && cruising) {
			cruising = false;
			velocity = new Vec3(0.0, velocity.y * 0.3, 0.0);
		}
		if (cruising) {
			cruise(player, input);
		} else {
			hover(player, input);
		}
		if (flying) {
			player.setDeltaMovement(velocity);
		}
	}

	/** Called when the local power changes: nothing flies on. */
	static void reset() {
		land();
		formWas = false;
		wholeSent = false;
	}

	private static void takeOff(LocalPlayer player) {
		owner = player;
		flying = true;
		wholeWanted = false;
		shiftWas = player.input.keyPresses.shift();
		cruising = false;
		speed = 0.0;
		Vec3 motion = player.getDeltaMovement();
		if (player.isSprinting() || player.input.keyPresses.sprint()) {
			// switched on and broke into a sprint: off along the look at once
			Vec3 look = player.getLookAngle();
			Vec3 dir = new Vec3(look.x, Math.max(look.y, SPRINT_TAKEOFF_LIFT), look.z).normalize();
			cruising = true;
			speed = Math.max(CRUISE_ENTER_SPEED, motion.length());
			velocity = dir.scale(speed);
			Accents.play(player, LAUNCH);
			ScreenShake.add(0.6F);
			return;
		}
		velocity = new Vec3(motion.x * 0.5, Math.max(motion.y, TAKEOFF_BOOST), motion.z * 0.5);
		ScreenShake.add(0.6F);
	}

	private static void land() {
		flying = false;
		wholeWanted = false;
		cruising = false;
		speed = 0.0;
		velocity = Vec3.ZERO;
	}

	private static void hover(LocalPlayer player, Input input) {
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		// forward goes along the look, up or down with it (Shift is the whole-body smoke now, not a sink)
		Vec3 look = player.getLookAngle();
		double wishX = (look.x * move.y + cos * move.x) * HOVER_SPEED;
		double wishZ = (look.z * move.y + sin * move.x) * HOVER_SPEED;
		double wishY = look.y * move.y * HOVER_SPEED;
		if (input.jump()) {
			wishY += HOVER_VERTICAL;
		}
		if (wishY == 0.0 && wishX * wishX + wishZ * wishZ < 1.0E-4) {
			wishY = Math.cos(ticks * 0.1) * HOVER_BOB;
		}
		velocity = velocity.add(new Vec3(wishX, wishY, wishZ).subtract(velocity).scale(HOVER_RESPONSE));
		if (input.sprint()) {
			// pour it on
			cruising = true;
			speed = Math.max(CRUISE_ENTER_SPEED, velocity.length());
			velocity = velocity.lerp(player.getLookAngle().scale(speed), 0.5);
			Accents.play(player, LAUNCH, SmokeFlightSound.STEAM.louder(1.8F));
			ScreenShake.add(0.4F);
		}
	}

	private static void cruise(LocalPlayer player, Input input) {
		double top = top(player);
		if (input.backward()) {
			// S is the only way down to a hover: the cruise keeps its speed otherwise
			speed -= CRUISE_BRAKE;
		} else if (speed < top) {
			speed = Math.min(top, speed + CRUISE_ACCEL * (1.0 - CRUISE_ACCEL_TAPER * speed / top));
		} else if (speed > top) {
			// a dive's extra eases back off
			speed = Math.max(top, speed - CRUISE_DECEL);
		}
		Vec3 heading = velocity.lengthSqr() < 1.0E-6 ? player.getLookAngle() : velocity.normalize();
		// a dive picks up speed past what the smoke alone gives; a climb costs a little
		speed = Math.min(top * DIVE_OVERSPEED, speed - heading.y * (heading.y < 0.0 ? DIVE_GRAVITY : DIVE_GRAVITY * 0.25));
		if (speed < CRUISE_EXIT_SPEED) {
			cruising = false;
			velocity = heading.scale(Math.max(0.0, speed));
			return;
		}
		Vec3 look = player.getLookAngle();
		double angle = Math.acos(Mth.clamp(heading.dot(look), -1.0, 1.0));
		double rate = Mth.lerp(Math.sqrt(Mth.clamp(speed / top, 0.0, 1.0)), TURN_RATE_SLOW, TURN_RATE_FAST) * Mth.DEG_TO_RAD;
		velocity = rotateToward(heading, look, angle, rate).scale(speed);
	}

	/** {@code from} turned toward {@code to} (unit vectors {@code angle} apart) by {@code by} radians, on the great circle. */
	private static Vec3 rotateToward(Vec3 from, Vec3 to, double angle, double by) {
		if (by >= angle) {
			return to;
		}
		double sin = Math.sin(angle);
		if (sin < 1.0E-4) {
			Vec3 axis = Math.abs(from.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
			Vec3 side = from.cross(axis).normalize();
			return from.scale(Math.cos(by)).add(side.scale(Math.sin(by))).normalize();
		}
		return from.scale(Math.sin(angle - by) / sin).add(to.scale(Math.sin(by) / sin)).normalize();
	}
}
