package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/** The alien in first person: its arm in place of yours (Pyre's glowing), pulsing red through the form's last seconds. */
public final class XenoformHand {
	private XenoformHand() {
	}

	/** In place of the first-person arm: true if the alien's arm was drawn (the skin arm is then skipped). */
	public static boolean render(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !XenoformState.transformed(player)) {
			return false;
		}
		XenoFormModels.FormModel model = XenoformClient.model(player.getAttached(XenoformState.FORM), Boolean.TRUE.equals(player.getAttached(XenoformState.ULTIMATE)));
		if (model == null) {
			return false;
		}
		if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateGeode) {
			return true; // (0.20.21) Geode has no arm: its hidden humanoid limb is painted with other boxes' pixels, so nothing is drawn
		}
		// (0.20.9) an Ultimate's model wears its own skin
		boolean ultimateModel = model instanceof XenoFormModels.UltimateModel;
		net.minecraft.resources.Identifier skin = ultimateModel ? model.ultimateTexture : model.texture;
		boolean right = arm == source.rightArm;
		ModelPart limb = right ? model.rightArm : model.leftArm;
		limb.resetPose();
		// at the player arm's shoulder, wherever the form hangs its arms (Duplex's are low on a golem's body)
		limb.x = right ? -5.0F : 5.0F;
		limb.y = 2.0F;
		limb.visible = true;
		limb.zRot = right ? 0.1F : -0.1F;
		if (model instanceof XenoFormModels.Pyre pyre) {
			pyre.handPose();
		} else if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateTitan titan) {
			// (0.20.9) Titan's forearm and fist straightened for the first-person arm
			titan.handPose();
		} else if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateSunheart sun) {
			// (0.20.21) Sunheart's flame lick upright for the first-person arm
			sun.handPose();
		} else if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateSwarm swarm) {
			// (0.20.21) Swarm's forearm and fist straightened for the first-person arm
			swarm.handPose();
		} else if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateTolling tolling) {
			// (0.20.21) the Tolling's bent forearm straightened for the first-person arm
			tolling.handPose();
		} else if (model instanceof XenoFormModels.Bulwark bulwark) {
			// (0.19.84) its forearm and fist straightened for the first-person arm
			bulwark.handPose();
		} else if (model instanceof XenoFormModels.Knell knell) {
			// (0.19.84) its bent forearm and fist straightened, the shoulder brought back off the hump
			knell.handPose();
		} else if (model instanceof XenoFormModels.Shade shade) {
			// (0.19.84) its two-segment arm straightened (a crawling Shade drawn last leaves the elbow and hand bent)
			shade.handPose();
		} else if (model instanceof XenoFormModels.Lurk lurk) {
			// (0.19.84) its forearm and hand straightened (on all fours they're bent)
			lurk.handPose();
		} else if (model instanceof XenoFormModels.Rime rime) {
			// (0.19.84) its fists back at their rest curl (a blow drawn last leaves them curled further)
			rime.handPose();
		} else if (model instanceof XenoFormModels.Dud dud) {
			// (0.19.84) its nub hand down where a fist would be (it hangs at the shoulder)
			dud.handPose(limb);
		}
		poseStack.pushPose();
		// a slim skin's arm is a pixel thinner; the alien's never is, so nudge it in
		if (player.getSkin().model() == PlayerModelType.SLIM) {
			poseStack.translate(right ? -0.03F : 0.03F, 0.0F, 0.0F);
		}
		if (model instanceof XenoFormModels.Wisp wisp) {
			WispKi.submitArm(wisp, right, limb, poseStack, collector, player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
		} else if (model instanceof com.jaymospowers.client.power.xenoform.ultimate.UltimateRevenant revenant) {
			// (0.20.21) Revenant's arm is see-through and fullbright, in ki, as the rest of it is
			revenant.submitArm(right, limb, poseStack, collector, player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
		} else {
			collector.submitModelPart(limb, poseStack, RenderTypes.entityCutout(skin), light, OverlayTexture.NO_OVERLAY, null);
		}
		if (ultimateModel) {
			// (0.20.9) an Ultimate's seams, fullbright
			collector.submitModelPart(limb, poseStack, RenderTypes.entityTranslucentEmissive(model.ultimateGlow), 0xF000F0, OverlayTexture.NO_OVERLAY, null);
		}
		if (model instanceof XenoFormModels.Pyre) {
			collector.submitModelPart(limb, poseStack, RenderTypes.eyes(XenoFormModels.Pyre.GLOW), light, OverlayTexture.NO_OVERLAY, null);
		}
		if (XenoformState.ending(player)) {
			float time = player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
			collector.submitModelPart(limb, poseStack, RenderTypes.eyes(skin), light, OverlayTexture.NO_OVERLAY, null, XenoformLayer.glow(time));
		}
		poseStack.popPose();
		return true;
	}
}
