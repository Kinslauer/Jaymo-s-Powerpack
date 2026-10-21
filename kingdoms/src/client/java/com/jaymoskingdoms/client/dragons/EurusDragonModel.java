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
 * (0.7.139) Eurus, our own model (blocks, as the Kingdoms beasts are): the storm dragon, lean and long-winged, nothing
 * like Notus's bulk. A slim, narrow body on tall, slender, taloned legs; a long, supple four-segment neck ending in a
 * small, wedge-shaped head with a crest of swept-back horns (and a pair of small spines on its skull); very long, narrow
 * wings with a swept membrane; a whip tail of six thinning segments ending in a forked fin. Its riders' saddle, girth
 * and pillion pad show when saddled. Texture 256x256 ({@code tools/gen_eurus.py}, whose UV table these texOffs are).
 * <p>
 * Posed procedurally from its render state: on the ground it stalks on its long legs with its neck arched and its wings
 * folded along its flanks; in the air the wings beat long and fast (little when gliding), the legs trail and the tail
 * whips in a wave; hovering to breathe it beats slow with its neck arched, its head shivering and its jaw wide; landing,
 * the wings flare. Crashed, it lies sprawled with its legs out; sitting, it squats on its haunches. Its back top is
 * about 1.22 blocks high ({@code EurusDragon.SEAT_HEIGHT} sits the rider just over it).
 */
final class EurusDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** Wing beat: depth (radians), how far gliding cuts it; tip lag (radians behind the stroke) and its swing. */
	static final float FLAP_DEPTH = 0.75F, GLIDE_CUT = 0.85F, TIP_LAG = 1.0F, TIP_SWING = 0.55F;
	/** Folded wings: the arm's pitch, its sweep back, its droop (outward); the tip folded back along it. */
	static final float FOLD_PITCH = -1.0F, FOLD_SWEEP = -1.4F, FOLD_DROOP = -0.25F, FOLD_TIP = Mth.PI - 0.25F;
	/** The neck's segment angles on the ground, in flight and crashed; the tail's (six segments). */
	static final float[] NECK_GROUND = {-0.7F, 0.35F, 0.3F, 0.15F}, NECK_FLIGHT = {-0.12F, 0.05F, 0.04F, 0.04F},
		NECK_CRASH = {0.25F, 0.1F, 0.1F, 0.05F};
	static final float[] TAIL_GROUND = {-0.12F, -0.04F, 0.03F, 0.05F, 0.06F, 0.06F}, TAIL_FLIGHT = {0.03F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F},
		TAIL_CRASH = {-0.12F, -0.08F, -0.04F, -0.02F, 0.0F, 0.0F};
	/** How low it lies crashed and sitting (model pixels); how far its legs splay standing and crashed, and it tilts sitting (radians). */
	static final float CRASH_DROP = 6.5F, SIT_DROP = 5.0F, SPLAY = 0.08F, CRASH_SPLAY = 1.3F, SIT_TILT = 0.35F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[4], tail = new ModelPart[6], spikes = new ModelPart[DragonLook.SPIKES];
	private final ModelPart forkL, forkR;
	private final ModelPart hornL, hornR, shortL, shortR, browL, browR;
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR;
	private final float bodyY;

	EurusDragonModel(ModelPart root) {
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
		this.forkL = parent.getChild("fork_l");
		this.forkR = parent.getChild("fork_r");
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
		// (the body's middle is 9 px down: its top is 4.5 px, its legs reach the ground at 24)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -6.0F, -4.5F, -15.0F, 12, 9, 30), PartPose.offset(0.0F, 9.0F, 0.0F));
		// small swept spines down the back
		float[] spikeZ = {-11.0F, -5.5F, 0.0F, 5.5F, 11.0F};
		for (int i = 0; i < spikeZ.length; i++) {
			body.addOrReplaceChild("spike" + i, box(164, 65, -0.5F, -4.0F, -1.5F, 1, 4, 3), PartPose.offsetAndRotation(0.0F, -4.5F, spikeZ[i], 0.35F, 0.0F, 0.0F));
		}
		// tack
		body.addOrReplaceChild("saddle", box(48, 53, -5.0F, -2.0F, -5.0F, 10, 2, 10)
			.texOffs(74, 75).addBox(-1.5F, -5.0F, -5.0F, 3, 3, 2)
			.texOffs(84, 75).addBox(-4.0F, -5.0F, 3.0F, 8, 3, 2), PartPose.offset(0.0F, -4.5F, -3.0F));
		body.addOrReplaceChild("strap", box(88, 39, -7.0F, -5.5F, -1.5F, 14, 11, 3), PartPose.offset(0.0F, 0.0F, -3.0F));
		body.addOrReplaceChild("pad", box(20, 65, -4.5F, -2.0F, -4.0F, 9, 2, 8), PartPose.offset(0.0F, -4.5F, 7.0F));

		// the neck: four long slim segments, each hung from the end of the one before
		int[][] neckUv = {{122, 39}, {206, 39}, {230, 39}, {178, 53}};
		float[][] neckBox = {{7, 7, 6}, {6, 6, 6}, {6, 6, 6}, {5, 5, 5}};
		PartDefinition parent = body;
		for (int i = 0; i < 4; i++) {
			float w = neckBox[i][0], h = neckBox[i][1], d = neckBox[i][2];
			parent = parent.addOrReplaceChild("neck" + i, box(neckUv[i][0], neckUv[i][1], -w / 2.0F, -h / 2.0F, -d, w, h, d),
				i == 0 ? PartPose.offset(0.0F, -1.0F, -14.0F) : PartPose.offset(0.0F, 0.0F, -neckBox[i - 1][2] + 0.5F));
		}
		// the head: a small wedge, a narrow snout, eyes at the sides, a pair of spines on the skull's top
		PartDefinition head = parent.addOrReplaceChild("head", box(148, 39, -4.0F, -2.5F, -8.0F, 8, 5, 8)
			.texOffs(198, 53).addBox(-2.5F, -0.5F, -15.0F, 5, 3, 7), PartPose.offset(0.0F, 0.0F, -4.5F));
		head.addOrReplaceChild("jaw", box(54, 65, -2.0F, 0.0F, -7.0F, 4, 2, 7), PartPose.offset(0.0F, 2.5F, -8.0F));
		head.addOrReplaceChild("eye_l", box(104, 75, -1.0F, -1.0F, -1.0F, 2, 2, 2), PartPose.offset(4.5F, -1.0F, -5.5F));
		head.addOrReplaceChild("eye_r", box(104, 75, -1.0F, -1.0F, -1.0F, 2, 2, 2), PartPose.offset(-4.5F, -1.0F, -5.5F));
		head.addOrReplaceChild("crest0", box(76, 65, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -2.5F, -5.0F, 0.4F, 0.0F, 0.0F));
		head.addOrReplaceChild("crest1", box(76, 65, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offsetAndRotation(0.0F, -2.5F, -1.0F, 0.5F, 0.0F, 0.0F));
		// horns: swept back along the skull (a base and a tip bent further up); long, short, and the twin's brow pair
		horn(head, "horn_l", 1.0F, 0, 65, 108, 65, 8, 6);
		horn(head, "horn_r", -1.0F, 0, 65, 108, 65, 8, 6);
		horn(head, "short_horn_l", 1.0F, 172, 65, 62, 75, 5, 4);
		horn(head, "short_horn_r", -1.0F, 172, 65, 62, 75, 5, 4);
		head.addOrReplaceChild("brow_horn_l", box(186, 65, -1.0F, -1.0F, 0.0F, 2, 2, 5), PartPose.offsetAndRotation(3.5F, -1.5F, -6.5F, 0.1F, 0.7F, 0.0F));
		head.addOrReplaceChild("brow_horn_r", box(186, 65, -1.0F, -1.0F, 0.0F, 2, 2, 5), PartPose.offsetAndRotation(-3.5F, -1.5F, -6.5F, 0.1F, -0.7F, 0.0F));

		// the whip tail: six thinning segments, ending in a forked fin
		int[][] tailUv = {{180, 39}, {0, 53}, {24, 53}, {88, 53}, {110, 53}, {222, 53}};
		float[] tailSize = {5, 4, 4, 3, 3, 2};
		parent = body;
		for (int i = 0; i < 6; i++) {
			float s = tailSize[i];
			parent = parent.addOrReplaceChild("tail" + i, box(tailUv[i][0], tailUv[i][1], -s / 2.0F, -s / 2.0F, 0.0F, s, s, 8),
				i == 0 ? PartPose.offset(0.0F, -0.5F, 14.5F) : PartPose.offset(0.0F, 0.0F, 7.5F));
		}
		parent.addOrReplaceChild("fork_l", box(132, 53, -2.5F, -0.5F, 0.0F, 5, 1, 10), PartPose.offsetAndRotation(1.2F, 0.0F, 7.0F, 0.0F, 0.4F, 0.0F));
		parent.addOrReplaceChild("fork_r", mirrored(132, 53, -2.5F, -0.5F, 0.0F, 5, 1, 10), PartPose.offsetAndRotation(-1.2F, 0.0F, 7.0F, 0.0F, -0.4F, 0.0F));

		// legs: tall and slender, with taloned feet
		leg(body, "left_front", false, 5.0F, 3.0F, -10.0F, true);
		leg(body, "right_front", true, -5.0F, 3.0F, -10.0F, true);
		leg(body, "left_hind", false, 5.0F, 3.0F, 10.0F, false);
		leg(body, "right_hind", true, -5.0F, 3.0F, 10.0F, false);

		// wings: very long and narrow, an arm with its swept membrane, and the tip with its own
		PartDefinition left = body.addOrReplaceChild("left_wing", box(0, 75, 0.0F, -1.5F, -1.5F, 28, 3, 3)
			.texOffs(84, 0).addBox(0.0F, 0.0F, 1.5F, 28, 0, 16), PartPose.offset(6.0F, -3.0F, -8.0F));
		left.addOrReplaceChild("left_wing_tip", box(112, 75, 0.0F, -1.0F, -1.0F, 30, 2, 2)
			.texOffs(0, 39).addBox(0.0F, 0.0F, 1.0F, 30, 0, 14), PartPose.offset(27.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing", mirrored(0, 75, -28.0F, -1.5F, -1.5F, 28, 3, 3)
			.texOffs(84, 0).addBox(-28.0F, 0.0F, 1.5F, 28, 0, 16), PartPose.offset(-6.0F, -3.0F, -8.0F));
		right.addOrReplaceChild("right_wing_tip", mirrored(112, 75, -30.0F, -1.0F, -1.0F, 30, 2, 2)
			.texOffs(0, 39).addBox(-30.0F, 0.0F, 1.0F, 30, 0, 14), PartPose.offset(-27.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	/** A long swept horn: a base swept back and up from the skull's rear corner, and a tip bent further up. */
	private static void horn(PartDefinition head, String name, float side, int baseU, int baseV, int tipU, int tipV, int base, int tip) {
		PartDefinition h = head.addOrReplaceChild(name, box(baseU, baseV, -1.0F, -1.0F, 0.0F, 2, 2, base),
			PartPose.offsetAndRotation(3.0F * side, -2.0F, -1.5F, 0.25F, 0.25F * side, 0.0F));
		h.addOrReplaceChild(name + "_tip", box(tipU, tipV, -1.0F, -1.0F, 0.0F, 2, 2, tip), PartPose.offsetAndRotation(0.0F, 0.0F, base, 0.3F, 0.0F, 0.0F));
	}

	/** A leg: a thigh hung 3 px under the body's middle (it reaches 12 px down to the ground), a shin and a talon foot. */
	private static void leg(PartDefinition body, String name, boolean mirror, float x, float y, float z, boolean front) {
		CubeListBuilder up = front ? cube(mirror, 242, 53, -1.5F, -1.0F, -1.5F, 3, 7, 3) : cube(mirror, 162, 53, -2.0F, -1.0F, -2.0F, 4, 7, 4);
		PartDefinition leg = body.addOrReplaceChild(name + "_leg", up, PartPose.offset(x, y, z));
		CubeListBuilder shin = front ? cube(mirror, 124, 65, -1.5F, 0.0F, -1.5F, 3, 4, 3) : cube(mirror, 152, 65, -1.5F, 0.0F, -1.5F, 3, 4, 3);
		PartDefinition lower = leg.addOrReplaceChild(name + "_shin", shin, PartPose.offset(0.0F, 6.0F, 0.0F));
		CubeListBuilder talon = front ? cube(mirror, 136, 65, -1.5F, 0.0F, -4.0F, 3, 2, 5) : cube(mirror, 90, 65, -1.5F, 0.0F, -4.5F, 3, 2, 6);
		lower.addOrReplaceChild(name + "_foot", talon, PartPose.offset(0.0F, 4.0F, 0.0F));
	}

	private static CubeListBuilder cube(boolean mirror, int u, int v, float x, float y, float z, int w, int h, int d) {
		return mirror ? mirrored(u, v, x, y, z, w, h, d) : box(u, v, x, y, z, w, h, d);
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = s.fly, crash = s.crash * (1.0F - fly);
		float sit = s.sit * (1.0F - crash) * (1.0F - fly);
		float breath = s.breath, land = s.land * (1.0F - fly), glide = s.glide * fly;
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - fly) * (1.0F - crash);
		float step = s.walkAnimationPos * 0.6662F;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -40.0F, 60.0F) * Mth.DEG_TO_RAD;
		float beat = s.flap;

		// the body: low when crashed or sitting (tilted up sitting), bobbing against the wing stroke, pitched with its climb
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP - Mth.sin(beat) * 0.9F * fly * (1.0F - glide) + Mth.sin(t * 0.1F) * 0.2F;
		this.body.xRot = s.pitch * fly - sit * SIT_TILT;
		this.body.zRot = Mth.sin(step * 0.5F) * 0.05F * walk;

		// the neck: arched on the ground (higher breathing), streamlined in flight, laid out crashed
		for (int i = 0; i < 4; i++) {
			float x = Mth.lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i]);
			x = Mth.lerp(crash, x, NECK_CRASH[i]);
			x += breath * (i == 0 ? -0.2F : 0.07F) * (1.0F - crash);
			x += Mth.sin(t * 0.08F - i * 0.7F) * 0.03F * (1.0F + fly);
			x += sit * SIT_TILT * 0.25F;
			this.neck[i].xRot = x;
			this.neck[i].yRot = headYaw / 5.0F + Mth.sin(t * 0.05F - i * 0.6F) * 0.04F;
		}
		float headX = Mth.lerp(fly, 0.1F, 0.05F) - breath * 0.2F;
		this.head.xRot = headX + Mth.lerp(crash, headPitch * 0.5F, 0.1F);
		this.head.yRot = headYaw / 5.0F;
		this.head.zRot = Mth.sin(t * 1.9F) * 0.04F * breath;
		this.jaw.xRot = 0.04F + breath * 0.7F + Mth.sin(t * 0.7F) * 0.06F * breath;

		// the tail: a whip, rippling in a wave down its length (hard in flight) and curling up at its tip
		float sway = 0.06F + walk * 0.12F;
		for (int i = 0; i < 6; i++) {
			float x = Mth.lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]);
			x = Mth.lerp(crash, x, TAIL_CRASH[i]);
			this.tail[i].xRot = x + Mth.sin(t * 0.12F - i * 0.7F) * 0.05F * fly;
			float amp = Mth.lerp(fly, sway, 0.1F + 0.04F * i);
			this.tail[i].yRot = Mth.sin(t * (0.09F + 0.05F * fly) - i * 0.8F) * amp * (1.0F - crash);
		}
		// (the fork's two blades flutter apart and together)
		float flutter = Mth.sin(t * 0.35F) * 0.12F * fly;
		this.forkL.yRot = 0.4F + flutter;
		this.forkR.yRot = -0.4F - flutter;

		// legs: a long stalking stride on the ground, talons trailing in flight, reaching to land, sprawled flat crashed,
		// the hind legs folded under sitting
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.6F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.7F * walk;
			float up = swing, low = knee;
			float trailUp = front ? 0.45F : 1.1F, trailLow = front ? 1.3F : 0.7F;
			up = Mth.lerp(fly, up, trailUp + Mth.sin(t * 0.1F + i) * 0.05F);
			low = Mth.lerp(fly, low, trailLow);
			up = Mth.lerp(land, up, front ? -0.6F : -0.4F);
			if (!front) {
				up = Mth.lerp(sit, up, -1.2F);
				low = Mth.lerp(sit, low, 2.0F);
			} else {
				up = Mth.lerp(sit, up, SIT_TILT);
			}
			float splay = SPLAY * (1.0F - fly * 0.7F) + crash * CRASH_SPLAY;
			this.upper[i].xRot = Mth.lerp(crash, up, 0.0F);
			this.upper[i].zRot = left ? -splay : splay;
			this.lower[i].xRot = Mth.lerp(crash, low, 0.0F);
			this.lower[i].zRot = left ? splay * 0.05F : -splay * 0.05F;
			// the foot stays flat on the ground; in the air it hangs
			float flat = -(up + low);
			this.foot[i].xRot = Mth.lerp(fly, Mth.lerp(crash, flat, 0.0F), 0.5F);
		}

		// wings: open (in the air or flaring to land) or folded along the flanks; crashed, half-spread
		float open = Math.max(fly, land);
		float hover = breath * fly;
		float stroke = Mth.sin(beat) * FLAP_DEPTH * (1.0F - glide * GLIDE_CUT) * (1.0F + hover * 0.3F) * fly + glide * 0.08F;
		float lift = stroke - land * 0.7F;
		float openSweep = -0.28F + hover * 0.25F + land * 0.3F;
		float tipUp = (Mth.sin(beat - TIP_LAG) * TIP_SWING * (1.0F - glide) - 0.05F) * fly;
		float armX = Mth.lerp(open, FOLD_PITCH, 0.0F), armY = Mth.lerp(open, FOLD_SWEEP, openSweep), armZ = Mth.lerp(open, FOLD_DROOP, -lift);
		float tipZ = Mth.lerp(open, FOLD_TIP, -tipUp);
		armX = Mth.lerp(crash, armX, -0.15F);
		armY = Mth.lerp(crash, armY, -0.35F);
		armZ = Mth.lerp(crash, armZ, 0.3F);
		tipZ = Mth.lerp(crash, tipZ, -0.2F);
		// at rest the folded wings breathe a little
		armZ += Mth.sin(t * 0.1F) * 0.02F * (1.0F - open);
		this.wingL.xRot = armX;
		this.wingL.yRot = armY;
		this.wingL.zRot = armZ;
		this.tipL.zRot = tipZ;
		this.tipL.yRot = -0.2F * open;
		this.wingR.xRot = armX;
		this.wingR.yRot = -armY;
		this.wingR.zRot = -armZ;
		this.tipR.zRot = -tipZ;
		this.tipR.yRot = 0.2F * open;

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
