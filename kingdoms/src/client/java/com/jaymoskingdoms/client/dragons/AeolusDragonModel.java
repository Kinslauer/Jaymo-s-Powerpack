package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonLook;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.139) Aeolus, our own model (blocks, as the Kingdoms beasts are), drawn {@link #SCALE} times as big as it is
 * built. Built to read as huge and old: a heavy, broad body with a humped shoulder and a ridge of great spikes, a thick
 * neck with a ragged mane of wisps down it and a beard, a skull crowned with swept-back horns (the dragon's own look
 * adds long, short or brow horns to them), the broadest wings of the five (a long arm with thumb-claws and a longer
 * tip, so the span is over twenty blocks), and a long tail that ends in a flat spade with blades rather than a club.
 * Not ridden, so no tack. Texture 256x256 ({@code tools/gen_aeolus.py}, whose UV table these texOffs are).
 * <p>
 * Posed procedurally from its render state as the others are, but slower and heavier: slow deep wingbeats, the mane
 * and beard stirring in the wind he makes, the head low on the ground and the jaw wide to breathe.
 */
final class AeolusDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How much bigger than built he is drawn (the renderer's scale). */
	static final float SCALE = 2.2F;
	/** Wing beat: depth (radians, a little less than Notus': his wings are heavy), how far gliding cuts it; tip lag and swing. */
	static final float FLAP_DEPTH = 0.7F, GLIDE_CUT = 0.85F, TIP_LAG = 1.1F, TIP_SWING = 0.45F;
	/** Folded wings: the arm's pitch, its sweep back, its droop; the tip folded back along it. */
	static final float FOLD_PITCH = -Mth.HALF_PI + 0.1F, FOLD_SWEEP = -1.45F, FOLD_DROOP = -0.4F, FOLD_TIP = Mth.PI - 0.25F;
	/** The neck's and tail's segment angles on the ground and in flight. */
	static final float[] NECK_GROUND = {-0.7F, 0.15F, 0.2F, 0.15F}, NECK_FLIGHT = {-0.15F, 0.05F, 0.05F, 0.05F}, NECK_CRASH = {0.25F, 0.05F, 0.0F, -0.05F};
	static final float[] TAIL_GROUND = {-0.3F, 0.05F, 0.06F, 0.08F, 0.08F, 0.08F, 0.08F}, TAIL_FLIGHT = {0.05F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F},
		TAIL_CRASH = {-0.15F, 0.0F, 0.0F, 0.02F, 0.02F, 0.02F, 0.02F};
	/** How low it lies crashed and sitting (model pixels). */
	static final float CRASH_DROP = 5.0F, SIT_DROP = 4.0F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[4], tail = new ModelPart[7], spikes = new ModelPart[DragonLook.SPIKES];
	private final ModelPart[] mane = new ModelPart[12], beard = new ModelPart[3];
	private final ModelPart hornL, hornR, shortL, shortR, browL, browR;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR;
	private final float bodyY;

	AeolusDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart parent = this.body;
		for (int i = 0; i < 4; i++) {
			this.neck[i] = parent.getChild("neck" + i);
			for (int k = 0; k < 3; k++) {
				this.mane[i * 3 + k] = this.neck[i].getChild("mane" + i + "_" + k);
			}
			parent = this.neck[i];
		}
		this.head = parent.getChild("head");
		this.jaw = this.head.getChild("jaw");
		for (int i = 0; i < 3; i++) {
			this.beard[i] = this.jaw.getChild("beard" + i);
		}
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.browL = this.head.getChild("brow_horn_l");
		this.browR = this.head.getChild("brow_horn_r");
		parent = this.body;
		for (int i = 0; i < 7; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spikes[i] = this.body.getChild("spike" + i);
		}
		String[] legs = {"left_front", "right_front", "left_hind", "right_hind"};
		for (int i = 0; i < 4; i++) {
			this.upper[i] = this.body.getChild(legs[i] + "_leg");
			this.lower[i] = this.upper[i].getChild(legs[i] + "_shin");
			this.foot[i] = this.lower[i].getChild(legs[i] + "_foot");
		}
		this.wingL = this.body.getChild("left_wing");
		this.wingR = this.body.getChild("right_wing");
		this.tipL = this.wingL.getChild("left_wing_tip");
		this.tipR = this.wingR.getChild("right_wing_tip");
	}

	private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
	}

	private static CubeListBuilder cube(boolean mirror, int u, int v, float x, float y, float z, int w, int h, int d) {
		CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
		return (mirror ? b.mirror() : b).addBox(x, y, z, w, h, d);
	}

	/** A pair of a part (left at +x, right mirrored at -x) with the same pivot and swept angles. */
	private static void pair(PartDefinition parent, String name, int u, int v, float bx, float by, float bz, int w, int h, int d,
		float px, float py, float pz, float rx, float ry, float rz) {
		parent.addOrReplaceChild(name + "_l", cube(false, u, v, bx, by, bz, w, h, d), PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
		parent.addOrReplaceChild(name + "_r", cube(true, u, v, bx, by, bz, w, h, d), PartPose.offsetAndRotation(-px, py, pz, rx, -ry, -rz));
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// (the feet sit on the ground line, y = 24: the body's top is 34 px up)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -10.0F, -9.0F, -19.0F, 20, 18, 38), PartPose.offset(0.0F, -1.0F, 0.0F));
		// the humped shoulder, and the ridge: the five great spikes (the dragon's look may have lost one) and wisps of mane between them
		body.addOrReplaceChild("hump", box(44, 82, -7.0F, -6.0F, -7.0F, 14, 6, 14), PartPose.offset(0.0F, -9.0F, -9.0F));
		float[] spikeZ = {-14.0F, -7.0F, 0.0F, 7.0F, 14.0F};
		float[] spikeY = {-15.0F, -15.0F, -9.0F, -9.0F, -9.0F};
		for (int i = 0; i < spikeZ.length; i++) {
			body.addOrReplaceChild("spike" + i, box(116, 139, -1.5F, -7.0F, -1.5F, 3, 7, 3), PartPose.offset(0.0F, spikeY[i], spikeZ[i]));
		}

		// the neck: four thick segments, each hung from the end of the one before, with three tufts of mane on each
		int[][] neckUv = {{202, 56}, {100, 82}, {182, 82}, {72, 103}};
		float[][] neckBox = {{-6.0F, -6.0F, 12, 12}, {-5.5F, -5.5F, 11, 11}, {-5.0F, -5.0F, 10, 10}, {-5.0F, -4.5F, 10, 9}};
		PartDefinition parent = body;
		for (int i = 0; i < 4; i++) {
			float[] b = neckBox[i];
			parent = parent.addOrReplaceChild("neck" + i, box(neckUv[i][0], neckUv[i][1], b[0], b[1], -9.0F, (int)b[2], (int)b[3], 9),
				i == 0 ? PartPose.offset(0.0F, -3.0F, -18.0F) : PartPose.offset(0.0F, 0.0F, -8.0F));
			float top = b[1];
			float side = b[0] * -1.0F;
			// a tall ragged tuft on top and a shorter one off each side, each a different length and lean
			float lean = 0.15F + 0.1F * (i % 2);
			parent.addOrReplaceChild("mane" + i + "_0", box(82, 139, -1.5F, -8.0F, -1.5F, 3, 8, 3), PartPose.offsetAndRotation(0.0F, top, -4.0F, lean, 0.0F, 0.0F));
			parent.addOrReplaceChild("mane" + i + "_1", cube(false, 242, 139, -1.0F, -5.0F, -1.0F, 2, 5, 2), PartPose.offsetAndRotation(side, top + 2.0F, -3.0F, 0.1F, 0.0F, 0.8F + 0.1F * i));
			parent.addOrReplaceChild("mane" + i + "_2", cube(true, 242, 139, -1.0F, -5.0F, -1.0F, 2, 5, 2), PartPose.offsetAndRotation(-side, top + 2.0F, -5.0F, -0.1F, 0.0F, -0.8F - 0.1F * i));
		}
		PartDefinition head = parent.addOrReplaceChild("head", box(112, 56, -7.0F, -6.0F, -11.0F, 14, 11, 12)
			.texOffs(64, 122).addBox(-5.0F, -3.0F, -21.0F, 10, 6, 10), PartPose.offset(0.0F, 0.0F, -8.0F));
		PartDefinition jaw = head.addOrReplaceChild("jaw", box(0, 139, -4.5F, 0.0F, -10.0F, 9, 2, 10), PartPose.offset(0.0F, 3.0F, -11.0F));
		// the beard: wisps hanging from the chin
		jaw.addOrReplaceChild("beard0", box(128, 139, -1.5F, 0.0F, -1.5F, 3, 6, 3), PartPose.offsetAndRotation(0.0F, 2.0F, -8.0F, 0.15F, 0.0F, 0.0F));
		jaw.addOrReplaceChild("beard1", box(128, 139, -1.5F, 0.0F, -1.5F, 3, 6, 3), PartPose.offsetAndRotation(2.5F, 2.0F, -4.0F, 0.1F, 0.0F, -0.2F));
		jaw.addOrReplaceChild("beard2", box(128, 139, -1.5F, 0.0F, -1.5F, 3, 6, 3), PartPose.offsetAndRotation(-2.5F, 2.0F, -4.0F, 0.1F, 0.0F, 0.2F));
		// the look's horns (long, short, brow) ...
		pair(head, "horn", 30, 122, -1.5F, -1.5F, 0.0F, 3, 3, 14, 4.5F, -5.5F, -3.0F, 0.45F, 0.15F, 0.0F);
		pair(head, "short_horn", 38, 139, -1.5F, -1.5F, 0.0F, 3, 3, 8, 4.5F, -5.5F, -3.0F, 0.6F, 0.2F, 0.0F);
		pair(head, "brow_horn", 140, 139, -1.0F, -1.0F, 0.0F, 2, 2, 7, 6.0F, -4.0F, -8.0F, 0.3F, 0.5F, 0.0F);
		// ... and the crown every Aeolus wears: two swept-back pairs over the skull and a pair off the cheeks
		pair(head, "crown_a", 60, 139, -1.0F, -1.0F, 0.0F, 2, 2, 9, 2.5F, -6.0F, -1.0F, 0.95F, 0.3F, 0.0F);
		pair(head, "crown_b", 60, 139, -1.0F, -1.0F, 0.0F, 2, 2, 9, 5.5F, -5.0F, 0.0F, 0.7F, 0.75F, 0.0F);
		pair(head, "cheek", 226, 139, -1.0F, -1.0F, 0.0F, 2, 2, 6, 7.0F, 1.0F, -4.0F, 0.1F, 0.9F, 0.0F);

		// the tail: seven segments narrowing to a flat spade with blades
		int[][] tailUv = {{0, 82}, {140, 82}, {0, 103}, {110, 103}, {148, 103}, {104, 122}, {138, 122}};
		int[][] tailSize = {{12, 11}, {11, 10}, {10, 9}, {9, 8}, {8, 7}, {7, 6}, {6, 5}};
		parent = body;
		for (int i = 0; i < 7; i++) {
			int w = tailSize[i][0], h = tailSize[i][1];
			CubeListBuilder seg = box(tailUv[i][0], tailUv[i][1], -w / 2.0F, -h / 2.0F, 0.0F, w, h, 10);
			if (i < 5) {
				seg = seg.texOffs(82, 151).addBox(-1.0F, -h / 2.0F - 3.0F, 4.0F, 2, 3, 2);
			}
			parent = parent.addOrReplaceChild("tail" + i, seg, i == 0 ? PartPose.offset(0.0F, -3.0F, 18.0F) : PartPose.offset(0.0F, 0.0F, 9.0F));
		}
		PartDefinition spade = parent.addOrReplaceChild("spade", box(184, 103, -8.0F, -0.5F, 0.0F, 16, 1, 16), PartPose.offset(0.0F, 0.0F, 9.0F));
		spade.addOrReplaceChild("blade_c", box(94, 139, -1.5F, -1.0F, 0.0F, 3, 2, 8), PartPose.offset(0.0F, 0.0F, 15.0F));
		pair(spade, "blade", 94, 139, -1.5F, -1.0F, 0.0F, 3, 2, 8, 7.0F, 0.0F, 12.0F, 0.0F, 0.5F, 0.0F);

		// legs: upper, shin, foot (heavier than Notus')
		leg(body, "left_front", false, 8.0F, 6.0F, -12.0F, true);
		leg(body, "right_front", true, -8.0F, 6.0F, -12.0F, true);
		leg(body, "left_hind", false, 8.0F, 4.0F, 12.0F, false);
		leg(body, "right_hind", true, -8.0F, 4.0F, 12.0F, false);

		// wings: an arm with thumb-claws and its membrane, and the longer tip with its own
		PartDefinition left = body.addOrReplaceChild("left_wing", box(158, 139, 0.0F, -2.0F, -2.0F, 30, 4, 4)
			.texOffs(0, 56).addBox(0.0F, 0.0F, 2.0F, 30, 0, 26), PartPose.offset(9.0F, -7.0F, -9.0F));
		left.addOrReplaceChild("left_thumb0", box(116, 139, -1.5F, -7.0F, -1.5F, 3, 7, 3), PartPose.offsetAndRotation(8.0F, -2.0F, -1.0F, -0.5F, 0.0F, 0.0F));
		left.addOrReplaceChild("left_thumb1", box(116, 139, -1.5F, -7.0F, -1.5F, 3, 7, 3), PartPose.offsetAndRotation(20.0F, -2.0F, -1.0F, -0.5F, 0.0F, 0.0F));
		left.addOrReplaceChild("left_wing_tip", box(0, 151, 0.0F, -1.5F, -1.5F, 38, 3, 3)
			.texOffs(116, 0).addBox(0.0F, 0.0F, 1.5F, 38, 0, 30), PartPose.offset(30.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing", cube(true, 158, 139, -30.0F, -2.0F, -2.0F, 30, 4, 4)
			.texOffs(0, 56).addBox(-30.0F, 0.0F, 2.0F, 30, 0, 26), PartPose.offset(-9.0F, -7.0F, -9.0F));
		right.addOrReplaceChild("right_thumb0", cube(true, 116, 139, -1.5F, -7.0F, -1.5F, 3, 7, 3), PartPose.offsetAndRotation(-8.0F, -2.0F, -1.0F, -0.5F, 0.0F, 0.0F));
		right.addOrReplaceChild("right_thumb1", cube(true, 116, 139, -1.5F, -7.0F, -1.5F, 3, 7, 3), PartPose.offsetAndRotation(-20.0F, -2.0F, -1.0F, -0.5F, 0.0F, 0.0F));
		right.addOrReplaceChild("right_wing_tip", cube(true, 0, 151, -38.0F, -1.5F, -1.5F, 38, 3, 3)
			.texOffs(116, 0).addBox(-38.0F, 0.0F, 1.5F, 38, 0, 30), PartPose.offset(-30.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static void leg(PartDefinition body, String name, boolean mirror, float x, float y, float z, boolean front) {
		CubeListBuilder up = front ? cube(mirror, 40, 103, -4.0F, -1.0F, -4.0F, 8, 11, 8) : cube(mirror, 164, 56, -4.5F, -2.0F, -5.0F, 9, 12, 10);
		PartDefinition leg = body.addOrReplaceChild(name + "_leg", up, PartPose.offset(x, y, z));
		CubeListBuilder shin = front ? cube(mirror, 170, 122, -3.5F, 0.0F, -3.5F, 7, 7, 7) : cube(mirror, 0, 122, -3.5F, 0.0F, -4.0F, 7, 9, 8);
		PartDefinition lower = leg.addOrReplaceChild(name + "_shin", shin, front ? PartPose.offset(0.0F, 10.0F, 0.0F) : PartPose.offset(0.0F, 10.0F, 1.0F));
		lower.addOrReplaceChild(name + "_foot", cube(mirror, 198, 122, -4.5F, 0.0F, -7.0F, 9, 2, 11), PartPose.offset(0.0F, front ? 7.0F : 9.0F, 0.0F));
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = s.fly, crash = s.crash * (1.0F - fly), sit = s.sit * (1.0F - crash) * (1.0F - fly);
		float breath = s.breath, land = s.land * (1.0F - fly), glide = s.glide * fly;
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - fly) * (1.0F - crash);
		float step = s.walkAnimationPos * 0.6662F;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -40.0F, 60.0F) * Mth.DEG_TO_RAD;
		float beat = s.flap;

		// the body: lower crashed or sitting, bobbing against the wing stroke, pitched with its climb
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP - Mth.sin(beat) * 0.9F * fly * (1.0F - glide) + Mth.sin(t * 0.07F) * 0.3F;
		this.body.xRot = s.pitch * fly;
		this.body.zRot = Mth.sin(step * 0.5F) * 0.025F * walk;

		// the neck: rises on the ground (a heavy, low-hung head), streams out in flight, lowers to breathe
		for (int i = 0; i < 4; i++) {
			float x = Mth.lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i]);
			x = Mth.lerp(crash, x, NECK_CRASH[i]);
			x += breath * (i == 0 ? 0.25F : i == 3 ? -0.1F : 0.05F) * (1.0F - fly);
			x += Mth.sin(t * 0.06F - i * 0.7F) * 0.035F * (1.0F + fly);
			this.neck[i].xRot = x;
			this.neck[i].yRot = headYaw / 5.0F + Mth.sin(t * 0.04F - i * 0.6F) * 0.03F;
		}
		this.head.xRot = Mth.lerp(fly, 0.4F, 0.1F) + Mth.lerp(crash, headPitch * 0.6F, 0.15F);
		this.head.yRot = headYaw / 5.0F;
		this.jaw.xRot = 0.05F + breath * 0.75F + Mth.sin(t * 0.6F) * 0.05F * breath;
		// the mane and beard stir in the wind he makes, more in flight and breathing
		float stir = 0.05F + 0.1F * fly + 0.1F * breath;
		for (int i = 0; i < this.mane.length; i++) {
			this.mane[i].zRot = baseLean(i) + Mth.sin(t * 0.17F + i * 1.3F) * stir;
		}
		for (int i = 0; i < this.beard.length; i++) {
			this.beard[i].xRot = 0.15F + Mth.sin(t * 0.13F + i) * stir - fly * 0.4F;
		}

		// the tail: a chain that droops on the ground, streams in flight, and waves from root to spade
		float sway = 0.05F + walk * 0.08F;
		for (int i = 0; i < 7; i++) {
			float x = Mth.lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]);
			x = Mth.lerp(crash, x, TAIL_CRASH[i]);
			this.tail[i].xRot = x + Mth.sin(t * 0.05F - i * 0.5F) * 0.02F * fly;
			this.tail[i].yRot = Mth.sin(t * 0.06F - i * 0.6F) * Mth.lerp(fly, sway, 0.04F);
		}

		// legs: a slow heavy walk, tucked in flight, reaching to land, splayed crashed, the hind legs folded under sitting
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.5F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.55F * walk;
			float up = swing, low = knee;
			float tuckUp = front ? -0.45F : 1.1F, tuckLow = front ? 1.5F : 0.6F;
			up = Mth.lerp(fly, up, tuckUp + Mth.sin(t * 0.08F + i) * 0.05F);
			low = Mth.lerp(fly, low, tuckLow);
			up = Mth.lerp(land, up, front ? -0.5F : -0.3F);
			if (!front) {
				up = Mth.lerp(sit, up, -0.9F);
				low = Mth.lerp(sit, low, 1.5F);
			}
			this.upper[i].xRot = up;
			this.upper[i].zRot = (left ? -0.55F : 0.55F) * crash;
			this.lower[i].xRot = low;
			// the foot stays flat on the ground; in the air it hangs
			this.foot[i].xRot = Mth.lerp(fly, -(up + low), 0.6F);
		}

		// wings: open (in the air or flaring to land) or folded down the flanks; crashed, half-spread on the ground
		float open = Math.max(fly, land);
		float hover = breath * fly;
		float stroke = Mth.sin(beat) * FLAP_DEPTH * (1.0F - glide * GLIDE_CUT) * (1.0F + hover * 0.3F) * fly + glide * 0.08F;
		float lift = stroke - land * 0.7F;
		float openSweep = -0.1F + hover * 0.3F + land * 0.3F;
		float tipUp = (Mth.sin(beat - TIP_LAG) * TIP_SWING * (1.0F - glide) - 0.05F) * fly;
		float armX = Mth.lerp(open, FOLD_PITCH, 0.0F), armY = Mth.lerp(open, FOLD_SWEEP, openSweep), armZ = Mth.lerp(open, FOLD_DROOP, -lift);
		float tipZ = Mth.lerp(open, FOLD_TIP, -tipUp);
		armX = Mth.lerp(crash, armX, -0.15F);
		armY = Mth.lerp(crash, armY, -0.25F);
		armZ = Mth.lerp(crash, armZ, 0.3F);
		tipZ = Mth.lerp(crash, tipZ, -0.2F);
		// at rest the folded wings breathe a little
		armZ += Mth.sin(t * 0.08F) * 0.02F * (1.0F - open);
		this.wingL.xRot = armX;
		this.wingL.yRot = armY;
		this.wingL.zRot = armZ;
		this.tipL.zRot = tipZ;
		this.wingR.xRot = armX;
		this.wingR.yRot = -armY;
		this.wingR.zRot = -armZ;
		this.tipR.zRot = -tipZ;

		// its look: horns, a lost spike (no tack: he is never ridden)
		DragonLook look = s.look;
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		this.hornL.visible = this.hornR.visible = !shortHorns;
		this.shortL.visible = this.shortR.visible = shortHorns;
		this.browL.visible = this.browR.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < this.spikes.length; i++) {
			this.spikes[i].visible = look.spike(i);
		}
	}

	/** The resting lean of tuft {@code i} (0 top, 1 and 2 the sides, repeating down the neck): top ones stand, side ones lie outward. */
	private static float baseLean(int i) {
		int k = i % 3, seg = i / 3;
		return k == 0 ? 0.0F : (k == 1 ? 0.8F + 0.1F * seg : -0.8F - 0.1F * seg);
	}
}
