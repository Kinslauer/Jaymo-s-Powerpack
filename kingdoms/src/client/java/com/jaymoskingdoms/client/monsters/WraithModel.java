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
 * (0.5.56) The wraiths, from {@code MoCModelWraith} of
 * DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3: its boxes as they were, and its
 * animation as it was: a hood and robe adrift, arms reaching out, both raking down on a blow.
 */
final class WraithModel extends EntityModel<LegendState> {
	private final ModelPart head, headwear, body, rightArm, leftArm, rightLeg, leftLeg;

	WraithModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.headwear = root.getChild("headwear");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 40).addBox(-4, -8, -4, 1, 1, 1), PartPose.offset(0, 0, 0));
		root.addOrReplaceChild("headwear", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -8, -4, 8, 8, 8), PartPose.offset(0, 0, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 0).addBox(-6, 0, -2, 10, 20, 4), PartPose.offset(0, 0, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(16, 16).addBox(-5, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 16).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 2, 2, 2), PartPose.offset(-2, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 2, 2, 2), PartPose.offset(2, 12, 0));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float attackCounter = state.leftAttack * 2.0F;
		float f6 = 0.0F, f7 = 0.0F;
		this.head.yRot = this.headwear.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = this.headwear.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.rightArm.zRot = 0.0F;
		this.leftArm.zRot = 0.0F;
		this.rightArm.yRot = -(0.1F - (f6 * 0.6F));
		this.leftArm.yRot = 0.1F - (f6 * 0.6F);
		if (attackCounter != 0) {
			float armMov = (Mth.cos((attackCounter) * 0.12F) * 4F);

			this.rightArm.xRot = -armMov;
			this.leftArm.xRot = -armMov;
		} else {
			this.rightArm.xRot = -1.570796F;
			this.leftArm.xRot = -1.570796F;
			this.rightArm.xRot -= (f6 * 1.2F) - (f7 * 0.4F);
			this.leftArm.xRot -= (f6 * 1.2F) - (f7 * 0.4F);
			this.rightArm.xRot += Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
			this.leftArm.xRot -= Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
		}

		this.rightArm.zRot += (Mth.cos(state.ageInTicks * 0.09F) * 0.05F) + 0.05F;
		this.leftArm.zRot -= (Mth.cos(state.ageInTicks * 0.09F) * 0.05F) + 0.05F;
	}
}
