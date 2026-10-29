package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.bb.BbModel;
import com.jaymospowers.power.chrono.future.FutureWorky;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * A Worky drone ({@link FutureWorky}) on its Blockbench model (Blockbench Workshop, CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt): its own idle, fly and wave animations, picked by the state the server keeps, drawn at
 * {@link #SCALE} (the model stands over two blocks). It leans into its flight.
 */
public final class WorkyRenderer extends MobRenderer<FutureWorky, WorkyRenderer.State, WorkyRenderer.Model> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/bb/worky_drone.png");
	static final float SCALE = 0.45F;

	public static final class State extends LivingEntityRenderState {
		byte mode;
		float lean;
	}

	public WorkyRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosPowers.MOD_ID, "worky_drone")), 0.3F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FutureWorky drone, State state, float partialTicks) {
		super.extractRenderState(drone, state, partialTicks);
		state.mode = drone.state();
		state.lean = (float) Math.min(1.0, drone.getDeltaMovement().horizontalDistance() * 2.5);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** It falls dying as it is, not tipped over. */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		poseStack.rotateDegrees(Axis.XP, 18.0F * state.lean);
	}

	static final class Model extends EntityModel<State> {
		private final BbModel bb;

		Model(BbModel bb) {
			super(bb.root);
			this.bb = bb;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			String anim = state.mode == FutureWorky.STATE_WAVE ? "wave" : state.mode == FutureWorky.STATE_FLY ? "fly" : "idle";
			this.bb.play(anim, state.ageInTicks / 20.0F, 1.0F);
		}
	}
}
