package com.jaymoskingdoms.client.whaling;

import com.jaymoskingdoms.whaling.WhaleCarcass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.206) Draws a whale's carcass ({@link WhaleCarcass}): the whale's own model, still and belly up, riding low in the
 * water, darker and redder the more of it has been flensed away.
 */
public final class WhaleCarcassRenderer extends EntityRenderer<WhaleCarcass, WhaleCarcassRenderer.State> {
	/** How far down the model is set (model units, after scaling) so its belly rides just above the water. */
	static final float SETTLE = -0.99F;
	/** Its tint when stripped to the bone. */
	static final int STRIPPED = 0xFF9A6060;

	public static final class State extends EntityRenderState {
		final GreatWhaleRenderer.State whale = new GreatWhaleRenderer.State();
		float yRot, left;
	}

	private final GreatWhaleModel model;

	WhaleCarcassRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new GreatWhaleModel(GreatWhaleModel.createBodyLayer().bakeRoot());
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WhaleCarcass carcass, State state, float partialTicks) {
		super.extractRenderState(carcass, state, partialTicks);
		state.yRot = carcass.getYRot(partialTicks);
		state.left = carcass.left();
		state.whale.variant = carcass.variant();
		state.whale.carcass = true;
		state.whale.ageInTicks = 0.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		// belly up: the living whale's (-1, -1, 1) flip, turned over, is no flip at all
		poseStack.scale(GreatWhaleRenderer.SCALE, GreatWhaleRenderer.SCALE, GreatWhaleRenderer.SCALE);
		poseStack.translate(0.0F, SETTLE, 0.0F);
		int tint = ARGB.srgbLerp(1.0F - Mth.clamp(state.left, 0.0F, 1.0F), 0xFFFFFFFF, STRIPPED);
		collector.submitModel(this.model, state.whale, poseStack, this.model.renderType(GreatWhaleRenderer.SKINS[Math.floorMod(state.whale.variant, GreatWhaleRenderer.SKINS.length)]),
			state.lightCoords, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(WhaleCarcass carcass, float partialTicks) {
		return super.getBoundingBoxForCulling(carcass, partialTicks).inflate(7.0, 1.0, 7.0);
	}
}
