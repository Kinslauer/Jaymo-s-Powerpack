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
 * (0.5.56) The ogres, from {@code MoCModelOgre} of
 * DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3: its boxes as they were, and its
 * animation as it was: the stomping legs and swaying loincloth, fists (or the club, for a two-headed
 * one) brought down on a blow, and the heads following the look. One head, or two and a club.
 */
final class OgreModel extends EntityModel<LegendState> {
	private static final float DEG = 57.29578F;

	private final ModelPart head, brow, noseBridge, nose, rgtTusk, rgtTooth, lftTooth, lftTusk, lip, rgtEar, rgtRing, rgtRingHole, lftEar, lftRing, lftRingHole, hairRope, hair1, hair2, hair3, diamondHorn, rgtHorn, rgtHornTip, lftHorn, lftHornTip, neckRest, chest, stomach, buttCover, loinCloth, rgtThigh, rgtLeg, rgtKnee, rgtToes, rgtBigToe, lftThigh, lftLeg, lftKnee, lftToes, lftBigToe, lftShoulder, lftArm, lftHand, lftElbow, lftWeaponRoot, lftWeaponEnd, lftWeaponLump, lftWeaponBetween, lftWeaponTip, lftHammerNeck, lftHammerHeadSupport, lftHammerHead, lftSpike, lftSpike1, lftSpike2, lftSpike3, lftSpike4, rgtShoulder, rgtArm, rgtHand, rgtElbow, rgtWeaponRoot, rgtWeaponEnd, rgtWeaponLump, rgtWeaponBetween, rgtWeaponTip, rgtHammerNeck, rgtHammerHeadSupport, rgtHammerHead, rgtSpike, rgtSpike1, rgtSpike2, rgtSpike3, rgtSpike4, head3RgtEar, head3LftEar, head3Eyelid, head3Nose, head3, head3Brow, head3Hair, head3Lip, head3RgtTusk, head3RgtTooth, head3LftTooth, head3LftTusk, head3RingHole, head3Ring, head2Chin, head2, head2Lip, head2LftTusk, head2RgtTusk, head2Nose, head2NoseBridge, head2Brow, head2RgtHorn, head2LftHorn, head2DiamondHorn;

	OgreModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.brow = root.getChild("brow");
		this.noseBridge = root.getChild("nose_bridge");
		this.nose = root.getChild("nose");
		this.rgtTusk = root.getChild("rgt_tusk");
		this.rgtTooth = root.getChild("rgt_tooth");
		this.lftTooth = root.getChild("lft_tooth");
		this.lftTusk = root.getChild("lft_tusk");
		this.lip = root.getChild("lip");
		this.rgtEar = root.getChild("rgt_ear");
		this.rgtRing = root.getChild("rgt_ring");
		this.rgtRingHole = root.getChild("rgt_ring_hole");
		this.lftEar = root.getChild("lft_ear");
		this.lftRing = root.getChild("lft_ring");
		this.lftRingHole = root.getChild("lft_ring_hole");
		this.hairRope = root.getChild("hair_rope");
		this.hair1 = root.getChild("hair1");
		this.hair2 = root.getChild("hair2");
		this.hair3 = root.getChild("hair3");
		this.diamondHorn = root.getChild("diamond_horn");
		this.rgtHorn = root.getChild("rgt_horn");
		this.rgtHornTip = root.getChild("rgt_horn_tip");
		this.lftHorn = root.getChild("lft_horn");
		this.lftHornTip = root.getChild("lft_horn_tip");
		this.neckRest = root.getChild("neck_rest");
		this.chest = root.getChild("chest");
		this.stomach = root.getChild("stomach");
		this.buttCover = root.getChild("butt_cover");
		this.loinCloth = root.getChild("loin_cloth");
		this.rgtThigh = root.getChild("rgt_thigh");
		this.rgtLeg = root.getChild("rgt_thigh").getChild("rgt_leg");
		this.rgtKnee = root.getChild("rgt_thigh").getChild("rgt_leg").getChild("rgt_knee");
		this.rgtToes = root.getChild("rgt_thigh").getChild("rgt_leg").getChild("rgt_toes");
		this.rgtBigToe = root.getChild("rgt_thigh").getChild("rgt_leg").getChild("rgt_big_toe");
		this.lftThigh = root.getChild("lft_thigh");
		this.lftLeg = root.getChild("lft_thigh").getChild("lft_leg");
		this.lftKnee = root.getChild("lft_thigh").getChild("lft_leg").getChild("lft_knee");
		this.lftToes = root.getChild("lft_thigh").getChild("lft_leg").getChild("lft_toes");
		this.lftBigToe = root.getChild("lft_thigh").getChild("lft_leg").getChild("lft_big_toe");
		this.lftShoulder = root.getChild("lft_shoulder");
		this.lftArm = root.getChild("lft_shoulder").getChild("lft_arm");
		this.lftHand = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand");
		this.lftElbow = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_elbow");
		this.lftWeaponRoot = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root");
		this.lftWeaponEnd = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_end");
		this.lftWeaponLump = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump");
		this.lftWeaponBetween = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between");
		this.lftWeaponTip = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip");
		this.lftHammerNeck = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_hammer_neck");
		this.lftHammerHeadSupport = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_hammer_head_support");
		this.lftHammerHead = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_hammer_head_support").getChild("lft_hammer_head");
		this.lftSpike = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_spike");
		this.lftSpike1 = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_spike1");
		this.lftSpike2 = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_spike2");
		this.lftSpike3 = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_spike3");
		this.lftSpike4 = root.getChild("lft_shoulder").getChild("lft_arm").getChild("lft_hand").getChild("lft_weapon_root").getChild("lft_weapon_lump").getChild("lft_weapon_between").getChild("lft_weapon_tip").getChild("lft_spike4");
		this.rgtShoulder = root.getChild("rgt_shoulder");
		this.rgtArm = root.getChild("rgt_shoulder").getChild("rgt_arm");
		this.rgtHand = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand");
		this.rgtElbow = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_elbow");
		this.rgtWeaponRoot = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root");
		this.rgtWeaponEnd = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_end");
		this.rgtWeaponLump = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump");
		this.rgtWeaponBetween = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between");
		this.rgtWeaponTip = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip");
		this.rgtHammerNeck = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_hammer_neck");
		this.rgtHammerHeadSupport = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_hammer_head_support");
		this.rgtHammerHead = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_hammer_head_support").getChild("rgt_hammer_head");
		this.rgtSpike = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_spike");
		this.rgtSpike1 = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_spike1");
		this.rgtSpike2 = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_spike2");
		this.rgtSpike3 = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_spike3");
		this.rgtSpike4 = root.getChild("rgt_shoulder").getChild("rgt_arm").getChild("rgt_hand").getChild("rgt_weapon_root").getChild("rgt_weapon_lump").getChild("rgt_weapon_between").getChild("rgt_weapon_tip").getChild("rgt_spike4");
		this.head3RgtEar = root.getChild("head3_rgt_ear");
		this.head3LftEar = root.getChild("head3_lft_ear");
		this.head3Eyelid = root.getChild("head3_eyelid");
		this.head3Nose = root.getChild("head3_nose");
		this.head3 = root.getChild("head3");
		this.head3Brow = root.getChild("head3_brow");
		this.head3Hair = root.getChild("head3_hair");
		this.head3Lip = root.getChild("head3_lip");
		this.head3RgtTusk = root.getChild("head3_rgt_tusk");
		this.head3RgtTooth = root.getChild("head3_rgt_tooth");
		this.head3LftTooth = root.getChild("head3_lft_tooth");
		this.head3LftTusk = root.getChild("head3_lft_tusk");
		this.head3RingHole = root.getChild("head3_ring_hole");
		this.head3Ring = root.getChild("head3_ring");
		this.head2Chin = root.getChild("head2_chin");
		this.head2 = root.getChild("head2");
		this.head2Lip = root.getChild("head2_lip");
		this.head2LftTusk = root.getChild("head2_lft_tusk");
		this.head2RgtTusk = root.getChild("head2_rgt_tusk");
		this.head2Nose = root.getChild("head2_nose");
		this.head2NoseBridge = root.getChild("head2_nose_bridge");
		this.head2Brow = root.getChild("head2_brow");
		this.head2RgtHorn = root.getChild("head2_rgt_horn");
		this.head2LftHorn = root.getChild("head2_lft_horn");
		this.head2DiamondHorn = root.getChild("head2_diamond_horn");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(80, 0).addBox(-6, -12, -6, 12, 12, 12), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(68, 7).addBox(-5, -10.5F, -8, 10, 3, 2), PartPose.offsetAndRotation(0, -13, 0, -0.0872665F, 0, 0));
		root.addOrReplaceChild("nose_bridge", CubeListBuilder.create().texOffs(80, 4).addBox(-1, -7, -8, 2, 2, 1), PartPose.offsetAndRotation(0, -13, 0, -0.1745329F, 0, 0));
		root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(80, 0).addBox(-2, -7, -7, 4, 2, 2), PartPose.offsetAndRotation(0, -13, 0, 0.0872665F, 0, 0));
		root.addOrReplaceChild("rgt_tusk", CubeListBuilder.create().texOffs(60, 4).addBox(-3.5F, -6, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(0, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("rgt_tooth", CubeListBuilder.create().texOffs(64, 4).addBox(-1.5F, -5, -6.5F, 1, 1, 1), PartPose.offsetAndRotation(0, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("lft_tooth", CubeListBuilder.create().texOffs(72, 4).addBox(0.5F, -5, -6.5F, 1, 1, 1), PartPose.offsetAndRotation(0, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("lft_tusk", CubeListBuilder.create().texOffs(76, 4).addBox(2.5F, -6, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(0, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("lip", CubeListBuilder.create().texOffs(60, 0).addBox(-4, -4, -7, 8, 2, 2), PartPose.offsetAndRotation(0, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("rgt_ear", CubeListBuilder.create().texOffs(60, 12).addBox(-9, -9, -1, 3, 5, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("rgt_ring", CubeListBuilder.create().texOffs(32, 58).addBox(-8, -6, -2, 1, 4, 4), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("rgt_ring_hole", CubeListBuilder.create().texOffs(26, 50).addBox(-8, -5, -1, 1, 2, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("lft_ear", CubeListBuilder.create().texOffs(70, 12).addBox(6, -9, -1, 3, 5, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("lft_ring", CubeListBuilder.create().texOffs(32, 58).addBox(7, -6, -2, 1, 4, 4), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("lft_ring_hole", CubeListBuilder.create().texOffs(26, 50).addBox(7, -5, -1, 1, 2, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("hair_rope", CubeListBuilder.create().texOffs(82, 83).addBox(-2, -8, 9, 4, 4, 4), PartPose.offsetAndRotation(0, -13, 0, 0.6108652F, 0, 0));
		root.addOrReplaceChild("hair1", CubeListBuilder.create().texOffs(78, 107).addBox(-3, -9, 13, 6, 8, 3), PartPose.offsetAndRotation(0, -13, 0, 0.6108652F, 0, 0));
		root.addOrReplaceChild("hair2", CubeListBuilder.create().texOffs(60, 107).addBox(-3, -6.5F, 11.6F, 6, 8, 3), PartPose.offsetAndRotation(0, -13, 0, 0.2617994F, 0, 0));
		root.addOrReplaceChild("hair3", CubeListBuilder.create().texOffs(42, 107).addBox(-3, -2.4F, 11.4F, 6, 8, 3), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("diamond_horn", CubeListBuilder.create().texOffs(120, 31).addBox(-1, -17, -6, 2, 6, 2), PartPose.offsetAndRotation(0, -13, 0, 0.0872665F, 0, 0));
		root.addOrReplaceChild("rgt_horn", CubeListBuilder.create().texOffs(46, 6).addBox(-6, -12, -11, 2, 2, 5), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("rgt_horn_tip", CubeListBuilder.create().texOffs(44, 13).addBox(-6, -15, -11, 2, 3, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("lft_horn", CubeListBuilder.create().texOffs(46, 6).addBox(4, -12, -11, 2, 2, 5), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("lft_horn_tip", CubeListBuilder.create().texOffs(52, 13).addBox(4, -15, -11, 2, 3, 2), PartPose.offset(0, -13, 0));
		root.addOrReplaceChild("neck_rest", CubeListBuilder.create().texOffs(39, 20).addBox(-7, -19, -3, 14, 3, 11), PartPose.offset(0, 5, 0));
		root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(32, 34).addBox(-9.5F, -17.8F, -7.3F, 19, 11, 13), PartPose.offsetAndRotation(0, 5, 0, -0.1745329F, 0, 0));
		root.addOrReplaceChild("stomach", CubeListBuilder.create().texOffs(28, 58).addBox(-11, -8, -6, 22, 11, 14), PartPose.offset(0, 5, 0));
		root.addOrReplaceChild("butt_cover", CubeListBuilder.create().texOffs(32, 118).addBox(-4, 0, 0, 8, 8, 2), PartPose.offset(0, 8, 6));
		root.addOrReplaceChild("loin_cloth", CubeListBuilder.create().texOffs(32, 118).addBox(-4, 0, -2, 8, 8, 2), PartPose.offset(0, 8, -4));
		PartDefinition rgt_thigh = root.addOrReplaceChild("rgt_thigh", CubeListBuilder.create().texOffs(0, 83).addBox(-10, 0, -5, 10, 11, 10), PartPose.offset(-2, 4, 1));
		PartDefinition rgt_leg = rgt_thigh.addOrReplaceChild("rgt_leg", CubeListBuilder.create().texOffs(0, 104).addBox(-4, -1, -4, 8, 11, 8), PartPose.offset(-5, 10, 0));
		rgt_leg.addOrReplaceChild("rgt_knee", CubeListBuilder.create().texOffs(0, 88).addBox(-2, -2, -0.5F, 4, 4, 1), PartPose.offset(0, 2, -4.25F));
		rgt_leg.addOrReplaceChild("rgt_toes", CubeListBuilder.create().texOffs(0, 123).addBox(-2.5F, -1, -3, 5, 2, 3), PartPose.offset(-1.5F, 9, -3.5F));
		rgt_leg.addOrReplaceChild("rgt_big_toe", CubeListBuilder.create().texOffs(20, 123).addBox(-1.5F, -1, -3, 3, 2, 3), PartPose.offset(2.5F, 9, -4));
		PartDefinition lft_thigh = root.addOrReplaceChild("lft_thigh", CubeListBuilder.create().texOffs(88, 83).addBox(0, 0, -5, 10, 11, 10), PartPose.offset(2, 4, 1));
		PartDefinition lft_leg = lft_thigh.addOrReplaceChild("lft_leg", CubeListBuilder.create().texOffs(96, 104).addBox(-4, -1, -4, 8, 11, 8), PartPose.offset(5, 10, 0));
		lft_leg.addOrReplaceChild("lft_knee", CubeListBuilder.create().texOffs(118, 88).addBox(-2, -2, -0.5F, 4, 4, 1), PartPose.offset(0, 2, -4.25F));
		lft_leg.addOrReplaceChild("lft_toes", CubeListBuilder.create().texOffs(112, 123).addBox(-2.5F, -1, -3, 5, 2, 3), PartPose.offset(1.5F, 9, -3.5F));
		lft_leg.addOrReplaceChild("lft_big_toe", CubeListBuilder.create().texOffs(96, 123).addBox(-1.5F, -1, -3, 3, 2, 3), PartPose.offset(-2.5F, 9, -4));
		PartDefinition lft_shoulder = root.addOrReplaceChild("lft_shoulder", CubeListBuilder.create().texOffs(96, 31).addBox(0, -3, -4, 8, 7, 8), PartPose.offset(7, -10, 2));
		PartDefinition lft_arm = lft_shoulder.addOrReplaceChild("lft_arm", CubeListBuilder.create().texOffs(100, 66).addBox(0, 0, -4, 6, 9, 8), PartPose.offset(6, -1, 1));
		PartDefinition lft_hand = lft_arm.addOrReplaceChild("lft_hand", CubeListBuilder.create().texOffs(96, 46).addBox(-4, 0, -4, 8, 12, 8), PartPose.offset(3, 8, -1));
		lft_hand.addOrReplaceChild("lft_elbow", CubeListBuilder.create().texOffs(86, 64).addBox(-2, -1.5F, -0.5F, 4, 3, 1), PartPose.offset(0, 2.5F, 4));
		PartDefinition lft_weapon_root = lft_hand.addOrReplaceChild("lft_weapon_root", CubeListBuilder.create().texOffs(24, 104).addBox(-1.5F, -1.5F, -4, 3, 3, 4), PartPose.offset(-0.5F, 8.5F, -4));
		lft_weapon_root.addOrReplaceChild("lft_weapon_end", CubeListBuilder.create().texOffs(74, 90).addBox(-1.5F, -1.5F, 0, 3, 3, 2), PartPose.offset(0, 0, 8));
		PartDefinition lft_weapon_lump = lft_weapon_root.addOrReplaceChild("lft_weapon_lump", CubeListBuilder.create().texOffs(30, 83).addBox(-2.5F, -2.5F, -4, 5, 5, 4), PartPose.offset(0, 0, -4));
		PartDefinition lft_weapon_between = lft_weapon_lump.addOrReplaceChild("lft_weapon_between", CubeListBuilder.create().texOffs(83, 42).addBox(-1.5F, -1.5F, -2, 3, 3, 2), PartPose.offset(0, 0, -4));
		PartDefinition lft_weapon_tip = lft_weapon_between.addOrReplaceChild("lft_weapon_tip", CubeListBuilder.create().texOffs(60, 118).addBox(-2.5F, -2.5F, -5, 5, 5, 5), PartPose.offset(0, 0, -2));
		lft_weapon_tip.addOrReplaceChild("lft_hammer_neck", CubeListBuilder.create().texOffs(32, 39).addBox(-0.5F, -4, -4, 1, 4, 4), PartPose.offset(0, -2.5F, -1));
		PartDefinition lft_hammer_head_support = lft_weapon_tip.addOrReplaceChild("lft_hammer_head_support", CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -2, 2, 2, 4), PartPose.offset(0, 2.5F, -3));
		lft_hammer_head_support.addOrReplaceChild("lft_hammer_head", CubeListBuilder.create().texOffs(32, 3).addBox(-2, 0, -2.5F, 4, 3, 5), PartPose.offset(0, 2, 0));
		lft_weapon_tip.addOrReplaceChild("lft_spike", CubeListBuilder.create().texOffs(52, 118).addBox(-1, -1, -3, 2, 2, 3), PartPose.offset(0, 0, -5));
		lft_weapon_tip.addOrReplaceChild("lft_spike1", CubeListBuilder.create().texOffs(52, 118).addBox(-3, -1, -1, 3, 2, 2), PartPose.offset(-2.5F, 0, -3));
		lft_weapon_tip.addOrReplaceChild("lft_spike2", CubeListBuilder.create().texOffs(52, 118).addBox(3, -1, -1, 3, 2, 2), PartPose.offset(-0.5F, 0, -3));
		lft_weapon_tip.addOrReplaceChild("lft_spike3", CubeListBuilder.create().texOffs(52, 118).addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(0, 2.5F, -3));
		lft_weapon_tip.addOrReplaceChild("lft_spike4", CubeListBuilder.create().texOffs(52, 118).addBox(-1, -3, -1, 2, 3, 2), PartPose.offset(0, -2.5F, -3));
		PartDefinition rgt_shoulder = root.addOrReplaceChild("rgt_shoulder", CubeListBuilder.create().texOffs(0, 31).addBox(0, -3, -4, 8, 7, 8), PartPose.offset(-15, -10, 2));
		PartDefinition rgt_arm = rgt_shoulder.addOrReplaceChild("rgt_arm", CubeListBuilder.create().texOffs(0, 66).addBox(0, 0, -4, 6, 9, 8), PartPose.offset(-4, -1, 1));
		PartDefinition rgt_hand = rgt_arm.addOrReplaceChild("rgt_hand", CubeListBuilder.create().texOffs(0, 46).addBox(-4, 0, -4, 8, 12, 8), PartPose.offset(3, 8, -1));
		rgt_hand.addOrReplaceChild("rgt_elbow", CubeListBuilder.create().texOffs(86, 64).addBox(-2, -1.5F, -0.5F, 4, 3, 1), PartPose.offset(0, 2.5F, 4));
		PartDefinition rgt_weapon_root = rgt_hand.addOrReplaceChild("rgt_weapon_root", CubeListBuilder.create().texOffs(24, 104).addBox(-1.5F, -1.5F, -4, 3, 3, 4), PartPose.offset(-0.5F, 8.5F, -4));
		rgt_weapon_root.addOrReplaceChild("rgt_weapon_end", CubeListBuilder.create().texOffs(74, 90).addBox(-1.5F, -1.5F, 0, 3, 3, 2), PartPose.offset(0, 0, 8));
		PartDefinition rgt_weapon_lump = rgt_weapon_root.addOrReplaceChild("rgt_weapon_lump", CubeListBuilder.create().texOffs(30, 83).addBox(-2.5F, -2.5F, -4, 5, 5, 4), PartPose.offset(0, 0, -4));
		PartDefinition rgt_weapon_between = rgt_weapon_lump.addOrReplaceChild("rgt_weapon_between", CubeListBuilder.create().texOffs(83, 42).addBox(-1.5F, -1.5F, -2, 3, 3, 2), PartPose.offset(0, 0, -4));
		PartDefinition rgt_weapon_tip = rgt_weapon_between.addOrReplaceChild("rgt_weapon_tip", CubeListBuilder.create().texOffs(60, 118).addBox(-2.5F, -2.5F, -5, 5, 5, 5), PartPose.offset(0, 0, -2));
		rgt_weapon_tip.addOrReplaceChild("rgt_hammer_neck", CubeListBuilder.create().texOffs(32, 39).addBox(-0.5F, -4, -4, 1, 4, 4), PartPose.offset(0, -2.5F, -1));
		PartDefinition rgt_hammer_head_support = rgt_weapon_tip.addOrReplaceChild("rgt_hammer_head_support", CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -2, 2, 2, 4), PartPose.offset(0, 2.5F, -3));
		rgt_hammer_head_support.addOrReplaceChild("rgt_hammer_head", CubeListBuilder.create().texOffs(32, 3).addBox(-2, 0, -2.5F, 4, 3, 5), PartPose.offset(0, 2, 0));
		rgt_weapon_tip.addOrReplaceChild("rgt_spike", CubeListBuilder.create().texOffs(52, 118).addBox(-1, -1, -3, 2, 2, 3), PartPose.offset(0, 0, -5));
		rgt_weapon_tip.addOrReplaceChild("rgt_spike1", CubeListBuilder.create().texOffs(52, 118).addBox(-3, -1, -1, 3, 2, 2), PartPose.offset(-2.5F, 0, -3));
		rgt_weapon_tip.addOrReplaceChild("rgt_spike2", CubeListBuilder.create().texOffs(52, 118).addBox(3, -1, -1, 3, 2, 2), PartPose.offset(-0.5F, 0, -3));
		rgt_weapon_tip.addOrReplaceChild("rgt_spike3", CubeListBuilder.create().texOffs(52, 118).addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(0, 2.5F, -3));
		rgt_weapon_tip.addOrReplaceChild("rgt_spike4", CubeListBuilder.create().texOffs(52, 118).addBox(-1, -3, -1, 2, 3, 2), PartPose.offset(0, -2.5F, -3));
		root.addOrReplaceChild("head3_rgt_ear", CubeListBuilder.create().texOffs(110, 24).addBox(-8, -9, -1, 3, 5, 2), PartPose.offset(7, -13, 0));
		root.addOrReplaceChild("head3_lft_ear", CubeListBuilder.create().texOffs(100, 24).addBox(5, -9, -1, 3, 5, 2), PartPose.offset(7, -13, 0));
		root.addOrReplaceChild("head3_eyelid", CubeListBuilder.create().texOffs(46, 3).addBox(-3, -8, -4.5F, 6, 2, 1), PartPose.offsetAndRotation(7, -13, 0, 0.2617994F, 0, 0));
		root.addOrReplaceChild("head3_nose", CubeListBuilder.create().texOffs(60, 9).addBox(-1.5F, -8.5F, -3.5F, 3, 2, 1), PartPose.offsetAndRotation(7, -13, 0, 0.4886922F, 0, 0));
		root.addOrReplaceChild("head3", CubeListBuilder.create().texOffs(42, 83).addBox(-5, -12, -6, 10, 12, 12), PartPose.offset(7, -13, 0));
		root.addOrReplaceChild("head3_brow", CubeListBuilder.create().texOffs(46, 0).addBox(-3, -9, -8.5F, 6, 2, 1), PartPose.offsetAndRotation(7, -13, 0, -0.2617994F, 0, 0));
		root.addOrReplaceChild("head3_hair", CubeListBuilder.create().texOffs(80, 118).addBox(-2, -17, -5, 4, 6, 4), PartPose.offsetAndRotation(7, -13, 0, -0.6108652F, 0, 0));
		root.addOrReplaceChild("head3_lip", CubeListBuilder.create().texOffs(22, 68).addBox(-4, -4, -7, 8, 2, 2), PartPose.offsetAndRotation(7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head3_rgt_tusk", CubeListBuilder.create().texOffs(83, 34).addBox(-3.5F, -6, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head3_rgt_tooth", CubeListBuilder.create().texOffs(87, 34).addBox(-1.5F, -5, -6.5F, 1, 1, 1), PartPose.offsetAndRotation(7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head3_lft_tooth", CubeListBuilder.create().texOffs(96, 34).addBox(0.5F, -5, -6.5F, 1, 1, 1), PartPose.offsetAndRotation(7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head3_lft_tusk", CubeListBuilder.create().texOffs(100, 34).addBox(2.5F, -6, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head3_ring_hole", CubeListBuilder.create().texOffs(26, 50).addBox(6, -5, -1, 1, 2, 2), PartPose.offset(7, -13, 0));
		root.addOrReplaceChild("head3_ring", CubeListBuilder.create().texOffs(32, 58).addBox(6, -6, -2, 1, 4, 4), PartPose.offset(7, -13, 0));
		root.addOrReplaceChild("head2_chin", CubeListBuilder.create().texOffs(21, 24).addBox(-3, -5, -8, 6, 3, 3), PartPose.offsetAndRotation(-7, -13, 0, 0.2617994F, 0, 0));
		root.addOrReplaceChild("head2", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -12, -6, 10, 12, 12), PartPose.offset(-7, -13, 0));
		root.addOrReplaceChild("head2_lip", CubeListBuilder.create().texOffs(0, 24).addBox(-4, -5, -8, 8, 2, 2), PartPose.offset(-7, -13, 0));
		root.addOrReplaceChild("head2_lft_tusk", CubeListBuilder.create().texOffs(46, 28).addBox(2.5F, -8, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(-7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head2_rgt_tusk", CubeListBuilder.create().texOffs(39, 28).addBox(-3.5F, -8, -6.5F, 1, 2, 1), PartPose.offsetAndRotation(-7, -13, 0, 0.1745329F, 0, 0));
		root.addOrReplaceChild("head2_nose", CubeListBuilder.create().texOffs(116, 0).addBox(-2, -7, -7, 4, 2, 2), PartPose.offsetAndRotation(-7, -13, 0, 0.0872665F, 0, 0));
		root.addOrReplaceChild("head2_nose_bridge", CubeListBuilder.create().texOffs(116, 4).addBox(-1, -7, -8, 2, 2, 1), PartPose.offsetAndRotation(-7, -13, 0, -0.1745329F, 0, 0));
		root.addOrReplaceChild("head2_brow", CubeListBuilder.create().texOffs(80, 24).addBox(-4, -10.5F, -8, 8, 3, 2), PartPose.offsetAndRotation(-7, -13, 0, -0.0872665F, 0, 0));
		root.addOrReplaceChild("head2_rgt_horn", CubeListBuilder.create().texOffs(24, 30).addBox(-4, -8, -15, 2, 2, 5), PartPose.offsetAndRotation(-7, -13, 0, -0.5235988F, 0, 0));
		root.addOrReplaceChild("head2_lft_horn", CubeListBuilder.create().texOffs(24, 30).addBox(2, -8, -15, 2, 2, 5), PartPose.offsetAndRotation(-7, -13, 0, -0.5235988F, 0, 0));
		root.addOrReplaceChild("head2_diamond_horn", CubeListBuilder.create().texOffs(120, 46).addBox(-1, -17, -6, 2, 6, 2), PartPose.offsetAndRotation(-7, -13, 0, 0.0872665F, 0, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		int type = state.twoHeads ? 2 : 1;
		int arm = state.arm;
		// the original's counter ran twice as fast for one arm as for both
		float counter = arm == 3 ? state.leftAttack : 2.0F * (arm == 1 ? state.leftAttack : state.rightAttack);
		// a two-headed ogre looks about with one head, then the other
		int headMoving = (int) (state.ageInTicks / 100.0F) % 2 + 2;
		boolean one = type == 1;
		for (ModelPart part : new ModelPart[] {this.head, this.brow, this.noseBridge, this.nose, this.rgtTusk, this.rgtTooth, this.lftTooth, this.lftTusk,
			this.lip, this.rgtEar, this.rgtRing, this.rgtRingHole, this.lftEar, this.lftRing, this.lftRingHole, this.hairRope, this.hair1, this.hair2,
			this.hair3, this.diamondHorn, this.rgtHorn, this.rgtHornTip, this.lftHorn, this.lftHornTip}) {
			part.visible = one;
		}
		for (ModelPart part : new ModelPart[] {this.head3RgtEar, this.head3LftEar, this.head3Eyelid, this.head3Nose, this.head3, this.head3Brow,
			this.head3Hair, this.head3Lip, this.head3RgtTusk, this.head3RgtTooth, this.head3LftTooth, this.head3LftTusk, this.head3RingHole,
			this.head3Ring, this.head2Chin, this.head2, this.head2Lip, this.head2LftTusk, this.head2RgtTusk, this.head2Nose, this.head2NoseBridge,
			this.head2Brow, this.head2RgtHorn, this.head2LftHorn, this.head2DiamondHorn}) {
			part.visible = !one;
		}
		this.lftWeaponRoot.visible = !one;
		float hRotY = state.yRot / 57.29578F;
		float hRotX = state.xRot / 57.29578F;

		float RLegXRot = Mth.cos((state.walkAnimationPos * 0.6662F) + 3.141593F) * 0.8F * state.walkAnimationSpeed;
		float LLegXRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.8F * state.walkAnimationSpeed;
		float ClothRot = Mth.cos(state.walkAnimationPos * 0.9F) * 0.6F * state.walkAnimationSpeed;

		float RLegXRotB = RLegXRot;
		float LLegXRotB = LLegXRot;

		this.rgtThigh.xRot = RLegXRot;
		this.lftThigh.xRot = LLegXRot;

		float RLegXRot2 = Mth.cos(((state.walkAnimationPos + 0.1F) * 0.6662F) + 3.141593F) * 0.8F * state.walkAnimationSpeed;
		float LLegXRot2 = Mth.cos((state.walkAnimationPos + 0.1F) * 0.6662F) * 0.8F * state.walkAnimationSpeed;
		if (state.walkAnimationSpeed > 0.15F) {
			if (RLegXRot > RLegXRot2) // - - >
			{
				RLegXRotB = RLegXRot + (25 / 57.29578F);

			}
			/*
			 * if (RLegXRot < RLegXRot2) // < - - { RLegXRotC = RLegXRot + (15 /
			 * 57.29578F); }
			 */
			if (LLegXRot > LLegXRot2) // - - >
			{
				LLegXRotB = LLegXRot + (25 / 57.29578F);
			}
			/*
			 * if (LLegXRot < LLegXRot2) // < - - { LLegXRotC = LLegXRot + (15 /
			 * 57.29578F); }
			 */
		}

		this.lftLeg.xRot = (LLegXRotB);
		this.rgtLeg.xRot = (RLegXRotB);
		this.loinCloth.xRot = ClothRot;
		this.buttCover.xRot = ClothRot;

		float armMov = -(Mth.cos((counter) * 0.18F) * 3F);

		//leftArm
		//attacking with left arm
		if (arm == 1 || arm == 3) {
			this.lftShoulder.xRot = +armMov;
			this.lftHand.xRot = (-45F / DEG);
		} else //normal left arm movement
		{
			this.lftShoulder.zRot = (Mth.cos(state.ageInTicks * 0.09F) * 0.05F) - 0.05F;
			this.lftShoulder.xRot = RLegXRot;
			this.lftHand.xRot = 0F;
		}

		//rightArm
		//attacking with right arm
		if (arm == 2 || arm == 3) {
			this.rgtShoulder.xRot = +armMov;
			this.rgtHand.xRot = (-45F / DEG);
		} else //normal right arm movement
		{
			this.rgtShoulder.zRot = -(Mth.cos(state.ageInTicks * 0.09F) * 0.05F) + 0.05F;
			this.rgtShoulder.xRot = LLegXRot;
			this.rgtHand.xRot = 0F;
		}

		if (headMoving == 2) {
			this.head2.xRot = hRotX;
			this.head2.yRot = hRotY;
		}

		if (headMoving == 3) {
			this.head3.xRot = hRotX;
			this.head3.yRot = hRotY;
		}

		if (type == 1 || type == 3 || type == 5) {
			this.head.xRot = hRotX;
			this.head.yRot = hRotY;

			this.brow.xRot = this.head.xRot;
			this.noseBridge.xRot = this.head.xRot;
			this.nose.xRot = this.head.xRot;
			this.rgtTusk.xRot = this.head.xRot;
			this.rgtTooth.xRot = this.head.xRot;
			this.lftTooth.xRot = this.head.xRot;
			this.lftTusk.xRot = this.head.xRot;
			this.lip.xRot = this.head.xRot;
			this.rgtEar.xRot = this.head.xRot;
			this.rgtRing.xRot = this.head.xRot;
			this.rgtRingHole.xRot = this.head.xRot;
			this.lftEar.xRot = this.head.xRot;
			this.lftRing.xRot = this.head.xRot;
			this.lftRingHole.xRot = this.head.xRot;
			this.hairRope.xRot = 0.6108652F + this.head.xRot;
			this.hair1.xRot = 0.6108652F + this.head.xRot;
			this.hair2.xRot = 0.2617994F + this.head.xRot;
			this.hair3.xRot = this.head.xRot;
			this.diamondHorn.xRot = 0.0872665F + this.head.xRot;
			this.rgtHorn.xRot = this.head.xRot;
			this.rgtHornTip.xRot = this.head.xRot;
			this.lftHorn.xRot = this.head.xRot;
			this.lftHornTip.xRot = this.head.xRot;

			this.brow.yRot = this.head.yRot;
			this.noseBridge.yRot = this.head.yRot;
			this.nose.yRot = this.head.yRot;
			this.rgtTusk.yRot = this.head.yRot;
			this.rgtTooth.yRot = this.head.yRot;
			this.lftTooth.yRot = this.head.yRot;
			this.lftTusk.yRot = this.head.yRot;
			this.lip.yRot = this.head.yRot;
			this.rgtEar.yRot = this.head.yRot;
			this.rgtRing.yRot = this.head.yRot;
			this.rgtRingHole.yRot = this.head.yRot;
			this.lftEar.yRot = this.head.yRot;
			this.lftRing.yRot = this.head.yRot;
			this.lftRingHole.yRot = this.head.yRot;
			this.hairRope.yRot = this.head.yRot;
			this.hair1.yRot = this.head.yRot;
			this.hair2.yRot = this.head.yRot;
			this.hair3.yRot = this.head.yRot;
			this.diamondHorn.yRot = this.head.yRot;
			this.rgtHorn.yRot = this.head.yRot;
			this.rgtHornTip.yRot = this.head.yRot;
			this.lftHorn.yRot = this.head.yRot;
			this.lftHornTip.yRot = this.head.yRot;
		} else {

			this.head3RgtEar.xRot = this.head3.xRot;
			this.head3LftEar.xRot = this.head3.xRot;
			this.head3Eyelid.xRot = 0.2617994F + this.head3.xRot;
			this.head3Nose.xRot = 0.4886922F + this.head3.xRot;
			this.head3Brow.xRot = -0.2617994F + this.head3.xRot;
			this.head3Hair.xRot = -0.6108652F + this.head3.xRot;
			this.head3Lip.xRot = 0.1745329F + this.head3.xRot;
			this.head3RgtTusk.xRot = 0.1745329F + this.head3.xRot;
			this.head3RgtTooth.xRot = 0.1745329F + this.head3.xRot;
			this.head3LftTooth.xRot = 0.1745329F + this.head3.xRot;
			this.head3LftTusk.xRot = 0.1745329F + this.head3.xRot;
			this.head3RingHole.xRot = this.head3.xRot;
			this.head3Ring.xRot = this.head3.xRot;

			this.head3RgtEar.yRot = this.head3.yRot;
			this.head3LftEar.yRot = this.head3.yRot;
			this.head3Eyelid.yRot = this.head3.yRot;
			this.head3Nose.yRot = this.head3.yRot;
			this.head3Brow.yRot = this.head3.yRot;
			this.head3Hair.yRot = this.head3.yRot;
			this.head3Lip.yRot = this.head3.yRot;
			this.head3RgtTusk.yRot = this.head3.yRot;
			this.head3RgtTooth.yRot = this.head3.yRot;
			this.head3LftTooth.yRot = this.head3.yRot;
			this.head3LftTusk.yRot = this.head3.yRot;
			this.head3RingHole.yRot = this.head3.yRot;
			this.head3Ring.yRot = this.head3.yRot;

			this.head2Chin.xRot = 0.2617994F + this.head2.xRot;
			this.head2Lip.xRot = this.head2.xRot;
			this.head2LftTusk.xRot = 0.1745329F + this.head2.xRot;
			this.head2RgtTusk.xRot = 0.1745329F + this.head2.xRot;
			this.head2Nose.xRot = 0.0872665F + this.head2.xRot;
			this.head2NoseBridge.xRot = -0.1745329F + this.head2.xRot;
			this.head2Brow.xRot = -0.0872665F + this.head2.xRot;
			this.head2RgtHorn.xRot = -0.5235988F + this.head2.xRot;
			this.head2LftHorn.xRot = -0.5235988F + this.head2.xRot;
			this.head2DiamondHorn.xRot = 0.0872665F + this.head2.xRot;

			this.head2Chin.yRot = this.head2.yRot;
			this.head2Lip.yRot = this.head2.yRot;
			this.head2LftTusk.yRot = this.head2.yRot;
			this.head2RgtTusk.yRot = this.head2.yRot;
			this.head2Nose.yRot = this.head2.yRot;
			this.head2NoseBridge.yRot = this.head2.yRot;
			this.head2Brow.yRot = this.head2.yRot;
			this.head2RgtHorn.yRot = this.head2.yRot;
			this.head2LftHorn.yRot = this.head2.yRot;
			this.head2DiamondHorn.yRot = this.head2.yRot;
		}
	}
}
