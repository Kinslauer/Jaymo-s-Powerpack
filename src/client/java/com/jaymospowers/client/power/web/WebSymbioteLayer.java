package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * The symbiote's black suit, drawn over a bonded web slinger's whole body (and their skin's outer layer) with the
 * player model's own pose. Everyone sees it; it hides when the wearer is invisible or has the suit turned off.
 * The texture ({@code textures/entity/web_symbiote_suit.png}) is drawn by {@code tools/gen_web.py}.
 */
final class WebSymbioteLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_suit.png");

	WebSymbioteLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(WebSymbioteClient.SUIT) == null) {
			return;
		}
		coloredCutoutModelCopyLayerRender(this.getParentModel(), TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
	}
}
