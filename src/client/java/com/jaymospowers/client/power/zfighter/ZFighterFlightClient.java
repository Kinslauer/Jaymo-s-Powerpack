package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterState;
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
 * A Z Fighter's flight, run on the client like Wind's (player motion is client-authoritative): its own velocity,
 * steered toward what the controls ask, handed to the player every tick. Ki alone keeps him up (the server drains it).
 * <ul>
 * <li>Hold Z and scroll up: take off (pose 1), then step through the five poses of the arms; scroll down steps back,
 * and past the first lands. A tap of Z in the air also lands.</li>
 * <li>Hover: WASD, Jump rises, Sneak sinks (and lands on the ground).</li>
 * <li>Sprint: cruise along your look, faster the higher the form ({@link ZFighterPower#FORM_CRUISE}); let go and you
 * coast back to a hover; S brakes.</li>
 * <li>Charging ki (R) works in the air and keeps his speed (a cruise neither speeds up nor slows); badly hurt, he slows to
 * a near-standstill while he does.</li>
 * </ul>
 */
public final class ZFighterFlightClient {
	// ---- tuning (blocks per tick) ----
	static final double HOVER_SPEED = 0.5;
	static final double HOVER_VERTICAL = 0.4;
	static final double HOVER_RESPONSE = 0.16;
	static final double HOVER_BOB = 0.012;
	/** Take-off: a burst of ki throws him up this fast, then eases off over this many ticks into a hover. */
	static final double TAKEOFF_BURST = 1.6;
	static final int BURST_TICKS = 10;
	static final double BURST_DRAG = 0.82;
	static final double CRUISE_ENTER_SPEED = 0.7;
	static final double CRUISE_ACCEL = 0.05;
	static final double CRUISE_ACCEL_TAPER = 0.6;
	static final double CRUISE_DECEL = 0.015;
	static final double CRUISE_BRAKE = 0.12;
	/** Below this a coasting cruise settles back into a hover. */
	static final double CRUISE_EXIT_SPEED = 0.35;
	/** Turning rate (degrees a tick), from just-cruising to top speed. */
	static final double TURN_RATE_SLOW = 8.0;
	static final double TURN_RATE_FAST = 1.2;
	/** Charging ki in the air keeps his speed; only badly hurt (at or under this share of his health) does he slow to this. */
	static final float LOW_HEALTH = 0.3F;
	static final double CHARGING_SPEED = 0.08;
	/** The server's word wins: once it says he's down this long after our last word, the flight ends here too. */
	static final int SERVER_GRACE = 20;

	private static int pose;
	private static int burst;
	private static boolean cruising;
	private static Vec3 velocity = Vec3.ZERO;
	private static double speed;
	private static int sinceSent;
	private static boolean sinkArmed;
	private static long ticks;
	private static boolean zHeld;
	private static boolean zScrolled;
	private static @Nullable LocalPlayer owner;

	private ZFighterFlightClient() {
	}

	static boolean ours(@Nullable LocalPlayer player) {
		return player != null && Powers.has(player, ZFighterPower.INSTANCE);
	}

	/** Whether the local player is sprint-flying (the arms' pose and Wind's laid-out body follow it). */
	public static boolean cruising() {
		return pose > 0 && cruising;
	}

	/** The local player's flight pose (0: on foot), for the HUD. */
	public static int pose() {
		return pose;
	}

	/** From the scroll mixin: true if the scroll was taken (Z held by a Z Fighter who can fly). */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (minecraft.gui.screen() != null || !ours(player) || player.isSpectator() || amount == 0.0 || !JaymosPowersClient.modeKey().isDown()
			|| !ZFighterPower.INSTANCE.slotUnlocked(player, "mode")) {
			return false;
		}
		zScrolled = true;
		int next = Mth.clamp(pose + (amount > 0.0 ? 1 : -1), 0, ZFighterPower.FLIGHT_POSES);
		if (next == pose) {
			return true;
		}
		if (pose == 0) {
			if (!WindFlight.canFly(player) || player.isFallFlying()) {
				return true;
			}
			if (ZFighterPower.state(player).ki() < 5 && !player.isCreative()) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.zfighter_no_ki")
					.withStyle(net.minecraft.ChatFormatting.GRAY));
				return true;
			}
			takeOff(player);
		} else if (next == 0) {
			land(player, true);
			return true;
		}
		pose = next;
		send(pose);
		player.level().playLocalSound(player, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25F, 1.0F + 0.12F * pose);
		return true;
	}

	private static void takeOff(LocalPlayer player) {
		owner = player;
		cruising = false;
		speed = 0.0;
		sinkArmed = false;
		burst = BURST_TICKS;
		velocity = new Vec3(player.getDeltaMovement().x * 0.3, Math.max(player.getDeltaMovement().y, TAKEOFF_BURST), player.getDeltaMovement().z * 0.3);
	}

	private static void land(LocalPlayer player, boolean tell) {
		if (pose == 0) {
			return;
		}
		pose = 0;
		burst = 0;
		cruising = false;
		if (tell) {
			send(0);
		}
	}

	private static void send(int value) {
		sinceSent = 0;
		ClientPlayNetworking.send(new ZFighterNet.Fly(value));
	}

	/** Every client tick, from the power's own tick: the Z key, then the flight itself. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		ticks++;
		sinceSent++;
		boolean z = JaymosPowersClient.modeKey().isDown() && minecraft.gui.screen() == null;
		if (z && !zHeld) {
			zScrolled = false;
		} else if (!z && zHeld && !zScrolled && pose > 0) {
			// a tap of Z (no scroll) in the air: come down
			land(player, true);
		}
		zHeld = z;
		if (pose == 0 && sinceSent > SERVER_GRACE && !player.onGround() && ZFighterPower.flightPose(player) > 0) {
			// the server put him in the air already flying (a fusion swap mid-flight): hover where he is
			owner = player;
			pose = ZFighterPower.flightPose(player);
			burst = 0;
			cruising = false;
			speed = 0.0;
			sinkArmed = false;
			velocity = Vec3.ZERO;
		}
		if (pose == 0) {
			return;
		}
		if (player != owner) {
			// a respawn or a dimension change: a new player entity starts on the ground
			owner = player;
			land(player, true);
			return;
		}
		if (!WindFlight.canFly(player) || player.isFallFlying()) {
			land(player, true);
			return;
		}
		if (ZFighterPower.flightPose(player) == 0 && sinceSent > SERVER_GRACE) {
			// out of ki (or stopped by the server): down he comes
			land(player, false);
			return;
		}
		Input input = player.input.keyPresses;
		// charging ki keeps his speed, unless he's badly hurt: then he slows to a near-standstill
		boolean charging = ZFighterPower.state(player).has(ZFighterState.CHARGING);
		boolean slowed = charging && player.getHealth() <= player.getMaxHealth() * LOW_HEALTH;
		if (player.horizontalCollision && cruising) {
			// flew into something: the cruise is over
			cruising = false;
			velocity = new Vec3(0.0, velocity.y * 0.3, 0.0);
		}
		if (burst > 0) {
			// the take-off burst: shot up, slowing into a hover
			burst--;
			velocity = velocity.scale(BURST_DRAG);
		} else if (cruising) {
			cruise(player, input, charging, slowed);
		} else {
			hover(player, input, slowed);
		}
		if (pose > 0) {
			player.setDeltaMovement(velocity);
		}
	}

	private static void hover(LocalPlayer player, Input input, boolean slowed) {
		if (!input.shift()) {
			sinkArmed = true;
		}
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		double horizontal = slowed ? CHARGING_SPEED : HOVER_SPEED;
		double wishX = (move.x * cos - move.y * sin) * horizontal;
		double wishZ = (move.y * cos + move.x * sin) * horizontal;
		double wishY = 0.0;
		if (input.jump()) {
			wishY += slowed ? CHARGING_SPEED : HOVER_VERTICAL;
		}
		boolean sinking = input.shift() && sinkArmed;
		if (sinking) {
			wishY -= slowed ? CHARGING_SPEED : HOVER_VERTICAL;
		}
		if (wishY == 0.0 && wishX * wishX + wishZ * wishZ < 1.0E-4) {
			wishY = Math.cos(ticks * 0.1) * HOVER_BOB;
		}
		velocity = velocity.add(new Vec3(wishX, wishY, wishZ).subtract(velocity).scale(HOVER_RESPONSE));
		if (input.sprint() && !slowed) {
			cruising = true;
			speed = Math.max(CRUISE_ENTER_SPEED, velocity.length());
			velocity = velocity.lerp(player.getLookAngle().scale(speed), 0.5);
			player.level().playLocalSound(player, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.1F);
		} else if (sinking && player.onGround()) {
			land(player, true);
		}
	}

	private static void cruise(LocalPlayer player, Input input, boolean charging, boolean slowed) {
		double top = ZFighterPower.FORM_CRUISE[Mth.clamp(ZFighterPower.state(player).form(), 0, ZFighterPower.FORM_CRUISE.length - 1)];
		if (slowed) {
			speed = Math.max(0.0, speed - CRUISE_BRAKE);
		} else if (charging) {
			// charging holds the speed he had
		} else if (input.sprint() && speed < top) {
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
