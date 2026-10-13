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
 * The feral wolf, from Better Animals Plus's {@code ModelFeralWolf} (CC BY-NC-SA 4.0, by cybercat5555 and itsmeowdev;
 * see {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}), carried over to 26.3 names unchanged, with its
 * standing pose and quadruped walk.
 */
final class FeralWolfModel extends WildModel {
	FeralWolfModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(WildRenderer.State state) {
		super.setupAnim(state);
		this.look("head", state, 1.0F);
		this.angles("neck", -Mth.PI / 6.0F, 0.0F, 0.0F);
		this.angles("lHindLeg03", Mth.PI / 6.0F, 0.0F, 0.05235988F);
		this.angles("lHindPaw", 0.12217305F, 0.0F, 0.0F);
		this.angles("chest", -0.06981317F, 0.0F, 0.0F);
		this.angles("tail04", 0.06981317F, 0.0F, 0.0F);
		this.angles("lHindLeg02", -Mth.PI / 12.0F, 0.0F, 0.0F);
		this.angles("lForePaw", 0.034906585F, 0.0F, 0.0F);
		this.angles("rHindLeg01", -0.38397244F, 0.0F, 0.05235988F);
		this.angles("rForeleg01", 0.13962634F, 0.0F, 0.0F);
		this.angles("tail02", -Mth.PI / 10.0F, 0.0F, 0.0F);
		this.angles("torso", 0.06981317F, 0.0F, 0.0F);
		this.angles("lForeleg01", 0.13962634F, 0.0F, 0.0F);
		this.angles("lHindLeg01", -0.38397244F, 0.0F, -0.05235988F);
		this.angles("tail03", 0.13962634F, 0.0F, 0.0F);
		this.angles("rHindLeg02", -Mth.PI / 12.0F, 0.0F, 0.0F);
		this.angles("rForeleg02", -0.10471976F, 0.0F, 0.0F);
		this.angles("rHindLeg03", Mth.PI / 6.0F, 0.0F, -0.05235988F);
		this.angles("rForePaw", 0.034906585F, 0.0F, 0.0F);
		this.angles("lForeleg02", -0.10471976F, 0.0F, 0.0F);
		this.angles("rHindPaw", 0.12217305F, 0.0F, 0.0F);
		this.quadruped("lHindLeg01", "lForeleg01", "rHindLeg01", "rForeleg01", state.walkAnimationPos * 0.8665F, state.walkAnimationSpeed * 0.9F);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition chest = partdefinition.addOrReplaceChild(
			"chest",
			CubeListBuilder.create().texOffs(3, 0).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 9.0F, 10.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 7.8F, -5.0F, -0.0698F, 0.0F, 0.0F)
		);
		PartDefinition torso = chest.addOrReplaceChild(
			"torso",
			CubeListBuilder.create().texOffs(0, 19).addBox(-3.5F, -4.0F, 0.0F, 7.0F, 8.0F, 11.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.4F, 4.0F, 0.0698F, 0.0F, 0.0F)
		);
		PartDefinition tail01 = torso.addOrReplaceChild(
			"tail01",
			CubeListBuilder.create().texOffs(0, 39).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.9F, 10.0F, 0.6981F, 0.0F, 0.0F)
		);
		PartDefinition tail02 = tail01.addOrReplaceChild(
			"tail02",
			CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, -0.3142F, 0.0F, 0.0F)
		);
		PartDefinition tail03 = tail02.addOrReplaceChild(
			"tail03",
			CubeListBuilder.create().texOffs(13, 39).addBox(-1.5F, 0.2F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 6.5F, 0.1F, 0.1396F, 0.0F, 0.0F)
		);
		PartDefinition tail04 = tail03.addOrReplaceChild(
			"tail04",
			CubeListBuilder.create().texOffs(18, 49).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.4F, 0.0698F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg01 = torso.addOrReplaceChild(
			"lHindLeg01",
			CubeListBuilder.create().texOffs(90, 0).addBox(0.0F, -1.0F, -3.0F, 4.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(1.3F, -0.9F, 7.8F, -0.384F, 0.0F, -0.0524F)
		);
		PartDefinition lHindLeg02 = lHindLeg01.addOrReplaceChild(
			"lHindLeg02",
			CubeListBuilder.create().texOffs(90, 18).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(1.9F, 5.6F, -1.1F, -0.2618F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg03 = lHindLeg02.addOrReplaceChild(
			"lHindLeg03",
			CubeListBuilder.create().texOffs(94, 30).addBox(-1.0F, -0.3F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.1F, 0.0F, 5.8F, 0.5236F, 0.0F, 0.0524F)
		);
		PartDefinition lHindPaw = lHindLeg03.addOrReplaceChild(
			"lHindPaw",
			CubeListBuilder.create().texOffs(66, 27).addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 6.4F, -0.3F, 0.1222F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg01 = torso.addOrReplaceChild(
			"rHindLeg01",
			CubeListBuilder.create()
				.texOffs(90, 0)
				.mirror()
				.addBox(-4.0F, -1.0F, -3.0F, 4.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.3F, -0.9F, 7.8F, -0.384F, 0.0F, 0.0524F)
		);
		PartDefinition rHindLeg02 = rHindLeg01.addOrReplaceChild(
			"rHindLeg02",
			CubeListBuilder.create()
				.texOffs(90, 18)
				.mirror()
				.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 4.0F, 7.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.9F, 5.6F, -1.1F, -0.2618F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg03 = rHindLeg02.addOrReplaceChild(
			"rHindLeg03",
			CubeListBuilder.create()
				.texOffs(94, 30)
				.mirror()
				.addBox(-1.0F, -0.3F, -1.5F, 2.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.1F, 0.0F, 5.8F, 0.5236F, 0.0F, -0.0524F)
		);
		PartDefinition rHindPaw = rHindLeg03.addOrReplaceChild(
			"rHindPaw",
			CubeListBuilder.create()
				.texOffs(66, 27)
				.mirror()
				.addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 6.4F, -0.3F, 0.1222F, 0.0F, 0.0F)
		);
		PartDefinition neck = chest.addOrReplaceChild(
			"neck",
			CubeListBuilder.create().texOffs(38, 27).addBox(-3.0F, -2.5F, -5.0F, 6.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -1.7F, -3.3F, -0.5236F, 0.0F, 0.0F)
		);
		PartDefinition head = neck.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(40, 0).addBox(-3.5F, -5.0F, -3.0F, 7.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.5F, -3.6F, 2.1817F, 0.0F, 0.0F)
		);
		PartDefinition jawUpper01 = head.addOrReplaceChild(
			"jawUpper01",
			CubeListBuilder.create()
				.texOffs(51, 12)
				.mirror()
				.addBox(-1.1F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.2F, -5.2F, -1.1F, 0.0F, 0.0F, 0.1396F)
		);
		PartDefinition jawUpper02 = jawUpper01.addOrReplaceChild(
			"jawUpper02",
			CubeListBuilder.create().texOffs(51, 12).addBox(1.5F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.35F, 0.0F, 0.0F, 0.0F, -0.2793F)
		);
		PartDefinition upperTeeth02 = jawUpper02.addOrReplaceChild(
			"upperTeeth02",
			CubeListBuilder.create().texOffs(50, 20).addBox(-0.5F, -1.0F, -0.89F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offset(2.4F, -2.8F, -1.0F)
		);
		PartDefinition upperTeeth01 = jawUpper01.addOrReplaceChild(
			"upperTeeth01",
			CubeListBuilder.create()
				.texOffs(50, 20)
				.mirror()
				.addBox(-0.5F, -1.0F, -0.89F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offset(0.0F, -2.8F, -1.0F)
		);
		PartDefinition upperTeeth03 = jawUpper01.addOrReplaceChild(
			"upperTeeth03",
			CubeListBuilder.create()
				.texOffs(55, 28)
				.mirror()
				.addBox(-0.73F, -0.7F, -0.4F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -3.2F, -1.0F, 0.0F, 0.0F, 0.1367F)
		);
		PartDefinition jawLower = head.addOrReplaceChild(
			"jawLower",
			CubeListBuilder.create().texOffs(39, 20).addBox(-1.5F, -4.5F, -0.5F, 3.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -4.6F, -2.51F)
		);
		PartDefinition lowerTeeth01 = jawLower.addOrReplaceChild(
			"lowerTeeth01",
			CubeListBuilder.create().texOffs(57, 22).addBox(-1.6F, -0.7F, 0.4F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -3.7F, 0.1F)
		);
		PartDefinition lowerTeeth02 = lowerTeeth01.addOrReplaceChild(
			"lowerTeeth02",
			CubeListBuilder.create()
				.texOffs(57, 22)
				.mirror()
				.addBox(0.6F, -0.7F, 0.3F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offset(0.0F, 0.0F, 0.0F)
		);
		PartDefinition lEar01 = head.addOrReplaceChild(
			"lEar01",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.mirror()
				.addBox(-1.5F, -0.5F, 0.3F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.1F, -2.2F, 1.9F, 0.0873F, 0.2269F, 0.3665F)
		);
		PartDefinition lEar02 = lEar01.addOrReplaceChild(
			"lEar02",
			CubeListBuilder.create()
				.texOffs(0, 4)
				.mirror()
				.addBox(-1.0F, -0.7F, 0.2F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 1.2F, 0.2F, 0.3142F, 0.0F, 0.0F)
		);
		PartDefinition rEar01 = head.addOrReplaceChild(
			"rEar01",
			CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -0.5F, 0.3F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.1F, -2.2F, 1.9F, 0.0873F, -0.2269F, -0.3665F)
		);
		PartDefinition rEar02 = rEar01.addOrReplaceChild(
			"rEar02",
			CubeListBuilder.create().texOffs(0, 4).addBox(-1.0F, -0.7F, 0.2F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 1.2F, 0.2F, 0.3142F, 0.0F, 0.0F)
		);
		PartDefinition lCheekFur = head.addOrReplaceChild(
			"lCheekFur",
			CubeListBuilder.create().texOffs(30, -6).addBox(0.0F, -0.9F, -3.8F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(3.5F, -2.5F, -0.6F, -0.3491F, 0.0873F, -0.6981F)
		);
		PartDefinition rCheekFur = head.addOrReplaceChild(
			"rCheekFur",
			CubeListBuilder.create().texOffs(30, -6).addBox(0.0F, -0.8F, -3.6F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-3.5F, -2.5F, -0.6F, -0.3491F, -0.0873F, 0.6981F)
		);
		PartDefinition snout = head.addOrReplaceChild(
			"snout",
			CubeListBuilder.create().texOffs(40, 12).addBox(-1.5F, -4.45F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -4.9F, 0.1F, 0.182F, 0.0F, 0.0F)
		);
		PartDefinition mane01 = neck.addOrReplaceChild(
			"mane01",
			CubeListBuilder.create().texOffs(36, 50).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 3.1F, -3.9F, 0.5236F, 0.0F, 0.0F)
		);
		PartDefinition mane02 = neck.addOrReplaceChild(
			"mane02",
			CubeListBuilder.create().texOffs(49, 50).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 3.1F, -3.2F, 0.6283F, 0.0F, 0.0F)
		);
		PartDefinition mane03 = neck.addOrReplaceChild(
			"mane03",
			CubeListBuilder.create().texOffs(61, 50).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 3.1F, -2.0F, 0.6632F, 0.0F, 0.0F)
		);
		PartDefinition mane04 = neck.addOrReplaceChild(
			"mane04",
			CubeListBuilder.create().texOffs(75, 50).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 3.1F, -0.8F, 0.6981F, 0.0F, 0.0F)
		);
		PartDefinition lForeleg01 = chest.addOrReplaceChild(
			"lForeleg01",
			CubeListBuilder.create().texOffs(69, 0).addBox(-1.0F, -1.1F, -3.4F, 4.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(3.0F, -0.2F, -1.2F, 0.1396F, 0.0F, 0.0F)
		);
		PartDefinition lForeleg02 = lForeleg01.addOrReplaceChild(
			"lForeleg02",
			CubeListBuilder.create().texOffs(69, 14).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(1.5F, 6.7F, -0.5F, -0.1047F, 0.0F, 0.0F)
		);
		PartDefinition lForePaw = lForeleg02.addOrReplaceChild(
			"lForePaw",
			CubeListBuilder.create().texOffs(66, 27).addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 7.8F, -0.5F, 0.0349F, 0.0F, 0.0F)
		);
		PartDefinition rForeleg01 = chest.addOrReplaceChild(
			"rForeleg01",
			CubeListBuilder.create()
				.texOffs(69, 0)
				.mirror()
				.addBox(-3.0F, -1.1F, -3.4F, 4.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-3.0F, -0.2F, -1.2F, 0.1396F, 0.0F, 0.0F)
		);
		PartDefinition rForeleg02 = rForeleg01.addOrReplaceChild(
			"rForeleg02",
			CubeListBuilder.create()
				.texOffs(69, 14)
				.mirror()
				.addBox(-1.0F, 0.0F, -1.5F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.5F, 6.7F, -0.5F, -0.1047F, 0.0F, 0.0F)
		);
		PartDefinition rForePaw = rForeleg02.addOrReplaceChild(
			"rForePaw",
			CubeListBuilder.create()
				.texOffs(66, 27)
				.mirror()
				.addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 7.8F, -0.5F, 0.0349F, 0.0F, 0.0F)
		);
		PartDefinition hackles01 = chest.addOrReplaceChild(
			"hackles01",
			CubeListBuilder.create().texOffs(59, 37).addBox(-2.5F, -1.0F, 0.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -3.9F, -4.5F, 0.1396F, 0.0F, 0.0F)
		);
		PartDefinition hackles02 = chest.addOrReplaceChild(
			"hackles02",
			CubeListBuilder.create().texOffs(94, 42).addBox(-3.5F, -1.0F, 0.0F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -3.2F, -3.0F, 0.1047F, 0.0F, 0.0F)
		);
		return LayerDefinition.create(meshdefinition, 128, 64);
	}
}
