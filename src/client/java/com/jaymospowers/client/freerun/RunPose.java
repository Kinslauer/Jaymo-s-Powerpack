package com.jaymospowers.client.freerun;

import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.Slide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.11) The fast run and the slide as everyone sees them: a runner in a fast run ({@link FastRun#active}) leans
 * forward from the feet; a slider ({@link Slide#sliding}) lies in the swimming pose, arms straight ahead, legs together.
 * Both come from the synced flags, so they show on every player in view.
 */
public final class RunPose {
	/** The fast run's forward lean (degrees). */
	static final float LEAN_DEGREES = 14.0F;

	public static final RenderStateDataKey<Boolean> LEAN = RenderStateDataKey.create(() -> "jaymospowers:freerun_lean");
	public static final RenderStateDataKey<Boolean> SLIDE = RenderStateDataKey.create(() -> "jaymospowers:freerun_slide");

	private RunPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			return;
		}
		boolean slide = Slide.sliding(player);
		state.setData(SLIDE, slide ? Boolean.TRUE : null);
		state.setData(LEAN, !slide && FastRun.active(player) && !player.isFallFlying() && !player.isSwimming() ? Boolean.TRUE : null);
	}

	/** After the body faces its way: tip forward about the feet. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		if (state.getData(LEAN) != null && state.deathTime <= 0.0F) {
			poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES);
		}
	}

	/** After the model animates: the slide's arms and legs. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || !state.isVisuallySwimming
			|| state.getData(SLIDE) == null) {
			return;
		}
		set(h.rightArm, -(float)Math.PI, 0.0F, 0.12F);
		set(h.leftArm, -(float)Math.PI, 0.0F, -0.12F);
		set(h.rightLeg, 0.0F, 0.0F, 0.03F);
		set(h.leftLeg, 0.0F, 0.0F, -0.03F);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
