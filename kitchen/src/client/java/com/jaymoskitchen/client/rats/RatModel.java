package com.jaymoskitchen.client.rats;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.rats.Rat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The rat's model and moves, from the Rats mod (LGPL-3.0, by Alexthe666 and its maintainers; see LICENSE_rats): its
 * boxes and texture layout ({@code StaticRatModel}, texture 64x32), and its walk, run, sniff, sit-up-and-eat and
 * twitching nose and whiskers ({@code RatModel}), redone on vanilla model parts with Citadel's wave helpers written out
 * here. Faces -z, as vanilla models do.
 */
public class RatModel extends EntityModel<RatRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKitchen.id("rat"), "main");
	/** The Rats mod's walking, running and idle wave speeds and sizes. */
	private static final float SPEED_WALK = 1.0F, DEGREE_WALK = 0.3F, SPEED_RUN = 1.0F, DEGREE_RUN = 0.4F, SPEED_IDLE = 0.75F,
		DEGREE_IDLE = 0.15F;
	/** How far the sniffing neck dips and the nose twitches (degrees), and how fast (ticks per dip). */
	private static final float SNIFF_NECK_MIN = -30.0F, SNIFF_NECK_MAX = -60.0F, SNIFF_NOSE = 15.0F, SNIFF_TICKS = 6.0F;

	private final ModelPart body1, body2, neck, head, snout, nose, leftEar, rightEar, wisker1, wisker2, leftArm, rightArm, leftHand,
		rightHand, leftThigh, rightThigh, leftFoot, rightFoot, tail1, tail2;

	public RatModel(ModelPart root) {
		super(root);
		body1 = root.getChild("body");
		body2 = body1.getChild("lower_body");
		neck = body2.getChild("neck");
		head = neck.getChild("head");
		snout = head.getChild("snout");
		nose = snout.getChild("nose");
		wisker1 = snout.getChild("left_whisker");
		wisker2 = snout.getChild("right_whisker");
		leftEar = head.getChild("left_ear");
		rightEar = head.getChild("right_ear");
		leftArm = body2.getChild("left_arm");
		rightArm = body2.getChild("right_arm");
		leftHand = leftArm.getChild("left_hand");
		rightHand = rightArm.getChild("right_hand");
		leftThigh = body1.getChild("left_thigh");
		rightThigh = body1.getChild("right_thigh");
		leftFoot = leftThigh.getChild("left_foot");
		rightFoot = rightThigh.getChild("right_foot");
		tail1 = body1.getChild("upper_tail");
		tail2 = tail1.getChild("lower_tail");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body1 = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3.0F, 0.0F, 5, 5, 6),
			PartPose.offset(0.0F, 20.0F, 0.0F));
		PartDefinition body2 = body1.addOrReplaceChild("lower_body", CubeListBuilder.create().texOffs(22, 0).addBox(-2.0F, -2.0F, -5.0F, 4, 4, 5),
			PartPose.offset(0.0F, -0.4F, 0.5F));
		PartDefinition neck = body2.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(32, 17).addBox(-1.0F, -1.0F, -1.0F, 2, 2, 1),
			PartPose.offset(0.0F, 0.0F, -5.0F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 20).addBox(-2.0F, -1.5F, -2.5F, 4, 3, 3),
			PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.18656417F, 0.0F, 0.0F));
		head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(37, 20).addBox(-0.5F, -0.7F, -0.5F, 1, 1, 1),
			PartPose.offset(-2.0F, -0.5F, -1.6F));
		head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(37, 20).addBox(-0.5F, -0.7F, -0.5F, 1, 1, 1),
			PartPose.offset(2.0F, -0.5F, -1.6F));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(33, 20).mirror().addBox(0.0F, -2.0F, 0.0F, 2, 2, 0),
			PartPose.offsetAndRotation(1.5F, -0.5F, 0.0F, 0.0F, -Mth.HALF_PI / 2.0F, 0.0F));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(33, 20).addBox(-2.0F, -2.0F, 0.0F, 2, 2, 0),
			PartPose.offsetAndRotation(-1.5F, -0.5F, 0.0F, 0.0F, Mth.HALF_PI / 2.0F, 0.0F));
		PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(22, 26).addBox(-1.5F, -1.0F, -2.0F, 3, 2, 2),
			PartPose.offset(0.0F, 0.5F, -2.5F));
		snout.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 26).addBox(-0.5F, -0.3F, -1.0F, 1, 1, 2),
			PartPose.offset(0.0F, -0.6F, -2.1F));
		snout.addOrReplaceChild("left_whisker", CubeListBuilder.create().texOffs(40, 0).addBox(0.5F, -1.5F, 0.0F, 2, 3, 0),
			PartPose.offset(1.0F, 0.0F, -1.0F));
		snout.addOrReplaceChild("right_whisker", CubeListBuilder.create().texOffs(35, 0).addBox(-2.5F, -1.5F, 0.0F, 2, 3, 0),
			PartPose.offset(-1.0F, 0.0F, -1.0F));
		PartDefinition leftArm = body2.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 9).mirror().addBox(0.0F, 0.0F, -1.0F, 1, 2, 2),
			PartPose.offset(2.0F, 1.5F, -3.0F));
		leftArm.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(32, 13).mirror().addBox(-0.5F, 0.0F, -1.5F, 1, 1, 2),
			PartPose.offset(0.4F, 2.0F, -0.3F));
		PartDefinition rightArm = body2.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(32, 9).addBox(-1.0F, 0.0F, -1.0F, 1, 2, 2),
			PartPose.offset(-2.0F, 1.5F, -3.0F));
		rightArm.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(32, 13).addBox(-0.5F, 0.0F, -1.5F, 1, 1, 2),
			PartPose.offset(-0.4F, 2.0F, -0.3F));
		PartDefinition leftThigh = body1.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(22, 9).mirror().addBox(-1.0F, 0.0F, -1.5F, 2, 3, 3),
			PartPose.offset(2.5F, 0.0F, 5.0F));
		leftThigh.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(22, 15).mirror().addBox(-0.5F, 0.0F, -3.5F, 1, 1, 4),
			PartPose.offset(0.0F, 3.0F, 0.5F));
		PartDefinition rightThigh = body1.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(22, 9).addBox(-1.0F, 0.0F, -1.5F, 2, 3, 3),
			PartPose.offset(-2.5F, 0.0F, 5.0F));
		rightThigh.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(22, 15).addBox(-0.5F, 0.0F, -3.5F, 1, 1, 4),
			PartPose.offset(0.0F, 3.0F, 0.5F));
		PartDefinition tail1 = body1.addOrReplaceChild("upper_tail", CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -1.0F, 0.0F, 2, 2, 8),
			PartPose.offset(0.0F, 0.0F, 6.0F));
		tail1.addOrReplaceChild("lower_tail", CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -1.0F, 0.0F, 2, 2, 8),
			PartPose.offset(0.0F, 0.0F, 8.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(RatRenderer.State state) {
		super.setupAnim(state);
		float f = state.walkAnimationPos;
		float f1 = state.walkAnimationSpeed;
		float age = state.ageInTicks;
		float sit = state.sit;
		float sniff = state.sniff;

		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		head.yRot = state.yRot * Mth.DEG_TO_RAD;

		// sitting up on its haunches to nibble (the Rats mod's sit pose)
		progressRotation(rightEar, sit, -0.17453292F, 0.7853982F, -0.7853982F);
		progressRotation(leftEar, sit, -0.17453292F, -0.7853982F, 0.7853982F);
		progressRotation(leftThigh, sit, 1.134464F, 0.0F, 0.0F);
		progressRotation(rightThigh, sit, 1.134464F, 0.0F, 0.0F);
		progressRotation(body1, sit, -1.134464F, 0.0F, 0.0F);
		progressRotation(neck, sit, 0.091106184F, 0.0F, 0.0F);
		progressRotation(head, sit, 0.8285004F, 0.0F, 0.0F);
		progressRotation(leftArm, sit, 1.3089969F, 0.0F, 0.0F);
		progressRotation(rightArm, sit, 1.3089969F, 0.0F, 0.0F);
		progressRotation(rightHand, sit, 0.9599311F, -0.17453292F, 0.08726646F);
		progressRotation(leftHand, sit, 0.9599311F, 0.17453292F, -0.08726646F);
		progressRotation(body2, sit, 0.34906584F, 0.0F, 0.0F);
		progressRotation(tail2, sit, 0.20943952F, 0.61086524F, 0.0F);
		progressRotation(tail1, sit, 1.2F, 0.17453292F, 0.6981317F);
		progressPosition(body1, sit, 0.0F, 16.0F, 0.0F);
		progressPosition(leftThigh, sit, 2.5F, 0.0F, 4.5F);
		progressPosition(rightThigh, sit, -2.5F, 0.0F, 4.5F);

		if (state.nibbling) {
			// the Rats mod's eating: head bobbing, paws working at the food
			walk(neck, SPEED_IDLE * 1.5F, DEGREE_IDLE * 1.5F, true, 2.0F, -0.4F, age, 1.0F);
			walk(head, SPEED_IDLE * 1.5F, DEGREE_IDLE * 1.5F, true, 2.0F, -0.2F, age, 1.0F);
			walk(leftArm, SPEED_IDLE, DEGREE_IDLE * 0.5F, true, 1.0F, 0.0F, age, 1.0F);
			walk(rightArm, SPEED_IDLE, DEGREE_IDLE * 0.5F, true, 1.0F, 0.0F, age, 1.0F);
			walk(rightHand, SPEED_IDLE, DEGREE_IDLE * 0.5F, true, 0.0F, -0.1F, age, 1.0F);
			walk(leftHand, SPEED_IDLE, DEGREE_IDLE * 0.5F, true, 0.0F, -0.1F, age, 1.0F);
		}

		if (sniff > 0.0F) {
			// the Rats mod's idle sniff: the neck dipping between two angles, the nose twitching
			float wave = Mth.sin(age * Mth.TWO_PI / SNIFF_TICKS);
			float neckDeg = (SNIFF_NECK_MIN + SNIFF_NECK_MAX) / 2.0F + (SNIFF_NECK_MIN - SNIFF_NECK_MAX) / 2.0F * wave;
			float weight = sniff / Rat.SNIFF_FULL;
			neck.xRot += neckDeg * Mth.DEG_TO_RAD * weight;
			nose.xRot += SNIFF_NOSE * Mth.DEG_TO_RAD * wave * weight;
		}

		// the tail lifts a little as it moves
		float tailLift = Mth.clamp(-15.0F * Mth.DEG_TO_RAD + f1, -15.0F * Mth.DEG_TO_RAD, 15.0F * Mth.DEG_TO_RAD);
		tail1.xRot += tailLift;
		tail2.xRot -= tailLift / 2.0F;

		if (!state.dead) {
			float ulating = 0.9F + Mth.sin(age * 0.75F) * 0.1F;
			swing(wisker2, SPEED_IDLE, DEGREE_IDLE, false, 0.0F, 0.0F, age, 1.0F);
			swing(wisker1, SPEED_IDLE, DEGREE_IDLE, true, 0.0F, 0.0F, age, 1.0F);
			flap(wisker2, SPEED_IDLE, DEGREE_IDLE, false, 1.0F, 0.0F, age, 1.0F);
			flap(wisker1, SPEED_IDLE, DEGREE_IDLE, false, 1.0F, 0.0F, age, 1.0F);
			walk(wisker2, SPEED_IDLE, DEGREE_IDLE, false, 2.0F, 0.0F, age, 1.0F);
			walk(wisker1, SPEED_IDLE, DEGREE_IDLE, false, 2.0F, 0.0F, age, 1.0F);
			nose.xScale = nose.yScale = nose.zScale = ulating;
		}

		if (state.fleeing) {
			bob(body1, SPEED_RUN, DEGREE_RUN * 5.0F, f, f1);
			walk(body1, SPEED_RUN, DEGREE_RUN, false, 0.0F, 0.0F, f, f1);
			walk(body2, SPEED_RUN, DEGREE_RUN * 0.5F, false, 1.0F, -0.1F, f, f1);
			walk(neck, SPEED_RUN, DEGREE_RUN * 0.5F, false, 2.0F, 0.0F, f, f1);
			walk(tail1, SPEED_RUN, DEGREE_RUN, false, -1.0F, 0.0F, f, f1);
			walk(tail2, SPEED_RUN, DEGREE_RUN * 0.5F, false, -2.0F, 0.0F, f, f1);
			walk(leftThigh, SPEED_RUN, DEGREE_RUN * 2.0F, true, 0.0F, 0.0F, f, f1);
			walk(rightThigh, SPEED_RUN, DEGREE_RUN * 2.0F, true, 0.0F, 0.0F, f, f1);
			walk(rightFoot, SPEED_RUN, DEGREE_RUN * 2.0F, true, 3.0F, -0.1F, f, f1);
			walk(leftFoot, SPEED_RUN, DEGREE_RUN * 2.0F, true, 3.0F, -0.1F, f, f1);
			walk(leftArm, SPEED_RUN, DEGREE_RUN * 2.0F, true, 2.0F, 0.0F, f, f1);
			walk(rightArm, SPEED_RUN, DEGREE_RUN * 2.0F, true, 2.0F, 0.0F, f, f1);
			walk(rightHand, SPEED_RUN, DEGREE_RUN * 2.0F, true, 5.0F, -0.1F, f, f1);
			walk(leftHand, SPEED_RUN, DEGREE_RUN * 2.0F, true, 5.0F, -0.1F, f, f1);
		} else {
			walk(body1, SPEED_WALK, DEGREE_WALK * 0.25F, false, 0.0F, 0.0F, f, f1);
			walk(body2, SPEED_WALK, DEGREE_WALK * 0.25F, false, 1.0F, 0.1F, f, f1);
			walk(rightThigh, SPEED_WALK, DEGREE_WALK * 4.0F, false, 1.0F, 0.0F, f, f1);
			walk(rightFoot, SPEED_WALK, DEGREE_WALK * 2.0F, false, 3.5F, -0.1F, f, f1);
			walk(leftThigh, SPEED_WALK, DEGREE_WALK * 4.0F, true, 1.0F, 0.0F, f, f1);
			walk(leftFoot, SPEED_WALK, DEGREE_WALK * 2.0F, true, 3.5F, 0.1F, f, f1);
			if (!state.nibbling) {
				walk(rightArm, SPEED_WALK, DEGREE_WALK * 4.0F, true, 1.0F, 0.0F, f, f1);
				walk(rightHand, SPEED_WALK, DEGREE_WALK * 2.0F, true, 3.0F, 0.1F, f, f1);
				walk(leftArm, SPEED_WALK, DEGREE_WALK * 4.0F, false, 1.0F, 0.0F, f, f1);
				walk(leftHand, SPEED_WALK, DEGREE_WALK * 2.0F, false, 3.0F, -0.1F, f, f1);
			}
			walk(tail1, SPEED_WALK, DEGREE_WALK, false, -1.0F, -0.15F, f, f1);
			walk(tail2, SPEED_WALK, DEGREE_WALK * 0.5F, true, 0.0F, -0.15F, f, f1);
			walk(neck, SPEED_WALK, DEGREE_WALK * 0.25F, false, 2.0F, 0.0F, f, f1);
		}
	}

	/** Moves the pose to the rat's mouth, under its snout (call after {@link #setupAnim}). */
	public void translateToMouth(PoseStack poseStack) {
		root.translateAndRotate(poseStack);
		body1.translateAndRotate(poseStack);
		body2.translateAndRotate(poseStack);
		neck.translateAndRotate(poseStack);
		head.translateAndRotate(poseStack);
		snout.translateAndRotate(poseStack);
	}

	// Citadel's wave helpers (as the Rats mod's model used them), on vanilla parts

	private static float wave(float speed, float degree, boolean invert, float offset, float weight, float f, float f1) {
		float rotation = Mth.cos(f * speed + offset) * degree * f1 + weight * f1;
		return invert ? -rotation : rotation;
	}

	private static void walk(ModelPart part, float speed, float degree, boolean invert, float offset, float weight, float f, float f1) {
		part.xRot += wave(speed, degree, invert, offset, weight, f, f1);
	}

	private static void swing(ModelPart part, float speed, float degree, boolean invert, float offset, float weight, float f, float f1) {
		part.yRot += wave(speed, degree, invert, offset, weight, f, f1);
	}

	private static void flap(ModelPart part, float speed, float degree, boolean invert, float offset, float weight, float f, float f1) {
		part.zRot += wave(speed, degree, invert, offset, weight, f, f1);
	}

	private static void bob(ModelPart part, float speed, float degree, float f, float f1) {
		part.y += Mth.sin(f * speed) * f1 * degree - f1 * degree;
	}

	/** Eases a part from its rest pose toward a pose, {@code progress} of the way ({@link Rat#SIT_FULL} is all the way). */
	private static void progressRotation(ModelPart part, float progress, float x, float y, float z) {
		PartPose rest = part.getInitialPose();
		float k = progress / Rat.SIT_FULL;
		part.xRot += k * (x - rest.xRot());
		part.yRot += k * (y - rest.yRot());
		part.zRot += k * (z - rest.zRot());
	}

	private static void progressPosition(ModelPart part, float progress, float x, float y, float z) {
		PartPose rest = part.getInitialPose();
		float k = progress / Rat.SIT_FULL;
		part.x += k * (x - rest.x());
		part.y += k * (y - rest.y());
		part.z += k * (z - rest.z());
	}
}
