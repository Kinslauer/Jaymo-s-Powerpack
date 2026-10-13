package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.5.56) The silver skeleton, from {@code MoCModelSilverSkeleton} of
 * DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3: its boxes as they were, and its
 * animation as it was: long strides, each arm's sword cutting down in turn.
 */
final class SilverSkeletonModel extends EntityModel<LegendState> {
	private static final float DEG = 57.29578F;

	private final ModelPart head, body, back, rightArm, rightHand, rightSwordA, rightSwordB, rightSwordC, leftArm, leftHand, leftSwordA, leftSwordB, leftSwordC, rightThigh, rightKnee, rightLeg, rightFoot, leftThigh, leftKnee, leftLeg, leftFoot;

	SilverSkeletonModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.back = root.getChild("back");
		this.rightArm = root.getChild("right_arm");
		this.rightHand = root.getChild("right_hand");
		this.rightSwordA = root.getChild("right_sword_a");
		this.rightSwordB = root.getChild("right_sword_b");
		this.rightSwordC = root.getChild("right_sword_c");
		this.leftArm = root.getChild("left_arm");
		this.leftHand = root.getChild("left_hand");
		this.leftSwordA = root.getChild("left_sword_a");
		this.leftSwordB = root.getChild("left_sword_b");
		this.leftSwordC = root.getChild("left_sword_c");
		this.rightThigh = root.getChild("right_thigh");
		this.rightKnee = root.getChild("right_knee");
		this.rightLeg = root.getChild("right_thigh").getChild("right_leg");
		this.rightFoot = root.getChild("right_thigh").getChild("right_leg").getChild("right_foot");
		this.leftThigh = root.getChild("left_thigh");
		this.leftKnee = root.getChild("left_knee");
		this.leftLeg = root.getChild("left_thigh").getChild("left_leg");
		this.leftFoot = root.getChild("left_thigh").getChild("left_leg").getChild("left_foot");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -2, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0).addBox(-4, 0, -2, 8, 12, 4), PartPose.offset(0, -2, 0));
		root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(44, 54).addBox(-4, -4, 0.5F, 8, 8, 2), PartPose.offsetAndRotation(0, 2, 2, -0.1570796F, 0, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 31).addBox(-3, -2.5F, -2.5F, 4, 11, 4), PartPose.offset(-5, 1, 0));
		root.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(24, 16).addBox(-2.5F, -2, -2, 3, 12, 3), PartPose.offset(-5, 1, 0));
		root.addOrReplaceChild("right_sword_a", CubeListBuilder.create().texOffs(52, 46).addBox(-1.5F, 8.5F, -3, 1, 1, 5), PartPose.offset(-5, 1, 0));
		root.addOrReplaceChild("right_sword_b", CubeListBuilder.create().texOffs(48, 50).addBox(-1.5F, 7.5F, -4, 1, 3, 1), PartPose.offset(-5, 1, 0));
		root.addOrReplaceChild("right_sword_c", CubeListBuilder.create().texOffs(28, 28).addBox(-1, 7.5F, -14, 0, 3, 10), PartPose.offset(-5, 1, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 16).addBox(-1, -2.5F, -2.5F, 4, 11, 4), PartPose.offset(5, 1, 0));
		root.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(36, 16).addBox(-0.5F, -2, -2, 3, 12, 3), PartPose.offset(5, 1, 0));
		root.addOrReplaceChild("left_sword_a", CubeListBuilder.create().texOffs(52, 46).addBox(0.5F, 8.5F, -3, 1, 1, 5), PartPose.offset(5, 1, 0));
		root.addOrReplaceChild("left_sword_b", CubeListBuilder.create().texOffs(48, 46).addBox(0.5F, 7.5F, -4, 1, 3, 1), PartPose.offset(5, 1, 0));
		root.addOrReplaceChild("left_sword_c", CubeListBuilder.create().texOffs(28, 31).addBox(1, 7.5F, -14, 0, 3, 10), PartPose.offset(5, 1, 0));
		PartDefinition right_thigh = root.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, 0, -1.5F, 3, 6, 3), PartPose.offset(-2, 10.5F, 0));
		root.addOrReplaceChild("right_knee", CubeListBuilder.create().texOffs(0, 46).addBox(-2, 1, -2, 4, 4, 4), PartPose.offset(-2, 10.5F, 0));
		PartDefinition right_leg = right_thigh.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 25).addBox(-1.5F, 0, -1.5F, 3, 6, 3), PartPose.offset(0, 6, 0));
		right_leg.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(0, 54).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 2, 0));
		PartDefinition left_thigh = root.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(12, 16).addBox(-1.5F, 0, -1.5F, 3, 6, 3), PartPose.offset(2, 10.5F, 0));
		root.addOrReplaceChild("left_knee", CubeListBuilder.create().texOffs(16, 46).addBox(-2, 1, -2, 4, 4, 4), PartPose.offset(2, 10.5F, 0));
		PartDefinition left_leg = left_thigh.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(12, 25).addBox(-1.5F, 0, -1.5F, 3, 6, 3), PartPose.offset(0, 6, 0));
		left_leg.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(16, 54).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 2, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float leftAttack = state.leftAttack, rightAttack = state.rightAttack;
		boolean riding = state.isPassenger;
		float hRotY = state.yRot / 57.29578F;
		float hRotX = state.xRot / 57.29578F;

		this.head.xRot = hRotX;
		this.head.yRot = hRotY;

		float RLegXRot = Mth.cos((state.walkAnimationPos * 0.6662F) + 3.141593F) * 0.8F * state.walkAnimationSpeed;
		float LLegXRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.8F * state.walkAnimationSpeed;
		// float ClothRot = Mth.cos(state.walkAnimationPos * 0.9F) * 0.6F * state.walkAnimationSpeed;

		float RLegXRotB = RLegXRot;
		float LLegXRotB = LLegXRot;

		if (leftAttack == 0) {
			this.leftArm.zRot = (Mth.cos(state.ageInTicks * 0.09F) * 0.05F) - 0.05F;
			this.leftArm.xRot = RLegXRot;
		} else {
			float armMov = -(Mth.cos((leftAttack) * 0.18F) * 3F);
			this.leftArm.xRot = +armMov;
		}

		if (rightAttack == 0) {
			this.rightArm.zRot = -(Mth.cos(state.ageInTicks * 0.09F) * 0.05F) + 0.05F;
			this.rightArm.xRot = LLegXRot;

		} else {
			float armMov = -(Mth.cos((rightAttack) * 0.18F) * 3F);
			this.rightArm.xRot = +armMov;
		}

		this.leftHand.xRot =
				this.leftSwordA.xRot = this.leftSwordB.xRot = this.leftSwordC.xRot = this.leftArm.xRot;
		this.leftHand.zRot =
				this.leftSwordA.zRot = this.leftSwordB.zRot = this.leftSwordC.zRot = this.leftArm.zRot;

		this.rightHand.xRot =
				this.rightSwordA.xRot = this.rightSwordB.xRot = this.rightSwordC.xRot = this.rightArm.xRot;
		this.rightHand.zRot =
				this.rightSwordA.zRot = this.rightSwordB.zRot = this.rightSwordC.zRot = this.rightArm.zRot;

		if (riding) {
			this.rightLeg.xRot = 0F;

			this.rightThigh.xRot = -60F / DEG;
			this.rightThigh.yRot = 20F / DEG;
			this.rightKnee.yRot = 20F / DEG;
			this.rightKnee.xRot = -60F / DEG;
			this.leftLeg.xRot = 0F;
			this.leftThigh.yRot = -20F / DEG;
			this.leftKnee.yRot = -20F / DEG;
			this.leftThigh.xRot = -60F / DEG;
			this.leftKnee.xRot = -60F / DEG;
		} else {
			this.rightThigh.yRot = 0F;
			this.rightKnee.yRot = 0F;
			this.leftThigh.yRot = 0F;
			this.leftKnee.yRot = 0F;
			this.rightThigh.xRot = RLegXRot;
			this.leftThigh.xRot = LLegXRot;
			this.rightKnee.xRot = this.rightThigh.xRot;
			this.leftKnee.xRot = this.leftThigh.xRot;

			float RLegXRot2 = Mth.cos(((state.walkAnimationPos + 0.1F) * 0.6662F) + 3.141593F) * 0.8F * state.walkAnimationSpeed;
			float LLegXRot2 = Mth.cos((state.walkAnimationPos + 0.1F) * 0.6662F) * 0.8F * state.walkAnimationSpeed;

			if (state.walkAnimationSpeed > 0.15F) {
				if (RLegXRot > RLegXRot2) // - - >
				{
					RLegXRotB = RLegXRot + (25 / 57.29578F);

				}

				if (LLegXRot > LLegXRot2) // - - >
				{
					LLegXRotB = LLegXRot + (25 / 57.29578F);
				}

			}

			this.rightLeg.xRot = (LLegXRotB);
			this.leftLeg.xRot = (RLegXRotB);
		}
	}
}
