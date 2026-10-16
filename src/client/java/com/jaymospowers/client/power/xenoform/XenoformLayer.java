package com.jaymospowers.client.power.xenoform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws the alien in a transformed player's place. {@link XenoformClient#extract} has already hidden the player's own
 * body (invisible, no armour, no held items), so only the form's model shows, in the player's pose and at the player's
 * (scaled) size. Pyre is drawn a second time, fullbright, from its glow skin. Through a form's last seconds ({@link XenoformClient#ENDING}) the whole alien pulses red, an emissive
 * pass over the body ({@link #glow}).
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
		if (model instanceof XenoFormModels.Pyre) {
			// Pyre is fire: its glow skin again, fullbright, so it shines in the dark
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.eyes(XenoFormModels.Pyre.GLOW), lightCoords, OverlayTexture.NO_OVERLAY,
				-1, null, 0);
		}
		boolean ending = Boolean.TRUE.equals(state.getData(XenoformClient.ENDING));
		if (ending) {
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.eyes(model.texture), lightCoords, OverlayTexture.NO_OVERLAY,
				glow(state.ageInTicks), null, 0);
		}
		if (model instanceof XenoFormModels.Wyrm) {
			// Wyrm's wings: the ender dragon's, small, from the game's own dragon texture
			WyrmWings wings = WyrmWings.get();
			renderColoredCutoutModel(wings, WyrmWings.TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			if (ending) {
				collector.order(1).submitModel(wings, state, poseStack, RenderTypes.eyes(WyrmWings.TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
					glow(state.ageInTicks), null, 0);
			}
		}
		// the body is drawn by now; let the shadow fall (EntityRenderer skips it for invisible states, after the layers)
		state.isInvisible = false;
	}

	/** The red of the ending glow: ARGB, its alpha pulsing about twice a second. */
	static int glow(float time) {
		float pulse = 0.5F + 0.5F * Mth.sin(time * 0.6F);
		int alpha = (int)((0.25F + 0.45F * pulse) * 255.0F);
		return alpha << 24 | 0xFF3828;
	}
}
