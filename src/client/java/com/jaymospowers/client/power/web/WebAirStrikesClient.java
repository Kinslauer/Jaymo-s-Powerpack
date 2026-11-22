package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebAirStrikes;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

/**
 * Strikes out of a swing, on the local player ({@link WebAirStrikes} has the blows): which one a click throws. Only in the
 * air after a web was let go ({@link WebTrickClient#releasedAt}), with no web out and nothing else having the body.
 * Sneak + click: the dive kick. Click while a trick is still turning you: the spinning strike. Otherwise: the rising blow.
 */
final class WebAirStrikesClient {
	private static int nextAt;

	private WebAirStrikesClient() {
	}

	/** A click in the air: sends the strike and answers whether it was one (the click is then used up). */
	static boolean onAttack(LocalPlayer player, WebSwing swing, WebTrickClient tricks) {
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.isPassenger() || swing.attached()
			|| swing.pull != null || swing.wingsTicks != 0 || WebSymbioteClient.driving() || player.tickCount < nextAt
			|| player.tickCount - tricks.releasedAt() > WebAirStrikes.WINDOW) {
			return false;
		}
		WebPose pose = player.getAttached(WebPower.POSE);
		boolean posed = pose != null && player.level().getGameTime() - pose.start() < pose.ticks();
		if (posed && !WebPose.trick(pose.kind())) {
			return false;
		}
		int kind = player.isShiftKeyDown() ? WebPose.DIVE_KICK : posed ? WebPose.SPIN_STRIKE : WebPose.RISING_BLOW;
		nextAt = player.tickCount + WebAirStrikes.COOLDOWN;
		tricks.strikeSent(player);
		ClientPlayNetworking.send(new WebAirStrikes.Strike(kind));
		return true;
	}
}
