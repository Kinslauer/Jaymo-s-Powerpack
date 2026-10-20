package com.jaymospowers.client.power.cryo;

import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.power.cryo.CryoNet;
import com.jaymospowers.power.cryo.CryoSlide;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) Ice Slide's skating, run on the client like Smoke's and Wind's flight (player motion is client-authoritative):
 * its own sideways velocity, handed to the player every tick while the server says you're sliding
 * ({@link CryoSlide#SLIDING}); up and down stay vanilla's, so you fall and jump as ever (and falls hurt).
 * <ul>
 * <li>On the ground: forward pushes off along your look (Sprint pushes harder, to a higher top speed); S digs in and
 * brakes; with nothing pressed you glide on, slowing only a little. Your heading turns toward your look only so fast,
 * slower the faster you go: inertia.</li>
 * <li>In the air you keep your speed (a jump off a ramp carries you).</li>
 * <li>Into a wall at speed: the server is told ({@link CryoNet.Crash}) and it hurts with the speed squared.</li>
 * </ul>
 * A fresh awakening (only the starter) has a lower top speed.
 */
public final class CryoSlideClient {
	// ---- tuning (blocks per tick) ----
	static final double PUSH = 0.03;
	static final double SPRINT_PUSH = 0.045;
	static final double TOP = 0.55;
	static final double SPRINT_TOP = 0.85;
	static final double STARTER_TOP = 0.4;
	static final double STARTER_SPRINT_TOP = 0.6;
	/** Kept a tick: gliding on the ground, and in the air. */
	static final double GLIDE = 0.993;
	static final double AIR_GLIDE = 0.985;
	static final double BRAKE = 0.04;
	/** Turning (degrees a tick), from a standstill to top speed. */
	static final double TURN_SLOW = 10.0;
	static final double TURN_FAST = 2.2;
	/** Faster than this into a wall is a crash. */
	static final double CRASH_SPEED = 0.45;

	private static Vec3 velocity = Vec3.ZERO;
	private static boolean skating;

	private CryoSlideClient() {
	}

	/** Whether the local player is skating now. */
	public static boolean skating() {
		return skating;
	}

	/** 0..1 of the top (sprint) speed, for the HUD. */
	public static double speedFraction(LocalPlayer player) {
		return skating ? Mth.clamp(velocity.horizontalDistance() / top(player, true), 0.0, 1.0) : 0.0;
	}

	private static double top(LocalPlayer player, boolean sprint) {
		boolean starter = Mastery.starterOnly(player);
		return sprint ? starter ? STARTER_SPRINT_TOP : SPRINT_TOP : starter ? STARTER_TOP : TOP;
	}

	static void reset() {
		skating = false;
		velocity = Vec3.ZERO;
	}

	static void tick(LocalPlayer player) {
		if (!CryoSlide.active(player) || !CryoSlide.canSlide(player) || player.getAbilities().flying || player.isInWater()) {
			reset();
			return;
		}
		if (!skating) {
			Vec3 motion = player.getDeltaMovement();
			velocity = new Vec3(motion.x, 0.0, motion.z);
			skating = true;
		}
		double speed = velocity.horizontalDistance();
		if (player.horizontalCollision) {
			if (speed > CRASH_SPEED) {
				if (ClientPlayNetworking.canSend(CryoNet.Crash.TYPE)) {
					ClientPlayNetworking.send(new CryoNet.Crash((float)speed));
				}
				ScreenShake.add((float)Math.min(2.0, speed * 1.5));
				player.level().playLocalSound(player, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			velocity = Vec3.ZERO;
			speed = 0.0;
		}
		if (player.onGround()) {
			Vec2 move = player.input.getMoveVector();
			boolean sprint = player.input.keyPresses.sprint() || player.isSprinting();
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
			// the heading swings round toward where you look, only so fast
			if (speed > 1.0E-3) {
				Vec3 heading = velocity.scale(1.0 / speed);
				double cross = heading.x * facing.z - heading.z * facing.x;
				double angle = Math.atan2(cross, heading.x * facing.x + heading.z * facing.z);
				double rate = Mth.lerp(Mth.clamp(speed / top(player, true), 0.0, 1.0), TURN_SLOW, TURN_FAST) * Mth.DEG_TO_RAD;
				double turn = Mth.clamp(angle, -rate, rate);
				// (a positive cross product turns the heading toward the facing: rotate by -turn about Y)
				velocity = heading.yRot((float)-turn).scale(speed);
			}
			if (move.y > 0.0F) {
				velocity = velocity.add(facing.scale((sprint ? SPRINT_PUSH : PUSH) * move.y));
			} else if (move.y < 0.0F) {
				double slowed = Math.max(0.0, velocity.horizontalDistance() - BRAKE);
				velocity = velocity.horizontalDistance() > 1.0E-4 ? velocity.normalize().scale(slowed) : Vec3.ZERO;
			}
			double top = top(player, sprint);
			double now = velocity.horizontalDistance();
			if (now > top) {
				// over the top (a sprint let go, or a downhill): ease back
				velocity = velocity.scale(Math.max(top, now - 0.02) / now);
			}
			velocity = velocity.scale(GLIDE);
		} else {
			velocity = velocity.scale(AIR_GLIDE);
		}
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, motion.y, velocity.z);
	}
}
