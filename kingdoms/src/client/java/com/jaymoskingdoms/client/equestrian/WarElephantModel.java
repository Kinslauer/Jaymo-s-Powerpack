package com.jaymoskingdoms.client.equestrian;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.5.34) The war elephant and the woolly mammoth: DrZhark's Mo' Creatures elephant (MoCModelElephant, GPL-3, see
 * {@code licenses/mocreatures}) ported to 26.3. Every part hangs off the root, posed each frame the way the original
 * does (the trunk's segments and the lower legs follow the part above them). The African hide has the big ears; the
 * mammoth its domed head and shaggy skirt. Saddled, it wears the original's harness and its wooden war platform (the
 * howdah), with our banner. Texture 128x256 (hides drawn at twice that).
 */
final class WarElephantModel extends EntityModel<WarElephantRenderer.State> {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** How often the ears flap, the tail swishes and the trunk swings (ticks), and for how long. */
	private static final int EAR_EVERY = 100, EAR_FOR = 30, TAIL_EVERY = 140, TAIL_FOR = 40, TRUNK_EVERY = 220, TRUNK_FOR = 32;

	private final ModelPart head, neck, headBump, chin, lowerLip, back, hump, body, skirt;
	private final ModelPart leftSmallEar, leftBigEar, rightSmallEar, rightBigEar;
	private final ModelPart rightTuskA, rightTuskB, rightTuskC, rightTuskD, leftTuskA, leftTuskB, leftTuskC, leftTuskD;
	private final ModelPart trunkA, trunkB, trunkC, trunkD, trunkE;
	private final ModelPart frontRightUpperLeg, frontRightLowerLeg, frontLeftUpperLeg, frontLeftLowerLeg;
	private final ModelPart backRightUpperLeg, backRightLowerLeg, backLeftUpperLeg, backLeftLowerLeg;
	private final ModelPart tailRoot, tail, tailPlush;
	private final ModelPart harnessBlanket, harnessUpperBelt, harnessLowerBelt;
	private final ModelPart fortNeckBeam, fortBackBeam, fortFloor1, fortFloor2, fortFloor3, fortBackWall, fortBackLeftWall, fortBackRightWall;
	private final ModelPart bannerPole, bannerFlag;

	WarElephantModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.neck = root.getChild("neck");
		this.headBump = root.getChild("head_bump");
		this.chin = root.getChild("chin");
		this.lowerLip = root.getChild("lower_lip");
		this.back = root.getChild("back");
		this.hump = root.getChild("hump");
		this.body = root.getChild("body");
		this.skirt = root.getChild("skirt");
		this.leftSmallEar = root.getChild("left_small_ear");
		this.leftBigEar = root.getChild("left_big_ear");
		this.rightSmallEar = root.getChild("right_small_ear");
		this.rightBigEar = root.getChild("right_big_ear");
		this.rightTuskA = root.getChild("right_tusk_a");
		this.rightTuskB = root.getChild("right_tusk_b");
		this.rightTuskC = root.getChild("right_tusk_c");
		this.rightTuskD = root.getChild("right_tusk_d");
		this.leftTuskA = root.getChild("left_tusk_a");
		this.leftTuskB = root.getChild("left_tusk_b");
		this.leftTuskC = root.getChild("left_tusk_c");
		this.leftTuskD = root.getChild("left_tusk_d");
		this.trunkA = root.getChild("trunk_a");
		this.trunkB = root.getChild("trunk_b");
		this.trunkC = root.getChild("trunk_c");
		this.trunkD = root.getChild("trunk_d");
		this.trunkE = root.getChild("trunk_e");
		this.frontRightUpperLeg = root.getChild("front_right_upper_leg");
		this.frontRightLowerLeg = root.getChild("front_right_lower_leg");
		this.frontLeftUpperLeg = root.getChild("front_left_upper_leg");
		this.frontLeftLowerLeg = root.getChild("front_left_lower_leg");
		this.backRightUpperLeg = root.getChild("back_right_upper_leg");
		this.backRightLowerLeg = root.getChild("back_right_lower_leg");
		this.backLeftUpperLeg = root.getChild("back_left_upper_leg");
		this.backLeftLowerLeg = root.getChild("back_left_lower_leg");
		this.tailRoot = root.getChild("tail_root");
		this.tail = root.getChild("tail");
		this.tailPlush = root.getChild("tail_plush");
		this.harnessBlanket = root.getChild("harness_blanket");
		this.harnessUpperBelt = root.getChild("harness_upper_belt");
		this.harnessLowerBelt = root.getChild("harness_lower_belt");
		this.fortNeckBeam = root.getChild("fort_neck_beam");
		this.fortBackBeam = root.getChild("fort_back_beam");
		this.fortFloor1 = root.getChild("fort_floor1");
		this.fortFloor2 = root.getChild("fort_floor2");
		this.fortFloor3 = root.getChild("fort_floor3");
		this.fortBackWall = root.getChild("fort_back_wall");
		this.fortBackLeftWall = root.getChild("fort_back_left_wall");
		this.fortBackRightWall = root.getChild("fort_back_right_wall");
		this.bannerPole = root.getChild("banner_pole");
		this.bannerFlag = root.getChild("banner_flag");
	}

	private static void part(PartDefinition root, String name, int u, int v, float x, float y, float z, float w, float h, float d,
		float px, float py, float pz, float rx, float ry, float rz) {
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		final float headY = -10.0F, headZ = -16.5F;
		part(r, "head", 60, 0, -5.5F, -6.0F, -8.0F, 11, 15, 10, 0.0F, headY, headZ, -0.1745329F, 0.0F, 0.0F);
		part(r, "neck", 46, 48, -4.95F, -6.0F, -8.0F, 10, 14, 8, 0.0F, -8.0F, -10.0F, -0.2617994F, 0.0F, 0.0F);
		part(r, "head_bump", 104, 41, -3.0F, -9.0F, -6.0F, 6, 3, 6, 0.0F, headY, headZ, -0.1745329F, 0.0F, 0.0F);
		part(r, "chin", 86, 56, -1.5F, -6.0F, -10.7F, 3, 5, 4, 0.0F, headY, headZ, 2.054118F, 0.0F, 0.0F);
		part(r, "lower_lip", 80, 65, -2.0F, -2.0F, -14.0F, 4, 2, 6, 0.0F, headY, headZ, 1.570796F, 0.0F, 0.0F);
		part(r, "back", 0, 48, -5.0F, -10.0F, -10.0F, 10, 2, 26, 0.0F, -4.0F, -3.0F, 0.0F, 0.0F, 0.0F);
		part(r, "left_small_ear", 102, 0, 2.0F, -8.0F, -5.0F, 8, 10, 1, 0.0F, headY, headZ, -0.1745329F, -0.5235988F, 0.5235988F);
		part(r, "left_big_ear", 102, 0, 2.0F, -8.0F, -5.0F, 12, 14, 1, 0.0F, headY, headZ, -0.1745329F, -0.5235988F, 0.5235988F);
		part(r, "right_small_ear", 106, 15, -10.0F, -8.0F, -5.0F, 8, 10, 1, 0.0F, headY, headZ, -0.1745329F, 0.5235988F, -0.5235988F);
		part(r, "right_big_ear", 102, 15, -14.0F, -8.0F, -5.0F, 12, 14, 1, 0.0F, headY, headZ, -0.1745329F, 0.5235988F, -0.5235988F);
		part(r, "hump", 88, 30, -6.0F, -2.0F, -3.0F, 12, 3, 8, 0.0F, -13.0F, -5.5F, 0.0F, 0.0F, 0.0F);
		part(r, "body", 0, 0, -8.0F, -10.0F, -10.0F, 16, 20, 28, 0.0F, -2.0F, -3.0F, 0.0F, 0.0F, 0.0F);
		part(r, "skirt", 28, 94, -8.0F, -10.0F, -6.0F, 16, 28, 6, 0.0F, 8.0F, -3.0F, 1.570796F, 0.0F, 0.0F);
		part(r, "right_tusk_a", 2, 60, -3.8F, -3.5F, -19.0F, 2, 2, 10, 0.0F, headY, headZ, 1.22173F, 0.0F, 0.1745329F);
		part(r, "right_tusk_b", 0, 0, -3.8F, 6.2F, -24.2F, 2, 2, 7, 0.0F, headY, headZ, 0.6981317F, 0.0F, 0.1745329F);
		part(r, "right_tusk_c", 0, 18, -3.8F, 17.1F, -21.9F, 2, 2, 5, 0.0F, headY, headZ, 0.1745329F, 0.0F, 0.1745329F);
		part(r, "right_tusk_d", 14, 18, -3.8F, 25.5F, -14.5F, 2, 2, 5, 0.0F, headY, headZ, -0.3490659F, 0.0F, 0.1745329F);
		part(r, "left_tusk_a", 2, 48, 1.8F, -3.5F, -19.0F, 2, 2, 10, 0.0F, headY, headZ, 1.22173F, 0.0F, -0.1745329F);
		part(r, "left_tusk_b", 0, 9, 1.8F, 6.2F, -24.2F, 2, 2, 7, 0.0F, headY, headZ, 0.6981317F, 0.0F, -0.1745329F);
		part(r, "left_tusk_c", 0, 18, 1.8F, 17.1F, -21.9F, 2, 2, 5, 0.0F, headY, headZ, 0.1745329F, 0.0F, -0.1745329F);
		part(r, "left_tusk_d", 14, 18, 1.8F, 25.5F, -14.5F, 2, 2, 5, 0.0F, headY, headZ, -0.3490659F, 0.0F, -0.1745329F);
		part(r, "trunk_a", 0, 76, -4.0F, -2.5F, -18.0F, 8, 7, 10, 0.0F, -3.0F, -22.46667F, 1.570796F, 0.0F, 0.0F);
		part(r, "trunk_b", 0, 93, -3.0F, -2.5F, -7.0F, 6, 5, 7, 0.0F, 6.5F, -22.5F, 1.658063F, 0.0F, 0.0F);
		part(r, "trunk_c", 0, 105, -2.5F, -2.0F, -4.0F, 5, 4, 5, 0.0F, 13.0F, -22.0F, 1.919862F, 0.0F, 0.0F);
		part(r, "trunk_d", 0, 114, -2.0F, -1.5F, -5.0F, 4, 3, 5, 0.0F, 16.0F, -21.5F, 2.216568F, 0.0F, 0.0F);
		part(r, "trunk_e", 0, 122, -1.5F, -1.0F, -4.0F, 3, 2, 4, 0.0F, 19.5F, -19.0F, 2.530727F, 0.0F, 0.0F);
		part(r, "front_right_upper_leg", 100, 109, -3.5F, 0.0F, -3.5F, 7, 12, 7, -4.6F, 4.0F, -9.6F, 0.0F, 0.0F, 0.0F);
		part(r, "front_right_lower_leg", 100, 73, -3.5F, 0.0F, -3.5F, 7, 10, 7, -4.6F, 14.0F, -9.6F, 0.0F, 0.0F, 0.0F);
		part(r, "front_left_upper_leg", 100, 90, -3.5F, 0.0F, -3.5F, 7, 12, 7, 4.6F, 4.0F, -9.6F, 0.0F, 0.0F, 0.0F);
		part(r, "front_left_lower_leg", 72, 73, -3.5F, 0.0F, -3.5F, 7, 10, 7, 4.6F, 14.0F, -9.6F, 0.0F, 0.0F, 0.0F);
		part(r, "back_right_upper_leg", 72, 109, -3.5F, 0.0F, -3.5F, 7, 12, 7, -4.6F, 4.0F, 11.6F, 0.0F, 0.0F, 0.0F);
		part(r, "back_right_lower_leg", 100, 56, -3.5F, 0.0F, -3.5F, 7, 10, 7, -4.6F, 14.0F, 11.6F, 0.0F, 0.0F, 0.0F);
		part(r, "back_left_upper_leg", 72, 90, -3.5F, 0.0F, -3.5F, 7, 12, 7, 4.6F, 4.0F, 11.6F, 0.0F, 0.0F, 0.0F);
		part(r, "back_left_lower_leg", 44, 77, -3.5F, 0.0F, -3.5F, 7, 10, 7, 4.6F, 14.0F, 11.6F, 0.0F, 0.0F, 0.0F);
		part(r, "tail_root", 20, 105, -1.0F, 0.0F, -2.0F, 2, 10, 2, 0.0F, -8.0F, 15.0F, 0.296706F, 0.0F, 0.0F);
		part(r, "tail", 20, 117, -1.0F, 9.7F, -0.2F, 2, 6, 2, 0.0F, -8.0F, 15.0F, 0.1134464F, 0.0F, 0.0F);
		part(r, "tail_plush", 26, 76, -1.5F, 15.5F, -0.7F, 3, 6, 3, 0.0F, -8.0F, 15.0F, 0.1134464F, 0.0F, 0.0F);
		part(r, "harness_blanket", 0, 196, -8.5F, -2.0F, -3.0F, 17, 14, 18, 0.0F, -13.2F, -3.5F, 0.0F, 0.0F, 0.0F);
		part(r, "harness_upper_belt", 70, 196, -8.5F, 0.5F, -2.0F, 17, 10, 2, 0.0F, -2.0F, -2.5F, 0.0F, 0.0F, 0.0F);
		part(r, "harness_lower_belt", 70, 196, -8.5F, 0.5F, -2.5F, 17, 10, 2, 0.0F, -2.0F, 7.0F, 0.0F, 0.0F, 0.0F);
		final float fortY = -16.0F, fortZ = 10.0F;
		part(r, "fort_neck_beam", 26, 180, -12.0F, 0.0F, -20.5F, 24, 4, 4, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		part(r, "fort_back_beam", 26, 180, -12.0F, 0.0F, 0.0F, 24, 4, 4, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		part(r, "fort_floor1", 0, 176, -0.5F, -20.0F, -6.0F, 1, 8, 12, 0.0F, fortY, fortZ, 1.570796F, 0.0F, 1.570796F);
		part(r, "fort_floor2", 0, 176, -0.5F, -12.0F, -6.0F, 1, 8, 12, 0.0F, fortY, fortZ, 1.570796F, 0.0F, 1.570796F);
		part(r, "fort_floor3", 0, 176, -0.5F, -4.0F, -6.0F, 1, 8, 12, 0.0F, fortY, fortZ, 1.570796F, 0.0F, 1.570796F);
		part(r, "fort_back_wall", 0, 176, -5.0F, -6.2F, -6.0F, 1, 8, 12, 0.0F, fortY, fortZ, 0.0F, 1.570796F, 0.0F);
		part(r, "fort_back_left_wall", 0, 176, 6.0F, -6.0F, -7.0F, 1, 8, 12, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		part(r, "fort_back_right_wall", 0, 176, -7.0F, -6.0F, -7.0F, 1, 8, 12, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		// our banner, stood on the platform's left wall at the back
		part(r, "banner_pole", 88, 41, 5.5F, -16.0F, 3.0F, 1, 10, 1, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		part(r, "banner_flag", 94, 41, 6.0F, -16.0F, 4.0F, 0, 6, 5, 0.0F, fortY, fortZ, 0.0F, 0.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 256);
	}

	@Override
	public void setupAnim(WarElephantRenderer.State state) {
		super.setupAnim(state);
		float f = state.walkAnimationPos;
		float f1 = Math.min(state.walkAnimationSpeed, 1.0F);
		float age = state.ageInTicks;
		int tick = (int)age;
		float rLeg = Mth.cos(f * 0.6662F + Mth.PI) * 0.8F * f1;
		float lLeg = Mth.cos(f * 0.6662F) * 0.8F * f1;
		float headX = Math.max(state.xRot, 0.0F) * DEG;
		float headY = Mth.clamp(state.yRot, -20.0F, 20.0F) * DEG;

		// the trunk now and then swings; rearing, it curls high to trumpet
		float trunkX = 0.0F;
		int trunkAt = tick % TRUNK_EVERY;
		if (trunkAt < TRUNK_FOR) {
			headX = 0.0F;
			trunkX = Mth.cos(trunkAt * 0.2F) * 12.0F;
		}
		if (state.standAnimation > 0.0F) {
			headX = Mth.lerp(state.standAnimation, headX, 0.0F);
			trunkX = Mth.lerp(state.standAnimation, trunkX, 25.0F);
		}

		this.head.xRot = -10.0F * DEG + headX;
		this.head.yRot = headY;
		this.headBump.xRot = this.head.xRot;
		this.headBump.yRot = headY;
		this.rightTuskA.yRot = headY;
		this.leftTuskA.yRot = headY;
		this.rightTuskA.xRot = 70.0F * DEG + headX;
		this.leftTuskA.xRot = 70.0F * DEG + headX;
		this.chin.yRot = headY;
		this.chin.xRot = 113.0F * DEG + headX;
		this.lowerLip.yRot = headY;
		this.lowerLip.xRot = 85.0F * DEG + headX;

		float ear = 0.0F;
		int earAt = (tick + 37) % EAR_EVERY;
		if (earAt < EAR_FOR) {
			ear = Mth.cos(earAt * 0.5F) * 0.35F;
		}
		ear += state.standAnimation * 0.35F;
		this.rightBigEar.yRot = 30.0F * DEG + headY + ear;
		this.rightSmallEar.yRot = 30.0F * DEG + headY + ear;
		this.leftBigEar.yRot = -30.0F * DEG + headY - ear;
		this.leftSmallEar.yRot = -30.0F * DEG + headY - ear;
		this.rightBigEar.xRot = -10.0F * DEG + headX;
		this.rightSmallEar.xRot = -10.0F * DEG + headX;
		this.leftBigEar.xRot = -10.0F * DEG + headX;
		this.leftSmallEar.xRot = -10.0F * DEG + headX;

		// each trunk segment hangs from the end of the one above
		this.trunkA.y = -3.0F;
		this.trunkB.y = 5.5F;
		this.trunkC.y = 13.0F;
		this.trunkD.y = 16.0F;
		this.trunkE.y = 19.5F;
		this.trunkA.z = -22.5F;
		follow(this.trunkA, this.head);
		this.trunkA.yRot = headY;
		this.trunkA.xRot = Math.max(90.0F - trunkX, 85.0F) * DEG + headX;
		this.trunkB.z = -22.5F;
		follow(this.trunkB, this.trunkA);
		this.trunkB.yRot = headY;
		this.trunkB.xRot = (95.0F - trunkX * 1.5F) * DEG + headX;
		this.trunkC.z = -22.5F;
		follow(this.trunkC, this.trunkB);
		this.trunkC.yRot = headY;
		this.trunkC.xRot = (110.0F - trunkX * 3.0F) * DEG + headX;
		this.trunkD.z = -21.5F;
		follow(this.trunkD, this.trunkC);
		this.trunkD.yRot = headY;
		this.trunkD.xRot = (127.0F - trunkX * 4.5F) * DEG + headX;
		this.trunkE.z = -19.0F;
		follow(this.trunkE, this.trunkD);
		this.trunkE.yRot = headY;
		this.trunkE.xRot = (145.0F - trunkX * 6.0F) * DEG + headX;

		this.frontRightUpperLeg.xRot = rLeg;
		this.frontLeftUpperLeg.xRot = lLeg;
		this.backLeftUpperLeg.xRot = rLeg;
		this.backRightUpperLeg.xRot = lLeg;
		knee(this.frontRightLowerLeg, this.frontRightUpperLeg);
		knee(this.backRightLowerLeg, this.backRightUpperLeg);
		knee(this.frontLeftLowerLeg, this.frontLeftUpperLeg);
		knee(this.backLeftLowerLeg, this.backLeftUpperLeg);
		float lBend = lLeg > 0.0F ? lLeg * 2.0F : lLeg;
		float rBend = rLeg > 0.0F ? rLeg * 2.0F : rLeg;
		this.frontLeftLowerLeg.xRot = lBend;
		this.frontRightLowerLeg.xRot = rBend;
		this.backLeftLowerLeg.xRot = rBend;
		this.backRightLowerLeg.xRot = lBend;

		for (ModelPart tusk : new ModelPart[] {this.leftTuskB, this.leftTuskC, this.leftTuskD, this.rightTuskB, this.rightTuskC, this.rightTuskD}) {
			tusk.yRot = headY;
		}
		this.leftTuskB.xRot = this.rightTuskB.xRot = 40.0F * DEG + headX;
		this.leftTuskC.xRot = this.rightTuskC.xRot = 10.0F * DEG + headX;
		this.leftTuskD.xRot = this.rightTuskD.xRot = -20.0F * DEG + headX;

		float tailMov = f1 * 0.9F;
		int tailAt = (tick + 71) % TAIL_EVERY;
		if (tailAt < TAIL_FOR) {
			this.tailRoot.yRot = Mth.cos(age * 0.4F) * 1.3F;
			tailMov = 30.0F * DEG;
		} else {
			this.tailRoot.yRot = 0.0F;
		}
		this.tailRoot.xRot = 17.0F * DEG + tailMov;
		this.tailPlush.xRot = this.tail.xRot = 6.5F * DEG + tailMov;
		this.tailPlush.yRot = this.tailRoot.yRot;
		this.tail.yRot = this.tailRoot.yRot;

		// the platform rocks with its stride
		float rock = lLeg / 50.0F;
		this.fortNeckBeam.zRot = rock;
		this.fortBackBeam.zRot = rock;
		this.fortBackRightWall.zRot = rock;
		this.fortBackLeftWall.zRot = rock;
		this.fortBackWall.xRot = -rock;
		this.bannerPole.zRot = rock;
		this.bannerFlag.zRot = rock;
		this.bannerFlag.yRot = Mth.sin(age * 0.15F) * 0.2F;

		// which parts it wears: tusks grow in with age, big ears on the African, dome and skirt on the mammoth
		boolean adult = !state.isBaby;
		this.leftTuskC.visible = this.rightTuskC.visible = adult;
		this.leftTuskD.visible = this.rightTuskD.visible = adult;
		this.leftBigEar.visible = this.rightBigEar.visible = state.bigEars;
		this.leftSmallEar.visible = this.rightSmallEar.visible = !state.bigEars;
		this.headBump.visible = state.mammoth;
		this.skirt.visible = state.mammoth || state.royal && state.saddled;
		for (ModelPart p : new ModelPart[] {this.harnessBlanket, this.harnessUpperBelt, this.harnessLowerBelt, this.fortNeckBeam, this.fortBackBeam,
			this.fortFloor1, this.fortFloor2, this.fortFloor3, this.fortBackWall, this.fortBackLeftWall, this.fortBackRightWall, this.bannerPole, this.bannerFlag}) {
			p.visible = state.saddled;
		}
	}

	/** A lower leg hangs from the end of its upper leg, wherever that's swung (the original's adjustXRotationPoints). */
	private static void knee(ModelPart lower, ModelPart upper) {
		float distance = Math.abs(lower.y - upper.y);
		lower.z = upper.z + Mth.sin(upper.xRot) * distance;
		lower.y = upper.y + Mth.cos(upper.xRot) * distance;
	}

	/** A trunk segment hangs from the end of the part above it (the original's adjustAllRotationPoints). */
	private static void follow(ModelPart target, ModelPart origin) {
		float dy = Math.abs(target.y - origin.y);
		target.y = origin.y + Mth.sin(origin.xRot) * dy;
		target.z = origin.z - Mth.cos(origin.yRot) * (Mth.cos(origin.xRot) * dy);
		target.x = origin.x - Mth.sin(origin.yRot) * (Mth.cos(origin.xRot) * dy);
	}
}
