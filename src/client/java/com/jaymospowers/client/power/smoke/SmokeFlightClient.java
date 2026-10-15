package com.jaymospowers.client.power.smoke;

import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.power.smoke.SmokeVapour;
import com.jaymospowers.power.wind.WindFlight;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * <li>Hover: WASD, Jump rises, Sneak sinks (and lands on the ground).</li>
 * <li>Sprint: cruise along your look, picking up speed (a dive adds more); let go and you coast back to a hover; S
 * brakes. Turning is slower the faster you go.</li>
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
	/** Cruise: speed on entering, acceleration while Sprint is held (tapering toward the top), coast-down and brake. */
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

	private static boolean flying;
	private static boolean cruising;
	/** Whether the server was last told this flier is wholly smoke. */
	private static boolean wholeSent;
	private static boolean formWas;
	private static boolean sinkArmed;
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

	private static double top(LocalPlayer player) {
		return Mastery.starterOnly(player) ? STARTER_TOP_SPEED : TOP_SPEED;
	}

	/** Every client tick, from the power's own tick. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		fly(player);
		// fully mastered, a cruise turns the whole body to smoke: the server is told when that starts and stops
		boolean whole = cruising() && Mastery.unlocked(player) >= Mastery.ALL;
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
		if (player.horizontalCollision && cruising && speed > 0.9) {
			// flew into something: the cruise is over
			ScreenShake.add((float)Math.min(2.0, speed));
			player.level().playLocalSound(player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 0.6F);
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
		cruising = false;
		speed = 0.0;
		sinkArmed = false;
		Vec3 motion = player.getDeltaMovement();
		velocity = new Vec3(motion.x * 0.5, Math.max(motion.y, TAKEOFF_BOOST), motion.z * 0.5);
		ScreenShake.add(0.6F);
	}

	private static void land() {
		flying = false;
		cruising = false;
		speed = 0.0;
		velocity = Vec3.ZERO;
	}

	private static void hover(LocalPlayer player, Input input) {
		if (!input.shift()) {
			sinkArmed = true;
		}
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		double wishX = (move.x * cos - move.y * sin) * HOVER_SPEED;
		double wishZ = (move.y * cos + move.x * sin) * HOVER_SPEED;
		double wishY = 0.0;
		if (input.jump()) {
			wishY += HOVER_VERTICAL;
		}
		boolean sinking = input.shift() && sinkArmed;
		if (sinking) {
			wishY -= HOVER_VERTICAL;
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
			player.level().playLocalSound(player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.5F);
			player.level().playLocalSound(player, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.4F, 0.8F);
			ScreenShake.add(0.4F);
		}
		// Sneak on the ground lands: the server ends the form and the legs come back
	}

	private static void cruise(LocalPlayer player, Input input) {
		double top = top(player);
		if (input.sprint() && speed < top) {
			speed = Math.min(top, speed + CRUISE_ACCEL * (1.0 - CRUISE_ACCEL_TAPER * speed / top));
		} else if (!input.sprint()) {
			speed -= CRUISE_DECEL;
		}
		if (input.backward()) {
			speed -= CRUISE_BRAKE;
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
