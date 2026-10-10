package com.jaymoskitchen.client.quiver;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the quiver a player carries on their back, with one shaft per two arrows left in it. */
public class QuiverLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosKitchen.id("textures/entity/quiver.png");

	private final QuiverModel model;
	private final QuiverModel.Fletching[] fletchings = new QuiverModel.Fletching[QuiverRenderData.SHAFTS];

	public QuiverLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models) {
		super(renderer);
		this.model = new QuiverModel(models.bakeLayer(QuiverModel.LAYER));
		for (int i = 0; i < fletchings.length; i++) {
			fletchings[i] = new QuiverModel.Fletching(models.bakeLayer(QuiverModel.FLETCHING_LAYER), i);
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot,
		float xRot) {
		QuiverRenderData data = state.getData(QuiverRenderData.KEY);
		if (data == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		if (!state.chestEquipment.isEmpty()) {
			// sit on top of a chestplate rather than inside it (the cape does the same)
			poseStack.translate(0.0F, -0.053125F, 0.06875F);
		}
		RenderType renderType = RenderTypes.entityCutout(TEXTURE);
		collector.submitModel(model, state, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		for (int i = 0; i < data.shafts(); i++) {
			collector.submitModel(fletchings[i], state, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY,
				data.fletchingColors()[i], null, state.outlineColor);
		}
		poseStack.popPose();
	}
}
