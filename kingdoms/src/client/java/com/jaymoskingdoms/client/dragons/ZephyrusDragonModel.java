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
 * (0.7.137) Zephyrus, our own model (blocks, as the Kingdoms beasts are): a low, broad, amphibious body, nothing like
 * Notus's. A wide flattish body with a row of fin-like back spikes; a short thick neck of three segments; a broad flat
 * head with eyes on bumps, frilled gills at the cheeks and short, curved, backswept horns; a tail flattened sideways
 * with a fin along it, ending in a paddle; short sprawled legs with webbed feet; shorter, broader wings with ragged
 * webbed membranes. Its riders' saddle, girth and pillion pad show when saddled. Texture 256x256
 * ({@code tools/gen_zephyrus.py}, whose UV table these texOffs are).
 * <p>
 * Posed procedurally from its render state: on the ground it waddles with its legs sprawled and its wings folded down
 * its flanks; in the air the wings beat (hard climbing or hovering, little gliding), the webbed feet trail and the tail
 * streams; hovering to breathe it beats slow with its jaw wide; landing, the wings flare. In water ({@code swim}) it
 * lies low with its legs paddling, its tail sculling side to side and its wings folded tight. Crashed, it lies flat;
 * sitting, it squats.
 */
final class ZephyrusDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** Wing beat: depth (radians), how far gliding cuts it; tip lag (radians behind the stroke) and its swing. */
	static final float FLAP_DEPTH = 0.8F, GLIDE_CUT = 0.8F, TIP_LAG = 1.1F, TIP_SWING = 0.5F;
	/**
	 * Folded wings: the arm's pitch (the membrane hanging slanted so it clears the ground), its sweep back, its droop
	 * (outward, so the membrane clears its flank); the tip folded back along it. Swimming it hugs tighter.
	 */
	static final float FOLD_PITCH = -1.0F, FOLD_SWEEP = -1.45F, FOLD_DROOP = -0.25F, FOLD_TIP = Mth.PI - 0.25F;
	static final float TIGHT_SWEEP = -1.55F, TIGHT_DROOP = -0.05F;
	/** The neck's segment angles on the ground, in flight, crashed and swimming; the tail's (four segments and the paddle). */
	static final float[] NECK_GROUND = {-0.35F, 0.1F, 0.15F}, NECK_FLIGHT = {-0.1F, 0.02F, 0.03F}, NECK_CRASH = {0.1F, 0.0F, 0.0F},
		NECK_SWIM = {-0.04F, 0.06F, 0.1F};
	static final float[] TAIL_GROUND = {-0.1F, 0.05F, 0.06F, 0.06F, 0.05F}, TAIL_FLIGHT = {0.03F, 0.02F, 0.02F, 0.02F, 0.02F};
	/** How low it lies crashed, sitting and swimming (model pixels); how far its legs sprawl standing. */
	static final float CRASH_DROP = 3.0F, SIT_DROP = 3.0F, SWIM_DROP = 2.5F, SPLAY = 0.3F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[3], tail = new ModelPart[5], spikes = new ModelPart[DragonLook.SPIKES];
	private final ModelPart hornL, hornR, shortL, shortR, browL, browR;
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR;
	private final float bodyY;

	ZephyrusDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart parent = this.body;
		for (int i = 0; i < 3; i++) {
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
		for (int i = 0; i < 5; i++) {
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
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -11.0F, -5.0F, -17.0F, 22, 10, 34), PartPose.offset(0.0F, 12.0F, 0.0F));
		// fin-like back spikes: thin upright fins with rays
		float[] spikeZ = {-12.0F, -6.0F, 0.0F, 6.0F, 12.0F};
		for (int i = 0; i < spikeZ.length; i++) {
			body.addOrReplaceChild("spike" + i, box(32, 82, -0.5F, -7.0F, -3.0F, 1, 7, 6), PartPose.offset(0.0F, -5.0F, spikeZ[i]));
		}
		// tack
		body.addOrReplaceChild("saddle", box(24, 66, -8.0F, -2.0F, -7.0F, 16, 2, 14)
			.texOffs(182, 95).addBox(-2.0F, -5.0F, -7.0F, 4, 3, 2)
			.texOffs(194, 95).addBox(-6.0F, -5.0F, 5.0F, 12, 3, 2), PartPose.offset(0.0F, -5.0F, -4.0F));
		body.addOrReplaceChild("strap", box(84, 66, -12.5F, -6.5F, -1.5F, 25, 13, 3), PartPose.offset(0.0F, 0.0F, -4.0F));
		body.addOrReplaceChild("pad", box(46, 82, -7.0F, -2.0F, -5.0F, 14, 2, 10), PartPose.offset(0.0F, -5.0F, 9.0F));

		// the neck: three short thick segments, each hung from the end of the one before
		int[][] neckUv = {{140, 66}, {176, 66}, {0, 82}};
		float[][] neckBox = {{12, 8}, {11, 8}, {10, 7}};
		PartDefinition parent = body;
		for (int i = 0; i < 3; i++) {
			float w = neckBox[i][0], h = neckBox[i][1];
			parent = parent.addOrReplaceChild("neck" + i, box(neckUv[i][0], neckUv[i][1], -w / 2.0F, -h / 2.0F, -6.0F, w, h, 6),
				i == 0 ? PartPose.offset(0.0F, -1.0F, -15.0F) : PartPose.offset(0.0F, 0.0F, -5.0F));
		}
		// the head: broad and flat, a short snout, eyes on bumps, frilled gills at the cheeks
		PartDefinition head = parent.addOrReplaceChild("head", box(166, 44, -8.0F, -3.0F, -10.0F, 16, 6, 11)
			.texOffs(168, 82).addBox(-5.0F, -1.0F, -16.0F, 10, 4, 6), PartPose.offset(0.0F, 0.0F, -5.0F));
		head.addOrReplaceChild("jaw", box(0, 95, -4.0F, 0.0F, -6.0F, 8, 2, 6), PartPose.offset(0.0F, 3.0F, -10.0F));
		head.addOrReplaceChild("eye_l", box(160, 95, -1.5F, -2.0F, -1.5F, 3, 2, 3), PartPose.offset(5.5F, -3.0F, -6.0F));
		head.addOrReplaceChild("eye_r", box(160, 95, -1.5F, -2.0F, -1.5F, 3, 2, 3), PartPose.offset(-5.5F, -3.0F, -6.0F));
		head.addOrReplaceChild("frill_l0", box(94, 82, -0.5F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(8.0F, -1.0F, -4.0F, 0.0F, 0.5F, 0.0F));
		head.addOrReplaceChild("frill_l1", box(94, 82, -0.5F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(8.0F, 1.5F, -3.0F, 0.0F, 0.85F, 0.3F));
		head.addOrReplaceChild("frill_r0", box(94, 82, -0.5F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(-8.0F, -1.0F, -4.0F, 0.0F, -0.5F, 0.0F));
		head.addOrReplaceChild("frill_r1", box(94, 82, -0.5F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(-8.0F, 1.5F, -3.0F, 0.0F, -0.85F, -0.3F));
		// horns: backswept and curved (a base and a tip bent further up); long, short, and the twin's brow pair
		horn(head, "horn_l", 1.0F, 48, 95, 80, 95, 6, 5);
		horn(head, "horn_r", -1.0F, 48, 95, 80, 95, 6, 5);
		horn(head, "short_horn_l", 1.0F, 136, 95, 172, 95, 4, 3);
		horn(head, "short_horn_r", -1.0F, 136, 95, 172, 95, 4, 3);
		head.addOrReplaceChild("brow_horn_l", box(148, 95, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(7.0F, -2.5F, -6.0F, 0.15F, 0.9F, 0.0F));
		head.addOrReplaceChild("brow_horn_r", box(148, 95, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(-7.0F, -2.5F, -6.0F, 0.15F, -0.9F, 0.0F));

		// the tail: flattened sideways (tall and thin) with a fin along its top, ending in a paddle
		int[][] tailUv = {{108, 44}, {138, 44}, {220, 44}, {0, 66}};
		float[][] tailSize = {{6, 10}, {5, 9}, {4, 8}, {3, 7}};
		parent = body;
		for (int i = 0; i < 4; i++) {
			float w = tailSize[i][0], h = tailSize[i][1];
			parent = parent.addOrReplaceChild("tail" + i, box(tailUv[i][0], tailUv[i][1], -w / 2.0F, -h / 2.0F, 0.0F, w, h, 9)
					.texOffs(210, 66).addBox(-0.5F, -h / 2.0F - 5.0F, 0.0F, 1, 5, 9),
				i == 0 ? PartPose.offset(0.0F, -1.0F, 16.0F) : PartPose.offset(0.0F, 0.0F, 8.0F));
		}
		parent.addOrReplaceChild("tail4", box(84, 44, -1.0F, -6.0F, 0.0F, 2, 12, 10), PartPose.offset(0.0F, 0.0F, 8.0F));

		// legs: short and sprawled, with webbed feet
		leg(body, "left_front", false, 9.0F, 3.0F, -10.0F, true);
		leg(body, "right_front", true, -9.0F, 3.0F, -10.0F, true);
		leg(body, "left_hind", false, 9.0F, 3.0F, 11.0F, false);
		leg(body, "right_hind", true, -9.0F, 3.0F, 11.0F, false);

		// wings: shorter and broader than Notus's, an arm with its ragged membrane, and the tip with its own
		PartDefinition left = body.addOrReplaceChild("left_wing", box(94, 95, 0.0F, -1.5F, -1.5F, 18, 3, 3)
			.texOffs(112, 0).addBox(0.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(10.0F, -4.0F, -9.0F));
		left.addOrReplaceChild("left_wing_tip", box(0, 103, 0.0F, -1.0F, -1.0F, 20, 2, 2)
			.texOffs(0, 44).addBox(0.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(17.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing", mirrored(94, 95, -18.0F, -1.5F, -1.5F, 18, 3, 3)
			.texOffs(112, 0).addBox(-18.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(-10.0F, -4.0F, -9.0F));
		right.addOrReplaceChild("right_wing_tip", mirrored(0, 103, -20.0F, -1.0F, -1.0F, 20, 2, 2)
			.texOffs(0, 44).addBox(-20.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(-17.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	/** A curved horn: a base swept back and up from the head's top, and a tip bent further up. */
	private static void horn(PartDefinition head, String name, float side, int baseU, int baseV, int tipU, int tipV, int base, int tip) {
		PartDefinition h = head.addOrReplaceChild(name, box(baseU, baseV, -1.0F, -1.0F, 0.0F, 2, 2, base),
			PartPose.offsetAndRotation(4.0F * side, -3.0F, -1.0F, 0.35F, 0.3F * side, 0.0F));
		h.addOrReplaceChild(name + "_tip", box(tipU, tipV, -1.0F, -1.0F, 0.0F, 2, 2, tip), PartPose.offsetAndRotation(0.0F, 0.0F, base, 0.5F, 0.0F, 0.0F));
	}

	private static void leg(PartDefinition body, String name, boolean mirror, float x, float y, float z, boolean front) {
		CubeListBuilder up = front ? cube(mirror, 200, 82, -2.5F, -1.0F, -2.5F, 5, 5, 5) : cube(mirror, 108, 82, -3.0F, -1.0F, -3.0F, 6, 5, 6);
		PartDefinition leg = body.addOrReplaceChild(name + "_leg", up, PartPose.offset(x, y, z));
		CubeListBuilder shin = front ? cube(mirror, 64, 95, -2.0F, 0.0F, -2.0F, 4, 3, 4) : cube(mirror, 28, 95, -2.5F, 0.0F, -2.5F, 5, 3, 5);
		PartDefinition lower = leg.addOrReplaceChild(name + "_shin", shin, PartPose.offset(0.0F, 4.0F, 0.0F));
		CubeListBuilder web = front ? cube(mirror, 220, 82, -4.0F, 0.0F, -5.0F, 8, 2, 7) : cube(mirror, 132, 82, -4.5F, 0.0F, -6.0F, 9, 2, 9);
		lower.addOrReplaceChild(name + "_foot", web, PartPose.offset(0.0F, 3.0F, 0.0F));
	}

	private static CubeListBuilder cube(boolean mirror, int u, int v, float x, float y, float z, int w, int h, int d) {
		return mirror ? mirrored(u, v, x, y, z, w, h, d) : box(u, v, x, y, z, w, h, d);
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = s.fly, crash = s.crash * (1.0F - fly), swim = s.swim * (1.0F - fly) * (1.0F - crash);
		float sit = s.sit * (1.0F - crash) * (1.0F - fly) * (1.0F - swim);
		float breath = s.breath, land = s.land * (1.0F - fly), glide = s.glide * fly;
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - fly) * (1.0F - crash) * (1.0F - swim);
		float step = s.walkAnimationPos * 0.6662F;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -40.0F, 60.0F) * Mth.DEG_TO_RAD;
		float beat = s.flap;
		// (how hard it paddles: a swimmer that's moving strokes faster)
		float paddle = t * 0.3F;

		// the body: low when crashed, sitting or swimming (bobbing in the water), bobbing against the wing stroke, pitched with its climb
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP + swim * (SWIM_DROP + Mth.sin(t * 0.11F) * 0.6F)
			- Mth.sin(beat) * 0.8F * fly * (1.0F - glide) + Mth.sin(t * 0.1F) * 0.25F;
		this.body.xRot = s.pitch * fly + swim * Mth.sin(t * 0.09F) * 0.03F;
		this.body.zRot = Mth.sin(step * 0.5F) * 0.06F * walk + swim * Mth.sin(paddle * 0.5F) * 0.04F;

		// the neck: short and thick, lifting a little on the ground, held low and forward swimming
		for (int i = 0; i < 3; i++) {
			float x = Mth.lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i]);
			x = Mth.lerp(crash, x, NECK_CRASH[i]);
			x = Mth.lerp(swim, x, NECK_SWIM[i]);
			x += breath * (i == 0 ? -0.15F : 0.05F) * (1.0F - fly);
			x += Mth.sin(t * 0.08F - i * 0.7F) * 0.03F * (1.0F + fly);
			this.neck[i].xRot = x;
			this.neck[i].yRot = headYaw / 4.0F + Mth.sin(t * 0.05F - i * 0.6F) * 0.03F + swim * Mth.sin(paddle * 0.5F - i * 0.5F) * 0.08F;
		}
		float headX = Mth.lerp(fly, 0.2F, 0.08F);
		headX = Mth.lerp(swim, headX, 0.02F) - breath * 0.2F;
		this.head.xRot = headX + Mth.lerp(crash, headPitch * 0.5F, 0.1F);
		this.head.yRot = headYaw / 4.0F;
		this.jaw.xRot = 0.04F + breath * 0.65F + Mth.sin(t * 0.6F) * 0.05F * breath;

		// the tail: a flat, tall blade that sculls side to side (hard swimming) and trails in flight
		float sway = 0.07F + walk * 0.12F;
		for (int i = 0; i < 5; i++) {
			float x = Mth.lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]);
			x = Mth.lerp(crash, x, 0.0F);
			this.tail[i].xRot = x + Mth.sin(t * 0.06F - i * 0.5F) * 0.02F * fly;
			float wave = Mth.sin(t * 0.07F - i * 0.6F) * Mth.lerp(fly, sway, 0.04F);
			this.tail[i].yRot = Mth.lerp(swim, wave, Mth.sin(paddle - i * 0.8F) * 0.34F);
		}

		// legs: a sprawling waddle on the ground, webbed feet trailing in flight, reaching to land, splayed flat crashed,
		// the hind legs folded under sitting, and in water paddling out at the sides
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.5F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.5F * walk;
			float up = swing, low = knee;
			float trailUp = front ? 0.25F : 1.0F, trailLow = front ? 0.7F : 0.8F;
			up = Mth.lerp(fly, up, trailUp + Mth.sin(t * 0.1F + i) * 0.05F);
			low = Mth.lerp(fly, low, trailLow);
			up = Mth.lerp(land, up, front ? -0.5F : -0.3F);
			if (!front) {
				up = Mth.lerp(sit, up, -0.7F);
				low = Mth.lerp(sit, low, 1.2F);
			}
			// (a stroke each, the diagonal pairs together)
			float stroke = Mth.sin(paddle + phase);
			up = Mth.lerp(swim, up, stroke * 0.7F + (front ? -0.2F : 0.5F));
			low = Mth.lerp(swim, low, 0.5F + Math.max(0.0F, -stroke) * 0.5F);
			float splay = SPLAY * (1.0F - fly * 0.7F) + crash * 0.25F + swim * 0.7F;
			this.upper[i].xRot = up;
			this.upper[i].zRot = left ? -splay : splay;
			this.lower[i].xRot = low;
			this.lower[i].zRot = left ? splay * 0.6F : -splay * 0.6F;
			// the foot stays flat on the ground; in the air it hangs; paddling, it pushes back
			float flat = -(up + low);
			this.foot[i].xRot = Mth.lerp(fly, Mth.lerp(swim, flat, -0.3F + stroke * 0.3F), 0.5F);
		}

		// wings: open (in the air or flaring to land) or folded down the flanks (tight in water); crashed, half-spread
		float open = Math.max(fly, land);
		float hover = breath * fly;
		float stroke = Mth.sin(beat) * FLAP_DEPTH * (1.0F - glide * GLIDE_CUT) * (1.0F + hover * 0.3F) * fly + glide * 0.08F;
		float lift = stroke - land * 0.7F;
		float openSweep = -0.12F + hover * 0.3F + land * 0.3F;
		float tipUp = (Mth.sin(beat - TIP_LAG) * TIP_SWING * (1.0F - glide) - 0.05F) * fly;
		float foldSweep = Mth.lerp(swim, FOLD_SWEEP, TIGHT_SWEEP), foldDroop = Mth.lerp(swim, FOLD_DROOP, TIGHT_DROOP);
		float armX = Mth.lerp(open, FOLD_PITCH, 0.0F), armY = Mth.lerp(open, foldSweep, openSweep), armZ = Mth.lerp(open, foldDroop, -lift);
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
