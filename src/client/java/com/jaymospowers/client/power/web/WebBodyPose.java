package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A web slinger's whole-body moves ({@link WebPower#POSE}), drawn for everyone: Spider-Sense's dodges (a spinning
 * sidestep, a backflip into a crouch, a duck and roll, a weave) and the limp sprawl after a slingshot's floor gives
 * way. The body turns about its middle ({@link #rotate}); the limbs tuck and reach to match ({@link #applyModel}).
 */
public final class WebBodyPose {
	/** The move, which way it goes, and how far through it (0-1). */
	record Pose(int kind, int side, float p, float time) {
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_body_pose");

	private WebBodyPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		WebPose pose = entity.getAttached(WebPower.POSE);
		if (pose == null) {
			state.setData(KEY, null);
			return;
		}
		float time = entity.level().getGameTime() + partialTicks;
		float p = pose.progress(time);
		state.setData(KEY, p >= 0.0F && p < 1.0F ? new Pose(pose.kind(), pose.side(), p, time) : null);
	}

	/** Eased 0-1-0 over the move: in fast, out slower. */
	private static float swell(float p) {
		float e = p < 0.3F ? p / 0.3F : 1.0F - (p - 0.3F) / 0.7F;
		return e * e * (3.0F - 2.0F * e);
	}

	/** After the body faces its way: the whole body spins, flips, rolls or sways about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		float p = pose.p();
		float turn = p * p * (3.0F - 2.0F * p);
		switch (pose.kind()) {
			case WebPose.SPIN -> {
				// a full pirouette, leaning out of it
				poseStack.rotateDegrees(Axis.YP, pose.side() * 360.0F * turn);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 15.0F * swell(p));
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.BACKFLIP -> {
				// over backwards, landing low
				float flip = Math.min(1.0F, p / 0.8F);
				flip = flip * flip * (3.0F - 2.0F * flip);
				poseStack.translate(0.0F, middle + 0.6F * swell(p), 0.0F);
				poseStack.rotateDegrees(Axis.XP, 360.0F * flip);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.ROLL -> {
				// ducked, then tumbling over a shoulder along the ground
				float roll = Mth.clamp((p - 0.15F) / 0.7F, 0.0F, 1.0F);
				roll = roll * roll * (3.0F - 2.0F * roll);
				float low = swell(p);
				poseStack.translate(0.0F, middle * (1.0F - 0.55F * low), 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -pose.side() * 360.0F * roll);
				poseStack.rotateDegrees(Axis.XP, -35.0F * low);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case WebPose.WEAVE -> {
				// a quick bob and weave: down and to one side, back through to the other
				float s = Mth.sin(p * Mth.TWO_PI);
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, pose.side() * 30.0F * s);
				poseStack.rotateDegrees(Axis.XP, -20.0F * swell(p));
				poseStack.translate(0.0F, -middle - 0.25F * swell(p), 0.0F);
			}
			case WebPose.RAGDOLL -> {
				// flat on the back, a little twitch to it
				float fall = Math.min(1.0F, p * 6.0F);
				float twitch = Mth.sin(pose.time() * 0.8F) * 4.0F * (1.0F - p);
				poseStack.translate(0.0F, 0.2F * fall, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -90.0F * fall);
				poseStack.rotateDegrees(Axis.ZP, twitch);
			}
			default -> {
			}
		}
	}

	/** After the model animates: tucked for a flip or roll, arms out for the spin, limp for the ragdoll. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float e = swell(pose.p());
		switch (pose.kind()) {
			case WebPose.SPIN -> {
				set(h.rightArm, -0.3F, 0.0F, 1.3F, e);
				set(h.leftArm, -0.3F, 0.0F, -1.3F, e);
			}
			case WebPose.BACKFLIP, WebPose.ROLL -> {
				// knees to chest, arms hugging them
				set(h.rightLeg, -1.9F, 0.0F, 0.1F, e);
				set(h.leftLeg, -1.9F, 0.0F, -0.1F, e);
				set(h.rightArm, -1.6F, -0.3F, 0.2F, e);
				set(h.leftArm, -1.6F, 0.3F, -0.2F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, 0.6F);
			}
			case WebPose.WEAVE -> {
				// guard up
				set(h.rightArm, -1.4F, -0.4F, 0.2F, e);
				set(h.leftArm, -1.4F, 0.4F, -0.2F, e);
				set(h.rightLeg, -0.4F, 0.0F, 0.1F, e);
			}
			case WebPose.RAGDOLL -> {
				float t = pose.time();
				float give = 1.0F - pose.p() * 0.7F;
				h.rightArm.xRot = -0.4F + Mth.sin(t * 0.9F) * 0.5F * give;
				h.rightArm.zRot = 1.0F;
				h.leftArm.xRot = -0.3F + Mth.sin(t * 0.9F + 2.0F) * 0.5F * give;
				h.leftArm.zRot = -1.0F;
				h.rightLeg.xRot = 0.1F + Mth.sin(t * 0.7F + 1.0F) * 0.3F * give;
				h.rightLeg.zRot = 0.3F;
				h.leftLeg.xRot = 0.1F + Mth.sin(t * 0.7F + 3.0F) * 0.3F * give;
				h.leftLeg.zRot = -0.3F;
				h.head.xRot = 0.3F;
			}
			default -> {
			}
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
