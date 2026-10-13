package com.jaymoskingdoms.client.wild;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The coyote, from Better Animals Plus's {@code ModelCoyote} (CC BY-NC-SA 4.0, by cybercat5555 and itsmeowdev; see
 * {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}): at night (hostile) its fangs show and its jaw hangs open.
 */
final class CoyoteModel extends WildModel {
	CoyoteModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(WildRenderer.State state) {
		super.setupAnim(state);
		this.quadruped("lHindLeg01", "lArm01", "rHindLeg01", "rArm01", state.walkAnimationPos * 0.8665F, state.walkAnimationSpeed * 0.9F);
		this.look("head", state, 1.0F);
		this.part("rUpperFang_r1").visible = this.part("lUpperFang_r1").visible = state.hostile;
		this.part("lowerJaw").xRot = state.hostile ? 45.0F * Mth.DEG_TO_RAD : 0.0F;
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition body = partdefinition.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.5F, -5.0F, 6.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 13.25F, -2.0F)
		);
		PartDefinition stomach = body.addOrReplaceChild(
			"stomach",
			CubeListBuilder.create().texOffs(0, 14).addBox(-2.5F, -2.0F, -0.5F, 5.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -0.25F, 3.0F)
		);
		PartDefinition tail01 = stomach.addOrReplaceChild(
			"tail01",
			CubeListBuilder.create().texOffs(0, 27).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.7F, 5.25F, 0.6981F, 0.0F, 0.0F)
		);
		PartDefinition tail02 = tail01.addOrReplaceChild(
			"tail02",
			CubeListBuilder.create().texOffs(0, 34).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F, new CubeDeformation(0.1F)),
			PartPose.offsetAndRotation(0.0F, 2.5F, 0.25F, -0.3142F, 0.0F, 0.0F)
		);
		PartDefinition tail03 = tail02.addOrReplaceChild(
			"tail03",
			CubeListBuilder.create().texOffs(12, 27).addBox(-1.5F, 0.2F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(-0.2F)),
			PartPose.offsetAndRotation(0.0F, 4.75F, -0.4F, 0.2269F, 0.0F, 0.0F)
		);
		PartDefinition tail04 = tail03.addOrReplaceChild(
			"tail04",
			CubeListBuilder.create().texOffs(13, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.4F, 0.0698F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg01 = stomach.addOrReplaceChild(
			"lHindLeg01",
			CubeListBuilder.create().texOffs(28, 12).addBox(-0.95F, -3.2F, -2.95F, 3.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(1.5F, 2.0F, 4.5F, -0.384F, -0.0436F, -0.096F)
		);
		PartDefinition lHindLeg02 = lHindLeg01.addOrReplaceChild(
			"lHindLeg02",
			CubeListBuilder.create().texOffs(28, 24).addBox(-0.75F, 0.4F, 0.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.25F, 2.0F, -2.3F, -0.2618F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg03 = lHindLeg02.addOrReplaceChild(
			"lHindLeg03",
			CubeListBuilder.create().texOffs(29, 32).addBox(-1.0F, -0.4F, -1.5F, 2.0F, 5.0F, 2.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.3F, 1.0F, 4.8F, 0.5236F, 0.0F, 0.096F)
		);
		PartDefinition lHindPaw = lHindLeg03.addOrReplaceChild(
			"lHindPaw",
			CubeListBuilder.create().texOffs(27, 40).addBox(-1.45F, -0.2F, -2.2F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.05F, 4.0F, -0.5F, 0.1222F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg01 = stomach.addOrReplaceChild(
			"rHindLeg01",
			CubeListBuilder.create()
				.texOffs(28, 12)
				.mirror()
				.addBox(-2.05F, -3.2F, -2.95F, 3.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.5F, 2.0F, 4.5F, -0.384F, 0.0436F, 0.096F)
		);
		PartDefinition rHindLeg02 = rHindLeg01.addOrReplaceChild(
			"rHindLeg02",
			CubeListBuilder.create()
				.texOffs(28, 24)
				.mirror()
				.addBox(-1.25F, 0.4F, 0.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.25F, 2.0F, -2.3F, -0.2618F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg03 = rHindLeg02.addOrReplaceChild(
			"rHindLeg03",
			CubeListBuilder.create()
				.texOffs(29, 32)
				.mirror()
				.addBox(-1.0F, -0.4F, -1.5F, 2.0F, 5.0F, 2.0F, new CubeDeformation(-0.1F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.3F, 1.0F, 4.8F, 0.5236F, 0.0F, -0.096F)
		);
		PartDefinition rHindPaw = rHindLeg03.addOrReplaceChild(
			"rHindPaw",
			CubeListBuilder.create()
				.texOffs(27, 40)
				.mirror()
				.addBox(-1.55F, -0.2F, -2.2F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.05F, 4.0F, -0.5F, 0.1222F, 0.0F, 0.0F)
		);
		PartDefinition neck = body.addOrReplaceChild(
			"neck",
			CubeListBuilder.create().texOffs(29, 0).addBox(-1.5F, -1.65F, -4.5F, 4.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.5F, -1.0F, -5.0F, -0.1309F, 0.0F, 0.0F)
		);
		PartDefinition neckFur03_r1 = neck.addOrReplaceChild(
			"neckFur03_r1",
			CubeListBuilder.create().texOffs(41, 49).addBox(-1.0F, -0.25F, -1.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -1.75F, -1.0F, 0.3491F, 0.0F, 0.0F)
		);
		PartDefinition neckFur02_r1 = neck.addOrReplaceChild(
			"neckFur02_r1",
			CubeListBuilder.create().texOffs(41, 41).addBox(-1.5F, -0.25F, -0.5F, 4.0F, 2.0F, 5.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.0F, -1.75F, -1.0F, 0.2182F, 0.0F, 0.0F)
		);
		PartDefinition neckFur01_r1 = neck.addOrReplaceChild(
			"neckFur01_r1",
			CubeListBuilder.create().texOffs(42, 32).addBox(-1.5F, 2.5F, -4.0F, 4.0F, 2.0F, 6.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F)
		);
		PartDefinition head = neck.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(0, 46).addBox(-2.0F, -2.25F, -2.0F, 5.0F, 5.0F, 4.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.0F, 0.25F, -4.0F, 0.1309F, 0.0F, 0.0F)
		);
		PartDefinition rCheekFur_r1 = head.addOrReplaceChild(
			"rCheekFur_r1",
			CubeListBuilder.create()
				.texOffs(0, 56)
				.mirror()
				.addBox(-3.25F, -0.75F, 0.0F, 4.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0F, 0.3927F, -0.4363F)
		);
		PartDefinition lCheekFur_r1 = head.addOrReplaceChild(
			"lCheekFur_r1",
			CubeListBuilder.create().texOffs(0, 56).addBox(-0.75F, -0.75F, 0.0F, 4.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.0F, -0.3927F, 0.4363F)
		);
		PartDefinition muzzle = head.addOrReplaceChild(
			"muzzle",
			CubeListBuilder.create().texOffs(19, 49).addBox(-1.0F, -0.5F, -2.5F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.5F, 0.25F, -2.0F, 0.1745F, 0.0F, 0.0F)
		);
		PartDefinition mUpperFang_r1 = muzzle.addOrReplaceChild(
			"mUpperFang_r1",
			CubeListBuilder.create()
				.texOffs(13, 56)
				.mirror()
				.addBox(-0.5F, -0.5F, 0.05F, 2.0F, 1.0F, 0.0F, new CubeDeformation(-0.1F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.5F, 1.0F, -2.25F, -0.0873F, 0.0F, 0.0F)
		);
		PartDefinition rUpperFang_r1 = muzzle.addOrReplaceChild(
			"rUpperFang_r1",
			CubeListBuilder.create()
				.texOffs(9, 56)
				.mirror()
				.addBox(-0.5F, -0.25F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.1F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.5F, 1.0F, -2.25F, 0.0F, 1.5708F, 0.0F)
		);
		PartDefinition lUpperFang_r1 = muzzle.addOrReplaceChild(
			"lUpperFang_r1",
			CubeListBuilder.create().texOffs(9, 56).addBox(-0.5F, -0.25F, 0.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.5F, 1.0F, -2.25F, 0.0F, -1.5708F, 0.0F)
		);
		PartDefinition rLip_r1 = muzzle.addOrReplaceChild(
			"rLip_r1",
			CubeListBuilder.create()
				.texOffs(5, 59)
				.mirror()
				.addBox(-1.4F, 0.25F, -2.35F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1309F, -0.1745F, 0.0F)
		);
		PartDefinition lLip_r1 = muzzle.addOrReplaceChild(
			"lLip_r1",
			CubeListBuilder.create().texOffs(5, 59).addBox(0.4F, 0.25F, -2.35F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1309F, 0.1745F, 0.0F)
		);
		PartDefinition lowerJaw = head.addOrReplaceChild(
			"lowerJaw",
			CubeListBuilder.create()
				.texOffs(19, 59)
				.addBox(-1.0F, 0.0F, -2.5F, 2.0F, 1.0F, 3.0F, new CubeDeformation(-0.2F))
				.texOffs(9, 56)
				.addBox(-0.1F, -0.5F, -2.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.2F))
				.texOffs(9, 56)
				.mirror()
				.addBox(-0.9F, -0.5F, -2.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(-0.2F))
				.mirror(false),
			PartPose.offset(0.5F, 1.25F, -2.0F)
		);
		PartDefinition lEar01 = head.addOrReplaceChild(
			"lEar01",
			CubeListBuilder.create().texOffs(31, 50).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(2.25F, -1.5F, 0.0F, 0.0F, -0.0873F, 0.2182F)
		);
		PartDefinition lEar02 = lEar01.addOrReplaceChild("lEar02", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, -0.75F, 0.1745F, 0.0F, 0.0F));
		PartDefinition cube_r1 = lEar02.addOrReplaceChild(
			"cube_r1",
			CubeListBuilder.create().texOffs(31, 54).addBox(-0.25F, -3.5F, -0.25F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.829F, 0.0F)
		);
		PartDefinition rEar01 = head.addOrReplaceChild(
			"rEar01",
			CubeListBuilder.create()
				.texOffs(31, 50)
				.mirror()
				.addBox(-1.0F, -2.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.25F, -1.5F, 0.0F, 0.0F, 0.0873F, -0.2182F)
		);
		PartDefinition rEar02 = rEar01.addOrReplaceChild("rEar02", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, -0.75F, 0.2182F, 0.0F, 0.0F));
		PartDefinition cube_r2 = rEar02.addOrReplaceChild(
			"cube_r2",
			CubeListBuilder.create()
				.texOffs(31, 54)
				.mirror()
				.addBox(-1.75F, -3.5F, -0.25F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.1F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.829F, 0.0F)
		);
		PartDefinition lArm01 = body.addOrReplaceChild("lArm01", CubeListBuilder.create(), PartPose.offset(3.0F, 0.0F, -2.0F));
		PartDefinition cube_r3 = lArm01.addOrReplaceChild(
			"cube_r3",
			CubeListBuilder.create().texOffs(46, 12).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1309F, 0.0F, 0.0F)
		);
		PartDefinition lArm02 = lArm01.addOrReplaceChild("lArm02", CubeListBuilder.create(), PartPose.offset(0.0F, 4.5F, 0.0F));
		PartDefinition cube_r4 = lArm02.addOrReplaceChild(
			"cube_r4",
			CubeListBuilder.create().texOffs(46, 22).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0873F, 0.0F, 0.0F)
		);
		PartDefinition lForePaw = lArm02.addOrReplaceChild(
			"lForePaw",
			CubeListBuilder.create().texOffs(27, 40).addBox(-1.45F, -0.2F, -2.2F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 5.75F, -0.25F, -0.0087F, 0.0F, 0.0F)
		);
		PartDefinition rArm01 = body.addOrReplaceChild("rArm01", CubeListBuilder.create(), PartPose.offset(-3.0F, 0.0F, -2.0F));
		PartDefinition cube_r5 = rArm01.addOrReplaceChild(
			"cube_r5",
			CubeListBuilder.create()
				.texOffs(46, 12)
				.mirror()
				.addBox(-1.5F, -1.5F, -2.0F, 3.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1309F, 0.0F, 0.0F)
		);
		PartDefinition rArm02 = rArm01.addOrReplaceChild("rArm02", CubeListBuilder.create(), PartPose.offset(0.0F, 4.5F, 0.0F));
		PartDefinition cube_r6 = rArm02.addOrReplaceChild(
			"cube_r6",
			CubeListBuilder.create()
				.texOffs(46, 22)
				.mirror()
				.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0873F, 0.0F, 0.0F)
		);
		PartDefinition rForePaw = rArm02.addOrReplaceChild(
			"rForePaw",
			CubeListBuilder.create()
				.texOffs(27, 40)
				.mirror()
				.addBox(-1.55F, -0.2F, -2.2F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 5.75F, -0.25F, -0.0087F, 0.0F, 0.0F)
		);
		return LayerDefinition.create(meshdefinition, 64, 64);
	}
}
