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
 * (scaled) size, times the form's own {@code renderScale}. Pyre is drawn a second time, fullbright, from its glow skin; Wisp floats, made of ki ({@link WispKi}). Through a form's last seconds ({@link XenoformClient#ENDING}) the whole alien pulses red, an emissive
 * pass over the body ({@link #glow}).
 */
final class XenoformLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** (0.18.90) How brightly the chest's insignia lights as the hand comes up to it; it's full once the hand is on it. */
	static final float INSIGNIA_HELD = 0.3F;

	XenoformLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		XenoFormModels.FormModel model = XenoFormModels.of(state.getData(XenoformClient.FORM));
		if (model == null) {
			return;
		}
		boolean ultimate = Boolean.TRUE.equals(state.getData(XenoformClient.ULTIMATE));
		poseStack.pushPose();
		if (model.renderScale != 1.0F) {
			// a form built smaller than the player (Duplex) is drawn up from the feet (model y 24, 1.501 below here)
			poseStack.translate(0.0F, 1.501F, 0.0F);
			poseStack.scale(model.renderScale, model.renderScale, model.renderScale);
			poseStack.translate(0.0F, -1.501F, 0.0F);
		}
		if (model instanceof XenoFormModels.Wisp wisp) {
			// (0.18.90) Wisp floats, a body of ki under its skin
			WispKi.lift(poseStack, state);
			WispKi.submit(wisp, state, poseStack, collector);
		} else {
			// renderColoredCutoutModel, not the "copy layer" one: that skips invisible states, and the body was made invisible
			// (0.18.93) the Ultimate wears its armoured skin instead
			renderColoredCutoutModel(model, ultimate ? model.ultimateTexture : model.texture, poseStack, collector, lightCoords, state, -1, 1);
		}
		if (ultimate) {
			ultimate(model, poseStack, collector, lightCoords, state);
		}
		if (model instanceof XenoFormModels.Pyre) {
			// Pyre is fire: its glow skin again, fullbright, so it shines in the dark
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.eyes(XenoFormModels.Pyre.GLOW), lightCoords, OverlayTexture.NO_OVERLAY,
				-1, null, 0);
		}
		XenoformSlap.Pose slap = state.getData(XenoformSlap.KEY);
		if (slap != null && slap.chest()) {
			// (0.18.90) tapping the insignia on its chest: it lights under the hand (the body posed now, as it will be drawn)
			model.setupAnim(state);
			XenoformFlightPose.applyModel(model, state);
			XenoBand.submitInsignia(poseStack, collector, lightCoords, model, Math.max(INSIGNIA_HELD * slap.raise(), slap.strike()));
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
		poseStack.popPose();
		// the body is drawn by now; let the shadow fall (EntityRenderer skips it for invisible states, after the layers)
		state.isInvisible = false;
	}

	/**
	 * (0.18.93) The Ultimate over the alien: its plate seams glowing fullbright (pulsing slowly), and its spikes, horns and
	 * crests in a darker shade of its colour ({@link UltimateSpikes}).
	 */
	private static void ultimate(XenoFormModels.FormModel model, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state) {
		com.jaymospowers.power.xenoform.XenoForm form = com.jaymospowers.power.xenoform.XenoForms.get(model.id);
		int color = form != null ? form.color : 0xFFFFFF;
		float pulse = 0.75F + 0.25F * Mth.sin(state.ageInTicks * SEAM_PULSE);
		int seams = (int)(pulse * 255.0F) << 24 | 0xFFFFFF;
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(model.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			seams, null, 0);
		UltimateSpikes spikes = UltimateSpikes.of(model.id, model);
		if (spikes != null) {
			int r = (color >> 16 & 0xFF) * 3 / 5 + 60, g = (color >> 8 & 0xFF) * 3 / 5 + 60, b = (color & 0xFF) * 3 / 5 + 60;
			renderColoredCutoutModel(spikes, UltimateSpikes.TEXTURE, poseStack, collector, lightCoords, state, 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8
				| Math.min(255, b), 1);
		}
	}

	/** The Ultimate's seams pulse this fast (radians a tick); fullbright light. */
	private static final float SEAM_PULSE = 0.15F;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** The red of the ending glow: ARGB, its alpha pulsing about twice a second. */
	static int glow(float time) {
		float pulse = 0.5F + 0.5F * Mth.sin(time * 0.6F);
		int alpha = (int)((0.25F + 0.45F * pulse) * 255.0F);
		return alpha << 24 | 0xFF3828;
	}
}
