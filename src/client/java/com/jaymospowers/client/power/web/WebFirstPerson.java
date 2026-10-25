package com.jaymospowers.client.power.web;

import com.jaymospowers.client.CarryPose;
import com.jaymospowers.client.LedgePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Your own moves seen from your own eyes, with First-person Model drawing your body: the counters, swings, strikes,
 * slams, weapon arm, roar, carry and ledge climb pose the body's own arms, so while one plays they're the only arms you
 * see. The game's usual first-person hands (and the suit's sleeve or a web shooter drawn on them) would float in front
 * as a second pair, and First-person Model would hide a posed body arm whose hand holds something: neither happens
 * then. Without First-person Model (or with it toggled off) nothing changes, as there's no body to pose.
 */
public final class WebFirstPerson {
	private WebFirstPerson() {
	}

	/** {@code state} is you, in first person with your body shown, partway through a move that poses your arms. */
	public static boolean posing(@Nullable AvatarRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state == null || minecraft.player == null || state.id != minecraft.player.getId() || !FirstPersonCompat.bodyShown()) {
			return false;
		}
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		return state.getData(WebArms.KEY) != null
			|| state.getData(WebCounterPose.KEY) != null
			|| state.getData(WebBodyPose.KEY) != null
			|| state.getData(WebHangPose.KEY) != null
			|| state.getData(WebClimbPose.KEY) != null
			|| state.getData(WebHeldGrabPose.KEY) != null
			|| state.getData(WebMorphLayer.KEY) != null
			|| state.getData(WebMorphLayer.GATHER) != null
			|| state.getData(WebSymbioteClient.SLAM) != null
			|| state.getData(WebSymbioteClient.EATING) != null
			|| state.getData(WebSymbioteClient.ROAR) != null
			|| tendrils != null && (tendrils.strike() >= 0.0F || tendrils.combo() >= 0 || tendrils.roar() >= 0.0F)
			|| state.getData(CarryPose.CARRIER_KEY) != null
			|| state.getData(LedgePose.KEY) != null;
	}
}
