package com.jaymoskingdoms.client.equestrian;

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
 * (0.5.44) Horses, skeleton horses and zombie horses: DrZhark's Mo' Creatures horse (MoCModelNewHorse, GPL-3, see
 * {@code licenses/mocreatures}) ported to 26.3, without its wings, horn, mule ears and bags. Every part hangs off the
 * root and is posed each frame the way the original does: the jointed legs (each lower leg swings from the end of the
 * one above, the cannon bending as the hoof comes forward), the head, neck and mane moving together, the mouth opening
 * to eat, the tail lifting with speed and swishing. Vanilla's rear and graze come in as blends (the original's are
 * on or off). A vanilla saddle shows as the original's saddle, stirrups, headstall and reins; our war and racing
 * saddles and the bridle are drawn by {@link TackLayer} on these same bones. Texture 128x128 (coats drawn at twice that).
 */
class MocHorseModel extends EntityModel<MocHorseRenderer.State> {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** The head's pitch at rest, reared and grazing. */
	private static final float HEAD_TILT = 30.0F * DEG, HEAD_TILT_REARED = 15.0F * DEG, HEAD_TILT_GRAZING = 2.18166F;
	/** The open mouth: the upper jaw lifts, the lower drops. */
	private static final float UPPER_JAW_OPEN = -5.0F * DEG, LOWER_JAW_OPEN = 15.0F * DEG;
	/** How long each upper leg is, to where the cannon hangs from it. */
	private static final float THIGH = 7.0F;
	/** The mouth opens past this much of vanilla's feeding animation. */
	private static final float MOUTH_OPEN = 0.3F;

	private final ModelPart body, tailA, tailB, tailC, head, upperMouth, lowerMouth, upperMouthOpen, lowerMouthOpen;
	private final ModelPart leftEar, rightEar, neck, mane;
	private final ModelPart leg1A, leg1B, leg1C, leg2A, leg2B, leg2C, leg3A, leg3B, leg3C, leg4A, leg4B, leg4C;
	private final ModelPart headSaddle, saddle, saddleB, saddleC, stirrupLeft, stirrupLeftIron, stirrupRight, stirrupRightIron;
	private final ModelPart bitLeft, bitRight, reinLeft, reinRight;

	MocHorseModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.tailA = root.getChild("tail_a");
		this.tailB = root.getChild("tail_b");
		this.tailC = root.getChild("tail_c");
		this.leg1A = root.getChild("leg1_a");
		this.leg1B = root.getChild("leg1_b");
		this.leg1C = root.getChild("leg1_c");
		this.leg2A = root.getChild("leg2_a");
		this.leg2B = root.getChild("leg2_b");
		this.leg2C = root.getChild("leg2_c");
		this.leg3A = root.getChild("leg3_a");
		this.leg3B = root.getChild("leg3_b");
		this.leg3C = root.getChild("leg3_c");
		this.leg4A = root.getChild("leg4_a");
		this.leg4B = root.getChild("leg4_b");
		this.leg4C = root.getChild("leg4_c");
		this.head = root.getChild("head");
		this.upperMouth = root.getChild("upper_mouth");
		this.lowerMouth = root.getChild("lower_mouth");
		this.upperMouthOpen = root.getChild("upper_mouth_open");
		this.lowerMouthOpen = root.getChild("lower_mouth_open");
		this.leftEar = root.getChild("left_ear");
		this.rightEar = root.getChild("right_ear");
		this.neck = root.getChild("neck");
		this.mane = root.getChild("mane");
		this.headSaddle = root.getChild("head_saddle");
		this.saddle = root.getChild("saddle");
		this.saddleB = root.getChild("saddle_b");
		this.saddleC = root.getChild("saddle_c");
		this.stirrupLeft = root.getChild("stirrup_left");
		this.stirrupLeftIron = root.getChild("stirrup_left_iron");
		this.stirrupRight = root.getChild("stirrup_right");
		this.stirrupRightIron = root.getChild("stirrup_right_iron");
		this.bitLeft = root.getChild("bit_left");
		this.bitRight = root.getChild("bit_right");
		this.reinLeft = root.getChild("rein_left");
		this.reinRight = root.getChild("rein_right");
	}

	/** The horse itself. */
	static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(mesh(true, CubeDeformation.NONE), 128, 128);
	}

	/** Its armour: the same boxes, a little larger, for an overlay texture drawn over the coat. */
	static LayerDefinition createArmorLayer() {
		return LayerDefinition.create(mesh(true, new CubeDeformation(0.1F)), 128, 128);
	}

	/** The bones alone (no boxes), for {@link TackLayer} to hang its pieces on. */
	static MeshDefinition bones() {
		return mesh(false, CubeDeformation.NONE);
	}

	private static MeshDefinition mesh(boolean boxes, CubeDeformation grow) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		Parts p = new Parts(r, boxes);
		p.add("body", 0, 34, -5, -8, -19, 10, 10, 24, grow, PartPose.offset(0, 11, 9));
		p.add("tail_a", 44, 0, -1, -1, 0, 2, 2, 3, grow, PartPose.offsetAndRotation(0, 3, 14, -1.134464F, 0, 0));
		p.add("tail_b", 38, 7, -1.5F, -2, 3, 3, 4, 7, grow, PartPose.offsetAndRotation(0, 3, 14, -1.134464F, 0, 0));
		p.add("tail_c", 24, 3, -1.5F, -4.5F, 9, 3, 4, 7, grow, PartPose.offsetAndRotation(0, 3, 14, -1.40215F, 0, 0));
		// back left, back right, front left, front right: the upper leg, the cannon, the hoof
		p.add("leg1_a", 78, 29, -2.5F, -2, -2.5F, 4, 9, 5, grow, PartPose.offset(4, 9, 11));
		p.add("leg1_b", 78, 43, -2, 0, -1.5F, 3, 5, 3, grow, PartPose.offset(4, 16, 11));
		p.add("leg1_c", 78, 51, -2.5F, 5.1F, -2, 4, 3, 4, grow, PartPose.offset(4, 16, 11));
		p.add("leg2_a", 96, 29, -1.5F, -2, -2.5F, 4, 9, 5, grow, PartPose.offset(-4, 9, 11));
		p.add("leg2_b", 96, 43, -1, 0, -1.5F, 3, 5, 3, grow, PartPose.offset(-4, 16, 11));
		p.add("leg2_c", 96, 51, -1.5F, 5.1F, -2, 4, 3, 4, grow, PartPose.offset(-4, 16, 11));
		p.add("leg3_a", 44, 29, -1.9F, -1, -2.1F, 3, 8, 4, grow, PartPose.offset(4, 9, -8));
		p.add("leg3_b", 44, 41, -1.9F, 0, -1.6F, 3, 5, 3, grow, PartPose.offset(4, 16, -8));
		p.add("leg3_c", 44, 51, -2.4F, 5.1F, -2.1F, 4, 3, 4, grow, PartPose.offset(4, 16, -8));
		p.add("leg4_a", 60, 29, -1.1F, -1, -2.1F, 3, 8, 4, grow, PartPose.offset(-4, 9, -8));
		p.add("leg4_b", 60, 41, -1.1F, 0, -1.6F, 3, 5, 3, grow, PartPose.offset(-4, 16, -8));
		p.add("leg4_c", 60, 51, -1.6F, 5.1F, -2.1F, 4, 3, 4, grow, PartPose.offset(-4, 16, -8));
		PartPose headPose = PartPose.offsetAndRotation(0, 4, -10, HEAD_TILT, 0, 0);
		p.add("head", 0, 0, -2.5F, -10, -1.5F, 5, 5, 7, grow, headPose);
		p.add("upper_mouth", 24, 18, -2, -10, -7, 4, 3, 6, grow, headPose);
		p.add("lower_mouth", 24, 27, -2, -7, -6.5F, 4, 2, 5, grow, headPose);
		p.add("upper_mouth_open", 24, 18, -2, -10, -8, 4, 3, 6, grow, PartPose.offsetAndRotation(0, 4, -10, 0.4363323F, 0, 0));
		p.add("lower_mouth_open", 24, 27, -2, -7, -5.5F, 4, 2, 5, grow, PartPose.offsetAndRotation(0, 4, -10, 0.7853982F, 0, 0));
		p.add("left_ear", 0, 0, 0.45F, -12, 4, 2, 3, 1, grow, headPose);
		p.add("right_ear", 0, 0, -2.45F, -12, 4, 2, 3, 1, grow, headPose);
		p.add("neck", 0, 12, -2.05F, -9.8F, -2, 4, 14, 8, grow, headPose);
		p.add("mane", 58, 0, -1, -11.5F, 5, 2, 16, 4, grow, headPose);
		// the vanilla saddle: the original's headstall, saddle, stirrups, bit rings and reins
		p.add("head_saddle", 80, 12, -2.5F, -10.1F, -7, 5, 5, 12, grow.extend(0.2F), headPose);
		p.add("saddle", 80, 0, -5, 0, -3, 10, 1, 8, grow, PartPose.offset(0, 2, 2));
		p.add("saddle_b", 106, 9, -1.5F, -1, -3, 3, 1, 2, grow, PartPose.offset(0, 2, 2));
		p.add("saddle_c", 80, 9, -4, -1, 3, 8, 1, 2, grow, PartPose.offset(0, 2, 2));
		p.add("stirrup_left", 70, 0, -0.5F, 0, -0.5F, 1, 6, 1, grow, PartPose.offset(5, 3, 2));
		p.add("stirrup_left_iron", 74, 0, -0.5F, 6, -1, 1, 2, 2, grow, PartPose.offset(5, 3, 2));
		p.add("stirrup_right", 70, 0, -0.5F, 0, -0.5F, 1, 6, 1, grow, PartPose.offset(-5, 3, 2));
		p.add("stirrup_right_iron", 74, 4, -0.5F, 6, -1, 1, 2, 2, grow, PartPose.offset(-5, 3, 2));
		p.add("bit_left", 74, 13, 1.5F, -8, -4, 1, 2, 2, grow, headPose);
		p.add("bit_right", 74, 13, -2.5F, -8, -4, 1, 2, 2, grow, headPose);
		p.add("rein_left", 44, 10, 2.6F, -6, -6, 0, 3, 16, grow, PartPose.offset(0, 4, -10));
		p.add("rein_right", 44, 5, -2.6F, -6, -6, 0, 3, 16, grow, PartPose.offset(0, 4, -10));
		return mesh;
	}

	/** Adds root parts, with their boxes or (for the tack's bones) without. */
	private record Parts(PartDefinition root, boolean boxes) {
		void add(String name, int u, int v, float x, float y, float z, int w, int h, int d, CubeDeformation grow, PartPose pose) {
			this.root.addOrReplaceChild(name, this.boxes ? CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, grow) : CubeListBuilder.create(), pose);
		}
	}

	@Override
	public void setupAnim(MocHorseRenderer.State state) {
		super.setupAnim(state);
		float walked = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float ticks = state.ageInTicks;
		float stand = state.standAnimation;
		float graze = state.eatAnimation * (1.0F - stand);
		float rest = 1.0F - stand - graze;
		float yaw = Mth.clamp(state.yRot, -20.0F, 20.0F) * DEG;
		float rLeg = Mth.cos(walked * 0.6662F + Mth.PI) * 0.8F * speed;
		float lLeg = Mth.cos(walked * 0.6662F) * 0.8F * speed;
		float pitch = state.xRot * DEG;
		if (speed > 0.2F) {
			pitch += Mth.cos(walked * 0.4F) * 0.15F * speed;
		}

		// the head, neck, mane and ears: up when reared, down to the grass when grazing
		float headY = 4.0F * rest + -6.0F * stand + 11.0F * graze;
		float headZ = -10.0F * rest + -1.0F * stand + -10.0F * graze;
		float headX = (HEAD_TILT + pitch) * rest + (HEAD_TILT_REARED + pitch) * stand + HEAD_TILT_GRAZING * graze;
		float headYaw = yaw * (rest + stand);
		boolean open = state.feedingAnimation > MOUTH_OPEN;
		for (ModelPart part : new ModelPart[] {this.head, this.leftEar, this.rightEar, this.neck, this.mane, this.upperMouth, this.lowerMouth,
			this.upperMouthOpen, this.lowerMouthOpen, this.headSaddle, this.bitLeft, this.bitRight}) {
			pose(part, headY, headZ, headX, headYaw);
		}
		this.upperMouthOpen.xRot = headX + UPPER_JAW_OPEN;
		this.lowerMouthOpen.xRot = headX + LOWER_JAW_OPEN;
		this.upperMouth.visible = this.lowerMouth.visible = !open;
		this.upperMouthOpen.visible = this.lowerMouthOpen.visible = open;
		pose(this.reinLeft, headY, headZ, pitch, headYaw);
		pose(this.reinRight, headY, headZ, pitch, headYaw);

		// rearing: up on the hind legs
		this.body.xRot = -45.0F * DEG * stand;
		this.tailA.y = 3.0F + 6.0F * stand;

		// the legs: walking, and reared (the forelegs paw the air, the hind legs fold under)
		float rB = rLeg, lB = lLeg, rC = rLeg, lC = lLeg;
		if (speed > 0.2F) {
			float rNext = Mth.cos((walked + 0.1F) * 0.6662F + Mth.PI) * 0.8F * speed;
			float lNext = Mth.cos((walked + 0.1F) * 0.6662F) * 0.8F * speed;
			if (rLeg > rNext) {
				rB = rLeg + 55.0F * DEG;
			} else if (rLeg < rNext) {
				rC = rLeg + 15.0F * DEG;
			}
			if (lLeg > lNext) {
				lB = lLeg + 55.0F * DEG;
			} else if (lLeg < lNext) {
				lC = lLeg + 15.0F * DEG;
			}
		}
		float rPaw = -60.0F * DEG + Mth.cos(ticks * 0.4F + Mth.PI);
		float lPaw = -60.0F * DEG + Mth.cos(ticks * 0.4F);
		float pawBend = 45.0F * DEG, hindUpper = -15.0F * DEG, hindLower = 15.0F * DEG;
		float frontY = Mth.lerp(stand, 9.0F, -2.0F), frontZ = Mth.lerp(stand, -8.0F, -2.0F);
		this.leg3A.y = this.leg4A.y = frontY;
		this.leg3A.z = this.leg4A.z = frontZ;
		leg(this.leg1A, this.leg1B, this.leg1C, Mth.lerp(stand, lLeg, hindUpper), Mth.lerp(stand, lC, hindLower), lLeg, hindUpper, stand);
		leg(this.leg2A, this.leg2B, this.leg2C, Mth.lerp(stand, rLeg, hindUpper), Mth.lerp(stand, rC, hindLower), rLeg, hindUpper, stand);
		leg(this.leg3A, this.leg3B, this.leg3C, Mth.lerp(stand, rLeg, rPaw), Mth.lerp(stand, rB, pawBend), rLeg, rPaw, stand);
		leg(this.leg4A, this.leg4B, this.leg4C, Mth.lerp(stand, lLeg, lPaw), Mth.lerp(stand, lB, pawBend), lLeg, lPaw, stand);

		// the saddle sits back along the reared body; the stirrups swing unless someone's feet are in them
		float saddleY = Mth.lerp(stand, 2.0F, 0.5F), saddleZ = Mth.lerp(stand, 2.0F, 11.0F);
		for (ModelPart part : new ModelPart[] {this.saddle, this.saddleB, this.saddleC, this.stirrupLeft, this.stirrupLeftIron, this.stirrupRight, this.stirrupRightIron}) {
			part.y = saddleY;
			part.z = saddleZ;
		}
		this.saddle.xRot = this.saddleB.xRot = this.saddleC.xRot = this.body.xRot;
		boolean ridden = state.isRidden;
		float swingX = ridden ? -60.0F * DEG : rLeg / 3.0F;
		float swingZ = ridden ? 0.0F : rLeg / 5.0F;
		this.stirrupLeft.xRot = this.stirrupLeftIron.xRot = this.stirrupRight.xRot = this.stirrupRightIron.xRot = swingX;
		this.stirrupLeft.zRot = this.stirrupLeftIron.zRot = swingZ;
		this.stirrupRight.zRot = this.stirrupRightIron.zRot = -swingZ;

		// the tail: hangs at rest, lifts as it goes faster, swishes now and then
		float tailLift = Math.min(0.0F, -1.3089F + speed * 1.5F);
		float tailYaw = 0.0F;
		if (state.animateTail) {
			tailYaw = Mth.cos(ticks * 0.7F);
			tailLift = 0.0F;
		}
		this.tailB.y = this.tailC.y = this.tailA.y;
		this.tailA.yRot = this.tailB.yRot = this.tailC.yRot = tailYaw;
		this.tailA.xRot = this.tailB.xRot = tailLift;
		this.tailC.xRot = -0.2618F + tailLift;

		// what it wears (a vanilla saddle as the original's; ours and the bridle are TackLayer's)
		boolean saddle = state.mocSaddle;
		for (ModelPart part : new ModelPart[] {this.saddle, this.saddleB, this.saddleC, this.stirrupLeft, this.stirrupLeftIron, this.stirrupRight, this.stirrupRightIron}) {
			part.visible = saddle;
		}
		this.headSaddle.visible = this.bitLeft.visible = this.bitRight.visible = state.mocHeadstall;
		this.reinLeft.visible = this.reinRight.visible = state.mocHeadstall && ridden;
	}

	private static void pose(ModelPart part, float y, float z, float xRot, float yRot) {
		part.y = y;
		part.z = z;
		part.xRot = xRot;
		part.yRot = yRot;
	}

	/**
	 * One leg: the upper leg swung by {@code upper}, the cannon and hoof hung from its end and swung by {@code lower}. The
	 * original places the cannon by the upper leg's angle while walking but by the hind thigh's fixed angle reared; both
	 * are blended by {@code stand}.
	 */
	private static void leg(ModelPart a, ModelPart b, ModelPart c, float upper, float lower, float walkAngle, float standAngle, float stand) {
		a.xRot = upper;
		float hang = Mth.lerp(stand, walkAngle, standAngle);
		b.y = c.y = a.y + Mth.sin(90.0F * DEG + hang) * THIGH;
		b.z = c.z = a.z + Mth.cos(270.0F * DEG + hang) * THIGH;
		b.xRot = c.xRot = lower;
	}
}
