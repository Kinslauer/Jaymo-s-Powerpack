package com.jaymospowers.client.jump;

import com.jaymospowers.client.baubles.ArtifactBaublesClient;
import com.jaymospowers.core.jump.PowerJump;
import com.jaymospowers.power.arsenal.ArsenalRide;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.smoke.SmokeJet;
import com.jaymospowers.power.smoke.SmokeVapour;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindJet;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (pb-jump): a second jump in the air for every power ({@link PowerJump}). A fresh press of Jump with nothing under
 * the feet, a few ticks after leaving the ground, kicks once; the ground, water, lava, a ladder, a mount or flying give it
 * back. How hard it kicks and which way is each kind's own ({@link #LIFT}, {@link #PUSH}): Arsenal's rift pushes him
 * highest (the peak stands under him), Chrono's remnant throws him forward, Wind's gust a little further than the rest.
 * <p>
 * The fall is counted from the top of the jump, as vanilla counts it (the server clears it with the packet), so fall
 * damage still applies outside creative. A power's own flight, a web line or a ride keeps its own Jump. With a cloud in a
 * bottle, its jump goes first and this one is a second press: one each, never three on a press.
 * <p>
 * Someone with no power can have the lift too: a draught registers itself with {@link #mortal} (the Draught of Mettle's
 * extra jump), which kicks like it always did (straight up, {@link #MORTAL_LIFT}).
 */
public final class PowerJumpClient {
	/** Upward speed of each kind's jump (blocks a tick; a plain jump is 0.42), by kind. Arsenal's is the highest. */
	static final double[] LIFT = new double[PowerJump.MORTAL + 1];
	/** How far each kind throws him the way he is steering (blocks a tick), by kind; Chrono's goes the way he looks. */
	static final double[] PUSH = new double[PowerJump.MORTAL + 1];

	static {
		LIFT[PowerJump.ARSENAL] = 0.74;
		LIFT[PowerJump.CHRONO] = 0.46;
		PUSH[PowerJump.CHRONO] = 0.45;
		LIFT[PowerJump.WIND] = 0.5;
		PUSH[PowerJump.WIND] = 0.2;
		LIFT[PowerJump.FIRE] = 0.5;
		LIFT[PowerJump.WEB] = 0.5;
		PUSH[PowerJump.WEB] = 0.15;
		LIFT[PowerJump.GRAVITY] = 0.52;
		LIFT[PowerJump.SMOKE] = 0.46;
		PUSH[PowerJump.SMOKE] = 0.1;
		LIFT[PowerJump.CRYO] = 0.46;
		LIFT[PowerJump.DARKTIDE] = 0.48;
		LIFT[PowerJump.RESONANCE] = 0.46;
		LIFT[PowerJump.XENOFORM] = 0.5;
		LIFT[PowerJump.ZFIGHTER] = 0.5;
		PUSH[PowerJump.ZFIGHTER] = 0.1;
	}

	/** The no-power draught's kick (the Draught of Mettle's, straight up). */
	public static final double MORTAL_LIFT = 0.5;
	/** Ticks off the ground before a press counts (a late jump just off a ledge is a plain jump's). */
	static final int AIR_TICKS = 5;

	private static final java.util.List<Predicate<LocalPlayer>> MORTAL = new java.util.ArrayList<>();

	private static boolean spent, wasJump;
	private static int air;

	private PowerJumpClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PowerJumpClient::tick);
	}

	/** Gives a player with no power the lift while {@code active} holds (the Draught of Mettle's second jump). */
	public static void mortal(Predicate<LocalPlayer> active) {
		MORTAL.add(active);
	}

	/** Whether {@code player} has a power's second jump (read by Kingdoms' sorcerers, who have their own, so they don't both fire). */
	public static boolean has(LocalPlayer player) {
		return PowerJump.kindOf(player) != PowerJump.NONE;
	}

	private static byte kind(LocalPlayer player) {
		byte kind = PowerJump.kindOf(player);
		if (kind == PowerJump.NONE) {
			for (Predicate<LocalPlayer> active : MORTAL) {
				if (active.test(player)) {
					return PowerJump.MORTAL;
				}
			}
		}
		return kind;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			spent = false;
			wasJump = false;
			air = 0;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		boolean pressed = jump && !wasJump;
		wasJump = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isPassenger()
			|| player.onClimbable()) {
			spent = false;
			air = 0;
			return;
		}
		air++;
		if (!pressed || spent || air < AIR_TICKS || busy(player) || ArtifactBaublesClient.cloudJump(player)) {
			return;
		}
		byte kind = kind(player);
		if (kind == PowerJump.NONE) {
			return;
		}
		spent = true;
		Vec3 v = player.getDeltaMovement();
		double push = PUSH[kind];
		double dx = 0.0, dz = 0.0;
		if (push > 0.0) {
			double yaw = Math.toRadians(player.getYRot());
			if (kind == PowerJump.CHRONO) {
				dx = -Math.sin(yaw);
				dz = Math.cos(yaw);
			} else {
				dx = -Math.sin(yaw) * player.zza + Math.cos(yaw) * player.xxa;
				dz = Math.cos(yaw) * player.zza + Math.sin(yaw) * player.xxa;
				double length = Math.sqrt(dx * dx + dz * dz);
				if (length > 1.0) {
					dx /= length;
					dz /= length;
				}
			}
		}
		double lift = kind == PowerJump.MORTAL ? MORTAL_LIFT : LIFT[kind];
		player.setDeltaMovement(v.x + dx * push, lift, v.z + dz * push);
		player.resetFallDistance();
		if (ClientPlayNetworking.canSend(PowerJump.Jump.TYPE)) {
			ClientPlayNetworking.send(new PowerJump.Jump(kind));
		}
	}

	/** Not a time for a second jump: in a power's flight, on a web line, on a ride, gliding. */
	private static boolean busy(LocalPlayer player) {
		return player.isSpectator() || player.isFallFlying() || player.isSleeping() || positive(player.getAttached(WindPower.FLIGHT))
			|| Boolean.TRUE.equals(player.getAttached(WindJet.JETTING)) || positive(player.getAttached(ZFighterPower.FLIGHT))
			|| positive(player.getAttached(XenoformState.FLIGHT)) || player.getAttached(GravityPower.ROCK_FLIGHT) != null
			|| Boolean.TRUE.equals(player.getAttached(SmokeVapour.FORM)) || Boolean.TRUE.equals(player.getAttached(SmokeJet.JETTING))
			|| Boolean.TRUE.equals(player.getAttached(WebPower.WINGS)) || Boolean.TRUE.equals(player.getAttached(WebPower.UPSIDE))
			|| player.getAttached(WebPower.LINE) != null || player.getAttached(WebPower.TETHER) != null
			|| player.getAttached(ArsenalRide.RIDE) != null;
	}

	private static boolean positive(@Nullable Integer value) {
		return value != null && value > 0;
	}
}
