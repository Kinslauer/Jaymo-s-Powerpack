package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.85) Pyre's Flame Jet, flown on the client like a Z Fighter's flight (Andy: "a more controllable flight like the
 * z fighters"); player motion is client-authoritative, so its own velocity is steered toward what the controls ask and
 * handed to the player every tick. The server only keeps the jet on ({@code Pyre}: G toggles it, Sneak on the ground
 * lands) and says so through the synced {@link XenoformState#FLIGHT}.
 * <ul>
 * <li>G: lift off the ground into a hover; G again ends it and you drift down.</li>
 * <li>Hover: WASD, Jump rises, Sneak sinks (and lands on the ground).</li>
 * <li>Sprint: streak along your look, picking up speed, turning slower the faster you go; let go and you coast back
 * to a hover; S brakes. Under Sunheart ({@link XenoformState#FLY_SUN}) the streak is faster.</li>
 * </ul>
 */
public final class PyreFlightClient {
	// ---- tuning (blocks per tick) ----
	static final double HOVER_SPEED = 0.5, HOVER_VERTICAL = 0.4, HOVER_RESPONSE = 0.18, HOVER_BOB = 0.012;
	/** Take-off: a lift this fast, about two blocks before it settles into a hover. */
	static final double LIFT_SPEED = 0.35;
	/** The streak: its top speed (Sunheart's), how fast it starts, speeds up, coasts down, brakes and gives way to a hover. */
	static final double CRUISE_TOP = 1.4, CRUISE_TOP_SUN = 2.0;
	static final double CRUISE_ENTER_SPEED = 0.7, CRUISE_ACCEL = 0.06, CRUISE_ACCEL_TAPER = 0.6, CRUISE_DECEL = 0.015, CRUISE_BRAKE = 0.12;
	static final double CRUISE_EXIT_SPEED = 0.35;
	/** Turning rate (degrees a tick), from just-streaking to top speed. */
	static final double TURN_RATE_SLOW = 8.0, TURN_RATE_FAST = 1.5;
	/** The server's word wins: once it says the jet is off this long after our last toggle, the flight ends here too. */
	static final int SERVER_GRACE = 20;

	private static boolean flying;
	private static boolean cruising;
	private static boolean sinkArmed;
	private static Vec3 velocity = Vec3.ZERO;
	private static double speed;
	private static int sinceToggle;
	private static long ticks;
	private static @Nullable LocalPlayer owner;

	private PyreFlightClient() {
	}

	/** G as Pyre (the server toggles the jet on the same press). */
	static void toggle(LocalPlayer player) {
		sinceToggle = 0;
		if (flying) {
			flying = false;
			return;
		}
		if (!WindFlight.canFly(player) || player.isFallFlying()) {
			return;
		}
		flying = true;
		owner = player;
		cruising = false;
		sinkArmed = false;
		speed = 0.0;
		velocity = new Vec3(player.getDeltaMovement().x * 0.3, Math.max(player.getDeltaMovement().y, LIFT_SPEED), player.getDeltaMovement().z * 0.3);
	}

	static void reset() {
		flying = false;
		cruising = false;
		owner = null;
	}

	/** Every client tick, from the power's own tick. */
	static void tick(LocalPlayer player) {
		ticks++;
		sinceToggle++;
		if (!flying) {
			return;
		}
		if (player != owner || !"pyre".equals(player.getAttached(XenoformState.FORM)) || !WindFlight.canFly(player) || player.isFallFlying()) {
			// a respawn, a dimension change, the form gone
			reset();
			return;
		}
		int kind = XenoformState.flight(player);
		if (sinceToggle > SERVER_GRACE && !player.onGround() && !XenoformState.jet(kind)) {
			// the server ended it (the form ran out, a glitch changed it)
			reset();
			return;
		}
		Input input = player.input.keyPresses;
		if (player.horizontalCollision && cruising) {
			// flew into something: the streak is over
			cruising = false;
			velocity = new Vec3(0.0, velocity.y * 0.3, 0.0);
		}
		if (cruising) {
			cruise(player, input, kind == XenoformState.FLY_SUN ? CRUISE_TOP_SUN : CRUISE_TOP);
		} else if (!hover(player, input)) {
			return;
		}
		player.setDeltaMovement(velocity);
	}

	/** False when it landed. */
	private static boolean hover(LocalPlayer player, Input input) {
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
			cruising = true;
			speed = Math.max(CRUISE_ENTER_SPEED, velocity.length());
			velocity = velocity.lerp(player.getLookAngle().scale(speed), 0.5);
			player.level().playLocalSound(player, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 0.6F);
		} else if (sinking && player.onGround()) {
			// set down (the server ends the jet on the same sneak)
			reset();
			return false;
		}
		return true;
	}

	private static void cruise(LocalPlayer player, Input input, double top) {
		if (input.sprint() && speed < top) {
			speed = Math.min(top, speed + CRUISE_ACCEL * (1.0 - CRUISE_ACCEL_TAPER * speed / top));
		} else if (!input.sprint()) {
			speed -= CRUISE_DECEL;
			if (input.backward()) {
				speed -= CRUISE_BRAKE;
			}
		}
		speed = Math.min(speed, top);
		if (speed < CRUISE_EXIT_SPEED) {
			cruising = false;
			return;
		}
		Vec3 heading = velocity.lengthSqr() < 1.0E-6 ? player.getLookAngle() : velocity.normalize();
		Vec3 look = player.getLookAngle();
		double angle = Math.acos(Mth.clamp(heading.dot(look), -1.0, 1.0));
		double rate = Mth.lerp(Mth.clamp(speed / top, 0.0, 1.0), TURN_RATE_SLOW, TURN_RATE_FAST) * Mth.DEG_TO_RAD;
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
