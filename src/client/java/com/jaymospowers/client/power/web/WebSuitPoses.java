package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The poses of the symbiote's suit moves and the suit-up (Legends patch 4, our own hand-made poses): arms crossed then thrown
 * out as a suit forms ({@link WebPose#SUIT_UP}), a rush along the ground with a foe dragged behind and then heaved overhead
 * ({@link WebPose#GRIND_RUSH}, {@link WebPose#GRIND_LIFT}), thrown at a foe ({@link WebPose#LUNGE}), both arms snatching and
 * flinging ({@link WebPose#TEMPEST}), a foot stamping a fire out ({@link WebPose#SNUFF}) and standing up on two tendrils with
 * the legs hanging ({@link WebPose#STILTS}; {@code p} is how high). Called from {@link WebBodyPose}'s fall-through.
 */
final class WebSuitPoses {
	private WebSuitPoses() {
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** 0 → 1 over {@code in}, held, then 1 → 0 from {@code out} to the end. */
	private static float hold(float p, float in, float out) {
		return p < in ? ease(p / in) : p > out ? ease(1.0F - (p - out) / (1.0F - out)) : 1.0F;
	}

	private static void about(PoseStack poseStack, float middle, Axis axis, float degrees) {
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(axis, degrees);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	/** After the body faces its way: the whole body leans or turns. */
	static void rotate(WebBodyPose.Pose pose, PoseStack poseStack, float middle) {
		float p = pose.p();
		switch (pose.kind()) {
			case WebPose.SUIT_UP -> {
				// folded in as it gathers, then rocked back as the arms are thrown out
				float gather = hold(p, 0.25F, 0.5F) * (p < 0.5F ? 1.0F : 0.0F);
				float fling = p < 0.5F ? 0.0F : hold((p - 0.5F) / 0.5F, 0.2F, 0.6F);
				about(poseStack, middle, Axis.XP, -12.0F * gather + 8.0F * fling);
			}
			case WebPose.GRIND_RUSH -> about(poseStack, middle, Axis.XP, -28.0F * hold(p, 0.15F, 0.85F));
			case WebPose.GRIND_LIFT -> {
				// bent over to take it, then back and up as it goes overhead, forward again as it is driven down
				float bend = p < 0.2F ? ease(p / 0.2F) : 0.0F;
				float rear = p < 0.2F ? 0.0F : p < 0.62F ? ease((p - 0.2F) / 0.2F) : 0.0F;
				float drive = p < 0.62F ? 0.0F : hold((p - 0.62F) / 0.38F, 0.3F, 0.7F);
				about(poseStack, middle, Axis.XP, -22.0F * bend + 14.0F * rear - 30.0F * drive);
			}
			case WebPose.LUNGE -> about(poseStack, middle, Axis.XP, -62.0F * hold(p, 0.15F, 0.8F));
			case WebPose.TEMPEST -> {
				float shudder = Mth.sin(pose.time() * 2.7F) * 3.0F * hold(p, 0.2F, 0.55F);
				float fling = p < 0.55F ? 0.0F : hold((p - 0.55F) / 0.45F, 0.25F, 0.6F);
				about(poseStack, middle, Axis.ZP, shudder);
				about(poseStack, middle, Axis.XP, 14.0F * fling);
			}
			case WebPose.SNUFF -> about(poseStack, middle, Axis.XP, -14.0F * hold(p, 0.25F, 0.7F));
			default -> {
			}
		}
	}

	/** After the model animates: the arms and legs of {@code pose}. */
	static void applyModel(HumanoidModel<?> h, WebBodyPose.Pose pose) {
		float p = pose.p();
		switch (pose.kind()) {
			case WebPose.SUIT_UP -> {
				// the arms crossed tight over the chest while it forms; then thrown wide and up as it finishes, legs braced
				float cross = p < 0.5F ? hold(p, 0.2F, 0.45F) : 0.0F;
				float tremble = Mth.sin(pose.time() * 3.1F) * 0.06F * cross;
				float out = p < 0.5F ? 0.0F : hold((p - 0.5F) / 0.5F, 0.2F, 0.6F);
				set(h.rightArm, -1.55F + tremble, -0.85F, 0.1F, cross);
				set(h.leftArm, -1.55F - tremble, 0.85F, -0.1F, cross);
				h.head.xRot = Mth.lerp(cross, h.head.xRot, 0.35F);
				set(h.rightArm, -0.3F, 0.0F, 1.9F, out);
				set(h.leftArm, -0.3F, 0.0F, -1.9F, out);
				set(h.rightLeg, 0.0F, 0.0F, 0.3F, out);
				set(h.leftLeg, 0.0F, 0.0F, -0.3F, out);
				h.head.xRot = Mth.lerp(out, h.head.xRot, -0.4F);
			}
			case WebPose.GRIND_RUSH -> {
				// both fists forward and low on the hold, one leg driving, the head down on it
				float e = hold(p, 0.15F, 0.85F);
				float stride = Mth.sin(pose.time() * 1.1F) * 0.7F;
				set(h.rightArm, -1.1F, -0.25F, 0.15F, e);
				set(h.leftArm, -1.2F, 0.3F, -0.1F, e);
				h.rightLeg.xRot = Mth.lerp(e, h.rightLeg.xRot, stride);
				h.leftLeg.xRot = Mth.lerp(e, h.leftLeg.xRot, -stride);
				h.head.xRot = Mth.lerp(e, h.head.xRot, 0.5F);
			}
			case WebPose.GRIND_LIFT -> {
				// hands down at the hold, then both thrown up overhead, then driven down in front
				float take = p < 0.2F ? ease(p / 0.2F) : 0.0F;
				float up = p < 0.2F ? 0.0F : p < 0.62F ? ease((p - 0.2F) / 0.2F) : 0.0F;
				float down = p < 0.62F ? 0.0F : hold((p - 0.62F) / 0.38F, 0.3F, 0.7F);
				set(h.rightArm, -0.8F, 0.0F, 0.1F, take);
				set(h.leftArm, -0.8F, 0.0F, -0.1F, take);
				set(h.rightArm, -3.0F, 0.0F, 0.12F, up);
				set(h.leftArm, -3.0F, 0.0F, -0.12F, up);
				set(h.rightArm, -0.5F, 0.0F, 0.1F, down);
				set(h.leftArm, -0.5F, 0.0F, -0.1F, down);
				float brace = Math.max(take, down);
				set(h.rightLeg, -0.3F, 0.0F, 0.25F, brace);
				set(h.leftLeg, 0.3F, 0.0F, -0.25F, brace);
				h.head.xRot = Mth.lerp(down, h.head.xRot, 0.5F);
			}
			case WebPose.LUNGE -> {
				// laid out flat after it: arms thrown ahead, legs trailing
				float e = hold(p, 0.15F, 0.8F);
				set(h.rightArm, -3.0F, 0.0F, 0.1F, e);
				set(h.leftArm, -3.0F, 0.0F, -0.1F, e);
				set(h.rightLeg, 0.5F, 0.0F, 0.08F, e);
				set(h.leftLeg, 0.4F, 0.0F, -0.08F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, -0.5F);
			}
			case WebPose.TEMPEST -> {
				// both arms thrown wide to snatch, drawn in across the body, then flung up together
				float snatch = hold(p, 0.2F, 0.5F) * (p < 0.55F ? 1.0F : 0.0F);
				float fling = p < 0.55F ? 0.0F : hold((p - 0.55F) / 0.45F, 0.25F, 0.6F);
				set(h.rightArm, -0.2F, 0.0F, 1.6F, snatch);
				set(h.leftArm, -0.2F, 0.0F, -1.6F, snatch);
				set(h.rightArm, -3.1F, 0.0F, 0.3F, fling);
				set(h.leftArm, -3.1F, 0.0F, -0.3F, fling);
				set(h.rightLeg, 0.0F, 0.0F, 0.3F, Math.max(snatch, fling));
				set(h.leftLeg, 0.0F, 0.0F, -0.3F, Math.max(snatch, fling));
				h.head.xRot = Mth.lerp(fling, h.head.xRot, -0.5F);
			}
			case WebPose.SNUFF -> {
				// one foot lifted and brought down, an arm out low to steady it
				float e = hold(p, 0.25F, 0.7F);
				float stamp = Mth.abs(Mth.sin(p * Mth.PI * 2.0F));
				h.rightLeg.xRot = Mth.lerp(e, h.rightLeg.xRot, -1.0F * stamp);
				set(h.leftArm, 0.2F, 0.0F, -0.9F, e);
				set(h.rightArm, 0.1F, 0.0F, 0.5F, e);
			}
			case WebPose.STILTS -> {
				// carried up on the tendrils: legs hanging together and a touch apart, arms loose; they sway
				float e = Mth.clamp(p * 4.0F, 0.0F, 1.0F);
				float sway = Mth.sin(pose.time() * 0.12F) * 0.04F;
				set(h.rightLeg, 0.08F + sway, 0.0F, 0.07F, e);
				set(h.leftLeg, -0.02F - sway, 0.0F, -0.07F, e);
				set(h.rightArm, 0.05F + sway, 0.0F, 0.25F, e);
				set(h.leftArm, 0.05F - sway, 0.0F, -0.25F, e);
			}
			default -> {
			}
		}
	}
}
