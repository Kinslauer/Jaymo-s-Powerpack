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
 * (0.7.137) Notus, our own model (blocks, as the Kingdoms beasts are). A heavy body with spikes down its back; a neck
 * and a tail as chains of segments that wave; a horned head whose jaw drops to breathe; a clubbed tail; four legs with
 * knees and feet; two-segment wings with membranes (zero-thick, scalloped by the texture's alpha). Its riders' saddle,
 * girth and pillion pad show when saddled. Texture 256x256 ({@code tools/gen_dragons.py}, whose UV table these
 * texOffs are).
 * <p>
 * Posed procedurally from its render state: on the ground the neck rises, the wings fold down along its flanks (each
 * tip folded back along its arm) and the legs walk; in the air the wings beat (hard climbing or hovering, barely at all
 * gliding fast, the tips lagging the stroke), the legs tuck, neck and tail stream out and the body pitches with its
 * climb; hovering to breathe it beats deep and slow with its wings forward and its jaw wide; landing, the wings flare.
 * Crashed, it lies low with its wings half-spread on the ground; sitting, it folds its hind legs under.
 */
final class NotusDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** Wing beat: depth (radians), how far gliding cuts it; tip lag (radians behind the stroke) and its swing. */
	static final float FLAP_DEPTH = 0.85F, GLIDE_CUT = 0.85F, TIP_LAG = 1.2F, TIP_SWING = 0.5F;
	/** Folded wings: the arm's pitch (membrane hanging), its sweep back, its droop (outward, so the membrane clears its flank); the tip folded back along it. */
	static final float FOLD_PITCH = -Mth.HALF_PI + 0.1F, FOLD_SWEEP = -1.45F, FOLD_DROOP = -0.3F, FOLD_TIP = Mth.PI - 0.25F;
	/** The neck's and tail's segment angles on the ground and in flight. */
	static final float[] NECK_GROUND = {-0.75F, 0.15F, 0.2F, 0.15F}, NECK_FLIGHT = {-0.15F, 0.05F, 0.05F, 0.05F},
		NECK_CRASH = {0.25F, 0.05F, 0.0F, -0.05F};
	static final float[] TAIL_GROUND = {-0.35F, 0.05F, 0.08F, 0.1F, 0.1F, 0.1F}, TAIL_FLIGHT = {0.06F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F},
		TAIL_CRASH = {-0.15F, 0.0F, 0.0F, 0.02F, 0.02F, 0.02F};
	/** How low it lies crashed and sitting (model pixels). */
	static final float CRASH_DROP = 5.0F, SIT_DROP = 4.0F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[4], tail = new ModelPart[6], spikes = new ModelPart[DragonLook.SPIKES];
	private final ModelPart hornL, hornR, shortL, shortR, browL, browR;
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR;
	private final float bodyY;

	NotusDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart parent = this.body;
		for (int i = 0; i < 4; i++) {
			this.neck[i] = parent.getChild("neck" + i);
			parent = this.neck[i];
		}
		this.head = parent.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.browL = this.head.getChild("brow_horn_l");
		this.browR = this.head.getChild("brow_horn_r");
		parent = this.body;
		for (int i = 0; i < 6; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spikes[i] = this.body.getChild("spike" + i);
		}
		this.saddle = this.body.getChild("saddle");
		this.strap = this.body.getChild("strap");
		this.pad = this.body.getChild("pad");
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

	private static CubeListBuilder mirrored(int u, int v, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, w, h, d);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -9.0F, -8.0F, -18.0F, 18, 16, 36), PartPose.offset(0.0F, 4.0F, 0.0F));
		float[] spikeZ = {-14.0F, -7.0F, 0.0F, 7.0F, 14.0F};
		for (int i = 0; i < spikeZ.length; i++) {
			body.addOrReplaceChild("spike" + i, box(176, 108, -1.0F, -5.0F, -1.5F, 2, 5, 3), PartPose.offset(0.0F, -8.0F, spikeZ[i]));
		}
		// tack
		body.addOrReplaceChild("saddle", box(0, 92, -7.0F, -2.0F, -7.0F, 14, 2, 14)
			.texOffs(66, 121).addBox(-2.0F, -5.0F, -7.0F, 4, 3, 2)
			.texOffs(78, 121).addBox(-5.0F, -5.0F, 5.0F, 10, 3, 2), PartPose.offset(0.0F, -8.0F, -4.0F));
		body.addOrReplaceChild("strap", box(142, 52, -9.5F, -8.5F, -1.5F, 19, 17, 3), PartPose.offset(0.0F, 0.0F, -4.0F));
		body.addOrReplaceChild("pad", box(52, 108, -6.0F, -2.0F, -5.0F, 12, 2, 10), PartPose.offset(0.0F, -8.0F, 9.0F));

		// the neck: four segments, each hung from the end of the one before, a small spike on each
		int[][] neckUv = {{186, 52}, {68, 74}, {138, 74}, {56, 92}};
		float[][] neckBox = {{-5.0F, -5.0F, 10, 10}, {-4.5F, -4.5F, 9, 9}, {-4.0F, -4.0F, 8, 8}, {-4.0F, -3.5F, 8, 7}};
		PartDefinition parent = body;
		for (int i = 0; i < 4; i++) {
			float[] b = neckBox[i];
			parent = parent.addOrReplaceChild("neck" + i, box(neckUv[i][0], neckUv[i][1], b[0], b[1], -8.0F, (int)b[2], (int)b[3], 8)
					.texOffs(58, 121).addBox(-1.0F, b[1] - 3.0F, -5.0F, 2, 3, 2),
				i == 0 ? PartPose.offset(0.0F, -3.0F, -17.0F) : PartPose.offset(0.0F, 0.0F, -7.0F));
		}
		PartDefinition head = parent.addOrReplaceChild("head", box(96, 52, -6.0F, -5.0F, -10.0F, 12, 9, 11)
			.texOffs(144, 92).addBox(-4.0F, -2.0F, -18.0F, 8, 5, 9), PartPose.offset(0.0F, 0.0F, -7.0F));
		head.addOrReplaceChild("jaw", box(96, 108, -3.5F, 0.0F, -9.0F, 7, 2, 9), PartPose.offset(0.0F, 3.0F, -9.0F));
		head.addOrReplaceChild("horn_l", box(28, 108, -1.0F, -1.0F, 0.0F, 2, 2, 10), PartPose.offsetAndRotation(3.5F, -4.0F, -3.0F, 0.45F, 0.15F, 0.0F));
		head.addOrReplaceChild("horn_r", mirrored(28, 108, -1.0F, -1.0F, 0.0F, 2, 2, 10), PartPose.offsetAndRotation(-3.5F, -4.0F, -3.0F, 0.45F, -0.15F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(186, 108, -1.0F, -1.0F, 0.0F, 2, 2, 6), PartPose.offsetAndRotation(3.5F, -4.0F, -3.0F, 0.6F, 0.2F, 0.0F));
		head.addOrReplaceChild("short_horn_r", mirrored(186, 108, -1.0F, -1.0F, 0.0F, 2, 2, 6),
			PartPose.offsetAndRotation(-3.5F, -4.0F, -3.0F, 0.6F, -0.2F, 0.0F));
		head.addOrReplaceChild("brow_horn_l", box(202, 108, -1.0F, -1.0F, 0.0F, 2, 2, 5), PartPose.offsetAndRotation(5.0F, -3.0F, -7.0F, 0.3F, 0.5F, 0.0F));
		head.addOrReplaceChild("brow_horn_r", mirrored(202, 108, -1.0F, -1.0F, 0.0F, 2, 2, 5),
			PartPose.offsetAndRotation(-5.0F, -3.0F, -7.0F, 0.3F, -0.5F, 0.0F));

		// the tail: six segments narrowing to the club, which carries three knobs
		int[][] tailUv = {{0, 74}, {102, 74}, {170, 74}, {88, 92}, {178, 92}, {0, 108}};
		int[][] tailSize = {{10, 9}, {9, 8}, {8, 7}, {7, 6}, {6, 5}, {5, 4}};
		parent = body;
		for (int i = 0; i < 6; i++) {
			int w = tailSize[i][0], h = tailSize[i][1];
			CubeListBuilder seg = box(tailUv[i][0], tailUv[i][1], -w / 2.0F, -h / 2.0F, 0.0F, w, h, 9);
			if (i < 3) {
				seg = seg.texOffs(58, 121).addBox(-1.0F, -h / 2.0F - 3.0F, 3.0F, 2, 3, 2);
			}
			parent = parent.addOrReplaceChild("tail" + i, seg, i == 0 ? PartPose.offset(0.0F, -4.0F, 17.0F) : PartPose.offset(0.0F, 0.0F, 8.0F));
		}
		parent.addOrReplaceChild("club", box(204, 74, -4.5F, -3.5F, 0.0F, 9, 7, 9)
			.texOffs(216, 108).addBox(4.0F, -1.5F, 3.0F, 3, 3, 3)
			.texOffs(216, 108).addBox(-7.0F, -1.5F, 3.0F, 3, 3, 3)
			.texOffs(216, 108).addBox(-1.5F, -5.5F, 3.0F, 3, 3, 3), PartPose.offset(0.0F, 0.0F, 8.0F));

		// legs: upper, shin, foot
		leg(body, "left_front", false, 7.0F, 4.0F, -12.0F, true);
		leg(body, "right_front", true, -7.0F, 4.0F, -12.0F, true);
		leg(body, "left_hind", false, 7.0F, 2.0F, 12.0F, false);
		leg(body, "right_hind", true, -7.0F, 2.0F, 12.0F, false);

		// wings: an arm with its membrane, and the tip with its own
		PartDefinition left = body.addOrReplaceChild("left_wing", box(0, 121, 0.0F, -1.5F, -1.5F, 26, 3, 3)
			.texOffs(0, 52).addBox(0.0F, 0.0F, 1.5F, 26, 0, 22), PartPose.offset(8.0F, -6.0F, -9.0F));
		left.addOrReplaceChild("left_wing_tip", box(102, 121, 0.0F, -1.0F, -1.0F, 30, 2, 2)
			.texOffs(108, 0).addBox(0.0F, 0.0F, 1.0F, 30, 0, 24), PartPose.offset(25.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing", mirrored(0, 121, -26.0F, -1.5F, -1.5F, 26, 3, 3)
			.texOffs(0, 52).addBox(-26.0F, 0.0F, 1.5F, 26, 0, 22), PartPose.offset(-8.0F, -6.0F, -9.0F));
		right.addOrReplaceChild("right_wing_tip", mirrored(102, 121, -30.0F, -1.0F, -1.0F, 30, 2, 2)
			.texOffs(108, 0).addBox(-30.0F, 0.0F, 1.0F, 30, 0, 24), PartPose.offset(-25.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static void leg(PartDefinition body, String name, boolean mirror, float x, float y, float z, boolean front) {
		CubeListBuilder up = front ? cube(mirror, 120, 92, -3.0F, -1.0F, -3.0F, 6, 9, 6) : cube(mirror, 38, 74, -3.5F, -2.0F, -4.0F, 7, 10, 8);
		PartDefinition leg = body.addOrReplaceChild(name + "_leg", up, PartPose.offset(x, y, z));
		CubeListBuilder shin = front ? cube(mirror, 128, 108, -2.5F, 0.0F, -2.5F, 5, 6, 5) : cube(mirror, 208, 92, -2.5F, 0.0F, -3.0F, 5, 8, 6);
		PartDefinition lower = leg.addOrReplaceChild(name + "_shin", shin, front ? PartPose.offset(0.0F, 8.0F, 0.0F) : PartPose.offset(0.0F, 8.0F, 1.0F));
		lower.addOrReplaceChild(name + "_foot", cube(mirror, 148, 108, -3.0F, 0.0F, -5.0F, 6, 2, 8), PartPose.offset(0.0F, front ? 6.0F : 8.0F, 0.0F));
	}

	private static CubeListBuilder cube(boolean mirror, int u, int v, float x, float y, float z, int w, int h, int d) {
		return mirror ? mirrored(u, v, x, y, z, w, h, d) : box(u, v, x, y, z, w, h, d);
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
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP - Mth.sin(beat) * 0.8F * fly * (1.0F - glide) + Mth.sin(t * 0.1F) * 0.25F;
		this.body.xRot = s.pitch * fly;
		this.body.zRot = Mth.sin(step * 0.5F) * 0.03F * walk;

		// the neck: a chain that rises on the ground and streams out in flight, rippling; it lowers to breathe
		for (int i = 0; i < 4; i++) {
			float x = Mth.lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i]);
			x = Mth.lerp(crash, x, NECK_CRASH[i]);
			x += breath * (i == 0 ? 0.25F : i == 3 ? -0.1F : 0.05F) * (1.0F - fly);
			x += Mth.sin(t * 0.08F - i * 0.7F) * 0.04F * (1.0F + fly);
			this.neck[i].xRot = x;
			this.neck[i].yRot = headYaw / 5.0F + Mth.sin(t * 0.05F - i * 0.6F) * 0.03F;
		}
		this.head.xRot = Mth.lerp(fly, 0.35F, 0.1F) + Mth.lerp(crash, headPitch * 0.6F, 0.15F);
		this.head.yRot = headYaw / 5.0F;
		this.jaw.xRot = 0.05F + breath * 0.7F + Mth.sin(t * 0.6F) * 0.05F * breath;

		// the tail: a chain that droops and curls on the ground, streams in flight, and waves from root to club
		float sway = 0.06F + walk * 0.1F;
		for (int i = 0; i < 6; i++) {
			float x = Mth.lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]);
			x = Mth.lerp(crash, x, TAIL_CRASH[i]);
			this.tail[i].xRot = x + Mth.sin(t * 0.06F - i * 0.5F) * 0.02F * fly;
			this.tail[i].yRot = Mth.sin(t * 0.07F - i * 0.6F) * Mth.lerp(fly, sway, 0.04F);
		}

		// legs: walking on the ground (heavy, the knees bending as each leg comes forward), tucked in flight, reaching to
		// land, splayed crashed, the hind legs folded under sitting
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.55F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.6F * walk;
			float up = swing, low = knee;
			float tuckUp = front ? -0.45F : 1.1F, tuckLow = front ? 1.5F : 0.6F;
			up = Mth.lerp(fly, up, tuckUp + Mth.sin(t * 0.1F + i) * 0.05F);
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
		float openSweep = -0.12F + hover * 0.3F + land * 0.3F;
		float tipUp = (Mth.sin(beat - TIP_LAG) * TIP_SWING * (1.0F - glide) - 0.05F) * fly;
		float armX = Mth.lerp(open, FOLD_PITCH, 0.0F), armY = Mth.lerp(open, FOLD_SWEEP, openSweep), armZ = Mth.lerp(open, FOLD_DROOP, -lift);
		float tipZ = Mth.lerp(open, FOLD_TIP, -tipUp);
		armX = Mth.lerp(crash, armX, -0.15F);
		armY = Mth.lerp(crash, armY, -0.25F);
		armZ = Mth.lerp(crash, armZ, 0.3F);
		tipZ = Mth.lerp(crash, tipZ, -0.2F);
		// at rest the folded wings breathe a little
		armZ += Mth.sin(t * 0.1F) * 0.02F * (1.0F - open);
		this.wingL.xRot = armX;
		this.wingL.yRot = armY;
		this.wingL.zRot = armZ;
		this.tipL.zRot = tipZ;
		this.wingR.xRot = armX;
		this.wingR.yRot = -armY;
		this.wingR.zRot = -armZ;
		this.tipR.zRot = -tipZ;

		// its look: horns, a lost spike; its tack
		DragonLook look = s.look;
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		this.hornL.visible = this.hornR.visible = !shortHorns;
		this.shortL.visible = this.shortR.visible = shortHorns;
		this.browL.visible = this.browR.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < this.spikes.length; i++) {
			// the saddle and the pillion pad sit where the middle three spikes would
			this.spikes[i].visible = look.spike(i) && !(s.saddled && i >= 1 && i <= 3);
		}
		this.saddle.visible = this.strap.visible = this.pad.visible = s.saddled;
	}
}
