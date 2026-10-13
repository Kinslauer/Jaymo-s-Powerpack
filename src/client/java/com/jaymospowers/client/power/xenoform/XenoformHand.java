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

/** The alien in first person: its arm in place of yours. */
public final class XenoformHand {
	private XenoformHand() {
	}

	/** In place of the first-person arm: true if the alien's arm was drawn (the skin arm is then skipped). */
	public static boolean render(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !XenoformState.transformed(player)) {
			return false;
		}
		XenoFormModels.FormModel model = XenoFormModels.of(player.getAttached(XenoformState.FORM));
		if (model == null) {
			return false;
		}
		boolean right = arm == source.rightArm;
		ModelPart limb = right ? model.rightArm : model.leftArm;
		limb.resetPose();
		limb.visible = true;
		limb.zRot = right ? 0.1F : -0.1F;
		poseStack.pushPose();
		// a slim skin's arm is a pixel thinner; the alien's never is, so nudge it in
		if (player.getSkin().model() == PlayerModelType.SLIM) {
			poseStack.translate(right ? -0.03F : 0.03F, 0.0F, 0.0F);
		}
		collector.submitModelPart(limb, poseStack, RenderTypes.entityCutout(model.texture), light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		return true;
	}
}
