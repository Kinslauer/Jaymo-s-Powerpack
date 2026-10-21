package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * A player carrying a corpse holds both arms out in front, under it. Set on the player's render state by
 * {@code AvatarRendererMixin}; applied (to their body and their armour alike) after the model animates.
 * <p>
 * (0.5.9) Eased and weighted ({@link PoseEase}): the arms go down to take it and lift it, then hold it with a strain;
 * each step the arms dip under the weight and the body leans back against it; letting go, the arms ease back down.
 */
public final class CarryPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:carrying");

	/** Holding it: forward and down (radians), angled in, and the strain's tremble (radians, per tick). */
	static final float HOLD = -0.8F, HOLD_IN = 0.3F, STRAIN = 0.03F, STRAIN_RATE = 0.067F;
	/** Taking it up: the arms lower first (radians) for this long (seconds), then lift into the hold by this. */
	static final float TAKE = -0.35F, TAKE_TIME = 0.2F, LIFT_TIME = 0.5F;
	/** Walking with it: the arms' dip a step, the lean back against the weight and the head's bow (radians). */
	static final float STEP_DIP = 0.12F, LEAN_BACK = -0.06F, HEAD_BOW = 0.08F;
	/** The legs' stride shortened under the load (share of the walk kept). */
	static final float STRIDE_KEPT = 0.8F;

	private static final PoseEase EASE = new PoseEase();

	private CarryPose() {
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		boolean on = Boolean.TRUE.equals(avatar.getData(KEY));
		PoseEase.Ease ease = EASE.step(avatar.id, on);
		if (ease == null) {
			return;
		}
		float w = ease.weight;
		float t = ease.age();
		// taking it up: down to it, then lifting; set down, the arms are lowered as the weight eases off
		float lift = on ? smooth((t - TAKE_TIME) / (LIFT_TIME - TAKE_TIME)) : 1.0F;
		float arm = Mth.lerp(lift, TAKE, HOLD);
		float strain = Mth.sin(avatar.ageInTicks * STRAIN_RATE) * STRAIN;
		float speed = Mth.clamp(avatar.walkAnimationSpeed, 0.0F, 1.0F);
		float step = Math.abs(Mth.sin(avatar.walkAnimationPos * 0.6662F)) * speed;
		float dip = step * STEP_DIP * lift;
		set(humanoid.rightArm, arm + strain + dip, -HOLD_IN, 0.0F, w);
		set(humanoid.leftArm, arm - strain + dip, HOLD_IN, 0.0F, w);
		float k = Math.min(1.0F, w);
		humanoid.body.xRot += LEAN_BACK * lift * k;
		humanoid.head.xRot += HEAD_BOW * k;
		humanoid.rightLeg.xRot *= Mth.lerp(k, 1.0F, STRIDE_KEPT);
		humanoid.leftLeg.xRot *= Mth.lerp(k, 1.0F, STRIDE_KEPT);
	}

	private static float smooth(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static void set(ModelPart part, float x, float y, float z, float w) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
