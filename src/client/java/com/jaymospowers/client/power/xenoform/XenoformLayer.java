package com.jaymospowers.client.power.xenoform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Draws the alien in a transformed player's place. {@link XenoformClient#extract} has already hidden the player's own
 * body (invisible, no armour, no held items), so only the form's model shows, in the player's pose and at the player's
 * (scaled) size.
 */
final class XenoformLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	XenoformLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		XenoFormModels.FormModel model = XenoFormModels.of(state.getData(XenoformClient.FORM));
		if (model == null) {
			return;
		}
		// renderColoredCutoutModel, not the "copy layer" one: that skips invisible states, and the body was made invisible
		renderColoredCutoutModel(model, model.texture, poseStack, collector, lightCoords, state, -1, 1);
		// the body is drawn by now; let the shadow fall (EntityRenderer skips it for invisible states, after the layers)
		state.isInvisible = false;
	}
}
