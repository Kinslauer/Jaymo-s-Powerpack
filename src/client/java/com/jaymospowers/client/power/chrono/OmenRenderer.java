package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.bb.BbModel;
import com.jaymospowers.power.chrono.future.FutureOmen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * An Omen ({@link FutureOmen}) on its Blockbench model (Blockbench Workshop, CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt), red or blue: its own models, baked by tools/bbmodel_bake.py. It stands in the model's
 * rest pose and moves in code: legs stride with its walk, the cannon arms come up when it aims, both arms smash down when
 * it strikes, and its head follows where it looks. Dying, it holds the model's own "pose_1" stance (buckled, arms
 * thrown) as it goes down. The model stands 4.5 blocks, its entity's own height.
 */
public final class OmenRenderer extends MobRenderer<FutureOmen, OmenRenderer.State, OmenRenderer.Model> {
	private final Identifier texture;

	public static final class State extends LivingEntityRenderState {
		boolean aiming;
		float attackTicks;
	}

	public static OmenRenderer red(EntityRendererProvider.Context context) {
		return new OmenRenderer(context, "omen_red");
	}

	public static OmenRenderer blue(EntityRendererProvider.Context context) {
		return new OmenRenderer(context, "omen_blue");
	}

	private OmenRenderer(EntityRendererProvider.Context context, String name) {
		super(context, new Model(BbModel.load(JaymosPowers.MOD_ID, name)), 1.4F);
		this.texture = JaymosPowers.id("textures/entity/bb/" + name + ".png");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FutureOmen omen, State state, float partialTicks) {
		super.extractRenderState(omen, state, partialTicks);
		state.aiming = omen.aiming();
		state.attackTicks = omen.getAttackAnimationTick() > 0 ? omen.getAttackAnimationTick() - partialTicks : 0.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture;
	}

	/** It goes down as it stands, not tipped over. */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.walkAnimationSpeed >= 0.01F) {
			// a heavy, swaying stride
			float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
			poseStack.rotateDegrees(com.mojang.math.Axis.ZP, 1.8F * wave);
		}
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
			if (state.deathTime > 0.0F) {
				// the model's own stance, eased in over the first moments of the fall
				this.bb.play("pose_1", 0.0F, Math.min(1.0F, state.deathTime / 8.0F));
				return;
			}
			float swing = state.walkAnimationPos * 0.5F;
			float amount = Math.min(1.0F, state.walkAnimationSpeed * 2.2F);
			float stride = Mth.cos(swing) * 0.75F * amount;
			// legs: the thigh swings (negative is forward), the knee bends as it comes through
			this.bb.part("left_thigh_joint").xRot += -stride;
			this.bb.part("right_thigh_joint").xRot += stride;
			this.bb.part("left_calf_joint").xRot += Math.max(0.0F, stride) * 0.9F;
			this.bb.part("right_calf_joint").xRot += Math.max(0.0F, -stride) * 0.9F;
			// arms: they counter-swing, or come up level on the target, or smash
			float leftArm = stride * 0.5F;
			float rightArm = -stride * 0.5F;
			if (state.aiming) {
				leftArm = -1.35F + Mth.sin(state.ageInTicks * 0.9F) * 0.02F;
				rightArm = -1.45F + Mth.sin(state.ageInTicks * 0.9F + 1.0F) * 0.02F;
			}
			if (state.attackTicks > 0.0F) {
				float p = 1.0F - state.attackTicks / 12.0F;
				float slam = -2.5F * Mth.sin(p * Mth.PI);
				leftArm = slam;
				rightArm = slam;
			}
			this.bb.part("left_arm_joint").xRot += leftArm;
			this.bb.part("right_arm_joint").xRot += rightArm;
			// the head follows where it looks
			ModelHead.look(this.bb.part("head"), state.yRot, state.xRot);
		}
	}

	/** Turns a head to where the entity looks, as the vanilla models do. */
	private static final class ModelHead {
		static void look(net.minecraft.client.model.geom.ModelPart head, float yRot, float xRot) {
			head.yRot += Mth.clamp(yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
			head.xRot += Mth.clamp(xRot, -25.0F, 25.0F) * Mth.DEG_TO_RAD;
		}
	}
}
