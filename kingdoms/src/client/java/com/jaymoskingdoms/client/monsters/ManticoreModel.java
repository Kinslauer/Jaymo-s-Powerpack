package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The manticore, from {@code MoCModelManticore} (a {@code MoCModelNewBigCat} with its wings, mane, sabre teeth and stinger on)
 * of DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3: its boxes and animation as they were
 * (a lion's stride, wings that beat in the air and fold on the ground, a scorpion's tail that rears and strikes), without the
 * saddle, harness, collar and chest the original also carried.
 */
final class ManticoreModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Chest, NeckBase, HeadBack, Head, Nose, RightUpperLip, LeftUpperLip, UpperTeeth, LeftFang, RightFang, InsideMouth, LowerJaw, LowerJawTeeth, ChinHair, LeftChinBeard, RightChinBeard, ForeheadHair, Mane, RightEar, LeftEar, NeckHair, InnerWing, MidWing, OuterWing, InnerWingR, MidWingR, OuterWingR, Abdomen, Ass, TailRoot, Tail2, Tail3, Tail4, TailTip, TailTusk, STailRoot, STail2, STail3, STail4, STail5, StingerLump, Stinger, LeftUpperLeg, LeftLowerLeg, LeftFrontFoot, LeftClaw1, LeftClaw2, LeftClaw3, RightUpperLeg, RightLowerLeg, RightFrontFoot, RightClaw1, RightClaw2, RightClaw3, LeftHindUpperLeg, LeftAnkle, LeftHindLowerLeg, LeftHindFoot, RightHindUpperLeg, RightAnkle, RightHindLowerLeg, RightHindFoot;
	private boolean hasMane = true, isRidden, isSaddled, flapwings, onAir, floating, poisoning, movingTail, isSitting, isFlyer = true, hasStinger = true,
		isGhost, isMovingVertically;
	private int openMouthCounter;
	private float lLegMov, rLegMov;

	ManticoreModel(ModelPart root) {
		super(root);
		this.Chest = root.getChild("Chest");
		this.NeckBase = root.getChild("Chest").getChild("NeckBase");
		this.HeadBack = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack");
		this.Head = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head");
		this.Nose = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("Nose");
		this.RightUpperLip = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("RightUpperLip");
		this.LeftUpperLip = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LeftUpperLip");
		this.UpperTeeth = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("UpperTeeth");
		this.LeftFang = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LeftFang");
		this.RightFang = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("RightFang");
		this.InsideMouth = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("InsideMouth");
		this.LowerJaw = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LowerJaw");
		this.LowerJawTeeth = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LowerJaw").getChild("LowerJawTeeth");
		this.ChinHair = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LowerJaw").getChild("ChinHair");
		this.LeftChinBeard = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LeftChinBeard");
		this.RightChinBeard = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("RightChinBeard");
		this.ForeheadHair = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("ForeheadHair");
		this.Mane = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("Mane");
		this.RightEar = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("RightEar");
		this.LeftEar = root.getChild("Chest").getChild("NeckBase").getChild("HeadBack").getChild("Head").getChild("LeftEar");
		this.NeckHair = root.getChild("Chest").getChild("NeckBase").getChild("NeckHair");
		this.InnerWing = root.getChild("InnerWing");
		this.MidWing = root.getChild("MidWing");
		this.OuterWing = root.getChild("OuterWing");
		this.InnerWingR = root.getChild("InnerWingR");
		this.MidWingR = root.getChild("MidWingR");
		this.OuterWingR = root.getChild("OuterWingR");
		this.Abdomen = root.getChild("Chest").getChild("Abdomen");
		this.Ass = root.getChild("Chest").getChild("Abdomen").getChild("Ass");
		this.TailRoot = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot");
		this.Tail2 = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot").getChild("Tail2");
		this.Tail3 = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot").getChild("Tail2").getChild("Tail3");
		this.Tail4 = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot").getChild("Tail2").getChild("Tail3").getChild("Tail4");
		this.TailTip = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot").getChild("Tail2").getChild("Tail3").getChild("Tail4").getChild("TailTip");
		this.TailTusk = root.getChild("Chest").getChild("Abdomen").getChild("TailRoot").getChild("Tail2").getChild("Tail3").getChild("Tail4").getChild("TailTusk");
		this.STailRoot = root.getChild("STailRoot");
		this.STail2 = root.getChild("STail2");
		this.STail3 = root.getChild("STail3");
		this.STail4 = root.getChild("STail4");
		this.STail5 = root.getChild("STail5");
		this.StingerLump = root.getChild("StingerLump");
		this.Stinger = root.getChild("Stinger");
		this.LeftUpperLeg = root.getChild("Chest").getChild("LeftUpperLeg");
		this.LeftLowerLeg = root.getChild("Chest").getChild("LeftUpperLeg").getChild("LeftLowerLeg");
		this.LeftFrontFoot = root.getChild("Chest").getChild("LeftUpperLeg").getChild("LeftLowerLeg").getChild("LeftFrontFoot");
		this.LeftClaw1 = root.getChild("Chest").getChild("LeftUpperLeg").getChild("LeftLowerLeg").getChild("LeftFrontFoot").getChild("LeftClaw1");
		this.LeftClaw2 = root.getChild("Chest").getChild("LeftUpperLeg").getChild("LeftLowerLeg").getChild("LeftFrontFoot").getChild("LeftClaw2");
		this.LeftClaw3 = root.getChild("Chest").getChild("LeftUpperLeg").getChild("LeftLowerLeg").getChild("LeftFrontFoot").getChild("LeftClaw3");
		this.RightUpperLeg = root.getChild("Chest").getChild("RightUpperLeg");
		this.RightLowerLeg = root.getChild("Chest").getChild("RightUpperLeg").getChild("RightLowerLeg");
		this.RightFrontFoot = root.getChild("Chest").getChild("RightUpperLeg").getChild("RightLowerLeg").getChild("RightFrontFoot");
		this.RightClaw1 = root.getChild("Chest").getChild("RightUpperLeg").getChild("RightLowerLeg").getChild("RightFrontFoot").getChild("RightClaw1");
		this.RightClaw2 = root.getChild("Chest").getChild("RightUpperLeg").getChild("RightLowerLeg").getChild("RightFrontFoot").getChild("RightClaw2");
		this.RightClaw3 = root.getChild("Chest").getChild("RightUpperLeg").getChild("RightLowerLeg").getChild("RightFrontFoot").getChild("RightClaw3");
		this.LeftHindUpperLeg = root.getChild("Chest").getChild("Abdomen").getChild("LeftHindUpperLeg");
		this.LeftAnkle = root.getChild("Chest").getChild("Abdomen").getChild("LeftHindUpperLeg").getChild("LeftAnkle");
		this.LeftHindLowerLeg = root.getChild("Chest").getChild("Abdomen").getChild("LeftHindUpperLeg").getChild("LeftAnkle").getChild("LeftHindLowerLeg");
		this.LeftHindFoot = root.getChild("Chest").getChild("Abdomen").getChild("LeftHindUpperLeg").getChild("LeftAnkle").getChild("LeftHindLowerLeg").getChild("LeftHindFoot");
		this.RightHindUpperLeg = root.getChild("Chest").getChild("Abdomen").getChild("RightHindUpperLeg");
		this.RightAnkle = root.getChild("Chest").getChild("Abdomen").getChild("RightHindUpperLeg").getChild("RightAnkle");
		this.RightHindLowerLeg = root.getChild("Chest").getChild("Abdomen").getChild("RightHindUpperLeg").getChild("RightAnkle").getChild("RightHindLowerLeg");
		this.RightHindFoot = root.getChild("Chest").getChild("Abdomen").getChild("RightHindUpperLeg").getChild("RightAnkle").getChild("RightHindLowerLeg").getChild("RightHindFoot");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Chest = root.addOrReplaceChild("Chest", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-3.5F, 0F, -8F, 7F, 8F, 9F), PartPose.offset(0F, 8F, 0F));
		PartDefinition p_NeckBase = p_Chest.addOrReplaceChild("NeckBase", CubeListBuilder.create().texOffs(0, 7).mirror(false).addBox(-2.5F, 0F, -2.5F, 5F, 6F, 5F), PartPose.offsetAndRotation(0F, -0.5F, -8F, -0.2443461F, 0F, 0F));
		PartDefinition p_HeadBack = p_NeckBase.addOrReplaceChild("HeadBack", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-2.51F, -2.5F, -1F, 5F, 5F, 2F), PartPose.offsetAndRotation(0F, 2.7F, -2.9F, 0.2443461F, 0F, 0F));
		PartDefinition p_Head = p_HeadBack.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(32, 0).mirror(false).addBox(-3.5F, -3F, -2F, 7F, 6F, 4F), PartPose.offset(0F, 0.2F, -2.2F));
		PartDefinition p_Nose = p_Head.addOrReplaceChild("Nose", CubeListBuilder.create().texOffs(46, 19).mirror(false).addBox(-1.5F, -1F, -2F, 3F, 2F, 4F), PartPose.offsetAndRotation(0F, 0F, -3F, 0.4712389F, 0F, 0F));
		PartDefinition p_RightUpperLip = p_Head.addOrReplaceChild("RightUpperLip", CubeListBuilder.create().texOffs(34, 19).mirror(false).addBox(-1F, -1F, -2F, 2F, 2F, 4F), PartPose.offsetAndRotation(-1.25F, 1F, -2.8F, 0.1745329F, 0.0349066F, -0.2617994F));
		PartDefinition p_LeftUpperLip = p_Head.addOrReplaceChild("LeftUpperLip", CubeListBuilder.create().texOffs(34, 25).mirror(false).addBox(-1F, -1F, -2F, 2F, 2F, 4F), PartPose.offsetAndRotation(1.25F, 1F, -2.8F, 0.1745329F, -0.0349066F, 0.2617994F));
		PartDefinition p_UpperTeeth = p_Head.addOrReplaceChild("UpperTeeth", CubeListBuilder.create().texOffs(20, 7).mirror(false).addBox(-1.5F, -1F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(0F, 2F, -2.5F, 0.2617994F, 0F, 0F));
		PartDefinition p_LeftFang = p_Head.addOrReplaceChild("LeftFang", CubeListBuilder.create().texOffs(44, 10).mirror(false).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(1.2F, 2.8F, -3.4F, 0.2617994F, 0F, 0F));
		PartDefinition p_RightFang = p_Head.addOrReplaceChild("RightFang", CubeListBuilder.create().texOffs(48, 10).mirror(false).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-1.2F, 2.8F, -3.4F, 0.2617994F, 0F, 0F));
		PartDefinition p_InsideMouth = p_Head.addOrReplaceChild("InsideMouth", CubeListBuilder.create().texOffs(50, 0).mirror(false).addBox(-1.5F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(0F, 2F, -1F));
		PartDefinition p_LowerJaw = p_Head.addOrReplaceChild("LowerJaw", CubeListBuilder.create().texOffs(46, 25).mirror(false).addBox(-1.5F, -1F, -4F, 3F, 2F, 4F), PartPose.offset(0F, 2.1F, 0F));
		PartDefinition p_LowerJawTeeth = p_LowerJaw.addOrReplaceChild("LowerJawTeeth", CubeListBuilder.create().texOffs(20, 12).mirror(false).addBox(-1F, 0F, -1F, 2F, 1F, 2F), PartPose.offset(0F, -1.8F, -2.7F));
		PartDefinition p_ChinHair = p_LowerJaw.addOrReplaceChild("ChinHair", CubeListBuilder.create().texOffs(76, 7).mirror(false).addBox(-2.5F, 0F, -2F, 5F, 6F, 4F), PartPose.offset(0F, 0F, 1F));
		PartDefinition p_LeftChinBeard = p_Head.addOrReplaceChild("LeftChinBeard", CubeListBuilder.create().texOffs(48, 10).mirror(false).addBox(-1F, -2.5F, -2F, 2F, 5F, 4F), PartPose.offsetAndRotation(3.6F, 0F, 0.25F, 0F, 0.5235988F, 0F));
		PartDefinition p_RightChinBeard = p_Head.addOrReplaceChild("RightChinBeard", CubeListBuilder.create().texOffs(36, 10).mirror(false).addBox(-1F, -2.5F, -2F, 2F, 5F, 4F), PartPose.offsetAndRotation(-3.6F, 0F, 0.25F, 0F, -0.5235988F, 0F));
		PartDefinition p_ForeheadHair = p_Head.addOrReplaceChild("ForeheadHair", CubeListBuilder.create().texOffs(88, 0).mirror(false).addBox(-1.5F, -1.5F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, -3.2F, 0F, 0.1745329F, 0F, 0F));
		PartDefinition p_Mane = p_Head.addOrReplaceChild("Mane", CubeListBuilder.create().texOffs(94, 0).mirror(false).addBox(-5.5F, -5.5F, -3F, 11F, 11F, 6F), PartPose.offsetAndRotation(0F, 0.7F, 3.7F, -0.0872665F, 0F, 0F));
		PartDefinition p_RightEar = p_Head.addOrReplaceChild("RightEar", CubeListBuilder.create().texOffs(54, 7).mirror(false).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(-2.7F, -3.5F, 1F, 0F, 0F, -0.2617994F));
		PartDefinition p_LeftEar = p_Head.addOrReplaceChild("LeftEar", CubeListBuilder.create().texOffs(54, 4).mirror(false).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(2.7F, -3.5F, 1F, 0F, 0F, 0.2617994F));
		PartDefinition p_NeckHair = p_NeckBase.addOrReplaceChild("NeckHair", CubeListBuilder.create().texOffs(108, 17).mirror(false).addBox(-2F, -1F, -3F, 4F, 2F, 6F), PartPose.offsetAndRotation(0F, -0.5F, 3F, -0.1850049F, 0F, 0F));
		PartDefinition p_Abdomen = p_Chest.addOrReplaceChild("Abdomen", CubeListBuilder.create().texOffs(0, 35).mirror(false).addBox(-3F, 0F, 0F, 6F, 7F, 7F), PartPose.offsetAndRotation(0F, 0F, 0F, -0.0523599F, 0F, 0F));
		PartDefinition p_Ass = p_Abdomen.addOrReplaceChild("Ass", CubeListBuilder.create().texOffs(0, 49).mirror(false).addBox(-2.5F, 0F, 0F, 5F, 5F, 3F), PartPose.offsetAndRotation(0F, 0F, 7F, -0.3490658F, 0F, 0F));
		PartDefinition p_TailRoot = p_Abdomen.addOrReplaceChild("TailRoot", CubeListBuilder.create().texOffs(96, 83).mirror(false).addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 1F, 7F, 1.5184364F, 0F, 0F));
		PartDefinition p_Tail2 = p_TailRoot.addOrReplaceChild("Tail2", CubeListBuilder.create().texOffs(96, 75).mirror(false).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-0.01F, 3.5F, 0F, -0.5235988F, 0F, 0F));
		PartDefinition p_Tail3 = p_Tail2.addOrReplaceChild("Tail3", CubeListBuilder.create().texOffs(96, 67).mirror(false).addBox(-1F, 0F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0.01F, 5.5F, 0F, -0.296706F, 0F, 0F));
		PartDefinition p_Tail4 = p_Tail3.addOrReplaceChild("Tail4", CubeListBuilder.create().texOffs(96, 61).mirror(false).addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-0.01F, 5.5F, 0F, 0.3665191F, 0F, 0F));
		PartDefinition p_TailTip = p_Tail4.addOrReplaceChild("TailTip", CubeListBuilder.create().texOffs(96, 55).mirror(false).addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0.01F, 3.5F, 0F, 0.3665191F, 0F, 0F));
		PartDefinition p_TailTusk = p_Tail4.addOrReplaceChild("TailTusk", CubeListBuilder.create().texOffs(96, 49).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, 3.5F, 0F, 0.3665191F, 0F, 0F));
		PartDefinition p_LeftHindUpperLeg = p_Abdomen.addOrReplaceChild("LeftHindUpperLeg", CubeListBuilder.create().texOffs(0, 67).mirror(false).addBox(-2F, -1F, -1.5F, 3F, 8F, 5F), PartPose.offsetAndRotation(3F, 3F, 6.8F, -0.4363323F, 0F, 0F));
		PartDefinition p_LeftAnkle = p_LeftHindUpperLeg.addOrReplaceChild("LeftAnkle", CubeListBuilder.create().texOffs(0, 80).mirror(false).addBox(-1F, 0F, -1.5F, 2F, 3F, 3F), PartPose.offset(-0.5F, 4F, 5F));
		PartDefinition p_LeftHindLowerLeg = p_LeftAnkle.addOrReplaceChild("LeftHindLowerLeg", CubeListBuilder.create().texOffs(0, 86).mirror(false).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 3F, 0.5F));
		PartDefinition p_LeftHindFoot = p_LeftHindLowerLeg.addOrReplaceChild("LeftHindFoot", CubeListBuilder.create().texOffs(0, 91).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(0F, 2.6F, -0.8F, 0.4712389F, 0F, 0F));
		PartDefinition p_RightHindUpperLeg = p_Abdomen.addOrReplaceChild("RightHindUpperLeg", CubeListBuilder.create().texOffs(16, 67).mirror(false).addBox(-2F, -1F, -1.5F, 3F, 8F, 5F), PartPose.offsetAndRotation(-2F, 3F, 6.8F, -0.4363323F, 0F, 0F));
		PartDefinition p_RightAnkle = p_RightHindUpperLeg.addOrReplaceChild("RightAnkle", CubeListBuilder.create().texOffs(10, 80).mirror(false).addBox(-1F, 0F, -1.5F, 2F, 3F, 3F), PartPose.offset(-0.5F, 4F, 5F));
		PartDefinition p_RightHindLowerLeg = p_RightAnkle.addOrReplaceChild("RightHindLowerLeg", CubeListBuilder.create().texOffs(8, 86).mirror(false).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 3F, 0.5F));
		PartDefinition p_RightHindFoot = p_RightHindLowerLeg.addOrReplaceChild("RightHindFoot", CubeListBuilder.create().texOffs(12, 91).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(0F, 2.6F, -0.8F, 0.4712389F, 0F, 0F));
		PartDefinition p_LeftUpperLeg = p_Chest.addOrReplaceChild("LeftUpperLeg", CubeListBuilder.create().texOffs(0, 96).mirror(false).addBox(-1.5F, 0F, -2F, 3F, 7F, 4F), PartPose.offsetAndRotation(3.99F, 3F, -7F, 0.2617994F, 0F, 0F));
		PartDefinition p_LeftLowerLeg = p_LeftUpperLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create().texOffs(0, 107).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 6F, 3F), PartPose.offsetAndRotation(-0.01F, 6.5F, 0.2F, -0.3752458F, 0F, 0F));
		PartDefinition p_LeftFrontFoot = p_LeftLowerLeg.addOrReplaceChild("LeftFrontFoot", CubeListBuilder.create().texOffs(0, 116).mirror(false).addBox(-2F, 0F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 5F, -1F, 0.1134464F, 0F, 0F));
		PartDefinition p_LeftClaw1 = p_LeftFrontFoot.addOrReplaceChild("LeftClaw1", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(-1.3F, 1.2F, -3F, 0.7853982F, 0F, -0.0174533F));
		PartDefinition p_LeftClaw2 = p_LeftFrontFoot.addOrReplaceChild("LeftClaw2", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 1.1F, -3F, 0.7853982F, 0F, 0F));
		PartDefinition p_LeftClaw3 = p_LeftFrontFoot.addOrReplaceChild("LeftClaw3", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(1.3F, 1.2F, -3F, 0.7853982F, 0F, 0.0174533F));
		PartDefinition p_RightUpperLeg = p_Chest.addOrReplaceChild("RightUpperLeg", CubeListBuilder.create().texOffs(14, 96).mirror(false).addBox(-1.5F, 0F, -2F, 3F, 7F, 4F), PartPose.offsetAndRotation(-3.99F, 3F, -7F, 0.2617994F, 0F, 0F));
		PartDefinition p_RightLowerLeg = p_RightUpperLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create().texOffs(12, 107).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 6F, 3F), PartPose.offsetAndRotation(0.01F, 6.5F, 0.2F, -0.3752458F, 0F, 0F));
		PartDefinition p_RightFrontFoot = p_RightLowerLeg.addOrReplaceChild("RightFrontFoot", CubeListBuilder.create().texOffs(0, 122).mirror(false).addBox(-2F, 0F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 5F, -1F, 0.1134464F, 0F, 0F));
		PartDefinition p_RightClaw1 = p_RightFrontFoot.addOrReplaceChild("RightClaw1", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(-1.3F, 1.2F, -3F, 0.7853982F, 0F, -0.0174533F));
		PartDefinition p_RightClaw2 = p_RightFrontFoot.addOrReplaceChild("RightClaw2", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 1.1F, -3F, 0.7853982F, 0F, 0F));
		PartDefinition p_RightClaw3 = p_RightFrontFoot.addOrReplaceChild("RightClaw3", CubeListBuilder.create().texOffs(16, 125).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 1F, 2F), PartPose.offsetAndRotation(1.3F, 1.2F, -3F, 0.7853982F, 0F, 0.0174533F));
		PartDefinition p_InnerWing = root.addOrReplaceChild("InnerWing", CubeListBuilder.create().texOffs(26, 115).mirror(false).addBox(0F, 0F, 0F, 7F, 2F, 11F), PartPose.offsetAndRotation(4F, 9F, -7F, 0F, -0.3490658F, 0F));
		PartDefinition p_MidWing = root.addOrReplaceChild("MidWing", CubeListBuilder.create().texOffs(36, 89).mirror(false).addBox(1F, 0.1F, 1F, 12F, 2F, 11F), PartPose.offsetAndRotation(4F, 9F, -7F, 0F, 0.0872665F, 0F));
		PartDefinition p_OuterWing = root.addOrReplaceChild("OuterWing", CubeListBuilder.create().texOffs(62, 115).mirror(false).addBox(0F, 0F, 0F, 22F, 2F, 11F), PartPose.offsetAndRotation(16F, 9F, -7F, 0F, -0.3141593F, 0F));
		PartDefinition p_InnerWingR = root.addOrReplaceChild("InnerWingR", CubeListBuilder.create().texOffs(26, 102).mirror(false).addBox(-7F, 0F, 0F, 7F, 2F, 11F), PartPose.offsetAndRotation(-4F, 9F, -7F, 0F, 0.3490658F, 0F));
		PartDefinition p_MidWingR = root.addOrReplaceChild("MidWingR", CubeListBuilder.create().texOffs(82, 89).mirror(false).addBox(-13F, 0.1F, 1F, 12F, 2F, 11F), PartPose.offsetAndRotation(-4F, 9F, -7F, 0F, -0.0872665F, 0F));
		PartDefinition p_OuterWingR = root.addOrReplaceChild("OuterWingR", CubeListBuilder.create().texOffs(62, 102).mirror(false).addBox(-22F, 0F, 0F, 22F, 2F, 11F), PartPose.offsetAndRotation(-16F, 9F, -7F, 0F, 0.3141593F, 0F));
		PartDefinition p_STailRoot = root.addOrReplaceChild("STailRoot", CubeListBuilder.create().texOffs(104, 79).mirror(false).addBox(-3F, 4F, 5F, 6F, 4F, 6F), PartPose.offsetAndRotation(0F, 8F, 0F, 0.5796765F, 0F, 0F));
		PartDefinition p_STail2 = root.addOrReplaceChild("STail2", CubeListBuilder.create().texOffs(106, 69).mirror(false).addBox(-2.5F, 7.5F, 7.3F, 5F, 4F, 6F), PartPose.offsetAndRotation(0F, 8F, 0F, 0.9514626F, 0F, 0F));
		PartDefinition p_STail3 = root.addOrReplaceChild("STail3", CubeListBuilder.create().texOffs(108, 60).mirror(false).addBox(-2F, 13.5F, 3.3F, 4F, 3F, 6F), PartPose.offsetAndRotation(0F, 8F, 0F, 1.660128F, 0F, 0F));
		PartDefinition p_STail4 = root.addOrReplaceChild("STail4", CubeListBuilder.create().texOffs(108, 51).mirror(false).addBox(-2F, 15.2F, -5.3F, 4F, 3F, 6F), PartPose.offsetAndRotation(0F, 8F, 0F, 2.478058F, 0F, 0F));
		PartDefinition p_STail5 = root.addOrReplaceChild("STail5", CubeListBuilder.create().texOffs(108, 42).mirror(false).addBox(-2F, 12.9F, -9F, 4F, 3F, 6F), PartPose.offsetAndRotation(0F, 8F, 0F, 3.035737F, 0F, 0F));
		PartDefinition p_StingerLump = root.addOrReplaceChild("StingerLump", CubeListBuilder.create().texOffs(112, 34).mirror(false).addBox(-1.5F, 7.9F, 6F, 3F, 3F, 5F), PartPose.offsetAndRotation(0F, 8F, 0F, 2.031914F, 0F, 0F));
		PartDefinition p_Stinger = root.addOrReplaceChild("Stinger", CubeListBuilder.create().texOffs(118, 29).mirror(false).addBox(-0.5F, 1.9F, 8F, 1F, 1F, 4F), PartPose.offsetAndRotation(0F, 8F, 0F, 1.213985F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.flapwings = state.flapping;
		this.onAir = state.airborne;
		this.floating = state.airborne;
		this.poisoning = state.stinging;
		this.movingTail = state.stinging;
		this.openMouthCounter = state.mouth;
		this.isMovingVertically = state.airborne;
		this.isFlyer = !state.plain;
		this.hasStinger = !state.plain;
		for (ModelPart p : new ModelPart[] {InnerWing, MidWing, OuterWing, InnerWingR, MidWingR, OuterWingR, STailRoot, STail2, STail3, STail4, STail5, StingerLump, Stinger}) {
			p.visible = !state.plain;
		}
		for (ModelPart p : new ModelPart[] {Mane, NeckHair, ChinHair, ForeheadHair, LeftChinBeard, RightChinBeard}) {
			p.visible = !state.maneless;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {


        //f = time
        //f1 = movement speed!
        //f2 = ??timer!
        //f3 = Head Y movement or rotation yaw
        //f4 = Head X movement or rotation Pitch
        //f5 = ?

        float RLegXRot = Mth.cos((f * 0.8F) + 3.141593F) * 0.8F * f1;
        float LLegXRot = Mth.cos(f * 0.8F) * 0.8F * f1;
        float gallopRLegXRot = Mth.cos((f * 0.6F) + 3.141593F) * 0.8F * f1;
        float gallopLLegXRot = Mth.cos(f * 0.6F) * 0.8F * f1;
        
        float stingYOffset = 8F;
        float stingZOffset = 0F;

        //float TailXRot = Mth.cos(f * 0.4F) * 0.2F * f1;
        // cos(f * 0.4F) ==> determines the speed of the movement
        // * 0.2F * f1 ==> determines the amplitude of the pendulum movement

        //float legDisparity = (-3.141593F * f1 * 0.5F) + 3.141593F;
        //float rLegRotFinal = Mth.cos(this.rLegMov) * 0.8F * f1;
        //float lLegRotFinal = Mth.cos((this.rLegMov) + legDisparity) * 0.8F * f1;
        //this.rLegMov = this.rLegMov + (f1 * 0.25F);//(Mth.cos((f1 * 10F) + rLegMov) * f1);
        
        //lLegMov = lLegMov + (f1*0.05F);
        //leg should not return to 0 when stopped, and increment based on movement speed
        //the movement should still follow a cos function i.e. pendulum
        //the faster the speed, the higher the increment

        //TODO sync with legmovemnt speed
        if (this.movingTail) {
            this.Tail2.yRot = Mth.cos(f2 * 0.3F);

        } else {
            this.Tail2.yRot = 0F;
        }

        if (this.isSitting) {
            stingYOffset = 17F;
            stingZOffset = -3F;
            this.Chest.y = 14F;
            this.Abdomen.xRot = -10F / RAD;
            this.Chest.xRot = -45F / RAD;
            this.RightUpperLeg.xRot = (35F / RAD);
            this.RightLowerLeg.xRot = 5F / RAD;
            this.LeftUpperLeg.xRot = (35F / RAD);
            this.LeftLowerLeg.xRot = 5F / RAD;
            this.NeckBase.xRot = 20F / RAD;
            this.RightHindUpperLeg.y = 1F;
            this.RightHindUpperLeg.xRot = -50F / RAD;
            this.LeftHindUpperLeg.y = 1F;
            this.LeftHindUpperLeg.xRot = -50F / RAD;
            this.RightHindFoot.xRot = 90F / RAD;
            this.LeftHindFoot.xRot = 90F / RAD;
            //RightAnkle.rotateAngleX = 20F/RAD;
            //RightHindLowerLeg.rotateAngleX = 20F/RAD;
            this.TailRoot.xRot = 100F / RAD;
            this.Tail2.xRot = 35F / RAD;
            this.Tail3.xRot = 10F / RAD;
            this.NeckHair.y = 2F;
            //lying
            //NeckHair.setRotationPoint(0F, -1F, 3F);
            /*
             * 
            this.Chest.y = 16F;
            Abdomen.rotateAngleZ= -30F/RAD;
             */

            //this.TailRoot.yRot = Mth.cos(f2 * 0.3F);
            /*float tailMov = 0F;
            float f2a = f2 % 100F;
            if (f2a > 0 & f2a < 20) {
                tailMov = f2a / RAD;
                
                
            }*/
            //this.LArm3.yRot = (9F / RAD) - lHand;
            //this.LArm4.yRot = +lHand;

        }

        else {
            stingYOffset = 8F;
            stingZOffset = 0F;

            this.Chest.y = 8F;
            this.Abdomen.xRot = 0F;
            this.Chest.xRot = 0F;
            this.NeckBase.xRot = -14F / RAD;
            this.TailRoot.xRot = 87F / RAD;
            this.Tail2.xRot = -30F / RAD;
            this.Tail3.xRot = -17F / RAD;
            this.RightLowerLeg.xRot = -21.5F / RAD;
            this.LeftLowerLeg.xRot = -21.5F / RAD;
            this.RightHindUpperLeg.y = 3F;
            this.LeftHindUpperLeg.y = 3F;
            this.RightHindFoot.xRot = 27F / RAD;
            this.LeftHindFoot.xRot = 27F / RAD;
            //this.TailRoot.yRot = 0F;
            //this.Tail2.yRot = 0F;
            this.NeckHair.y = -0.5F;
            //RightAnkle.rotateAngleX = 0F;
            //RightHindLowerLeg.rotateAngleX = 0F;

            boolean galloping = (f1 >= 0.97F);
            if (this.onAir || this.isGhost) {
                if (this.isGhost || (this.isFlyer && f1 > 0)) {
                    float speedMov = (f1 * 0.5F);
                    this.RightUpperLeg.xRot = (45F / RAD) + speedMov;//(15F/RAD);
                    this.LeftUpperLeg.xRot = (45F / RAD) + speedMov;//(15F/RAD);
                    this.RightHindUpperLeg.xRot = (10F / RAD) + speedMov;//(30F/RAD);//LLegXRot;
                    this.LeftHindUpperLeg.xRot = (10F / RAD) + speedMov;//(30F/RAD);//RLegXRot;  
                } else if (this.isMovingVertically) //only when moving up or down
                {
                    this.RightUpperLeg.xRot = (-35F / RAD);
                    this.LeftUpperLeg.xRot = (-35F / RAD);
                    this.RightHindUpperLeg.xRot = (35F / RAD);//LLegXRot;
                    this.LeftHindUpperLeg.xRot = (35F / RAD);//RLegXRot;    
                }

            } else {
                if (galloping) {

                    this.RightUpperLeg.xRot = (15F / RAD) + gallopRLegXRot;//RLegXRot;
                    this.LeftUpperLeg.xRot = (15F / RAD) + gallopRLegXRot;//LLegXRot;
                    this.RightHindUpperLeg.xRot = (-25F / RAD) + gallopLLegXRot;//LLegXRot;
                    this.LeftHindUpperLeg.xRot = (-25F / RAD) + gallopLLegXRot;//RLegXRot;

                    this.Abdomen.yRot = 0F;
                } else {
                    this.RightUpperLeg.xRot = (15F / RAD) + RLegXRot;//rLegRotFinal;//RLegXRot;
                    this.LeftHindUpperLeg.xRot = (-25F / RAD) + RLegXRot;//rLegRotFinal;//RLegXRot;
                    this.LeftUpperLeg.xRot = (15F / RAD) + LLegXRot;//lLegRotFinal;//LLegXRot;
                    this.RightHindUpperLeg.xRot = (-25F / RAD) + LLegXRot;//lLegRotFinal;//LLegXRot;
                    if (!this.hasStinger) {
                        this.Abdomen.yRot = Mth.cos(f * 0.3F) * 0.25F * f1;
                    }
                }
            }


            //Tail2.rotateAngleY = LLegXRot;

            //y = A * sin(w * t - k *x)
            /*
             * w1 = speed of wave propagation +/- as needed t = time k = number of
             * waves A = amplitude of wave (how much will it depart from center x =
             * position? :)A 1.3k 0.5w -3.5
             */

            float TailXRot = Mth.cos(f * 0.4F) * 0.15F * f1;
            //float TailXRot = Mth.cos(f * 0.4F) * 0.2F * f1;
            // cos(f * 0.4F) ==> determines the speed of the movement
            // * 0.2F * f1 ==> determines the amplitude of the pendulum movement

            this.TailRoot.xRot = (87F / RAD) + TailXRot;
            this.Tail2.xRot = (-30F / RAD) + TailXRot;
            this.Tail3.xRot = (-17F / RAD) + TailXRot;
            this.Tail4.xRot = (21F / RAD) + TailXRot;
            this.TailTip.xRot = (21F / RAD) + TailXRot;
            this.TailTusk.xRot = (21F / RAD) + TailXRot;

        }//end of not sitting

        float HeadXRot = (f4 / 57.29578F);

        /* if (f3 > 10F) {
             f3 = 10F;
         }
         if (f3 < -10F) {
             f3 = -10F;
         }*/

        /**
         * f = distance walked f1 = speed 0 - 1 f2 = timer
         */

        this.HeadBack.xRot = 14F / RAD + HeadXRot;
        this.HeadBack.yRot = (f3 / 57.29578F);

        float mouthMov = 0F;
        if (this.openMouthCounter != 0) {
            if (this.openMouthCounter < 10) {
                mouthMov = 22 + (this.openMouthCounter * 3);
            } else if (this.openMouthCounter > 20) {
                mouthMov = 22 + (90 - (this.openMouthCounter * 3));
            } else {
                mouthMov = 55F;
            }
        }
        this.LowerJaw.xRot = mouthMov / RAD;



        /*float t = f / 2;
        boolean movetail = false;
        if (movetail) {
            t = f2 / 4F;
        }
        float A = 0.35F;//0.8F;
        float w = 0.6F;
        float k = 0.6F;

        int i = 0;
        float tailLat = 0F;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.TailRoot.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.Tail2.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.Tail3.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.Tail4.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.TailTip.yRot = TailTusk.rotateAngleY = tailLat;*/

        if (this.isFlyer) {

            /**
             * flapping wings or cruising. IF flapping wings, move up and down.
             * if cruising, movement depends of speed
             */
            float WingRot = 0F;
            if (this.flapwings) {
                WingRot = Mth.cos((f2 * 0.3F) + 3.141593F) * 1.2F;// * f1;
            } else
            //cruising
            {
                WingRot = Mth.cos((f * 0.5F)) * 0.1F;//* 1.2F * f1;
            }

            //float WingRot = Mth.cos((f * 0.6662F) + 3.141593F) * f1 * 1.2F;
            //InnerWing.setRotationPoint(5F, 3F, -6F);
            //setRotation(InnerWing, 0F, -0.3490659F, 0F);
            //X dist = 12
            //OuterWing.setRotationPoint(17F, 3F, -6F);
            //setRotation(OuterWing, 0F, -0.3228859F, 0F);

            if (this.floating) {
                this.OuterWing.yRot = -0.3228859F + (WingRot / 2F);
                this.OuterWingR.yRot = 0.3228859F - (WingRot / 2F);

            } else {
                WingRot = 60 / RAD;//0.7854F;
                this.OuterWing.yRot = -90 / RAD;//-1.396F;
                this.OuterWingR.yRot = 90 / RAD;//1.396F;
            }

            this.InnerWingR.y = this.InnerWing.y;
            this.InnerWingR.z = this.InnerWing.z;

            //OuterWing.rotationPointX = InnerWing.rotationPointX + (Mth.cos(WingRot)*12F);
            //the rotation point X rotates depending of the cos of rotation times the distance of the other block:
            //cos (WingRot) * 12F
            //the rotation PointX of Innerwing = 4
            //the rotation PointX of Outerwing = 16
            //the difference = 12.
            // for the rotation point Y, sin is used instead.
            //OuterWing.rotationPointX = InnerWing.rotationPointX + (Mth.cos(WingRot)*12F);
            //OuterWing.rotationPointY = InnerWing.rotationPointY + (Mth.sin(WingRot)*12F);

            this.OuterWing.x = this.InnerWing.x + (Mth.cos(WingRot) * 12F);
            this.OuterWingR.x = this.InnerWingR.x - (Mth.cos(WingRot) * 12F);

            this.MidWing.y = this.InnerWing.y;
            this.MidWingR.y = this.InnerWing.y;
            this.OuterWing.y = this.InnerWing.y + (Mth.sin(WingRot) * 12F);
            this.OuterWingR.y = this.InnerWingR.y + (Mth.sin(WingRot) * 12F);

            this.MidWing.z = this.InnerWing.z;
            this.MidWingR.z = this.InnerWing.z;
            this.OuterWing.z = this.InnerWing.z;
            this.OuterWingR.z = this.InnerWing.z;

            this.MidWing.zRot = WingRot;
            this.InnerWing.zRot = WingRot;
            this.OuterWing.zRot = WingRot;

            this.InnerWingR.zRot = -WingRot;
            this.MidWingR.zRot = -WingRot;
            this.OuterWingR.zRot = -WingRot;

            if (this.hasStinger) {

                if (!this.poisoning) {
                    //this.Body.xRot = 5F / RAD;
                    this.STailRoot.xRot = 33F / RAD;
                    this.STailRoot.y = stingYOffset;//8F;
                    this.STailRoot.z = stingZOffset;

                    this.STail2.xRot = 54.5F / RAD;
                    this.STail2.y = stingYOffset;//8F;
                    this.STail2.z = stingZOffset;

                    this.STail3.xRot = 95.1F / RAD;
                    this.STail3.y = stingYOffset;//8F;
                    this.STail3.z = stingZOffset;

                    this.STail4.xRot = 141.8F / RAD;
                    this.STail4.y = stingYOffset;//8F;
                    this.STail4.z = stingZOffset;

                    this.STail5.xRot = 173.9F / RAD;
                    this.STail5.y = stingYOffset;//8F;
                    this.STail5.z = stingZOffset;

                    this.StingerLump.xRot = 116.4F / RAD;
                    this.StingerLump.y = stingYOffset;//8F;
                    this.StingerLump.z = stingZOffset;

                    this.Stinger.xRot = 69.5F / RAD;
                    this.Stinger.y = stingYOffset;//8F;
                    this.Stinger.z = stingZOffset;

                } else if (!this.isSitting) {
                    //this.Body.xRot = 50F / RAD;
                    this.STailRoot.xRot = 95.2F / RAD;
                    this.STailRoot.y = 14.5F;
                    this.STailRoot.z = 2F;

                    this.STail2.xRot = 128.5F / RAD;
                    this.STail2.y = 15F;
                    this.STail2.z = 4F;

                    this.STail3.xRot = 169F / RAD;
                    this.STail3.y = 14F;
                    this.STail3.z = 3.8F;

                    this.STail4.xRot = 177F / RAD;
                    this.STail4.y = 13.5F;
                    this.STail4.z = -8.5F;

                    this.STail5.xRot = 180F / RAD;
                    this.STail5.y = 11.5F;
                    this.STail5.z = -17F;

                    this.StingerLump.xRot = 35.4F / RAD;
                    this.StingerLump.y = -4F;
                    this.StingerLump.z = -28F;

                    this.Stinger.xRot = 25.5F / RAD;
                    this.Stinger.y = 4F;
                    this.Stinger.z = -29F;

                }
            }

        }
    
	}
}
