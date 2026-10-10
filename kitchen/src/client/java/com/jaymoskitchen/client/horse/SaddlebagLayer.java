package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.Saddlebags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws saddlebags on a horse wearing them in its armor slot. */
public class SaddlebagLayer extends RenderLayer<EquineRenderState, EntityModel<EquineRenderState>> {
	private static final Identifier TEXTURE = JaymosKitchen.id("textures/entity/saddlebag.png");

	private final SaddlebagModel model;

	public SaddlebagLayer(RenderLayerParent<EquineRenderState, EntityModel<EquineRenderState>> renderer, EntityModelSet models) {
		super(renderer);
		this.model = new SaddlebagModel(models.bakeLayer(SaddlebagModel.LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, EquineRenderState state, float yRot,
		float xRot) {
		if (state.isInvisible || state.isBaby || !state.bodyArmorItem.is(Saddlebags.SADDLEBAG)) {
			return;
		}
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			-1, null, state.outlineColor);
	}
}
