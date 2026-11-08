package com.jaymoskitchen.client.rats;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.rats.Rat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws a rat (the Rats mod's model and coats, see {@link RatModel}), with whatever it's stolen in its mouth. */
public class RatRenderer extends MobRenderer<Rat, RatRenderer.State, RatModel> {
	/** The coats, by {@link Rat#coat}: the Rats mod's wild brown, black and blue-grey rats. */
	static final Identifier[] COATS = {
		JaymosKitchen.id("textures/entity/rat/brown.png"),
		JaymosKitchen.id("textures/entity/rat/black.png"),
		JaymosKitchen.id("textures/entity/rat/blue.png")
	};
	static final float SHADOW = 0.25F;

	public static class State extends HoldingEntityRenderState {
		int coat;
		/** How far it's sat up (0-{@link Rat#SIT_FULL}) and into a sniff (0-{@link Rat#SNIFF_FULL}). */
		float sit, sniff;
		boolean nibbling, fleeing, dead;
	}

	public RatRenderer(EntityRendererProvider.Context context) {
		super(context, new RatModel(context.bakeLayer(RatModel.LAYER)), SHADOW);
		addLayer(new MouthLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Rat rat, State state, float partialTicks) {
		super.extractRenderState(rat, state, partialTicks);
		HoldingEntityRenderState.extractHoldingEntityRenderState(rat, state, itemModelResolver);
		state.coat = Math.floorMod(rat.coat(), COATS.length);
		state.sit = Mth.lerp(partialTicks, rat.sitProgressO, rat.sitProgress);
		state.sniff = Mth.lerp(partialTicks, rat.sniffProgressO, rat.sniffProgress);
		state.nibbling = rat.action() == Rat.NIBBLING;
		state.fleeing = rat.fleeing();
		state.dead = !rat.isAlive();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return COATS[state.coat];
	}

	/** The stolen food, held crosswise in its teeth. */
	static class MouthLayer extends RenderLayer<State, RatModel> {
		MouthLayer(RenderLayerParent<State, RatModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.heldItem.isEmpty()) {
				return;
			}
			poseStack.pushPose();
			getParentModel().translateToMouth(poseStack);
			// just under the front of the snout
			poseStack.translate(0.0F, 1.5F / 16.0F, -2.0F / 16.0F);
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.rotateDegrees(Axis.ZP, 90.0F);
			poseStack.scale(0.6F, 0.6F, 0.6F);
			state.heldItem.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
