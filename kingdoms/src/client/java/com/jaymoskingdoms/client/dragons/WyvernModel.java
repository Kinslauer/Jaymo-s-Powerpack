package com.jaymoskingdoms.client.dragons;

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
 * The wyvern, from {@code MoCModelWyvern} of DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3:
 * its boxes as they were and its animation as it was (a lean, two-legged flier with its wings for forearms: folded and
 * walked on the ground, spread and beating in the air, its tail whipping, its neck straight under a rider), without
 * the armour and the saddle bag its original carried.
 */
final class WyvernModel extends EntityModel<WindDragonRenderer.State> {
	private static final float RAD = 57.29578F;
	private final ModelPart Tail, back1, tail1, back2, tail2, back3, tail3, back4, tail4, tail5, chest, neckplate3, neck3, MainHead, neck2, neckplate2, neck1, neckplate1, head, snout, headplate, beak, righteyesock, lefteyesock, Jaw, leftupjaw, rightupjaw, mouthrod, helmetstrap1, helmetstrap2, controlrope1, controlrope2, rightearskin, leftearskin, rightspine1, rightspine2, rightspine3, leftspine1, leftspine2, leftspine3, torso, saddle, rightshoulder, leftshoulder, LeftWing, leftuparm, leftlowarm, leftfing1a, leftfing1b, leftfing2a, leftfing2b, leftfing3a, leftfing3b, leftwingflap1, leftwingflap2, leftwingflap3, RightWing, rightuparm, rightlowarm, rightfing1a, rightfing1b, rightwingflap1, rightfing2a, rightfing2b, rightwingflap2, rightfing3a, rightfing3b, rightwingflap3, leftupleg, leftmidleg, leftlowleg, leftfoot, lefttoe1, lefttoe3, lefttoe2, leftclaw1, leftclaw2, leftclaw3, rightupleg, rightmidleg, rightlowleg, rightfoot, righttoe1, righttoe3, righttoe2, rightclaw1, rightclaw2, rightclaw3, chestbelt, stomachbelt;
	private boolean isRidden, isSaddled, flapwings, onAir, diving, isSitting;
	private final boolean isGhost = false;
	private int openMouth;
	private float flapPhase;

	WyvernModel(ModelPart root) {
		super(root);
		this.Tail = root.getChild("Tail");
		this.back1 = root.getChild("back1");
		this.tail1 = root.getChild("Tail").getChild("tail1");
		this.back2 = root.getChild("Tail").getChild("tail1").getChild("back2");
		this.tail2 = root.getChild("Tail").getChild("tail1").getChild("tail2");
		this.back3 = root.getChild("Tail").getChild("tail1").getChild("tail2").getChild("back3");
		this.tail3 = root.getChild("Tail").getChild("tail1").getChild("tail2").getChild("tail3");
		this.back4 = root.getChild("Tail").getChild("tail1").getChild("tail2").getChild("tail3").getChild("back4");
		this.tail4 = root.getChild("Tail").getChild("tail1").getChild("tail2").getChild("tail3").getChild("tail4");
		this.tail5 = root.getChild("Tail").getChild("tail1").getChild("tail2").getChild("tail3").getChild("tail4").getChild("tail5");
		this.chest = root.getChild("chest");
		this.neckplate3 = root.getChild("neckplate3");
		this.neck3 = root.getChild("neck3");
		this.MainHead = root.getChild("MainHead");
		this.neck2 = root.getChild("MainHead").getChild("neck2");
		this.neckplate2 = root.getChild("MainHead").getChild("neck2").getChild("neckplate2");
		this.neck1 = root.getChild("MainHead").getChild("neck2").getChild("neck1");
		this.neckplate1 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("neckplate1");
		this.head = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head");
		this.snout = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("snout");
		this.headplate = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("headplate");
		this.beak = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("snout").getChild("beak");
		this.righteyesock = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("righteyesock");
		this.lefteyesock = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("lefteyesock");
		this.Jaw = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("Jaw");
		this.leftupjaw = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("leftupjaw");
		this.rightupjaw = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("rightupjaw");
		this.mouthrod = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("mouthrod");
		this.helmetstrap1 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("helmetstrap1");
		this.helmetstrap2 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("helmetstrap2");
		this.controlrope1 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("mouthrod").getChild("controlrope1");
		this.controlrope2 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("mouthrod").getChild("controlrope2");
		this.rightearskin = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("rightearskin");
		this.leftearskin = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("leftearskin");
		this.rightspine1 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("rightearskin").getChild("rightspine1");
		this.rightspine2 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("rightearskin").getChild("rightspine2");
		this.rightspine3 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("rightearskin").getChild("rightspine3");
		this.leftspine1 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("leftearskin").getChild("leftspine1");
		this.leftspine2 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("leftearskin").getChild("leftspine2");
		this.leftspine3 = root.getChild("MainHead").getChild("neck2").getChild("neck1").getChild("head").getChild("leftearskin").getChild("leftspine3");
		this.torso = root.getChild("torso");
		this.saddle = root.getChild("saddle");
		this.rightshoulder = root.getChild("rightshoulder");
		this.leftshoulder = root.getChild("leftshoulder");
		this.LeftWing = root.getChild("LeftWing");
		this.leftuparm = root.getChild("LeftWing").getChild("leftuparm");
		this.leftlowarm = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm");
		this.leftfing1a = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing1a");
		this.leftfing1b = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing1a").getChild("leftfing1b");
		this.leftfing2a = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing2a");
		this.leftfing2b = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing2a").getChild("leftfing2b");
		this.leftfing3a = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing3a");
		this.leftfing3b = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing3a").getChild("leftfing3b");
		this.leftwingflap1 = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing1a").getChild("leftwingflap1");
		this.leftwingflap2 = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing2a").getChild("leftwingflap2");
		this.leftwingflap3 = root.getChild("LeftWing").getChild("leftuparm").getChild("leftlowarm").getChild("leftfing3a").getChild("leftwingflap3");
		this.RightWing = root.getChild("RightWing");
		this.rightuparm = root.getChild("RightWing").getChild("rightuparm");
		this.rightlowarm = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm");
		this.rightfing1a = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing1a");
		this.rightfing1b = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing1a").getChild("rightfing1b");
		this.rightwingflap1 = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing1a").getChild("rightwingflap1");
		this.rightfing2a = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing2a");
		this.rightfing2b = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing2a").getChild("rightfing2b");
		this.rightwingflap2 = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing2a").getChild("rightwingflap2");
		this.rightfing3a = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing3a");
		this.rightfing3b = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing3a").getChild("rightfing3b");
		this.rightwingflap3 = root.getChild("RightWing").getChild("rightuparm").getChild("rightlowarm").getChild("rightfing3a").getChild("rightwingflap3");
		this.leftupleg = root.getChild("leftupleg");
		this.leftmidleg = root.getChild("leftupleg").getChild("leftmidleg");
		this.leftlowleg = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg");
		this.leftfoot = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot");
		this.lefttoe1 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe1");
		this.lefttoe3 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe3");
		this.lefttoe2 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe2");
		this.leftclaw1 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe1").getChild("leftclaw1");
		this.leftclaw2 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe2").getChild("leftclaw2");
		this.leftclaw3 = root.getChild("leftupleg").getChild("leftmidleg").getChild("leftlowleg").getChild("leftfoot").getChild("lefttoe3").getChild("leftclaw3");
		this.rightupleg = root.getChild("rightupleg");
		this.rightmidleg = root.getChild("rightupleg").getChild("rightmidleg");
		this.rightlowleg = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg");
		this.rightfoot = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot");
		this.righttoe1 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe1");
		this.righttoe3 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe3");
		this.righttoe2 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe2");
		this.rightclaw1 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe1").getChild("rightclaw1");
		this.rightclaw2 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe2").getChild("rightclaw2");
		this.rightclaw3 = root.getChild("rightupleg").getChild("rightmidleg").getChild("rightlowleg").getChild("rightfoot").getChild("righttoe3").getChild("rightclaw3");
		this.chestbelt = root.getChild("chestbelt");
		this.stomachbelt = root.getChild("stomachbelt");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_tail1 = p_Tail.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 22).mirror(false).addBox(-4F, 0F, 0F, 8F, 8F, 10F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_back2 = p_tail1.addOrReplaceChild("back2", CubeListBuilder.create().texOffs(100, 14).mirror(false).addBox(-2F, -2F, 0F, 4F, 2F, 10F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 40).mirror(false).addBox(-3F, 0F, 0F, 6F, 6F, 9F), PartPose.offset(0F, 0F, 10F));
		PartDefinition p_back3 = p_tail2.addOrReplaceChild("back3", CubeListBuilder.create().texOffs(104, 26).mirror(false).addBox(-1.5F, -2F, 0F, 3F, 2F, 9F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_tail3 = p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(0, 55).mirror(false).addBox(-2F, 0F, 0F, 4F, 5F, 8F), PartPose.offset(0F, 0F, 8F));
		PartDefinition p_back4 = p_tail3.addOrReplaceChild("back4", CubeListBuilder.create().texOffs(108, 37).mirror(false).addBox(-1F, -2F, 0F, 2F, 2F, 8F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_tail4 = p_tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(0, 68).mirror(false).addBox(-1F, 0F, 0F, 2F, 5F, 7F), PartPose.offset(0F, -1F, 7F));
		PartDefinition p_tail5 = p_tail4.addOrReplaceChild("tail5", CubeListBuilder.create().texOffs(0, 80).mirror(false).addBox(-0.5F, 0F, 0F, 1F, 3F, 7F), PartPose.offset(0F, 1F, 6F));
		PartDefinition p_back1 = root.addOrReplaceChild("back1", CubeListBuilder.create().texOffs(92, 0).mirror(false).addBox(-3F, -2F, -12F, 6F, 2F, 12F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_chest = root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(44, 0).mirror(false).addBox(-4.5F, 2.7F, -13F, 9F, 10F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, -0.2602503F, 0F, 0F));
		PartDefinition p_neckplate3 = root.addOrReplaceChild("neckplate3", CubeListBuilder.create().texOffs(112, 64).mirror(false).addBox(-2F, -2F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 0F, -12F, -0.669215F, 0F, 0F));
		PartDefinition p_neck3 = root.addOrReplaceChild("neck3", CubeListBuilder.create().texOffs(100, 113).mirror(false).addBox(-3F, 0F, -2F, 6F, 7F, 8F), PartPose.offsetAndRotation(0F, 0F, -12F, -0.669215F, 0F, 0F));
		PartDefinition p_MainHead = root.addOrReplaceChild("MainHead", CubeListBuilder.create(), PartPose.offset(0F, 3F, -15F));
		PartDefinition p_neck2 = p_MainHead.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(102, 99).mirror(false).addBox(-2.5F, -3F, -8F, 5F, 6F, 8F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_neckplate2 = p_neck2.addOrReplaceChild("neckplate2", CubeListBuilder.create().texOffs(106, 54).mirror(false).addBox(-1.5F, -2F, -8F, 3F, 2F, 8F), PartPose.offset(0F, -3F, 0F));
		PartDefinition p_neck1 = p_neck2.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(104, 85).mirror(false).addBox(-2F, -3F, -8F, 4F, 6F, 8F), PartPose.offset(0F, -0.5F, -5.5F));
		PartDefinition p_neckplate1 = p_neck1.addOrReplaceChild("neckplate1", CubeListBuilder.create().texOffs(80, 108).mirror(false).addBox(-1F, -2F, -8F, 2F, 2F, 8F), PartPose.offset(0F, -3F, 0F));
		PartDefinition p_head = p_neck1.addOrReplaceChild("head", CubeListBuilder.create().texOffs(98, 70).mirror(false).addBox(-3.5F, -3.5F, -8F, 7F, 7F, 8F), PartPose.offset(0F, 0F, -7F));
		PartDefinition p_snout = p_head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(72, 70).mirror(false).addBox(-2F, -1.5F, -9F, 4F, 3F, 9F), PartPose.offsetAndRotation(0F, -1.5F, -8F, 0.0349066F, 0F, 0F));
		PartDefinition p_beak = p_snout.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(60, 85).mirror(false).addBox(-1.5F, -2.5F, -1.5F, 3F, 5F, 3F), PartPose.offsetAndRotation(0F, 0.8F, -8F, -0.1047198F, 0.7853982F, -0.1047198F));
		PartDefinition p_headplate = p_head.addOrReplaceChild("headplate", CubeListBuilder.create().texOffs(80, 118).mirror(false).addBox(-1F, -1F, -4F, 2F, 2F, 8F), PartPose.offsetAndRotation(0F, -3F, -1F, 0.1745329F, 0F, 0F));
		PartDefinition p_righteyesock = p_head.addOrReplaceChild("righteyesock", CubeListBuilder.create().texOffs(70, 108).mirror(false).addBox(0F, 0F, 0F, 1F, 2F, 4F), PartPose.offset(-3.5F, -2.5F, -8F));
		PartDefinition p_lefteyesock = p_head.addOrReplaceChild("lefteyesock", CubeListBuilder.create().texOffs(70, 114).mirror(false).addBox(0F, 0F, 0F, 1F, 2F, 4F), PartPose.offset(2.5F, -2.5F, -8F));
		PartDefinition p_Jaw = p_head.addOrReplaceChild("Jaw", CubeListBuilder.create().texOffs(72, 82).mirror(false).addBox(-2F, -1F, -9F, 4F, 2F, 9F), PartPose.offsetAndRotation(0F, 2.5F, -7.5F, -0.1745329F, 0F, 0F));
		PartDefinition p_leftupjaw = p_head.addOrReplaceChild("leftupjaw", CubeListBuilder.create().texOffs(42, 93).mirror(false).addBox(-1F, -1F, -6.5F, 2F, 2F, 13F), PartPose.offsetAndRotation(2F, 0F, -10.5F, -0.1745329F, 0.1745329F, 0F));
		PartDefinition p_rightupjaw = p_head.addOrReplaceChild("rightupjaw", CubeListBuilder.create().texOffs(72, 93).mirror(false).addBox(-1F, -1F, -6.5F, 2F, 2F, 13F), PartPose.offsetAndRotation(-2F, 0F, -10.5F, -0.1745329F, -0.1745329F, 0F));
		PartDefinition p_mouthrod = p_head.addOrReplaceChild("mouthrod", CubeListBuilder.create().texOffs(104, 50).mirror(false).addBox(-5F, -1F, -1F, 10F, 2F, 2F), PartPose.offset(0F, 1F, -8F));
		PartDefinition p_controlrope1 = p_mouthrod.addOrReplaceChild("controlrope1", CubeListBuilder.create().texOffs(66, 43).mirror(false).addBox(0F, -2F, 0F, 0F, 4F, 23F), PartPose.offset(4.5F, 1F, 0F));
		PartDefinition p_controlrope2 = p_mouthrod.addOrReplaceChild("controlrope2", CubeListBuilder.create().texOffs(66, 43).mirror(false).addBox(0F, -2F, 0F, 0F, 4F, 23F), PartPose.offset(-4.5F, 1F, 0F));
		PartDefinition p_helmetstrap1 = p_head.addOrReplaceChild("helmetstrap1", CubeListBuilder.create().texOffs(32, 146).mirror(false).addBox(-4F, -2F, 0F, 8F, 4F, 1F), PartPose.offset(0F, 2F, -7.5F));
		PartDefinition p_helmetstrap2 = p_head.addOrReplaceChild("helmetstrap2", CubeListBuilder.create().texOffs(32, 141).mirror(false).addBox(-4F, -2F, 0F, 8F, 4F, 1F), PartPose.offset(0F, 2F, -3.5F));
		PartDefinition p_rightearskin = p_head.addOrReplaceChild("rightearskin", CubeListBuilder.create().texOffs(112, 201).mirror(false).addBox(0F, -4F, 0F, 0F, 8F, 8F), PartPose.offset(-3F, -0.5F, 0F));
		PartDefinition p_rightspine1 = p_rightearskin.addOrReplaceChild("rightspine1", CubeListBuilder.create().texOffs(50, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offsetAndRotation(0F, -2F, 0F, 0.2617994F, 0F, 0F));
		PartDefinition p_rightspine2 = p_rightearskin.addOrReplaceChild("rightspine2", CubeListBuilder.create().texOffs(50, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_rightspine3 = p_rightearskin.addOrReplaceChild("rightspine3", CubeListBuilder.create().texOffs(50, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offsetAndRotation(0F, 2F, 0F, -0.2617994F, 0F, 0F));
		PartDefinition p_leftearskin = p_head.addOrReplaceChild("leftearskin", CubeListBuilder.create().texOffs(96, 201).mirror(false).addBox(0F, -4F, 0F, 0F, 8F, 8F), PartPose.offset(3F, -0.5F, 0F));
		PartDefinition p_leftspine1 = p_leftearskin.addOrReplaceChild("leftspine1", CubeListBuilder.create().texOffs(68, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offsetAndRotation(0F, -2F, 0F, 0.2617994F, 0F, 0F));
		PartDefinition p_leftspine2 = p_leftearskin.addOrReplaceChild("leftspine2", CubeListBuilder.create().texOffs(68, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_leftspine3 = p_leftearskin.addOrReplaceChild("leftspine3", CubeListBuilder.create().texOffs(68, 141).mirror(false).addBox(-0.5F, -1F, 0F, 1F, 2F, 8F), PartPose.offsetAndRotation(0F, 2F, 0F, -0.2617994F, 0F, 0F));
		PartDefinition p_torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-5F, 0F, -12F, 10F, 10F, 12F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_saddle = root.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(38, 70).mirror(false).addBox(-3.5F, -2.5F, -8F, 7F, 3F, 10F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_rightshoulder = root.addOrReplaceChild("rightshoulder", CubeListBuilder.create().texOffs(42, 83).mirror(false).addBox(-6F, 1F, -12.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 0F, 0F, -0.2617994F, 0F, 0F));
		PartDefinition p_leftshoulder = root.addOrReplaceChild("leftshoulder", CubeListBuilder.create().texOffs(24, 83).mirror(false).addBox(2F, 1F, -12.5F, 4F, 5F, 5F), PartPose.offsetAndRotation(0F, 0F, 0F, -0.2617994F, 0F, 0F));
		PartDefinition p_LeftWing = root.addOrReplaceChild("LeftWing", CubeListBuilder.create(), PartPose.offset(4F, 1F, -11F));
		PartDefinition p_leftuparm = p_LeftWing.addOrReplaceChild("leftuparm", CubeListBuilder.create().texOffs(44, 14).mirror(false).addBox(0F, -2F, -2F, 10F, 4F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, -0.1745329F, 0F));
		PartDefinition p_leftlowarm = p_leftuparm.addOrReplaceChild("leftlowarm", CubeListBuilder.create().texOffs(72, 14).mirror(false).addBox(0F, -2F, -2F, 10F, 4F, 4F), PartPose.offsetAndRotation(9F, 0F, 0F, 0F, 0.1745329F, 0F));
		PartDefinition p_leftfing1a = p_leftlowarm.addOrReplaceChild("leftfing1a", CubeListBuilder.create().texOffs(52, 30).mirror(false).addBox(0F, 0F, -1F, 2F, 15F, 2F), PartPose.offsetAndRotation(9F, 1F, 0F, 1.5707963F, 1.2217305F, 0F));
		PartDefinition p_leftfing1b = p_leftfing1a.addOrReplaceChild("leftfing1b", CubeListBuilder.create().texOffs(52, 47).mirror(false).addBox(0F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0.6108652F));
		PartDefinition p_leftwingflap1 = p_leftfing1a.addOrReplaceChild("leftwingflap1", CubeListBuilder.create().texOffs(74, 153).mirror(false).addBox(3.5F, -3F, 0.95F, 14F, 24F, 0F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 1.2217305F));
		PartDefinition p_leftfing2a = p_leftlowarm.addOrReplaceChild("leftfing2a", CubeListBuilder.create().texOffs(44, 30).mirror(false).addBox(-1F, 0F, 0F, 2F, 15F, 2F), PartPose.offsetAndRotation(9F, 1F, 0F, 1.5707963F, 0.6108652F, 0F));
		PartDefinition p_leftfing2b = p_leftfing2a.addOrReplaceChild("leftfing2b", CubeListBuilder.create().texOffs(44, 47).mirror(false).addBox(-1F, 0F, 0F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0.5235988F));
		PartDefinition p_leftwingflap2 = p_leftfing2a.addOrReplaceChild("leftwingflap2", CubeListBuilder.create().texOffs(36, 153).mirror(false).addBox(-7F, 1.05F, 1.05F, 19F, 24F, 0F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0.6981317F));
		PartDefinition p_leftfing3a = p_leftlowarm.addOrReplaceChild("leftfing3a", CubeListBuilder.create().texOffs(36, 30).mirror(false).addBox(-1F, 0F, 1F, 2F, 15F, 2F), PartPose.offsetAndRotation(9F, 1F, 0F, 1.5707963F, -0.0872665F, 0F));
		PartDefinition p_leftfing3b = p_leftfing3a.addOrReplaceChild("leftfing3b", CubeListBuilder.create().texOffs(36, 47).mirror(false).addBox(-1F, 0F, 1F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, 0.5235988F));
		PartDefinition p_leftwingflap3 = p_leftfing3a.addOrReplaceChild("leftwingflap3", CubeListBuilder.create().texOffs(0, 153).mirror(false).addBox(-17.5F, 1F, 1.1F, 18F, 24F, 0F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_RightWing = root.addOrReplaceChild("RightWing", CubeListBuilder.create(), PartPose.offset(-4F, 1F, -11F));
		PartDefinition p_rightuparm = p_RightWing.addOrReplaceChild("rightuparm", CubeListBuilder.create().texOffs(44, 22).mirror(false).addBox(-10F, -2F, -2F, 10F, 4F, 4F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0.1745329F, 0F));
		PartDefinition p_rightlowarm = p_rightuparm.addOrReplaceChild("rightlowarm", CubeListBuilder.create().texOffs(72, 22).mirror(false).addBox(-10F, -2F, -2F, 10F, 4F, 4F), PartPose.offsetAndRotation(-9F, 0F, 0F, 0F, -0.1745329F, 0F));
		PartDefinition p_rightfing1a = p_rightlowarm.addOrReplaceChild("rightfing1a", CubeListBuilder.create().texOffs(36, 30).mirror(false).addBox(-1F, 0F, -1F, 2F, 15F, 2F), PartPose.offsetAndRotation(-9F, 1F, -1F, 1.5707963F, -1.2217305F, 0F));
		PartDefinition p_rightfing1b = p_rightfing1a.addOrReplaceChild("rightfing1b", CubeListBuilder.create().texOffs(36, 47).mirror(false).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, -0.6108652F));
		PartDefinition p_rightwingflap1 = p_rightfing1a.addOrReplaceChild("rightwingflap1", CubeListBuilder.create().texOffs(74, 177).mirror(false).addBox(-17.5F, -3F, 0.95F, 14F, 24F, 0F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, -1.2217305F));
		PartDefinition p_rightfing2a = p_rightlowarm.addOrReplaceChild("rightfing2a", CubeListBuilder.create().texOffs(44, 30).mirror(false).addBox(-1F, 0F, 0F, 2F, 15F, 2F), PartPose.offsetAndRotation(-9F, 1F, 0F, 1.5707963F, -0.6108652F, 0F));
		PartDefinition p_rightfing2b = p_rightfing2a.addOrReplaceChild("rightfing2b", CubeListBuilder.create().texOffs(44, 47).mirror(false).addBox(-1F, 0F, 0F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, -0.5235988F));
		PartDefinition p_rightwingflap2 = p_rightfing2a.addOrReplaceChild("rightwingflap2", CubeListBuilder.create().texOffs(36, 177).mirror(false).addBox(-19F, 1.05F, 1.05F, 19F, 24F, 0F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, -0.6981317F));
		PartDefinition p_rightfing3a = p_rightlowarm.addOrReplaceChild("rightfing3a", CubeListBuilder.create().texOffs(52, 30).mirror(false).addBox(-1F, 0F, 1F, 2F, 15F, 2F), PartPose.offsetAndRotation(-9F, 1F, 0F, 1.5707963F, 0.0872665F, 0F));
		PartDefinition p_rightfing3b = p_rightfing3a.addOrReplaceChild("rightfing3b", CubeListBuilder.create().texOffs(52, 47).mirror(false).addBox(-1F, 0F, 1F, 2F, 10F, 2F), PartPose.offsetAndRotation(0F, 14F, 0F, 0F, 0F, -0.5235988F));
		PartDefinition p_rightwingflap3 = p_rightfing3a.addOrReplaceChild("rightwingflap3", CubeListBuilder.create().texOffs(0, 177).mirror(false).addBox(-0.5F, 1F, 1.1F, 18F, 24F, 0F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_leftupleg = root.addOrReplaceChild("leftupleg", CubeListBuilder.create().texOffs(0, 111).mirror(false).addBox(-2F, -3F, -3F, 4F, 10F, 7F), PartPose.offsetAndRotation(5F, 6F, -5F, -0.4363323F, 0F, 0F));
		PartDefinition p_leftmidleg = p_leftupleg.addOrReplaceChild("leftmidleg", CubeListBuilder.create().texOffs(0, 102).mirror(false).addBox(-1.5F, -2F, 0F, 3F, 4F, 5F), PartPose.offset(0F, 5F, 4F));
		PartDefinition p_leftlowleg = p_leftmidleg.addOrReplaceChild("leftlowleg", CubeListBuilder.create().texOffs(0, 91).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F), PartPose.offset(0F, 2F, 3.5F));
		PartDefinition p_leftfoot = p_leftlowleg.addOrReplaceChild("leftfoot", CubeListBuilder.create().texOffs(44, 121).mirror(false).addBox(-2F, -1F, -3F, 4F, 3F, 4F), PartPose.offsetAndRotation(0F, 7F, 0.5F, 0.4363323F, 0F, 0F));
		PartDefinition p_lefttoe1 = p_leftfoot.addOrReplaceChild("lefttoe1", CubeListBuilder.create().texOffs(96, 35).mirror(false).addBox(-0.5F, -1F, -3F, 1F, 2F, 3F), PartPose.offset(-1.5F, 1F, -3F));
		PartDefinition p_leftclaw1 = p_lefttoe1.addOrReplaceChild("leftclaw1", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0.5F, -0.5F, -2.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_lefttoe3 = p_leftfoot.addOrReplaceChild("lefttoe3", CubeListBuilder.create().texOffs(96, 30).mirror(false).addBox(-0.5F, -1F, -3F, 1F, 2F, 3F), PartPose.offset(1.5F, 1F, -3F));
		PartDefinition p_leftclaw3 = p_lefttoe3.addOrReplaceChild("leftclaw3", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-0.5F, -0.5F, -2.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_lefttoe2 = p_leftfoot.addOrReplaceChild("lefttoe2", CubeListBuilder.create().texOffs(84, 30).mirror(false).addBox(-1F, -1.5F, -4F, 2F, 3F, 4F), PartPose.offset(0F, 0.5F, -3F));
		PartDefinition p_leftclaw2 = p_lefttoe2.addOrReplaceChild("leftclaw2", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, -1F, -3.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_rightupleg = root.addOrReplaceChild("rightupleg", CubeListBuilder.create().texOffs(0, 111).mirror(false).addBox(-2F, -3F, -3F, 4F, 10F, 7F), PartPose.offsetAndRotation(-5F, 6F, -5F, -0.4363323F, 0F, 0F));
		PartDefinition p_rightmidleg = p_rightupleg.addOrReplaceChild("rightmidleg", CubeListBuilder.create().texOffs(0, 102).mirror(false).addBox(-1.5F, -2F, 0F, 3F, 4F, 5F), PartPose.offset(0F, 5F, 4F));
		PartDefinition p_rightlowleg = p_rightmidleg.addOrReplaceChild("rightlowleg", CubeListBuilder.create().texOffs(0, 91).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F), PartPose.offset(0F, 2F, 3.5F));
		PartDefinition p_rightfoot = p_rightlowleg.addOrReplaceChild("rightfoot", CubeListBuilder.create().texOffs(44, 121).mirror(false).addBox(-2F, -1F, -3F, 4F, 3F, 4F), PartPose.offsetAndRotation(0F, 7F, 0.5F, 0.4363323F, 0F, 0F));
		PartDefinition p_righttoe1 = p_rightfoot.addOrReplaceChild("righttoe1", CubeListBuilder.create().texOffs(96, 35).mirror(false).addBox(-0.5F, -1F, -3F, 1F, 2F, 3F), PartPose.offset(-1.5F, 1F, -3F));
		PartDefinition p_rightclaw1 = p_righttoe1.addOrReplaceChild("rightclaw1", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0.5F, -0.5F, -2.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_righttoe3 = p_rightfoot.addOrReplaceChild("righttoe3", CubeListBuilder.create().texOffs(96, 30).mirror(false).addBox(-0.5F, -1F, -3F, 1F, 2F, 3F), PartPose.offset(1.5F, 1F, -3F));
		PartDefinition p_rightclaw3 = p_righttoe3.addOrReplaceChild("rightclaw3", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-0.5F, -0.5F, -2.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_righttoe2 = p_rightfoot.addOrReplaceChild("righttoe2", CubeListBuilder.create().texOffs(84, 30).mirror(false).addBox(-1F, -1.5F, -4F, 2F, 3F, 4F), PartPose.offset(0F, 0.5F, -3F));
		PartDefinition p_rightclaw2 = p_righttoe2.addOrReplaceChild("rightclaw2", CubeListBuilder.create().texOffs(100, 26).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, -1F, -3.5F, -0.4363323F, 0F, 0F));
		PartDefinition p_chestbelt = root.addOrReplaceChild("chestbelt", CubeListBuilder.create().texOffs(0, 201).mirror(false).addBox(-5.5F, -0.5F, -9F, 11F, 11F, 2F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_stomachbelt = root.addOrReplaceChild("stomachbelt", CubeListBuilder.create().texOffs(0, 201).mirror(false).addBox(-5.5F, -0.5F, -3F, 11F, 11F, 2F), PartPose.offset(0F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 256);
	}

	@Override
	public void setupAnim(WindDragonRenderer.State state) {
		super.setupAnim(state);
		this.isRidden = state.ridden;
		this.isSaddled = state.saddled;
		this.onAir = state.fly > 0.5F;
		this.flapwings = this.onAir && state.glide < 0.5F;
		this.diving = this.onAir && state.pitch > 0.35F;
		this.isSitting = state.sit > 0.5F;
		this.flapPhase = state.flap;
		this.openMouth = state.breath > 0.1F ? 1 + (int)(state.breath * 28.0F) : 0;
		this.saddle.visible = this.chestbelt.visible = this.stomachbelt.visible = this.isSaddled;
		this.mouthrod.visible = this.helmetstrap1.visible = this.helmetstrap2.visible = this.isSaddled;
		this.controlrope1.visible = this.controlrope2.visible = this.isSaddled && this.isRidden;
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {
//, boolean onAir, boolean flapwings, boolean rider, int openMouth, boolean diving, boolean sitting) {
        float RLegXRot = Mth.cos((f * 0.6662F) + 3.141593F) * 0.8F * f1;
        float LLegXRot = Mth.cos(f * 0.6662F) * 0.8F * f1;

        f3 = Mth.wrapDegrees(f3);

        float f10 = 60F;
        if (f3 > f10) {
            f3 = f10;
        }
        if (f3 < -f10) {
            f3 = -f10;
        }
        this.neck2.xRot = -66F / RAD + (f4 * 1 / 3F / RAD);
        this.neck1.xRot = 30F / RAD + (f4 * 2 / 3F / RAD);

        this.head.xRot = 45F / RAD;

        this.neck2.yRot = (f3 * 2 / 3F) / RAD;
        this.neck1.yRot = (f3 * 1 / 3F) / RAD;

        this.head.yRot = 0F;
        this.head.zRot = 0F;

        if (isRidden) {
            //straight neck when mounted
            this.neck1.yRot = 0F;
            this.neck2.yRot = 0F;

            if (onAir) {
                this.neck1.xRot = 0F;
                this.neck2.xRot = 0F;

            } else {

                this.neck2.xRot = -66F / RAD + (RLegXRot * 1 / 60F);
                this.neck1.xRot = 30F / RAD + (RLegXRot * 2 / 60F);
            }
        }
        float TailXRot = Mth.cos(f * 0.4F) * 0.2F * f1;
        this.tail1.xRot = (-19F / RAD);// - TailXRot;
        this.tail2.xRot = (-16F / RAD);// + TailXRot;
        this.tail3.xRot = (7F / RAD);// + TailXRot;
        this.tail4.xRot = (11F / RAD);// + TailXRot;
        this.tail5.xRot = (8F / RAD);

        float t = f / 2;

        /*
         * if (movetail) { t = f2/4F; }
         */
        float A = 0.15F;//0.8F;
        float w = 0.9F;
        float k = 0.6F;

        int i = 0;
        float tailLat = 0F;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.tail1.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.tail2.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.tail3.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.tail4.yRot = tailLat;
        tailLat = A * Mth.sin(w * t - k * i++);
        this.tail5.yRot = tailLat;

        float WingSpread = Mth.cos(f * 0.3F) * 0.9F * f1;

        /**
         * flapping wings or cruising. IF flapping wings, move up and down. if
         * cruising, movement depends of speed
         */
        //float WingRot = 0F;
        if (flapwings && !isGhost) {
            WingSpread = Mth.cos(this.flapPhase + 3.141593F) * 1.2F;// * f1;
        } else
        //cruising
        {
            //WingRot = Mth.cos((f * 0.6662F) + 3.141593F) * 1.2F * f1;
            WingSpread = Mth.cos((f * 0.5F)) * 0.1F;//* 1.2F * f1;
        }

        if (onAir || isGhost) {
            //rightfing1a.rotateAngleY = -70F/RAD+ WingSpread;

            float speedMov = (f1 * 0.5F);
            if (isGhost) {
                speedMov = 0.5F;
            }

            this.leftuparm.zRot = WingSpread * 2 / 3F;
            this.rightuparm.zRot = -WingSpread * 2 / 3F;
            this.leftlowarm.zRot = WingSpread * 0.1F;
            this.leftfing1a.zRot = WingSpread * 1F;
            this.leftfing2a.zRot = WingSpread * 0.8F;

            this.rightlowarm.zRot = -WingSpread * 0.1F;
            this.rightfing1a.zRot = -WingSpread * 1F;
            this.rightfing2a.zRot = -WingSpread * 0.8F;

            //System.out.println("WingSpread = " + WingSpread);
            this.leftuparm.yRot = -10F / RAD - (WingSpread / 2.5F);
            this.leftlowarm.yRot = 15F / RAD + (WingSpread / 2.5F);
            this.leftfing1a.yRot = 70F / RAD;
            this.leftfing2a.yRot = 35F / RAD;
            this.leftfing3a.yRot = -5F / RAD;
            this.rightuparm.yRot = 10F / RAD + (WingSpread / 2.5F);
            this.rightlowarm.yRot = -15F / RAD - (WingSpread / 2.5F);
            this.rightfing1a.yRot = -70F / RAD;
            this.rightfing2a.yRot = -35F / RAD;
            this.rightfing3a.yRot = 5F / RAD;

            this.leftupleg.xRot = speedMov;
            this.leftmidleg.xRot = speedMov;
            this.leftfoot.xRot = 25F / RAD;
            this.lefttoe1.xRot = speedMov;
            this.lefttoe2.xRot = speedMov;
            this.lefttoe3.xRot = speedMov;
            this.rightfoot.xRot = 25F / RAD;
            this.rightupleg.xRot = speedMov;
            this.rightmidleg.xRot = speedMov;
            this.righttoe1.xRot = speedMov;
            this.righttoe2.xRot = speedMov;
            this.righttoe3.xRot = speedMov;

        } else {

            this.leftlowarm.zRot = 0F;
            this.leftfing1a.zRot = 0F;
            this.leftfing2a.zRot = 0F;
            this.rightlowarm.zRot = 0F;
            this.rightfing1a.zRot = 0F;
            this.rightfing2a.zRot = 0F;
            //leftuparm.rotateAngleX = RLegXRot;
            //rightuparm.rotateAngleX = LLegXRot;

            this.leftuparm.zRot = 30F / RAD;
            this.leftuparm.yRot = -60F / RAD + (LLegXRot / 5F);

            this.leftlowarm.yRot = 105F / RAD;
            this.leftfing1a.yRot = -20F / RAD;
            this.leftfing2a.yRot = -26F / RAD;
            this.leftfing3a.yRot = -32F / RAD;

            this.rightuparm.yRot = 60F / RAD - (RLegXRot / 5F);
            this.rightuparm.zRot = -30F / RAD;

            this.rightlowarm.yRot = -105F / RAD;
            this.rightfing1a.yRot = 16F / RAD;
            this.rightfing2a.yRot = 26F / RAD;
            this.rightfing3a.yRot = 32F / RAD;

            //test mouth opening

            this.leftupleg.xRot = -25F / RAD + LLegXRot;
            this.rightupleg.xRot = -25F / RAD + RLegXRot;

            this.leftmidleg.xRot = 0F;
            this.leftlowleg.xRot = 0F;
            this.leftfoot.xRot = 25F / RAD - (LLegXRot);
            this.lefttoe1.xRot = LLegXRot;
            this.lefttoe2.xRot = LLegXRot;
            this.lefttoe3.xRot = LLegXRot;

            this.rightmidleg.xRot = 0F;
            this.rightlowleg.xRot = 0F;
            this.rightfoot.xRot = 25F / RAD - (RLegXRot);
            this.righttoe1.xRot = RLegXRot;
            this.righttoe2.xRot = RLegXRot;
            this.righttoe3.xRot = RLegXRot;

        }

        if (isSitting) {
            this.leftupleg.xRot = 45F / RAD + LLegXRot;
            this.rightupleg.xRot = 45F / RAD + RLegXRot;
            this.leftmidleg.xRot = 30F;
            this.rightmidleg.xRot = 30F;
            this.neck2.xRot = -36F / RAD + (f4 * 1 / 3F / RAD);
            this.neck1.xRot = 30F / RAD + (f4 * 2 / 3F / RAD);
        }
        if (diving) {
            this.leftuparm.zRot = -40F / RAD;
            this.rightuparm.zRot = 40F / RAD;
            this.leftlowarm.zRot = 0F;
            this.leftfing1a.zRot = 0F;
            this.leftfing2a.zRot = 0F;

            this.rightlowarm.zRot = 0F;
            this.rightfing1a.zRot = 0F;
            this.rightfing2a.zRot = 0F;

            //leftuparm.rotateAngleZ = 30F/RAD ;
            this.leftuparm.yRot = -50F / RAD;// + (LLegXRot /5F);
            this.leftlowarm.yRot = 30F / RAD;
            this.leftfing1a.yRot = 50F / RAD;
            this.leftfing2a.yRot = 30F / RAD;
            this.leftfing3a.yRot = 10F / RAD;

            this.rightuparm.yRot = 50F / RAD;// - (RLegXRot /5F);
            //rightuparm.rotateAngleZ = -30F/RAD;

            this.rightlowarm.yRot = -30F / RAD;
            this.rightfing1a.yRot = -50F / RAD;
            this.rightfing2a.yRot = -30F / RAD;
            this.rightfing3a.yRot = -10F / RAD;
        }

        if (openMouth != 0) {
            float mouthMov = (Mth.cos((openMouth - 15) * 0.11F) * 0.8F);
            this.Jaw.xRot = (-10F / RAD) + mouthMov;
            this.leftearskin.yRot = +mouthMov;
            this.rightearskin.yRot = -mouthMov;

        } else {
            this.Jaw.xRot = -10F / RAD;
            this.leftearskin.yRot = 0F;
            this.rightearskin.yRot = 0F;
        }

    }


}
