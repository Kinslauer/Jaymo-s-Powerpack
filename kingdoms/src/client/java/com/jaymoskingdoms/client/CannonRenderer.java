package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Cannon;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
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
 * (0.7.321) A field cannon: iexogen's "Canon" (Blockbench Workshop, CC BY 4.0, credits in textures/entity/bb/CREDITS.txt), a
 * long cast barrel on a four-wheeled wooden carriage, drawn to the cannon's own size with its barrel swung on its
 * trunnions to its mark and kicking back when it fires. (Until 0.7.320 it was put together from blocks.)
 */
public final class CannonRenderer extends EntityRenderer<Cannon, CannonRenderer.State> {
	/** How far the barrel kicks back when it fires (blocks). */
	private static final float RECOIL = 0.35F;
	/** The barrel's muzzle, from its trunnions, in the model (pixels): it's drawn so this lands on {@link Cannon#MUZZLE}. */
	private static final float MODEL_MUZZLE = 23.3F;
	/** The model's barrel rests this far raised (degrees); taken off so its mark reads level at 0. */
	private static final float REST_RAISE = 5.0F;

	public static final class State extends EntityRenderState {
		float yaw;
		float pitch;
		float recoil;
	}

	private static BbModel bb;
	private static Model model;

	public CannonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Cannon entity, float partialTicks) {
		return entity.getBoundingBox().inflate(2.0);
	}

	@Override
	public void extractRenderState(Cannon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.pitch = Mth.lerp(partialTicks, entity.pitchO, entity.barrelPitch());
		float since = entity.sinceFired + partialTicks;
		state.recoil = since < Cannon.RECOIL_TICKS ? RECOIL * (1.0F - since / Cannon.RECOIL_TICKS) : 0.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (model == null) {
			bb = BbModel.load(JaymosKingdoms.MOD_ID, "canon");
			model = new Model(bb);
		}
		float size = scale();
		ModelPart barrel = bb.part("Barrel");
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.scale(size, size, size);
		// the trunnions over the cannon's middle, the wheels on the ground
		poseStack.translate(0.0F, 0.0F, -barrel.getInitialPose().z() / 16.0F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.5F, -1.5F, 0.0F);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(bb.texture), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** How big the model's drawn: its muzzle as far out as the cannon's. */
	static float scale() {
		return (float) Cannon.MUZZLE * 16.0F / MODEL_MUZZLE;
	}

	/** The canon, its barrel raised and kicked back by the state. */
	static final class Model extends EntityModel<State> {
		private final ModelPart barrel;

		Model(BbModel bb) {
			super(bb.root);
			this.barrel = bb.part("Barrel");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			this.barrel.xRot += (state.pitch - REST_RAISE) * Mth.DEG_TO_RAD;
			// back along the barrel's own line
			float back = state.recoil * 16.0F / scale();
			float raise = state.pitch * Mth.DEG_TO_RAD;
			this.barrel.z -= back * Mth.cos(raise);
			this.barrel.y += back * Mth.sin(raise);
		}
	}
}
