package com.jaymoskingdoms.client.nether;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A Hornkin ({@link com.jaymoskingdoms.nether.Hornkin}), all our own: a man's body under a bull's head, a broad muzzle
 * with a ring through it and a pair of horns sweeping out and up. The horns dip in a charge, a hurler draws its arm
 * back to throw, a gold-struck one holds its gold up to its face, and the zombified shamble with arms out.
 * Texture 64x64: the humanoid layout above, the muzzle and horns below it ({@code tools/gen_nether.py}).
 */
final class HornkinModel extends HumanoidModel<HornkinRenderer.State> {
	/** How far the head dips in a charge (radians). */
	static final float CHARGE_DIP = Mth.PI / 5.0F;

	HornkinModel(ModelPart root) {
		super(root);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition head = mesh.getRoot().getChild("head");
		head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -4.5F, -6.5F, 6.0F, 4.0F, 3.0F), PartPose.ZERO);
		head.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(18, 32).addBox(-1.0F, -1.5F, -6.8F, 2.0F, 2.0F, 0.0F), PartPose.ZERO);
		head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(24, 32).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(-4.0F, -7.0F, -1.0F, 0.0F, 0.0F, -0.15F));
		head.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(36, 32).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(-7.5F, -7.5F, -1.0F, 0.0F, 0.0F, 0.35F));
		head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(24, 32).mirror().addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(4.0F, -7.0F, -1.0F, 0.0F, 0.0F, 0.15F));
		head.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(36, 32).mirror().addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(7.5F, -7.5F, -1.0F, 0.0F, 0.0F, -0.35F));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(44, 32).addBox(-3.0F, -0.5F, -0.5F, 3.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(-4.0F, -4.5F, 0.0F, 0.0F, 0.0F, 0.4F));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(44, 32).mirror().addBox(0.0F, -0.5F, -0.5F, 3.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(4.0F, -4.5F, 0.0F, 0.0F, 0.0F, -0.4F));
		// a loincloth front and back, swinging with the stride
		mesh.getRoot().getChild("body").addOrReplaceChild("loincloth", CubeListBuilder.create().texOffs(0, 40).addBox(-3.0F, 0.0F, -2.5F, 6.0F, 6.0F, 5.0F),
			PartPose.offset(0.0F, 11.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HornkinRenderer.State state) {
		super.setupAnim(state);
		if (state.zombified) {
			float attack = Mth.sin(state.swingAnimation * Mth.PI);
			this.rightArm.xRot = -Mth.PI / 2.25F + attack * 1.2F;
			this.leftArm.xRot = -Mth.PI / 2.25F + attack * 1.2F;
			this.rightArm.yRot = -0.1F + attack * 0.6F;
			this.leftArm.yRot = 0.1F - attack * 0.6F;
			AnimationUtils.bobArms(this.rightArm, this.leftArm, state.ageInTicks);
		}
		if (state.charging) {
			this.head.xRot = CHARGE_DIP;
			this.hat.xRot = CHARGE_DIP;
			this.rightArm.xRot = 0.6F;
			this.leftArm.xRot = 0.6F;
		}
		if (state.throwing && state.swingAnimation <= 0.0F) {
			// a hurler with a foe in sight, bomb drawn back
			this.rightArm.xRot = -2.6F;
			this.rightArm.zRot = 0.2F;
		}
		if (state.admiring) {
			this.leftArm.xRot = -1.4F;
			this.leftArm.yRot = 0.5F;
			this.head.xRot = 0.5F;
			this.hat.xRot = 0.5F;
		}
	}
}
