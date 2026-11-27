package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * The whole-body poses of the web attacks (Legends patch 2, hand-made: {@link WebPose#ZIP_KICK} to {@link WebPose#SURF}), drawn for
 * everyone beside the rest of {@link WebBodyPose}: {@link #rotate} turns the body about its middle, {@link #applyModel} sets the arms and
 * legs. A positive turn about X leans the body forward (a head-first dive), a negative one lays it back (feet first); a negative arm
 * or leg angle swings it forward and up.
 */
final class WebAttackPoses {
	private WebAttackPoses() {
	}

	/** Smoothstep of {@code x} clamped to 0-1. */
	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** 0 to 1 over {@code in}, held, then 1 to 0 from {@code out} to the end. */
	private static float hold(float p, float in, float out) {
		return p < in ? ease(p / in) : p > out ? ease(1.0F - (p - out) / (1.0F - out)) : 1.0F;
	}

	/** Turned about the middle of the body, by {@code degrees} about {@code axis}. */
	private static void about(PoseStack poseStack, float middle, Axis axis, float degrees) {
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(axis, degrees);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the body faces its way: the whole body of {@code pose} leans, flips or spins. {@code middle} is half its height. */
	static void rotate(WebBodyPose.Pose pose, PoseStack poseStack, float middle) {
		float p = pose.p();
		int side = pose.side() >= 0 ? 1 : -1;
		switch (pose.kind()) {
			case WebPose.ZIP_KICK -> about(poseStack, middle, Axis.XP, -55.0F * hold(p, 0.12F, 0.8F));
			case WebPose.SWING_UP_KICK -> about(poseStack, middle, Axis.XP, Mth.lerp(ease(p / 0.55F), 18.0F, -38.0F) * (1.0F - ease((p - 0.8F) / 0.2F)));
			case WebPose.PENDULUM_KICK -> {
				about(poseStack, middle, Axis.XP, -48.0F * hold(p, 0.2F, 0.7F));
				about(poseStack, middle, Axis.ZP, side * 12.0F * hold(p, 0.2F, 0.7F));
			}
			case WebPose.WEB_YANK_UP -> about(poseStack, middle, Axis.XP, -22.0F * hold(p, 0.2F, 0.7F));
			case WebPose.DIVE_SLAM -> about(poseStack, middle, Axis.XP, 78.0F * hold(p, 0.06F, 1.0F));
			case WebPose.PIN_THROW -> {
				about(poseStack, middle, Axis.XP, 14.0F * hold(p, 0.25F, 0.7F));
				about(poseStack, middle, Axis.ZP, side * 8.0F * hold(p, 0.25F, 0.7F));
			}
			case WebPose.DROP_KICK -> {
				// over backwards onto the head, the kick driven from there, then back upright
				poseStack.translate(0.0F, 0.35F * hold(p, 0.25F, 0.7F), 0.0F);
				about(poseStack, middle, Axis.XP, -175.0F * hold(p, 0.35F, 0.7F));
			}
			case WebPose.SHOCK_HOLD -> {
				float shudder = Mth.sin(pose.time() * 2.3F) * 2.0F;
				about(poseStack, middle, Axis.XP, -12.0F * hold(p, 0.05F, 0.95F));
				about(poseStack, middle, Axis.ZP, shudder * hold(p, 0.05F, 0.95F));
			}
			case WebPose.WRECK_SWING -> {
				poseStack.rotateDegrees(Axis.YP, side * pose.time() * 24.0F);
				about(poseStack, middle, Axis.ZP, side * 10.0F * hold(p, 0.05F, 0.95F));
			}
			case WebPose.WEB_STORM -> poseStack.rotateDegrees(Axis.YP, side * 720.0F * ease(p));
			case WebPose.FIST_JAB -> poseStack.rotateDegrees(Axis.YP, side * -14.0F * hold(p, 0.3F, 0.6F));
			case WebPose.FIST_KICK -> about(poseStack, middle, Axis.XP, -14.0F * hold(p, 0.3F, 0.6F));
			case WebPose.FIST_UPPER -> {
				poseStack.translate(0.0F, 0.12F * hold(p, 0.3F, 0.6F), 0.0F);
				about(poseStack, middle, Axis.XP, -10.0F * hold(p, 0.3F, 0.6F));
			}
			case WebPose.FIST_TWIST -> poseStack.rotateDegrees(Axis.YP, side * 55.0F * hold(p, 0.3F, 0.6F));
			case WebPose.FIST_CRUSH -> about(poseStack, middle, Axis.XP, 28.0F * hold(p, 0.35F, 0.7F));
			case WebPose.SENSE_TURN -> poseStack.rotateDegrees(Axis.YP, side * 180.0F * ease(p / 0.8F));
			case WebPose.HUNT_DIVE -> about(poseStack, middle, Axis.XP, 70.0F * hold(p, 0.1F, 0.9F));
			case WebPose.SURF -> {
				float sway = Mth.sin(pose.time() * 0.15F) * 5.0F;
				about(poseStack, middle, Axis.XP, 10.0F);
				about(poseStack, middle, Axis.ZP, sway);
			}
			default -> {
			}
		}
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	/** After the model animates: the arms and legs of {@code pose}. */
	static void applyModel(HumanoidModel<?> h, WebBodyPose.Pose pose) {
		float p = pose.p();
		int side = pose.side() >= 0 ? 1 : -1;
		switch (pose.kind()) {
			case WebPose.ZIP_KICK -> {
				float kick = hold(p, 0.12F, 0.8F);
				set(h.rightLeg, -1.55F, 0.0F, 0.05F, kick);
				set(h.leftLeg, -1.55F, 0.0F, -0.05F, kick);
				set(h.rightArm, -2.7F, 0.0F, 0.15F, kick);
				set(h.leftArm, 0.9F, 0.0F, -0.5F, kick);
			}
			case WebPose.SWING_UP_KICK -> {
				float up = hold(p, 0.15F, 0.8F);
				// one hand up on the web, the other flung out, the nearer knee driven up
				set(h.rightArm, -3.05F, 0.0F, 0.12F, up);
				set(h.leftArm, -0.4F, 0.0F, -1.2F, up);
				set(h.rightLeg, -1.6F * (p > 0.4F ? 1.0F : 0.4F), 0.0F, 0.1F, up);
				set(h.leftLeg, 0.3F, 0.0F, -0.1F, up);
			}
			case WebPose.PENDULUM_KICK -> {
				float swing = hold(p, 0.2F, 0.7F);
				set(h.rightArm, -3.05F, 0.0F, 0.1F, swing);
				set(h.leftArm, -3.05F, 0.0F, -0.1F, swing);
				set(h.rightLeg, -1.75F, 0.0F, 0.05F, swing);
				set(h.leftLeg, -1.45F, 0.0F, -0.05F, swing);
			}
			case WebPose.WEB_YANK_UP -> {
				float haul = hold(p, 0.2F, 0.7F);
				set(h.rightArm, -2.0F, -0.2F, 0.2F, haul);
				set(h.leftArm, -1.4F, 0.3F, -0.5F, haul);
				set(h.rightLeg, -0.4F, 0.0F, 0.15F, haul);
				set(h.leftLeg, 0.4F, 0.0F, -0.15F, haul);
			}
			case WebPose.DIVE_SLAM -> {
				float dive = hold(p, 0.06F, 1.0F);
				// both hands out ahead on the two lines, legs together and straight behind
				set(h.rightArm, -2.75F, 0.0F, 0.45F, dive);
				set(h.leftArm, -2.75F, 0.0F, -0.45F, dive);
				set(h.rightLeg, 0.1F, 0.0F, 0.04F, dive);
				set(h.leftLeg, 0.1F, 0.0F, -0.04F, dive);
				h.head.xRot = Mth.lerp(dive, h.head.xRot, -0.6F);
			}
			case WebPose.PIN_THROW -> {
				float throwing = hold(p, 0.25F, 0.7F);
				// the throwing arm swung hard across the body, the other drawn back
				set(side > 0 ? h.rightArm : h.leftArm, -1.55F, side * -0.9F, side * 0.2F, throwing);
				set(side > 0 ? h.leftArm : h.rightArm, 0.5F, 0.0F, side * -0.4F, throwing);
				set(h.rightLeg, -0.35F, 0.0F, 0.1F, throwing);
				set(h.leftLeg, 0.35F, 0.0F, -0.1F, throwing);
			}
			case WebPose.DROP_KICK -> {
				float flip = hold(p, 0.35F, 0.7F);
				set(h.rightLeg, -1.75F, 0.0F, 0.05F, flip);
				set(h.leftLeg, -0.5F, 0.0F, -0.15F, flip);
				set(h.rightArm, 0.6F, 0.0F, 0.6F, flip);
				set(h.leftArm, 0.6F, 0.0F, -0.6F, flip);
			}
			case WebPose.SHOCK_HOLD -> {
				float brace = hold(p, 0.05F, 0.95F);
				float tremble = Mth.sin(pose.time() * 2.1F) * 0.08F;
				set(h.rightArm, -1.5F + tremble, 0.0F, 0.3F, brace);
				set(h.leftArm, -1.5F - tremble, 0.0F, -0.3F, brace);
				set(h.rightLeg, -0.2F, 0.0F, 0.3F, brace);
				set(h.leftLeg, 0.3F, 0.0F, -0.3F, brace);
			}
			case WebPose.WRECK_SWING -> {
				float hang = hold(p, 0.03F, 0.97F);
				float t = pose.time();
				// both hands out ahead, hauling the ball round: they wheel with it
				set(h.rightArm, -1.3F + Mth.sin(t * 0.45F) * 0.25F, 0.5F, 0.5F, hang);
				set(h.leftArm, -1.3F - Mth.sin(t * 0.45F) * 0.25F, -0.5F, -0.5F, hang);
				set(h.rightLeg, 0.0F, 0.0F, 0.35F, hang);
				set(h.leftLeg, 0.0F, 0.0F, -0.35F, hang);
			}
			case WebPose.WEB_STORM -> {
				float out = hold(p, 0.15F, 0.8F);
				// both arms flung wide, spinning the lines out round them
				set(h.rightArm, -0.2F, 0.0F, 1.6F, out);
				set(h.leftArm, -0.2F, 0.0F, -1.6F, out);
				set(h.rightLeg, 0.0F, 0.0F, 0.3F, out);
				set(h.leftLeg, 0.0F, 0.0F, -0.3F, out);
			}
			case WebPose.FIST_JAB -> {
				float k = hold(p, 0.3F, 0.6F);
				set(side > 0 ? h.rightArm : h.leftArm, -1.55F, 0.0F, 0.0F, k);
				set(side > 0 ? h.leftArm : h.rightArm, -1.1F, 0.0F, side * -0.2F, k);
			}
			case WebPose.FIST_KICK -> {
				float k = hold(p, 0.3F, 0.6F);
				set(side > 0 ? h.rightLeg : h.leftLeg, -1.5F, 0.0F, 0.0F, k);
				set(h.rightArm, -1.0F, 0.0F, 0.3F, k);
				set(h.leftArm, -1.0F, 0.0F, -0.3F, k);
			}
			case WebPose.FIST_UPPER -> {
				float k = hold(p, 0.3F, 0.6F);
				set(side > 0 ? h.rightArm : h.leftArm, -3.0F, 0.0F, 0.15F, k);
				set(side > 0 ? h.leftArm : h.rightArm, -1.2F, 0.3F, -0.3F, k);
				set(side > 0 ? h.rightLeg : h.leftLeg, -0.4F, 0.0F, 0.0F, k);
			}
			case WebPose.FIST_TWIST -> {
				float k = hold(p, 0.3F, 0.6F);
				set(h.rightArm, -0.3F, 0.0F, 1.5F, k);
				set(h.leftArm, -0.3F, 0.0F, -1.5F, k);
				set(h.rightLeg, -0.6F, 0.0F, 0.4F, k);
				set(h.leftLeg, 0.2F, 0.0F, -0.1F, k);
			}
			case WebPose.FIST_CRUSH -> {
				// both fists up and over, then brought straight down
				float up = 1.0F - ease((p - 0.3F) / 0.2F);
				float k = hold(p, 0.1F, 0.8F);
				set(h.rightArm, Mth.lerp(up, -0.6F, -3.0F), 0.0F, 0.2F, k);
				set(h.leftArm, Mth.lerp(up, -0.6F, -3.0F), 0.0F, -0.2F, k);
				set(h.rightLeg, 0.0F, 0.0F, 0.3F, k);
				set(h.leftLeg, 0.0F, 0.0F, -0.3F, k);
			}
			case WebPose.SENSE_TURN -> {
				float k = hold(p, 0.2F, 0.7F);
				set(h.rightArm, -1.8F, 0.0F, 0.2F, k);
				set(h.leftArm, 0.3F, 0.0F, -0.8F, k);
				set(h.rightLeg, -0.3F, 0.0F, 0.2F, k);
				set(h.leftLeg, 0.3F, 0.0F, -0.2F, k);
			}
			case WebPose.HUNT_DIVE -> {
				float k = hold(p, 0.1F, 0.9F);
				// a spear: both arms together out ahead, legs together behind
				set(h.rightArm, -3.0F, 0.0F, 0.08F, k);
				set(h.leftArm, -3.0F, 0.0F, -0.08F, k);
				set(h.rightLeg, 0.15F, 0.0F, 0.03F, k);
				set(h.leftLeg, 0.15F, 0.0F, -0.03F, k);
				h.head.xRot = Mth.lerp(k, h.head.xRot, -0.5F);
			}
			case WebPose.SURF -> {
				float k = ease(p / 0.1F);
				// a surfer's stance on its back: knees bent, one foot ahead, arms out for balance
				float sway = Mth.sin(pose.time() * 0.15F) * 0.1F;
				set(h.rightLeg, -0.7F, 0.0F, 0.25F, k);
				set(h.leftLeg, 0.4F, 0.0F, -0.25F, k);
				set(h.rightArm, 0.1F, 0.0F, 1.2F + sway, k);
				set(h.leftArm, 0.1F, 0.0F, -1.2F - sway, k);
			}
			default -> {
			}
		}
	}
}
