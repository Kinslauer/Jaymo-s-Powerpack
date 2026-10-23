package com.jaymospowers.client.wounds;

import com.jaymospowers.aether.gear.AercloudGliderItem;
import com.jaymospowers.client.FlightRolls;
import com.jaymospowers.client.power.smoke.SmokeFlightClient;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.LimbUse;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.136) Flying and gliding on broken or severed limbs ({@link LimbUse}), on the flier's own client (movement is the
 * client's). Once a tick, after the power's own flight has set the speed:
 * <ul>
 * <li><b>Flying</b> (Wind, ki, Vapour Form, the Rig, an elytra): the whole body's {@link LimbUse#flight} strength. Below
 * full, you wobble side to side ({@link #FLIGHT_WOBBLE}), pull towards your weaker side ({@link #FLIGHT_PULL}), sag
 * ({@link #FLIGHT_SAG}) and lose speed ({@link #FLIGHT_DRAG}). Vapour Form's legs are smoke and don't count; an alien's
 * body is its own and doesn't either.</li>
 * <li><b>Gliding</b> (web wings, an aercloud glider): both arms ({@link LimbUse#BOTH_ARMS}): the same, harder, and it
 * sinks faster ({@link #GLIDE_SINK}). With an arm gone it never opens (WebSwing, WebPower, the glider).</li>
 * </ul>
 * Every amount is at no strength left; a broken limb gives {@code 1 - strength} of it.
 */
public final class LimbMotion {
	/** Flying: sideways wobble, pull towards the weak side, sag and drag (blocks a tick, and a share of the speed a tick). */
	static final double FLIGHT_WOBBLE = 0.09, FLIGHT_PULL = 0.05, FLIGHT_SAG = 0.03, FLIGHT_DRAG = 0.06;
	/** Gliding: the same, and the extra sink (blocks a tick). */
	static final double GLIDE_WOBBLE = 0.12, GLIDE_PULL = 0.08, GLIDE_SINK = 0.06, GLIDE_DRAG = 0.08;
	/** How fast the wobble swings (radians a tick). */
	static final float WOBBLE_RATE = 0.19F;

	private LimbMotion() {
	}

	public static void tick(LocalPlayer player) {
		if (player.onGround() || player.isPassenger() || player.isSpectator() || player.getAbilities().flying || player.isInWater()) {
			return;
		}
		if (gliding(player)) {
			float s = LimbUse.strength(player, LimbUse.BOTH_ARMS);
			push(player, s, side(player, false), GLIDE_WOBBLE, GLIDE_PULL, GLIDE_SINK, GLIDE_DRAG);
			return;
		}
		if ((FlightRolls.flying(player) || player.isFallFlying()) && !XenoformState.transformed(player)) {
			boolean legs = !SmokeFlightClient.flying();
			push(player, LimbUse.flight(player, legs), side(player, legs), FLIGHT_WOBBLE, FLIGHT_PULL, FLIGHT_SAG, FLIGHT_DRAG);
		}
	}

	/** Web wings spread, or an aercloud glider open. */
	private static boolean gliding(LocalPlayer player) {
		return player.getAttached(WebPower.WINGS) != null || player.isUsingItem() && player.getUseItem().getItem() instanceof AercloudGliderItem;
	}

	/** How much stronger the right side is than the left (-1 to 1): above 0 you're pulled left, below 0 right. */
	private static float side(LocalPlayer player, boolean legs) {
		float left = LimbUse.strength(player, BodyPart.LEFT_ARM), right = LimbUse.strength(player, BodyPart.RIGHT_ARM);
		if (legs) {
			left = (left + LimbUse.strength(player, BodyPart.LEFT_LEG)) / 2.0F;
			right = (right + LimbUse.strength(player, BodyPart.RIGHT_LEG)) / 2.0F;
		}
		return right - left;
	}

	private static void push(LocalPlayer player, float strength, float side, double wobble, double pull, double sink, double drag) {
		double lack = 1.0 - strength;
		if (lack <= 0.0) {
			return;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		// (the player's right, level)
		double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
		float t = player.tickCount * WOBBLE_RATE;
		double sway = (Mth.sin(t) + 0.6 * Mth.sin(t * 2.3F + 1.3F)) / 1.6 * wobble * lack;
		// a weak left side drags you left (side > 0: the right is stronger, so the pull is to the left, against the right vector)
		double lean = -side * pull;
		Vec3 v = player.getDeltaMovement();
		double keep = 1.0 - drag * lack;
		player.setDeltaMovement(v.x * keep + rx * (sway + lean), v.y - sink * lack, v.z * keep + rz * (sway + lean));
	}
}
