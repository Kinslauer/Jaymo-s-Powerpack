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
 * (0.5.56) The werewolf's beast form, from {@code MoCModelWere} of
 * DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3: its boxes as they were, and its
 * animation as it was: the loping legs, the swinging arms, the head and its jaws following the look,
 * and the hunch (head low, back bent, arms nearly to the ground) while it's out for blood.
 */
final class WerewolfModel extends EntityModel<LegendState> {
	private static final float DEG = 57.29578F;

	private final ModelPart head, nose, snout, teethU, teethL, mouth, lEar, rEar, neck, neck2, sideburnL, sideburnR, chest, abdomen, tailA, tailC, tailB, tailD, rLegA, rFoot, rLegB, rLegC, lLegB, lFoot, lLegC, lLegA, rArmB, rArmC, lArmB, rHand, rArmA, lArmA, lArmC, lHand, rFinger1, rFinger2, rFinger3, rFinger4, rFinger5, lFinger1, lFinger2, lFinger3, lFinger4, lFinger5;

	WerewolfModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.nose = root.getChild("nose");
		this.snout = root.getChild("snout");
		this.teethU = root.getChild("teeth_u");
		this.teethL = root.getChild("teeth_l");
		this.mouth = root.getChild("mouth");
		this.lEar = root.getChild("lear");
		this.rEar = root.getChild("rear");
		this.neck = root.getChild("neck");
		this.neck2 = root.getChild("neck2");
		this.sideburnL = root.getChild("sideburn_l");
		this.sideburnR = root.getChild("sideburn_r");
		this.chest = root.getChild("chest");
		this.abdomen = root.getChild("abdomen");
		this.tailA = root.getChild("tail_a");
		this.tailC = root.getChild("tail_c");
		this.tailB = root.getChild("tail_b");
		this.tailD = root.getChild("tail_d");
		this.rLegA = root.getChild("rleg_a");
		this.rFoot = root.getChild("rfoot");
		this.rLegB = root.getChild("rleg_b");
		this.rLegC = root.getChild("rleg_c");
		this.lLegB = root.getChild("lleg_b");
		this.lFoot = root.getChild("lfoot");
		this.lLegC = root.getChild("lleg_c");
		this.lLegA = root.getChild("lleg_a");
		this.rArmB = root.getChild("rarm_b");
		this.rArmC = root.getChild("rarm_c");
		this.lArmB = root.getChild("larm_b");
		this.rHand = root.getChild("rhand");
		this.rArmA = root.getChild("rarm_a");
		this.lArmA = root.getChild("larm_a");
		this.lArmC = root.getChild("larm_c");
		this.lHand = root.getChild("lhand");
		this.rFinger1 = root.getChild("rfinger1");
		this.rFinger2 = root.getChild("rfinger2");
		this.rFinger3 = root.getChild("rfinger3");
		this.rFinger4 = root.getChild("rfinger4");
		this.rFinger5 = root.getChild("rfinger5");
		this.lFinger1 = root.getChild("lfinger1");
		this.lFinger2 = root.getChild("lfinger2");
		this.lFinger3 = root.getChild("lfinger3");
		this.lFinger4 = root.getChild("lfinger4");
		this.lFinger5 = root.getChild("lfinger5");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -3, -6, 8, 8, 6), PartPose.offset(0, -8, -6));
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(44, 33).addBox(-1.5F, -1.7F, -12.3F, 3, 2, 7), PartPose.offsetAndRotation(0, -8, -6, 0.2792527F, 0, 0));
		root.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 25).addBox(-2, 2, -12, 4, 2, 6), PartPose.offset(0, -8, -6));
		root.addOrReplaceChild("teeth_u", CubeListBuilder.create().texOffs(46, 18).addBox(-2, 4.01F, -12, 4, 2, 5), PartPose.offset(0, -8, -6));
		root.addOrReplaceChild("teeth_l", CubeListBuilder.create().texOffs(20, 109).addBox(-1.5F, -12.5F, 2.01F, 3, 5, 2), PartPose.offsetAndRotation(0, -8, -6, 2.530727F, 0, 0));
		root.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(42, 69).addBox(-1.5F, -12.5F, 0, 3, 9, 2), PartPose.offsetAndRotation(0, -8, -6, 2.530727F, 0, 0));
		root.addOrReplaceChild("lear", CubeListBuilder.create().texOffs(13, 14).addBox(0.5F, -7.5F, -1, 3, 5, 1), PartPose.offsetAndRotation(0, -8, -6, 0, 0, 0.1745329F));
		root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(22, 0).addBox(-3.5F, -7.5F, -1, 3, 5, 1), PartPose.offsetAndRotation(0, -8, -6, 0, 0, -0.1745329F));
		root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(28, 0).addBox(-3.5F, -3, -7, 7, 8, 7), PartPose.offsetAndRotation(0, -5, -2, -0.6025001F, 0, 0));
		root.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(0, 14).addBox(-1.5F, -2, -5, 3, 4, 7), PartPose.offsetAndRotation(0, -1, -6, -0.4537856F, 0, 0));
		root.addOrReplaceChild("sideburn_l", CubeListBuilder.create().texOffs(28, 33).addBox(3, 0, -2, 2, 6, 6), PartPose.offsetAndRotation(0, -8, -6, -0.2094395F, 0.418879F, -0.0872665F));
		root.addOrReplaceChild("sideburn_r", CubeListBuilder.create().texOffs(28, 45).addBox(-5, 0, -2, 2, 6, 6), PartPose.offsetAndRotation(0, -8, -6, -0.2094395F, -0.418879F, 0.0872665F));
		root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(20, 15).addBox(-4, 0, -7, 8, 8, 10), PartPose.offsetAndRotation(0, -6, -2.5F, 0.641331F, 0, 0));
		root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 40).addBox(-3, -8, -8, 6, 14, 8), PartPose.offsetAndRotation(0, 4.5F, 5, 0.2695449F, 0, 0));
		root.addOrReplaceChild("tail_a", CubeListBuilder.create().texOffs(52, 42).addBox(-1.5F, -1, -2, 3, 4, 3), PartPose.offsetAndRotation(0, 9.5F, 6, 1.064651F, 0, 0));
		root.addOrReplaceChild("tail_c", CubeListBuilder.create().texOffs(48, 59).addBox(-2, 6.8F, -4.6F, 4, 6, 4), PartPose.offsetAndRotation(0, 9.5F, 6, 1.099557F, 0, 0));
		root.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(48, 49).addBox(-2, 2, -2, 4, 6, 4), PartPose.offsetAndRotation(0, 9.5F, 6, 0.7504916F, 0, 0));
		root.addOrReplaceChild("tail_d", CubeListBuilder.create().texOffs(52, 69).addBox(-1.5F, 9.8F, -4.1F, 3, 5, 3), PartPose.offsetAndRotation(0, 9.5F, 6, 1.099557F, 0, 0));
		root.addOrReplaceChild("rleg_a", CubeListBuilder.create().texOffs(12, 64).addBox(-2.5F, -1.5F, -3.5F, 3, 8, 5), PartPose.offsetAndRotation(-3, 9.5F, 3, -0.8126625F, 0, 0));
		root.addOrReplaceChild("rfoot", CubeListBuilder.create().texOffs(14, 93).addBox(-2.506667F, 12.5F, -5, 3, 2, 3), PartPose.offset(-3, 9.5F, 3));
		root.addOrReplaceChild("rleg_b", CubeListBuilder.create().texOffs(14, 76).addBox(-1.9F, 4.2F, 0.5F, 2, 2, 5), PartPose.offsetAndRotation(-3, 9.5F, 3, -0.8445741F, 0, 0));
		root.addOrReplaceChild("rleg_c", CubeListBuilder.create().texOffs(14, 83).addBox(-2, 6.2F, 0.5F, 2, 8, 2), PartPose.offsetAndRotation(-3, 9.5F, 3, -0.2860688F, 0, 0));
		root.addOrReplaceChild("lleg_b", CubeListBuilder.create().texOffs(0, 76).addBox(-0.1F, 4.2F, 0.5F, 2, 2, 5), PartPose.offsetAndRotation(3, 9.5F, 3, -0.8445741F, 0, 0));
		root.addOrReplaceChild("lfoot", CubeListBuilder.create().texOffs(0, 93).addBox(-0.5066667F, 12.5F, -5, 3, 2, 3), PartPose.offset(3, 9.5F, 3));
		root.addOrReplaceChild("lleg_c", CubeListBuilder.create().texOffs(0, 83).addBox(0, 6.2F, 0.5F, 2, 8, 2), PartPose.offsetAndRotation(3, 9.5F, 3, -0.2860688F, 0, 0));
		root.addOrReplaceChild("lleg_a", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -1.5F, -3.5F, 3, 8, 5), PartPose.offsetAndRotation(3, 9.5F, 3, -0.8126625F, 0, 0));
		root.addOrReplaceChild("rarm_b", CubeListBuilder.create().texOffs(48, 77).addBox(-3.5F, 1, -1.5F, 4, 8, 4), PartPose.offsetAndRotation(-4, -4, -2, 0.2617994F, 0, 0.3490659F));
		root.addOrReplaceChild("rarm_c", CubeListBuilder.create().texOffs(48, 112).addBox(-6, 5, 3, 4, 7, 4), PartPose.offsetAndRotation(-4, -4, -2, -0.3490659F, 0, 0));
		root.addOrReplaceChild("larm_b", CubeListBuilder.create().texOffs(48, 89).addBox(-0.5F, 1, -1.5F, 4, 8, 4), PartPose.offsetAndRotation(4, -4, -2, 0.2617994F, 0, -0.3490659F));
		root.addOrReplaceChild("rhand", CubeListBuilder.create().texOffs(32, 118).addBox(-6, 12.5F, -1.5F, 4, 3, 4), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("rarm_a", CubeListBuilder.create().texOffs(0, 108).addBox(-5, -3, -2, 5, 5, 5), PartPose.offsetAndRotation(-4, -4, -2, 0.6320364F, 0, 0));
		root.addOrReplaceChild("larm_a", CubeListBuilder.create().texOffs(0, 98).addBox(0, -3, -2, 5, 5, 5), PartPose.offsetAndRotation(4, -4, -2, 0.6320364F, 0, 0));
		root.addOrReplaceChild("larm_c", CubeListBuilder.create().texOffs(48, 101).addBox(2, 5, 3, 4, 7, 4), PartPose.offsetAndRotation(4, -4, -2, -0.3490659F, 0, 0));
		root.addOrReplaceChild("lhand", CubeListBuilder.create().texOffs(32, 111).addBox(2, 12.5F, -1.5F, 4, 3, 4), PartPose.offset(4, -4, -2));
		root.addOrReplaceChild("rfinger1", CubeListBuilder.create().texOffs(8, 120).addBox(-3, 15.5F, 1, 1, 3, 1), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("rfinger2", CubeListBuilder.create().texOffs(12, 124).addBox(-3.5F, 15.5F, -1.5F, 1, 3, 1), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("rfinger3", CubeListBuilder.create().texOffs(12, 119).addBox(-4.8F, 15.5F, -1.5F, 1, 4, 1), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("rfinger4", CubeListBuilder.create().texOffs(16, 119).addBox(-6, 15.5F, -0.5F, 1, 4, 1), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("rfinger5", CubeListBuilder.create().texOffs(16, 124).addBox(-6, 15.5F, 1, 1, 3, 1), PartPose.offset(-4, -4, -2));
		root.addOrReplaceChild("lfinger1", CubeListBuilder.create().texOffs(8, 124).addBox(2, 15.5F, 1, 1, 3, 1), PartPose.offset(4, -4, -2));
		root.addOrReplaceChild("lfinger2", CubeListBuilder.create().texOffs(0, 124).addBox(2.5F, 15.5F, -1.5F, 1, 3, 1), PartPose.offset(4, -4, -2));
		root.addOrReplaceChild("lfinger3", CubeListBuilder.create().texOffs(0, 119).addBox(3.8F, 15.5F, -1.5F, 1, 4, 1), PartPose.offset(4, -4, -2));
		root.addOrReplaceChild("lfinger4", CubeListBuilder.create().texOffs(4, 119).addBox(5, 15.5F, -0.5F, 1, 4, 1), PartPose.offset(4, -4, -2));
		root.addOrReplaceChild("lfinger5", CubeListBuilder.create().texOffs(4, 124).addBox(5, 15.5F, 1, 1, 3, 1), PartPose.offset(4, -4, -2));
		return LayerDefinition.create(mesh, 64, 128);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
				float RLegXRot = Mth.cos((state.walkAnimationPos * 0.6662F) + 3.141593F) * 0.8F * state.walkAnimationSpeed;
		float LLegXRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.8F * state.walkAnimationSpeed;

		this.head.yRot = state.yRot / DEG; //this moves head to left and right
		//Head.xRot = state.xRot / DEG; //this moves head up and down

		if (!state.hunched) {
			this.head.y = -8F;
			this.head.z = -6F;
			this.head.xRot = state.xRot / DEG;
			this.neck.xRot = -34 / DEG;
			this.neck.y = -5F;
			this.neck.z = -2F;
			this.neck2.y = -1F;
			this.neck2.z = -6F;
			this.chest.y = -6F;
			this.chest.z = -2.5F;
			this.chest.xRot = 36 / DEG;
			this.abdomen.xRot = 15 / DEG;
			this.lLegA.z = 3F;

			this.lArmA.y = -4F;
			this.lArmA.z = -2F;

			this.tailA.y = 9.5F;
			this.tailA.z = 6F;

		} else {
			this.head.y = 0F;
			this.head.z = -11F;
			this.head.xRot = (15F + state.xRot) / DEG;

			this.neck.xRot = -10 / DEG;
			this.neck.y = 2F;
			this.neck.z = -6F;
			this.neck2.y = 9F;
			this.neck2.z = -9F;
			this.chest.y = 1F;
			this.chest.z = -7.5F;
			this.chest.xRot = 60 / DEG;
			this.abdomen.xRot = 75 / DEG;
			this.lLegA.z = 7F;
			this.lArmA.y = 4.5F;
			this.lArmA.z = -6F;
			this.tailA.y = 7.5F;
			this.tailA.z = 10F;

		}

		this.nose.y = this.head.y;
		this.snout.y = this.head.y;
		this.teethU.y = this.head.y;
		this.lEar.y = this.head.y;
		this.rEar.y = this.head.y;
		this.teethL.y = this.head.y;
		this.mouth.y = this.head.y;
		this.sideburnL.y = this.head.y;
		this.sideburnR.y = this.head.y;

		this.nose.z = this.head.z;
		this.snout.z = this.head.z;
		this.teethU.z = this.head.z;
		this.lEar.z = this.head.z;
		this.rEar.z = this.head.z;
		this.teethL.z = this.head.z;
		this.mouth.z = this.head.z;
		this.sideburnL.z = this.head.z;
		this.sideburnR.z = this.head.z;

		this.lArmB.y = this.lArmA.y;
		this.lArmC.y = this.lArmA.y;
		this.lHand.y = this.lArmA.y;
		this.lFinger1.y = this.lArmA.y;
		this.lFinger2.y = this.lArmA.y;
		this.lFinger3.y = this.lArmA.y;
		this.lFinger4.y = this.lArmA.y;
		this.lFinger5.y = this.lArmA.y;
		this.rArmA.y = this.lArmA.y;
		this.rArmB.y = this.lArmA.y;
		this.rArmC.y = this.lArmA.y;
		this.rHand.y = this.lArmA.y;
		this.rFinger1.y = this.lArmA.y;
		this.rFinger2.y = this.lArmA.y;
		this.rFinger3.y = this.lArmA.y;
		this.rFinger4.y = this.lArmA.y;
		this.rFinger5.y = this.lArmA.y;

		this.lArmB.z = this.lArmA.z;
		this.lArmC.z = this.lArmA.z;
		this.lHand.z = this.lArmA.z;
		this.lFinger1.z = this.lArmA.z;
		this.lFinger2.z = this.lArmA.z;
		this.lFinger3.z = this.lArmA.z;
		this.lFinger4.z = this.lArmA.z;
		this.lFinger5.z = this.lArmA.z;
		this.rArmA.z = this.lArmA.z;
		this.rArmB.z = this.lArmA.z;
		this.rArmC.z = this.lArmA.z;
		this.rHand.z = this.lArmA.z;
		this.rFinger1.z = this.lArmA.z;
		this.rFinger2.z = this.lArmA.z;
		this.rFinger3.z = this.lArmA.z;
		this.rFinger4.z = this.lArmA.z;
		this.rFinger5.z = this.lArmA.z;

		this.rLegA.z = this.lLegA.z;
		this.rLegB.z = this.lLegA.z;
		this.rLegC.z = this.lLegA.z;
		this.rFoot.z = this.lLegA.z;
		this.lLegB.z = this.lLegA.z;
		this.lLegC.z = this.lLegA.z;
		this.lFoot.z = this.lLegA.z;

		this.tailB.y = this.tailA.y;
		this.tailB.z = this.tailA.z;
		this.tailC.y = this.tailA.y;
		this.tailC.z = this.tailA.z;
		this.tailD.y = this.tailA.y;
		this.tailD.z = this.tailA.z;

		this.nose.yRot = this.head.yRot;
		this.snout.yRot = this.head.yRot;
		this.teethU.yRot = this.head.yRot;
		this.lEar.yRot = this.head.yRot;
		this.rEar.yRot = this.head.yRot;
		this.teethL.yRot = this.head.yRot;
		this.mouth.yRot = this.head.yRot;

		this.teethL.xRot = this.head.xRot + 2.530727F;
		this.mouth.xRot = this.head.xRot + 2.530727F;

		this.sideburnL.xRot = -0.2094395F + this.head.xRot;
		this.sideburnL.yRot = 0.418879F + this.head.yRot;
		this.sideburnR.xRot = -0.2094395F + this.head.xRot;
		this.sideburnR.yRot = -0.418879F + this.head.yRot;

		this.nose.xRot = 0.2792527F + this.head.xRot;
		this.snout.xRot = this.head.xRot;
		this.teethU.xRot = this.head.xRot;

		this.lEar.xRot = this.head.xRot;
		this.rEar.xRot = this.head.xRot;

		this.rLegA.xRot = -0.8126625F + RLegXRot;
		this.rLegB.xRot = -0.8445741F + RLegXRot;
		this.rLegC.xRot = -0.2860688F + RLegXRot;
		this.rFoot.xRot = RLegXRot;

		this.lLegA.xRot = -0.8126625F + LLegXRot;
		this.lLegB.xRot = -0.8445741F + LLegXRot;
		this.lLegC.xRot = -0.2860688F + LLegXRot;
		this.lFoot.xRot = LLegXRot;

		this.rArmA.zRot = -(Mth.cos(state.ageInTicks * 0.09F) * 0.05F) + 0.05F;
		this.lArmA.zRot = (Mth.cos(state.ageInTicks * 0.09F) * 0.05F) - 0.05F;
		this.rArmA.xRot = LLegXRot;//Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
		this.lArmA.xRot = RLegXRot;//Mth.sin(state.ageInTicks * 0.067F) * 0.05F;

		this.rArmB.zRot = 0.3490659F + this.rArmA.zRot;
		this.lArmB.zRot = -0.3490659F + this.lArmA.zRot;
		this.rArmB.xRot = 0.2617994F + this.rArmA.xRot;
		this.lArmB.xRot = 0.2617994F + this.lArmA.xRot;

		this.rArmC.zRot = this.rArmA.zRot;
		this.lArmC.zRot = this.lArmA.zRot;
		this.rArmC.xRot = -0.3490659F + this.rArmA.xRot;
		this.lArmC.xRot = -0.3490659F + this.lArmA.xRot;

		this.rHand.zRot = this.rArmA.zRot;
		this.lHand.zRot = this.lArmA.zRot;
		this.rHand.xRot = this.rArmA.xRot;
		this.lHand.xRot = this.lArmA.xRot;

		this.rFinger1.xRot = this.rArmA.xRot;
		this.rFinger2.xRot = this.rArmA.xRot;
		this.rFinger3.xRot = this.rArmA.xRot;
		this.rFinger4.xRot = this.rArmA.xRot;
		this.rFinger5.xRot = this.rArmA.xRot;

		this.lFinger1.xRot = this.lArmA.xRot;
		this.lFinger2.xRot = this.lArmA.xRot;
		this.lFinger3.xRot = this.lArmA.xRot;
		this.lFinger4.xRot = this.lArmA.xRot;
		this.lFinger5.xRot = this.lArmA.xRot;

		this.rFinger1.zRot = this.rArmA.zRot;
		this.rFinger2.zRot = this.rArmA.zRot;
		this.rFinger3.zRot = this.rArmA.zRot;
		this.rFinger4.zRot = this.rArmA.zRot;
		this.rFinger5.zRot = this.rArmA.zRot;

		this.lFinger1.zRot = this.lArmA.zRot;
		this.lFinger2.zRot = this.lArmA.zRot;
		this.lFinger3.zRot = this.lArmA.zRot;
		this.lFinger4.zRot = this.lArmA.zRot;
		this.lFinger5.zRot = this.lArmA.zRot;

		if (state.beast < 1.0F) {
			this.morph(state.beast, state.ageInTicks);
		}
	}

	/**
	 * (0.7.0) A cursed one changing ({@code p}: 0 the person, 1 the beast): the muzzle, ears, claws and tail pushing out
	 * (or drawing back in), and the whole body convulsing, hardest early on, the head and arms thrashing.
	 */
	private void morph(float p, float t) {
		float jaw = 0.2F + 0.8F * p;
		for (ModelPart part : new ModelPart[] {this.nose, this.snout, this.teethU, this.teethL, this.mouth}) {
			part.xScale = part.yScale = part.zScale = jaw;
		}
		float ear = 0.1F + 0.9F * p;
		this.lEar.xScale = this.lEar.yScale = this.lEar.zScale = ear;
		this.rEar.xScale = this.rEar.yScale = this.rEar.zScale = ear;
		float fur = 0.3F + 0.7F * p;
		this.sideburnL.xScale = this.sideburnL.yScale = this.sideburnL.zScale = fur;
		this.sideburnR.xScale = this.sideburnR.yScale = this.sideburnR.zScale = fur;
		for (ModelPart part : new ModelPart[] {this.tailA, this.tailB, this.tailC, this.tailD}) {
			part.xScale = part.yScale = part.zScale = Math.max(0.05F, p);
		}
		float claw = 0.3F + 0.7F * p;
		for (ModelPart part : new ModelPart[] {this.rFinger1, this.rFinger2, this.rFinger3, this.rFinger4, this.rFinger5,
			this.lFinger1, this.lFinger2, this.lFinger3, this.lFinger4, this.lFinger5}) {
			part.yScale = claw;
		}
		float amp = Mth.sin(p * Mth.PI) * 0.55F + (1.0F - p) * 0.25F;
		float headX = Mth.sin(t * 2.7F) * amp, headY = Mth.cos(t * 1.9F) * amp * 0.8F, headZ = Mth.cos(t * 3.1F) * amp * 0.6F;
		for (ModelPart part : new ModelPart[] {this.head, this.nose, this.snout, this.teethU, this.teethL, this.mouth, this.lEar, this.rEar,
			this.sideburnL, this.sideburnR}) {
			part.xRot += headX;
			part.yRot += headY;
			part.zRot += headZ;
		}
		float rArmX = Mth.sin(t * 2.2F + 1.0F) * amp * 1.4F, rArmZ = Mth.cos(t * 3.4F) * amp * 0.7F;
		for (ModelPart part : new ModelPart[] {this.rArmA, this.rArmB, this.rArmC, this.rHand, this.rFinger1, this.rFinger2, this.rFinger3,
			this.rFinger4, this.rFinger5}) {
			part.xRot += rArmX;
			part.zRot += rArmZ;
		}
		float lArmX = Mth.sin(t * 2.5F + 2.0F) * amp * 1.4F, lArmZ = -Mth.cos(t * 2.9F + 0.5F) * amp * 0.7F;
		for (ModelPart part : new ModelPart[] {this.lArmA, this.lArmB, this.lArmC, this.lHand, this.lFinger1, this.lFinger2, this.lFinger3,
			this.lFinger4, this.lFinger5}) {
			part.xRot += lArmX;
			part.zRot += lArmZ;
		}
		this.chest.xRot += Mth.sin(t * 2.3F) * amp * 0.5F;
		this.abdomen.xRot += Mth.cos(t * 2.9F) * amp * 0.4F;
		this.root().zRot = Mth.sin(t * 3.3F) * amp * 0.15F;
		this.root().xRot = Mth.sin(t * 1.7F) * amp * 0.1F;
	}
}
