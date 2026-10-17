package com.jaymospowers.client.armory;

import com.jaymospowers.armory.BombGlider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * The bomb glider ({@link BombGliderModel}): turned to its heading, its nose tipped with its climb or dive, banked into
 * its turns, its bat wings held up in a shallow V and beating slowly (faster under power).
 */
public class BombGliderRenderer extends EntityRenderer<BombGlider, BombGliderRenderer.State> {
	/** The wings' rest above level, and their beat: how far, and how fast gliding and under power (degrees, radians a tick). */
	static final float DIHEDRAL = 8.0F, FLAP = 6.0F, FLAP_RATE = 0.12F, FLAP_RATE_POWERED = 0.3F;

	public static class State extends EntityRenderState {
		float yaw;
		float pitch;
		float roll;
		boolean burning;
		boolean landed;
	}

	public BombGliderRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.8F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(BombGlider entity, float partialTicks) {
		// the wings reach well past its box
		return entity.getBoundingBox().inflate(1.5, 0.5, 1.5);
	}

	@Override
	public void extractRenderState(BombGlider entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.pitch = entity.getXRot(partialTicks);
		state.roll = Mth.lerp(partialTicks, entity.rollO, entity.roll);
		state.burning = entity.burning();
		state.landed = entity.landed();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, (float)BombGlider.DECK, 0.0F);
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.rotateDegrees(Axis.XP, state.pitch);
		poseStack.rotateDegrees(Axis.ZP, state.roll);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		var look = RenderTypes.entityCutout(BombGliderModel.TEXTURE);
		collector.submitModelPart(BombGliderModel.body(), poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		float beat = state.landed ? 0.0F : Mth.sin(state.ageInTicks * (state.burning ? FLAP_RATE_POWERED : FLAP_RATE)) * FLAP;
		float wing = (state.landed ? DIHEDRAL * 0.5F : DIHEDRAL) + beat;
		// (y is down here: a wing tip rises as it turns toward -y)
		poseStack.pushPose();
		poseStack.translate(BombGliderModel.HINGE / 16.0F, BombGliderModel.HINGE_Y / 16.0F, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, -wing);
		collector.submitModelPart(BombGliderModel.wingRight(), poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.translate(-BombGliderModel.HINGE / 16.0F, BombGliderModel.HINGE_Y / 16.0F, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, wing);
		collector.submitModelPart(BombGliderModel.wingLeft(), poseStack, look, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
