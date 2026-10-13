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
 * The moose, from Better Animals Plus's {@code ModelMoose} (CC BY-NC-SA 4.0, by cybercat5555 and itsmeowdev; see
 * {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}), drawn 1.5x as there.
 */
final class MooseModel extends WildModel {
	MooseModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(WildRenderer.State state) {
		super.setupAnim(state);
		this.quadruped("lHindLeg01", "rHindLeg01", "lForeleg01", "rForeleg01", state.walkAnimationPos * 0.666F, state.walkAnimationSpeed * 1.4F);
		this.part("neck").xRot += state.xRot * Mth.DEG_TO_RAD;
		this.part("chest").yRot += state.yRot * Mth.DEG_TO_RAD * 0.5F;
		this.part("head").xRot = Mth.PI / 6.0F;
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition body = partdefinition.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -4.2F, 0.0F, 8.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 4.2F, -3.9F)
		);
		PartDefinition ass = body.addOrReplaceChild(
			"ass",
			CubeListBuilder.create().texOffs(0, 43).addBox(-3.5F, -4.4F, 0.0F, 7.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.3F, 9.2F, -0.182F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg01 = ass.addOrReplaceChild(
			"lHindLeg01",
			CubeListBuilder.create().texOffs(64, 0).addBox(-1.0F, -1.9F, -2.2F, 4.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(2.5F, -0.8F, 3.3F, -0.2276F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg02 = lHindLeg01.addOrReplaceChild(
			"lHindLeg02",
			CubeListBuilder.create().texOffs(65, 15).addBox(-2.5F, 0.0F, -1.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(1.4F, 6.0F, -1.1F, 0.9105F, 0.0F, 0.0F)
		);
		PartDefinition lHindLeg03 = lHindLeg02.addOrReplaceChild(
			"lHindLeg03",
			CubeListBuilder.create().texOffs(68, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.5F, 6.4F, 0.5F, -0.5009F, 0.0F, 0.0F)
		);
		PartDefinition lHindHoof = lHindLeg03.addOrReplaceChild(
			"lHindHoof",
			CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 7.8F, 0.3F)
		);
		PartDefinition rHindLeg01 = ass.addOrReplaceChild(
			"rHindLeg01",
			CubeListBuilder.create()
				.texOffs(64, 0)
				.mirror()
				.addBox(-3.0F, -1.9F, -2.2F, 4.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-2.5F, -0.8F, 3.3F, -0.2276F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg02 = rHindLeg01.addOrReplaceChild(
			"rHindLeg02",
			CubeListBuilder.create()
				.texOffs(65, 15)
				.mirror()
				.addBox(-1.5F, 0.0F, -1.5F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-1.4F, 6.0F, -1.1F, 0.9105F, 0.0F, 0.0F)
		);
		PartDefinition rHindLeg03 = rHindLeg02.addOrReplaceChild(
			"rHindLeg03",
			CubeListBuilder.create()
				.texOffs(68, 30)
				.mirror()
				.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.5F, 6.4F, 0.5F, -0.5009F, 0.0F, 0.0F)
		);
		PartDefinition rHindHoof = rHindLeg03.addOrReplaceChild(
			"rHindHoof",
			CubeListBuilder.create()
				.texOffs(32, 0)
				.mirror()
				.addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offset(0.0F, 7.8F, 0.3F)
		);
		PartDefinition tail = ass.addOrReplaceChild(
			"tail",
			CubeListBuilder.create().texOffs(29, 53).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -3.4F, 6.1F, 0.576F, 0.0F, 0.0F)
		);
		PartDefinition chest = body.addOrReplaceChild(
			"chest",
			CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4.6F, -5.7F, 9.0F, 11.0F, 10.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.6F, -3.6F, -0.0873F, 0.0F, 0.0F)
		);
		PartDefinition hump = chest.addOrReplaceChild(
			"hump",
			CubeListBuilder.create().texOffs(33, 39).addBox(-3.0F, -3.0F, -1.7F, 6.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.7F, -4.7F, 0.2269F, 0.0F, 0.0F)
		);
		PartDefinition hump02 = hump.addOrReplaceChild(
			"hump02",
			CubeListBuilder.create().texOffs(32, 52).addBox(-3.5F, -3.0F, -0.4F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.2F, 4.0F, -0.5236F, 0.0F, 0.0F)
		);
		PartDefinition lForeleg01 = chest.addOrReplaceChild(
			"lForeleg01",
			CubeListBuilder.create().texOffs(45, 0).addBox(-1.0F, -2.4F, -2.5F, 3.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(3.9F, 1.3F, -1.0F, 0.192F, 0.0F, -0.0873F)
		);
		PartDefinition lForeleg02 = lForeleg01.addOrReplaceChild(
			"lForeleg02",
			CubeListBuilder.create().texOffs(48, 14).addBox(-1.9F, -0.3F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.8F, 4.4F, 0.6F, 0.0F, 0.0F, 0.0911F)
		);
		PartDefinition lForeleg03 = lForeleg02.addOrReplaceChild(
			"lForeleg03",
			CubeListBuilder.create().texOffs(50, 25).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.4F, 4.2F, 0.0F, -0.1047F, 0.0F, 0.0F)
		);
		PartDefinition lForeHoof = lForeleg03.addOrReplaceChild(
			"lForeHoof",
			CubeListBuilder.create().texOffs(32, 0).addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 8.8F, 0.3F)
		);
		PartDefinition rForeleg01 = chest.addOrReplaceChild(
			"rForeleg01",
			CubeListBuilder.create()
				.texOffs(45, 0)
				.mirror()
				.addBox(-2.0F, -2.4F, -2.5F, 3.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-3.9F, 1.3F, -1.0F, 0.192F, 0.0F, 0.0873F)
		);
		PartDefinition rForeleg02 = rForeleg01.addOrReplaceChild(
			"rForeleg02",
			CubeListBuilder.create()
				.texOffs(48, 14)
				.mirror()
				.addBox(-1.0F, 0.0F, -2.0F, 3.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.8F, 4.4F, 0.6F, 0.0F, 0.0F, -0.0911F)
		);
		PartDefinition rForeleg03 = rForeleg02.addOrReplaceChild(
			"rForeleg03",
			CubeListBuilder.create()
				.texOffs(50, 25)
				.mirror()
				.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.4F, 4.2F, 0.0F, -0.1047F, 0.0F, 0.0F)
		);
		PartDefinition rForeHoof = rForeleg03.addOrReplaceChild(
			"rForeHoof",
			CubeListBuilder.create()
				.texOffs(32, 0)
				.mirror()
				.addBox(-1.5F, 0.0F, -2.4F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offset(0.0F, 8.8F, 0.3F)
		);
		PartDefinition neck = chest.addOrReplaceChild(
			"neck",
			CubeListBuilder.create().texOffs(88, 0).addBox(-2.5F, -2.5F, -7.5F, 5.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.9F, -3.2F, -0.3491F, 0.0F, 0.0F)
		);
		PartDefinition head = neck.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(88, 16).addBox(-3.0F, -3.0F, -5.1F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, -6.2F, 0.5236F, 0.0F, 0.0F)
		);
		PartDefinition snout = head.addOrReplaceChild(
			"snout",
			CubeListBuilder.create().texOffs(104, 36).addBox(-2.5F, -1.2F, -5.9F, 5.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 0.5F, -5.1F)
		);
		PartDefinition throat = head.addOrReplaceChild(
			"throat",
			CubeListBuilder.create().texOffs(102, 47).addBox(-2.5F, 0.6F, -2.7F, 5.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 1.6F, -5.2F)
		);
		PartDefinition beard = throat.addOrReplaceChild(
			"beard",
			CubeListBuilder.create().texOffs(56, 46).addBox(-1.5F, -0.2F, -3.8F, 3.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 1.9F, 1.7F, -0.1396F, 0.0F, 0.0F)
		);
		PartDefinition lowerJaw = throat.addOrReplaceChild(
			"lowerJaw",
			CubeListBuilder.create().texOffs(102, 54).addBox(-2.0F, -0.6F, -3.4F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 1.1F, -1.9F)
		);
		PartDefinition snoutSlope = head.addOrReplaceChild(
			"snoutSlope",
			CubeListBuilder.create().texOffs(78, 36).addBox(-2.0F, -1.3F, -6.6F, 4.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -1.6F, -5.0F, 0.1745F, 0.0F, 0.0F)
		);
		PartDefinition lEar = head.addOrReplaceChild(
			"lEar",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.mirror()
				.addBox(0.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.0F, -2.3F, 0.0F, 0.2269F, 0.0F, -0.9599F)
		);
		PartDefinition rEar = head.addOrReplaceChild(
			"rEar",
			CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.0F, -2.3F, 0.0F, 0.2269F, 0.0F, 0.9599F)
		);
		PartDefinition lAntler00 = head.addOrReplaceChild(
			"lAntler00",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(0.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.0F, -2.4F, -2.1F, 0.0F, 0.1745F, -0.3491F)
		);
		PartDefinition lAntler01a = lAntler00.addOrReplaceChild(
			"lAntler01a",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(0.0F, -0.8F, -0.5F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.9F, 0.0F, 0.1F, 0.0F, 0.4363F, 0.1396F)
		);
		PartDefinition lAntler01b = lAntler01a.addOrReplaceChild(
			"lAntler01b",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(0.0F, -0.2F, -0.5F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offset(0.0F, 0.0F, 0.0F)
		);
		PartDefinition lAntler01c = lAntler01a.addOrReplaceChild(
			"lAntler01c",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -3.2F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(4.9F, 0.0F, 0.0F, 0.3491F, 0.1745F, 1.1345F)
		);
		PartDefinition lAntler01d = lAntler01c.addOrReplaceChild(
			"lAntler01d",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -0.9F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler01e = lAntler01a.addOrReplaceChild(
			"lAntler01e",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.5236F, 0.0F, 0.4712F)
		);
		PartDefinition lAntler02a = lAntler01a.addOrReplaceChild(
			"lAntler02a",
			CubeListBuilder.create()
				.texOffs(83, 57)
				.mirror()
				.addBox(-1.5F, -0.6F, 0.4F, 4.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.8F, 0.0F, -0.3F, 0.4363F, -0.0873F, 0.0F)
		);
		PartDefinition lAntler03a = lAntler02a.addOrReplaceChild(
			"lAntler03a",
			CubeListBuilder.create()
				.texOffs(78, 47)
				.mirror()
				.addBox(-2.2F, -0.61F, -0.7F, 5.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.4F, 0.0F, 5.3F, 0.2094F, -0.4363F, 0.0F)
		);
		PartDefinition lAntler07a = lAntler03a.addOrReplaceChild(
			"lAntler07a",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(1.9F, 0.0F, 2.8F, -0.7854F, 0.1745F, 1.1345F)
		);
		PartDefinition lAntler07b = lAntler07a.addOrReplaceChild(
			"lAntler07b",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler08a = lAntler03a.addOrReplaceChild(
			"lAntler08a",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -2.2F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(1.9F, -0.1F, 5.3F, -1.0472F, 0.0F, 1.4835F)
		);
		PartDefinition lAntler08b = lAntler08a.addOrReplaceChild(
			"lAntler08b",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler09a = lAntler03a.addOrReplaceChild(
			"lAntler09a",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -2.2F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.3F, -0.1F, 5.7F, -1.4835F, -0.1745F, 1.5359F)
		);
		PartDefinition lAntler09b = lAntler09a.addOrReplaceChild(
			"lAntler09b",
			CubeListBuilder.create()
				.texOffs(84, 50)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler04a = lAntler02a.addOrReplaceChild(
			"lAntler04a",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(1.9F, 0.0F, 1.1F, -0.3142F, 0.1745F, 1.1345F)
		);
		PartDefinition lAntler04b = lAntler04a.addOrReplaceChild(
			"lAntler04b",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler05a = lAntler02a.addOrReplaceChild(
			"lAntler05a",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(1.9F, 0.0F, 3.1F, -0.5236F, 0.1745F, 1.1345F)
		);
		PartDefinition lAntler05b = lAntler05a.addOrReplaceChild(
			"lAntler05b",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -3.9F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition lAntler06a = lAntler02a.addOrReplaceChild(
			"lAntler06a",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(1.9F, 0.0F, 5.0F, -0.7854F, 0.1745F, 1.0472F)
		);
		PartDefinition lAntler06b = lAntler06a.addOrReplaceChild(
			"lAntler06b",
			CubeListBuilder.create()
				.texOffs(84, 57)
				.mirror()
				.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, -0.3665F)
		);
		PartDefinition rAntler00 = head.addOrReplaceChild(
			"rAntler00",
			CubeListBuilder.create().texOffs(84, 57).addBox(-3.0F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.0F, -2.4F, -2.1F, 0.0F, -0.1745F, 0.3491F)
		);
		PartDefinition rAntler01a = rAntler00.addOrReplaceChild(
			"rAntler01a",
			CubeListBuilder.create().texOffs(84, 57).addBox(-5.0F, -0.8F, -0.5F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.9F, 0.0F, 0.1F, 0.0F, -0.4363F, -0.1396F)
		);
		PartDefinition rAntler01b = rAntler01a.addOrReplaceChild(
			"rAntler01b",
			CubeListBuilder.create().texOffs(84, 57).addBox(-5.0F, -0.2F, -0.5F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 0.0F, 0.0F)
		);
		PartDefinition rAntler01c = rAntler01a.addOrReplaceChild(
			"rAntler01c",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -3.2F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-4.9F, 0.0F, 0.0F, 0.3491F, -0.1745F, -1.1345F)
		);
		PartDefinition rAntler01d = rAntler01c.addOrReplaceChild(
			"rAntler01d",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -0.9F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler01e = rAntler01a.addOrReplaceChild(
			"rAntler01e",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.5236F, 0.0F, -0.4712F)
		);
		PartDefinition rAntler02a = rAntler01a.addOrReplaceChild(
			"rAntler02a",
			CubeListBuilder.create().texOffs(83, 57).addBox(-2.5F, -0.6F, 0.4F, 4.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.8F, 0.0F, -0.3F, 0.4363F, 0.0873F, 0.0F)
		);
		PartDefinition rAntler03a = rAntler02a.addOrReplaceChild(
			"rAntler03a",
			CubeListBuilder.create().texOffs(78, 47).addBox(-2.8F, -0.61F, -0.7F, 5.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.4F, 0.0F, 5.3F, 0.2094F, 0.4363F, 0.0F)
		);
		PartDefinition rAntler07a = rAntler03a.addOrReplaceChild(
			"rAntler07a",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-1.9F, 0.0F, 2.8F, -0.7854F, -0.1745F, -1.1345F)
		);
		PartDefinition rAntler07b = rAntler07a.addOrReplaceChild(
			"rAntler07b",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler08a = rAntler03a.addOrReplaceChild(
			"rAntler08a",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -2.2F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-1.9F, -0.1F, 5.3F, -1.0472F, 0.0F, -1.4835F)
		);
		PartDefinition rAntler08b = rAntler08a.addOrReplaceChild(
			"rAntler08b",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler09a = rAntler03a.addOrReplaceChild(
			"rAntler09a",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -2.2F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-0.3F, -0.1F, 5.7F, -1.4835F, 0.1745F, -1.5359F)
		);
		PartDefinition rAntler09b = rAntler09a.addOrReplaceChild(
			"rAntler09b",
			CubeListBuilder.create().texOffs(84, 50).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler04a = rAntler02a.addOrReplaceChild(
			"rAntler04a",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-1.9F, 0.0F, 1.1F, -0.3142F, -0.1745F, -1.1345F)
		);
		PartDefinition rAntler04b = rAntler04a.addOrReplaceChild(
			"rAntler04b",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler05a = rAntler02a.addOrReplaceChild(
			"rAntler05a",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-1.9F, 0.0F, 3.1F, -0.5236F, -0.1745F, -1.1345F)
		);
		PartDefinition rAntler05b = rAntler05a.addOrReplaceChild(
			"rAntler05b",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -3.9F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		PartDefinition rAntler06a = rAntler02a.addOrReplaceChild(
			"rAntler06a",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-1.9F, 0.0F, 5.0F, -0.7854F, -0.1745F, -1.0472F)
		);
		PartDefinition rAntler06b = rAntler06a.addOrReplaceChild(
			"rAntler06b",
			CubeListBuilder.create().texOffs(84, 57).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, 0.3665F)
		);
		return LayerDefinition.create(meshdefinition, 128, 64);
	}
}
