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
 * The tarantula, from Better Animals Plus's {@code ModelTarantula} (CC BY-NC-SA 4.0, by cybercat5555 and itsmeowdev;
 * see {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}): eight legs scuttling in turn, twitching
 * mandibles and abdomen.
 */
final class TarantulaModel extends WildModel {
	TarantulaModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(WildRenderer.State state) {
		super.setupAnim(state);
		float limbSwing = state.walkAnimationPos, amount = state.walkAnimationSpeed, age = state.ageInTicks;
		this.look("head", state, 1.0F);
		String[] sides = {"r", "l"};
		float[] yBase = {-0.3054F, 0.0F, 0.0F, 0.3054F};
		float[] zBase = {-0.903F, -0.7721F, -0.7721F, -0.7721F};
		float[] phase = {0.0F, Mth.PI, Mth.PI * 0.5F, Mth.PI * 1.5F};
		for (int i = 0; i < 4; i++) {
			float y = -(Mth.cos(limbSwing * 0.6662F * 2.0F + phase[i]) * 0.4F) * amount;
			float z = Math.abs(Mth.sin(limbSwing * 0.6662F + phase[i]) * 0.4F) * amount;
			ModelPart r = this.part("rLeg0" + (i + 1) + "a"), l = this.part("lLeg0" + (i + 1) + "a");
			r.zRot = zBase[i] + z;
			l.zRot = -zBase[i] - z;
			r.yRot = yBase[i] + y;
			l.yRot = -yBase[i] - y;
		}
		this.part("rPedipalp01").xRot += Mth.cos(limbSwing * 0.6662F) * amount * 0.5F;
		this.part("lPedipalp01").xRot += Mth.cos(limbSwing * 0.6662F + Mth.PI) * amount * 0.5F;
		this.part("abdomen").xRot = wiggleAlt(age) - 0.182F;
		this.part("abdomen").zRot = wiggle(age);
		this.part("rMandible").xRot = wiggleAlt(age) - 0.2618F;
		this.part("rMandible").zRot = wiggle(age);
		this.part("lMandible").xRot = -wiggleAlt(age) - 0.2618F;
		this.part("lMandible").zRot = -wiggle(age);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition bodyBase = partdefinition.addOrReplaceChild(
			"bodyBase",
			CubeListBuilder.create().texOffs(25, 0).addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(-0.25F, 16.45F, 2.0F)
		);
		PartDefinition abdomen = bodyBase.addOrReplaceChild(
			"abdomen",
			CubeListBuilder.create().texOffs(0, 19).addBox(-4.5F, -3.0F, 0.0F, 9.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -0.3F, 0.6F, -0.182F, 0.0F, 0.0F)
		);
		PartDefinition thorax = bodyBase.addOrReplaceChild(
			"thorax",
			CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -8.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -1.6F, 0.0F)
		);
		PartDefinition head = thorax.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(36, 0).addBox(-4.0F, -3.0F, -3.0F, 7.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.5F, -0.1F, -7.2F)
		);
		PartDefinition lMandible = head.addOrReplaceChild(
			"lMandible",
			CubeListBuilder.create()
				.texOffs(50, 12)
				.mirror()
				.addBox(-1.55F, -2.45F, -1.95F, 3.0F, 5.0F, 2.0F, new CubeDeformation(-0.1F))
				.mirror(false),
			PartPose.offsetAndRotation(1.3F, 2.5F, -2.55F, 0.2618F, 0.0F, 0.0F)
		);
		PartDefinition lFang = lMandible.addOrReplaceChild("lFang", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.25F, -1.25F, -0.2618F, 0.0F, 0.0F));
		PartDefinition Box_r1 = lFang.addOrReplaceChild(
			"Box_r1",
			CubeListBuilder.create()
				.texOffs(49, 19)
				.mirror()
				.addBox(-0.8F, -0.6F, -0.3F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F)
		);
		PartDefinition rMandible = head.addOrReplaceChild(
			"rMandible",
			CubeListBuilder.create().texOffs(50, 12).addBox(-1.45F, -2.45F, -1.95F, 3.0F, 5.0F, 2.0F, new CubeDeformation(-0.1F)),
			PartPose.offsetAndRotation(-2.3F, 2.5F, -2.55F, 0.2618F, 0.0F, 0.0F)
		);
		PartDefinition rFang = rMandible.addOrReplaceChild("rFang", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.25F, -1.25F, -0.2618F, 0.0F, 0.0F));
		PartDefinition Box_r2 = rFang.addOrReplaceChild(
			"Box_r2",
			CubeListBuilder.create().texOffs(49, 19).addBox(-0.2F, -0.6F, -0.3F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F)
		);
		PartDefinition lLeg01a = thorax.addOrReplaceChild(
			"lLeg01a",
			CubeListBuilder.create()
				.texOffs(41, 42)
				.mirror()
				.addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.5F, 1.0F, -6.3F, 0.4363F, 0.3054F, 0.903F)
		);
		PartDefinition lLeg01b = lLeg01a.addOrReplaceChild(
			"lLeg01b",
			CubeListBuilder.create()
				.texOffs(42, 26)
				.mirror()
				.addBox(-0.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F))
				.mirror(false),
			PartPose.offsetAndRotation(0.3F, -5.6F, 0.0F, -0.3054F, 0.0F, -0.5236F)
		);
		PartDefinition lLeg01Fur02_r1 = lLeg01b.addOrReplaceChild(
			"lLeg01Fur02_r1",
			CubeListBuilder.create().texOffs(0, 47).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01Fur01_r1 = lLeg01b.addOrReplaceChild(
			"lLeg01Fur01_r1",
			CubeListBuilder.create().texOffs(0, 40).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01c = lLeg01b.addOrReplaceChild(
			"lLeg01c",
			CubeListBuilder.create()
				.texOffs(43, 32)
				.mirror()
				.addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(7.7F, -0.2F, 0.0F, 0.0F, -0.2618F, 0.7741F)
		);
		PartDefinition lLeg01Fur04_r1 = lLeg01c.addOrReplaceChild(
			"lLeg01Fur04_r1",
			CubeListBuilder.create().texOffs(21, 46).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01Fur03_r1 = lLeg01c.addOrReplaceChild(
			"lLeg01Fur03_r1",
			CubeListBuilder.create().texOffs(21, 40).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01d = lLeg01c.addOrReplaceChild(
			"lLeg01d",
			CubeListBuilder.create()
				.texOffs(49, 38)
				.mirror()
				.addBox(-0.55F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F))
				.mirror(false),
			PartPose.offsetAndRotation(6.7F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3054F)
		);
		PartDefinition rLeg01a = thorax.addOrReplaceChild(
			"rLeg01a",
			CubeListBuilder.create().texOffs(41, 42).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.5F, 1.0F, -6.3F, 0.4363F, -0.3054F, -0.903F)
		);
		PartDefinition rLeg01b = rLeg01a.addOrReplaceChild(
			"rLeg01b",
			CubeListBuilder.create().texOffs(42, 26).addBox(-8.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F)),
			PartPose.offsetAndRotation(-0.3F, -5.6F, 0.0F, -0.3054F, 0.0F, 0.5236F)
		);
		PartDefinition lLeg01Fur02_r2 = rLeg01b.addOrReplaceChild(
			"lLeg01Fur02_r2",
			CubeListBuilder.create()
				.texOffs(0, 47)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01Fur01_r2 = rLeg01b.addOrReplaceChild(
			"lLeg01Fur01_r2",
			CubeListBuilder.create()
				.texOffs(0, 40)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg01c = rLeg01b.addOrReplaceChild(
			"rLeg01c",
			CubeListBuilder.create().texOffs(43, 32).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-7.7F, -0.2F, 0.0F, 0.0F, 0.2618F, -0.7741F)
		);
		PartDefinition lLeg01Fur04_r2 = rLeg01c.addOrReplaceChild(
			"lLeg01Fur04_r2",
			CubeListBuilder.create()
				.texOffs(21, 46)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg01Fur03_r2 = rLeg01c.addOrReplaceChild(
			"lLeg01Fur03_r2",
			CubeListBuilder.create()
				.texOffs(21, 40)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg01d = rLeg01c.addOrReplaceChild(
			"rLeg01d",
			CubeListBuilder.create().texOffs(49, 38).addBox(-4.45F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)),
			PartPose.offsetAndRotation(-6.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3054F)
		);
		PartDefinition lLeg02a = thorax.addOrReplaceChild(
			"lLeg02a",
			CubeListBuilder.create()
				.texOffs(41, 42)
				.mirror()
				.addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.5F, 1.0F, -5.3F, 0.0873F, 0.0F, 0.7721F)
		);
		PartDefinition lLeg02b = lLeg02a.addOrReplaceChild(
			"lLeg02b",
			CubeListBuilder.create()
				.texOffs(42, 26)
				.mirror()
				.addBox(-0.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F))
				.mirror(false),
			PartPose.offsetAndRotation(0.3F, -5.6F, 0.0F, -0.1309F, 0.0436F, -0.3927F)
		);
		PartDefinition lLeg02Fur02_r1 = lLeg02b.addOrReplaceChild(
			"lLeg02Fur02_r1",
			CubeListBuilder.create().texOffs(0, 47).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02Fur01_r1 = lLeg02b.addOrReplaceChild(
			"lLeg02Fur01_r1",
			CubeListBuilder.create().texOffs(0, 40).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02c = lLeg02b.addOrReplaceChild(
			"lLeg02c",
			CubeListBuilder.create()
				.texOffs(43, 32)
				.mirror()
				.addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(7.7F, -0.2F, 0.0F, 0.0F, -0.0436F, 0.7741F)
		);
		PartDefinition lLeg02Fur04_r1 = lLeg02c.addOrReplaceChild(
			"lLeg02Fur04_r1",
			CubeListBuilder.create().texOffs(21, 46).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02Fur03_r1 = lLeg02c.addOrReplaceChild(
			"lLeg02Fur03_r1",
			CubeListBuilder.create().texOffs(21, 40).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02d = lLeg02c.addOrReplaceChild(
			"lLeg02d",
			CubeListBuilder.create()
				.texOffs(49, 38)
				.mirror()
				.addBox(-0.55F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F))
				.mirror(false),
			PartPose.offsetAndRotation(6.7F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3054F)
		);
		PartDefinition rLeg02a = thorax.addOrReplaceChild(
			"rLeg02a",
			CubeListBuilder.create().texOffs(41, 42).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.5F, 1.0F, -5.3F, 0.0873F, 0.0F, -0.7721F)
		);
		PartDefinition rLeg02b = rLeg02a.addOrReplaceChild(
			"rLeg02b",
			CubeListBuilder.create().texOffs(42, 26).addBox(-8.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F)),
			PartPose.offsetAndRotation(-0.3F, -5.6F, 0.0F, -0.1309F, -0.0436F, 0.3927F)
		);
		PartDefinition lLeg0Fur02_r1 = rLeg02b.addOrReplaceChild(
			"lLeg0Fur02_r1",
			CubeListBuilder.create()
				.texOffs(0, 47)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02Fur01_r2 = rLeg02b.addOrReplaceChild(
			"lLeg02Fur01_r2",
			CubeListBuilder.create()
				.texOffs(0, 40)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg02c = rLeg02b.addOrReplaceChild(
			"rLeg02c",
			CubeListBuilder.create().texOffs(43, 32).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-7.7F, -0.2F, 0.0F, 0.0F, 0.0436F, -0.7741F)
		);
		PartDefinition lLeg02Fur04_r2 = rLeg02c.addOrReplaceChild(
			"lLeg02Fur04_r2",
			CubeListBuilder.create()
				.texOffs(21, 46)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg02Fur03_r2 = rLeg02c.addOrReplaceChild(
			"lLeg02Fur03_r2",
			CubeListBuilder.create()
				.texOffs(21, 40)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg02d = rLeg02c.addOrReplaceChild(
			"rLeg02d",
			CubeListBuilder.create().texOffs(49, 38).addBox(-4.45F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)),
			PartPose.offsetAndRotation(-6.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3054F)
		);
		PartDefinition lLeg03a = thorax.addOrReplaceChild(
			"lLeg03a",
			CubeListBuilder.create()
				.texOffs(41, 42)
				.mirror()
				.addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.5F, 1.0F, -3.3F, -0.0436F, 0.0F, 0.7721F)
		);
		PartDefinition lLeg03b = lLeg03a.addOrReplaceChild(
			"lLeg03b",
			CubeListBuilder.create()
				.texOffs(42, 26)
				.mirror()
				.addBox(-0.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F))
				.mirror(false),
			PartPose.offsetAndRotation(0.3F, -5.6F, 0.0F, -0.0436F, -0.1745F, -0.3927F)
		);
		PartDefinition lLeg03Fur02_r1 = lLeg03b.addOrReplaceChild(
			"lLeg03Fur02_r1",
			CubeListBuilder.create().texOffs(0, 47).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03Fur01_r1 = lLeg03b.addOrReplaceChild(
			"lLeg03Fur01_r1",
			CubeListBuilder.create().texOffs(0, 40).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03c = lLeg03b.addOrReplaceChild(
			"lLeg03c",
			CubeListBuilder.create()
				.texOffs(43, 32)
				.mirror()
				.addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(7.7F, -0.2F, 0.0F, 0.0F, -0.0436F, 0.7741F)
		);
		PartDefinition lLeg03Fur04_r1 = lLeg03c.addOrReplaceChild(
			"lLeg03Fur04_r1",
			CubeListBuilder.create().texOffs(21, 46).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03Fur03_r1 = lLeg03c.addOrReplaceChild(
			"lLeg03Fur03_r1",
			CubeListBuilder.create().texOffs(21, 40).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03d = lLeg03c.addOrReplaceChild(
			"lLeg03d",
			CubeListBuilder.create()
				.texOffs(49, 38)
				.mirror()
				.addBox(-0.55F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F))
				.mirror(false),
			PartPose.offsetAndRotation(6.7F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3054F)
		);
		PartDefinition rLeg03a = thorax.addOrReplaceChild(
			"rLeg03a",
			CubeListBuilder.create().texOffs(41, 42).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.5F, 1.0F, -3.3F, -0.0436F, 0.0F, -0.7721F)
		);
		PartDefinition rLeg03b = rLeg03a.addOrReplaceChild(
			"rLeg03b",
			CubeListBuilder.create().texOffs(42, 26).addBox(-8.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F)),
			PartPose.offsetAndRotation(-0.3F, -5.6F, 0.0F, -0.0436F, 0.1745F, 0.3927F)
		);
		PartDefinition lLeg03Fur02_r2 = rLeg03b.addOrReplaceChild(
			"lLeg03Fur02_r2",
			CubeListBuilder.create()
				.texOffs(0, 47)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03Fur01_r2 = rLeg03b.addOrReplaceChild(
			"lLeg03Fur01_r2",
			CubeListBuilder.create()
				.texOffs(0, 40)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg03c = rLeg03b.addOrReplaceChild(
			"rLeg03c",
			CubeListBuilder.create().texOffs(43, 32).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-7.7F, -0.2F, 0.0F, 0.0F, 0.0436F, -0.7741F)
		);
		PartDefinition lLeg03Fur04_r2 = rLeg03c.addOrReplaceChild(
			"lLeg03Fur04_r2",
			CubeListBuilder.create()
				.texOffs(21, 46)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg03Fur03_r2 = rLeg03c.addOrReplaceChild(
			"lLeg03Fur03_r2",
			CubeListBuilder.create()
				.texOffs(21, 40)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg03d = rLeg03c.addOrReplaceChild(
			"rLeg03d",
			CubeListBuilder.create().texOffs(49, 38).addBox(-4.45F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)),
			PartPose.offsetAndRotation(-6.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3054F)
		);
		PartDefinition lLeg04a = thorax.addOrReplaceChild(
			"lLeg04a",
			CubeListBuilder.create()
				.texOffs(41, 42)
				.mirror()
				.addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(2.5F, 1.0F, -1.8F, -0.3491F, -0.3054F, 0.7721F)
		);
		PartDefinition lLeg04b = lLeg04a.addOrReplaceChild(
			"lLeg04b",
			CubeListBuilder.create()
				.texOffs(42, 26)
				.mirror()
				.addBox(-0.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F))
				.mirror(false),
			PartPose.offsetAndRotation(0.3F, -5.6F, 0.0F, 0.0F, 0.0436F, -0.3491F)
		);
		PartDefinition lLeg04Fur02_r1 = lLeg04b.addOrReplaceChild(
			"lLeg04Fur02_r1",
			CubeListBuilder.create().texOffs(0, 47).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04Fur01_r1 = lLeg04b.addOrReplaceChild(
			"lLeg04Fur01_r1",
			CubeListBuilder.create().texOffs(0, 40).addBox(-1.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04c = lLeg04b.addOrReplaceChild(
			"lLeg04c",
			CubeListBuilder.create()
				.texOffs(43, 32)
				.mirror()
				.addBox(0.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(7.7F, -0.2F, 0.0F, 0.0F, -0.0436F, 0.7741F)
		);
		PartDefinition lLeg04Fur04_r1 = lLeg04c.addOrReplaceChild(
			"lLeg04Fur04_r1",
			CubeListBuilder.create().texOffs(21, 46).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04Fur03_r1 = lLeg04c.addOrReplaceChild(
			"lLeg04Fur03_r1",
			CubeListBuilder.create().texOffs(21, 40).addBox(-1.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04d = lLeg04c.addOrReplaceChild(
			"lLeg04d",
			CubeListBuilder.create()
				.texOffs(49, 38)
				.mirror()
				.addBox(-0.55F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F))
				.mirror(false),
			PartPose.offsetAndRotation(6.7F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3054F)
		);
		PartDefinition rLeg04a = thorax.addOrReplaceChild(
			"rLeg04a",
			CubeListBuilder.create().texOffs(41, 42).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.5F, 1.0F, -1.8F, -0.3491F, 0.3054F, -0.7721F)
		);
		PartDefinition rLeg04b = rLeg04a.addOrReplaceChild(
			"rLeg04b",
			CubeListBuilder.create().texOffs(42, 26).addBox(-8.5F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(-0.01F)),
			PartPose.offsetAndRotation(-0.3F, -5.6F, 0.0F, 0.0F, -0.0436F, 0.3491F)
		);
		PartDefinition lLeg04Fur02_r2 = rLeg04b.addOrReplaceChild(
			"lLeg04Fur02_r2",
			CubeListBuilder.create()
				.texOffs(0, 47)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04Fur01_r2 = rLeg04b.addOrReplaceChild(
			"lLeg04Fur01_r2",
			CubeListBuilder.create()
				.texOffs(0, 40)
				.mirror()
				.addBox(-9.0F, -3.0F, 0.0F, 10.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg04c = rLeg04b.addOrReplaceChild(
			"rLeg04c",
			CubeListBuilder.create().texOffs(43, 32).addBox(-7.0F, -1.0F, -1.0F, 7.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-7.7F, -0.2F, 0.0F, 0.0F, 0.0436F, -0.7741F)
		);
		PartDefinition lLeg04Fur04_r2 = rLeg04c.addOrReplaceChild(
			"lLeg04Fur04_r2",
			CubeListBuilder.create()
				.texOffs(21, 46)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F)
		);
		PartDefinition lLeg04Fur03_r2 = rLeg04c.addOrReplaceChild(
			"lLeg04Fur03_r2",
			CubeListBuilder.create()
				.texOffs(21, 40)
				.mirror()
				.addBox(-8.0F, -2.25F, 0.0F, 9.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F)
		);
		PartDefinition rLeg04d = rLeg04c.addOrReplaceChild(
			"rLeg04d",
			CubeListBuilder.create().texOffs(49, 38).addBox(-4.45F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(-0.2F)),
			PartPose.offsetAndRotation(-6.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3054F)
		);
		PartDefinition lPedipalp01 = thorax.addOrReplaceChild(
			"lPedipalp01",
			CubeListBuilder.create()
				.texOffs(29, 20)
				.mirror()
				.addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(3.05F, 1.3F, -7.6F, -0.1367F, -0.3491F, 0.0F)
		);
		PartDefinition lPedipalp02 = lPedipalp01.addOrReplaceChild(
			"lPedipalp02",
			CubeListBuilder.create()
				.texOffs(30, 13)
				.mirror()
				.addBox(-0.94F, -1.0F, -4.05F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.05F, -0.4F, -4.15F, 0.9948F, 0.0F, 0.0F)
		);
		PartDefinition lPedipalp03 = lPedipalp02.addOrReplaceChild(
			"lPedipalp03",
			CubeListBuilder.create().texOffs(28, 11).addBox(-1.0F, -0.5F, -5.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.05F, 0.0F, -4.55F, 0.7418F, 0.0F, 0.0F)
		);
		PartDefinition rPedipalp01 = thorax.addOrReplaceChild(
			"rPedipalp01",
			CubeListBuilder.create().texOffs(29, 20).addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-3.05F, 1.3F, -7.6F, -0.1367F, 0.3491F, 0.0F)
		);
		PartDefinition rPedipalp02 = rPedipalp01.addOrReplaceChild(
			"rPedipalp02",
			CubeListBuilder.create().texOffs(30, 13).addBox(-1.06F, -1.0F, -4.05F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.05F, -0.4F, -4.15F, 0.9948F, 0.0F, 0.0F)
		);
		PartDefinition rPedipalp03 = rPedipalp02.addOrReplaceChild(
			"rPedipalp03",
			CubeListBuilder.create()
				.texOffs(28, 11)
				.mirror()
				.addBox(-1.0F, -0.5F, -5.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
				.mirror(false),
			PartPose.offsetAndRotation(-0.05F, 0.0F, -4.55F, 0.7418F, 0.0F, 0.0F)
		);
		return LayerDefinition.create(meshdefinition, 64, 64);
	}
}
