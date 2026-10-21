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
 * (0.7.137) Boreas, our own model (blocks, as the Kingdoms beasts are): long, lean and elegant, not Notus. A slender body
 * with a crest of icicle spikes down its back; a long neck of five segments and a tail of seven that waves and ends in a
 * flat blade with a fin on it, not a club; a narrow head with swept-back horns whose jaw drops to breathe; four slim
 * legs; and very long, narrow, swept-back wings, each a long arm and a tapering tip that comes to a point (zero-thick
 * membranes, shaped by the texture's alpha). Its riders' saddle, girth and pillion pad show when saddled. Texture 256x256
 * ({@code tools/gen_boreas.py}, whose UV table these texOffs are).
 * <p>
 * Posed procedurally from its render state, favouring the glide: gliding, it holds its wings spread, swept and level, the
 * tips curving up, with one slow beat now and then (the beat is slowed and comes in bursts); climbing or hovering it beats
 * properly; diving fast and steep, it tucks its wings back along its flanks and stretches out. On the ground the neck
 * rises in a swan's curve, the wings fold down along its flanks and the legs walk; hovering to breathe it beats deep and
 * slow with its wings forward and its jaw wide; landing, the wings flare. Crashed, it lies low with its wings
 * half-spread on the ground; sitting, it folds its hind legs under.
 */
final class BoreasDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** Wing beat: depth (radians); how much slower than the base beat it runs (a long, lazy stroke); tip lag (radians behind the stroke) and its swing. */
	static final float FLAP_DEPTH = 0.7F, BEAT_SLOW = 0.7F, TIP_LAG = 1.3F, TIP_SWING = 0.45F;
	/** Gliding, the beat comes in bursts: how fast they come round, and how sharply they're gated (the sine's gain and offset). */
	static final float BURST_RATE = 0.5F, BURST_GAIN = 3.0F, BURST_OFFSET = 1.4F;
	/** Spread wings: the arm's sweep back, the tip's extra sweep, and how far both pull back in a dive. */
	static final float SWEEP = -0.38F, TIP_SWEEP = -0.22F, DIVE_SWEEP = -0.8F, DIVE_TIP_SWEEP = -0.5F;
	/** A dive: the body pitch (radians) it starts at, and how much further makes it full. */
	static final float DIVE_FROM = 0.2F, DIVE_OVER = 0.3F;
	/** Folded wings: the arm's pitch (membrane hanging), its sweep back, its droop (outward, so the membrane clears its flank); the tip folded back along it. */
	static final float FOLD_PITCH = -Mth.HALF_PI + 0.1F, FOLD_SWEEP = -1.45F, FOLD_DROOP = -0.3F, FOLD_TIP = Mth.PI - 0.25F;
	/** The neck's and tail's segment angles on the ground and in flight. */
	static final float[] NECK_GROUND = {-0.95F, 0.3F, 0.3F, 0.25F, 0.1F}, NECK_FLIGHT = {-0.1F, 0.03F, 0.03F, 0.03F, 0.03F},
		NECK_CRASH = {0.25F, 0.05F, 0.0F, -0.05F, -0.05F};
	static final float[] TAIL_GROUND = {-0.3F, 0.05F, 0.08F, 0.1F, 0.1F, 0.1F, 0.1F}, TAIL_FLIGHT = {0.04F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F},
		TAIL_CRASH = {-0.15F, 0.0F, 0.0F, 0.02F, 0.02F, 0.02F, 0.02F};
	/** How low it lies crashed and sitting (model pixels). */
	static final float CRASH_DROP = 4.0F, SIT_DROP = 3.0F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[5], tail = new ModelPart[7], spikes = new ModelPart[DragonLook.SPIKES];
	private final ModelPart hornL, hornR, shortL, shortR, browL, browR;
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR;
	private final float bodyY;

	BoreasDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart parent = this.body;
		for (int i = 0; i < 5; i++) {
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
		for (int i = 0; i < 7; i++) {
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
		// the body: 12 wide, 10 tall, 34 long; its back is 1.5 blocks up (the rider's seat sits a hair above)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -6.0F, -5.0F, -17.0F, 12, 10, 34), PartPose.offset(0.0F, 5.0F, 0.0F));
		// the crest: five icicle spikes, swept back a little
		float[] spikeZ = {-14.0F, -7.0F, 0.0F, 6.0F, 14.0F};
		for (int i = 0; i < spikeZ.length; i++) {
			body.addOrReplaceChild("spike" + i, box(212, 70, -1.0F, -6.0F, -1.0F, 2, 6, 2), PartPose.offsetAndRotation(0.0F, -5.0F, spikeZ[i], -0.3F, 0.0F, 0.0F));
		}
		// tack (the saddle and the pad cover the middle three spikes)
		body.addOrReplaceChild("saddle", box(84, 44, -5.0F, -2.0F, -6.0F, 10, 2, 12)
			.texOffs(8, 80).addBox(-1.5F, -5.0F, -6.0F, 3, 3, 2)
			.texOffs(18, 80).addBox(-4.0F, -5.0F, 4.0F, 8, 3, 2), PartPose.offset(0.0F, -5.0F, -4.0F));
		body.addOrReplaceChild("strap", box(128, 44, -6.5F, -5.5F, -1.5F, 13, 11, 3), PartPose.offset(0.0F, 0.0F, -4.0F));
		body.addOrReplaceChild("pad", box(204, 58, -4.5F, -2.0F, -4.5F, 9, 2, 9), PartPose.offset(0.0F, -5.0F, 8.0F));

		// the neck: five slim segments, each hung from the end of the one before, a small icicle on the first four
		int[][] neckUv = {{0, 44}, {160, 44}, {186, 44}, {0, 58}, {24, 58}};
		float[] neckSize = {7.0F, 6.0F, 6.0F, 5.0F, 5.0F};
		PartDefinition parent = body;
		for (int i = 0; i < 5; i++) {
			float s = neckSize[i];
			CubeListBuilder seg = box(neckUv[i][0], neckUv[i][1], -s / 2.0F, -s / 2.0F, -7.0F, s, s, 7);
			if (i < 4) {
				seg = seg.texOffs(0, 80).addBox(-1.0F, -s / 2.0F - 3.0F, -5.0F, 2, 3, 2);
			}
			parent = parent.addOrReplaceChild("neck" + i, seg, i == 0 ? PartPose.offset(0.0F, -1.0F, -16.0F) : PartPose.offset(0.0F, 0.0F, -6.0F));
		}
		// the head: a narrow skull and a long snout; the jaw hangs from the snout's root
		PartDefinition head = parent.addOrReplaceChild("head", box(114, 0, -4.0F, -3.0F, -8.0F, 8, 6, 9)
			.texOffs(96, 70).addBox(-2.5F, -1.0F, -15.0F, 5, 3, 7), PartPose.offset(0.0F, 0.0F, -6.0F));
		head.addOrReplaceChild("jaw", box(158, 70, -2.0F, 0.0F, -7.0F, 4, 2, 7), PartPose.offset(0.0F, 2.0F, -8.0F));
		// horns, swept back along the neck, a little up and out
		head.addOrReplaceChild("horn_l", box(56, 44, -1.0F, -1.0F, 0.0F, 2, 2, 12), PartPose.offsetAndRotation(3.0F, -2.5F, -1.0F, 0.15F, 0.22F, 0.0F));
		head.addOrReplaceChild("horn_r", mirrored(56, 44, -1.0F, -1.0F, 0.0F, 2, 2, 12), PartPose.offsetAndRotation(-3.0F, -2.5F, -1.0F, 0.15F, -0.22F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(220, 70, -1.0F, -1.0F, 0.0F, 2, 2, 6), PartPose.offsetAndRotation(3.0F, -2.5F, -1.0F, 0.3F, 0.2F, 0.0F));
		head.addOrReplaceChild("short_horn_r", mirrored(220, 70, -1.0F, -1.0F, 0.0F, 2, 2, 6),
			PartPose.offsetAndRotation(-3.0F, -2.5F, -1.0F, 0.3F, -0.2F, 0.0F));
		head.addOrReplaceChild("brow_horn_l", box(236, 70, -1.0F, -1.0F, 0.0F, 2, 2, 5), PartPose.offsetAndRotation(4.0F, -1.5F, -5.0F, 0.1F, 0.7F, 0.0F));
		head.addOrReplaceChild("brow_horn_r", mirrored(236, 70, -1.0F, -1.0F, 0.0F, 2, 2, 5),
			PartPose.offsetAndRotation(-4.0F, -1.5F, -5.0F, 0.1F, -0.7F, 0.0F));

		// the tail: seven segments, long and thin, ending in a flat blade with a fin standing on it; small icicles on the first two
		int[][] tailUv = {{28, 44}, {212, 44}, {48, 58}, {74, 58}, {98, 58}, {122, 58}, {120, 70}};
		int[][] tailSize = {{6, 6}, {5, 5}, {5, 4}, {4, 4}, {4, 3}, {3, 3}, {3, 2}};
		parent = body;
		for (int i = 0; i < 7; i++) {
			int w = tailSize[i][0], h = tailSize[i][1];
			CubeListBuilder seg = box(tailUv[i][0], tailUv[i][1], -w / 2.0F, -h / 2.0F, 0.0F, w, h, 8);
			if (i < 2) {
				seg = seg.texOffs(0, 80).addBox(-1.0F, -h / 2.0F - 3.0F, 3.0F, 2, 3, 2);
			}
			parent = parent.addOrReplaceChild("tail" + i, seg, i == 0 ? PartPose.offset(0.0F, -1.0F, 16.0F) : PartPose.offset(0.0F, 0.0F, 7.0F));
		}
		parent.addOrReplaceChild("fin", box(144, 58, -6.0F, -0.5F, 0.0F, 12, 1, 10)
			.texOffs(188, 58).addBox(-0.5F, -4.0F, 2.0F, 1, 4, 7), PartPose.offset(0.0F, 0.0F, 7.0F));

		// legs: upper, shin, foot (slim, long-shanked; they end on the ground at 24)
		leg(body, "left_front", false, 4.0F, 3.0F, -11.0F, true);
		leg(body, "right_front", true, -4.0F, 3.0F, -11.0F, true);
		leg(body, "left_hind", false, 4.0F, 3.0F, 11.0F, false);
		leg(body, "right_hind", true, -4.0F, 3.0F, 11.0F, false);

		// wings: a long arm with its narrow membrane, and a longer tip with its own, tapering to a point
		PartDefinition left = body.addOrReplaceChild("left_wing", box(38, 80, 0.0F, -1.0F, -1.0F, 34, 2, 2)
			.texOffs(148, 0).addBox(0.0F, 0.0F, 1.0F, 34, 0, 14), PartPose.offset(6.0F, -4.0F, -9.0F));
		left.addOrReplaceChild("left_wing_tip", box(110, 80, 0.0F, -1.0F, -1.0F, 38, 2, 2)
			.texOffs(0, 70).addBox(0.0F, 0.0F, 1.0F, 38, 0, 10), PartPose.offset(33.0F, 0.0F, 0.0F));
		PartDefinition right = body.addOrReplaceChild("right_wing", mirrored(38, 80, -34.0F, -1.0F, -1.0F, 34, 2, 2)
			.texOffs(148, 0).addBox(-34.0F, 0.0F, 1.0F, 34, 0, 14), PartPose.offset(-6.0F, -4.0F, -9.0F));
		right.addOrReplaceChild("right_wing_tip", mirrored(110, 80, -38.0F, -1.0F, -1.0F, 38, 2, 2)
			.texOffs(0, 70).addBox(-38.0F, 0.0F, 1.0F, 38, 0, 10), PartPose.offset(-33.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	private static void leg(PartDefinition body, String name, boolean mirror, float x, float y, float z, boolean front) {
		CubeListBuilder up = front ? cube(mirror, 238, 44, -2.0F, -1.0F, -2.0F, 4, 9, 4) : cube(mirror, 92, 0, -2.5F, -2.0F, -3.0F, 5, 10, 6);
		PartDefinition leg = body.addOrReplaceChild(name + "_leg", up, PartPose.offset(x, y, z));
		CubeListBuilder shin = front ? cube(mirror, 180, 70, -1.5F, 0.0F, -1.5F, 3, 6, 3) : cube(mirror, 142, 70, -2.0F, 0.0F, -2.0F, 4, 6, 4);
		PartDefinition lower = leg.addOrReplaceChild(name + "_shin", shin, PartPose.offset(0.0F, 8.0F, 0.0F));
		lower.addOrReplaceChild(name + "_foot", cube(mirror, 192, 70, -2.0F, 0.0F, -4.0F, 4, 2, 6), PartPose.offset(0.0F, 6.0F, 0.0F));
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
		float beat = s.flap * BEAT_SLOW;
		// fast and steep: the dive, wings tucked back
		float dive = Mth.clamp((s.pitch - DIVE_FROM) / DIVE_OVER, 0.0F, 1.0F) * fly;

		// the body: lower crashed or sitting, bobbing a little against the wing stroke, pitched with its climb
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP - Mth.sin(beat) * 0.6F * fly * (1.0F - glide) + Mth.sin(t * 0.08F) * 0.2F;
		this.body.xRot = s.pitch * fly;
		this.body.zRot = Mth.sin(step * 0.5F) * 0.03F * walk;

		// the neck: a swan's curve on the ground, held straight out in flight (stretched further in a dive), rippling; it lowers to breathe
		for (int i = 0; i < 5; i++) {
			float x = Mth.lerp(fly, NECK_GROUND[i], NECK_FLIGHT[i]);
			x = Mth.lerp(crash, x, NECK_CRASH[i]);
			x += breath * (i == 0 ? 0.25F : i == 4 ? -0.1F : 0.05F) * (1.0F - fly);
			x += Mth.sin(t * 0.07F - i * 0.7F) * 0.035F * (1.0F + fly);
			this.neck[i].xRot = x * (1.0F - dive * 0.6F);
			this.neck[i].yRot = headYaw / 6.0F + Mth.sin(t * 0.05F - i * 0.6F) * 0.03F;
		}
		this.head.xRot = Mth.lerp(fly, 0.35F, 0.05F) + Mth.lerp(crash, headPitch * 0.6F, 0.15F);
		this.head.yRot = headYaw / 6.0F;
		this.jaw.xRot = 0.04F + breath * 0.7F + Mth.sin(t * 0.6F) * 0.05F * breath;

		// the tail: a chain that droops and curls on the ground, streams in flight, and waves more toward the blade
		float sway = 0.05F + walk * 0.08F;
		for (int i = 0; i < 7; i++) {
			float x = Mth.lerp(fly, TAIL_GROUND[i], TAIL_FLIGHT[i]);
			x = Mth.lerp(crash, x, TAIL_CRASH[i]);
			this.tail[i].xRot = x + Mth.sin(t * 0.05F - i * 0.5F) * 0.025F * fly;
			this.tail[i].yRot = Mth.sin(t * 0.06F - i * 0.55F) * Mth.lerp(fly, sway, 0.04F) * (0.5F + i * 0.12F);
		}

		// legs: walking on the ground (the knees bending as each leg comes forward), tucked in flight (fully in a dive),
		// reaching to land, splayed crashed, the hind legs folded under sitting
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.55F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.6F * walk;
			float up = swing, low = knee;
			float tuckUp = front ? -0.45F : 1.1F, tuckLow = front ? 1.5F : 0.6F;
			up = Mth.lerp(fly, up, tuckUp + Mth.sin(t * 0.1F + i) * 0.05F * (1.0F - dive));
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

		// wings: open (in the air or flaring to land) or folded down the flanks; crashed, half-spread on the ground.
		// Gliding, they hold out level, swept back, the tips curving up, and one slow beat comes round every so often (the
		// beat's gated into bursts); climbing and hovering beat the whole stroke; a dive pulls them back along the flanks.
		float open = Math.max(fly, land);
		float hover = breath * fly;
		float burst = Mth.clamp(Mth.sin(s.flap * BURST_RATE) * BURST_GAIN - BURST_OFFSET, 0.0F, 1.0F);
		float envelope = Mth.lerp(glide, 1.0F, burst);
		float stroke = Mth.sin(beat) * FLAP_DEPTH * envelope * (1.0F + hover * 0.3F) * fly * (1.0F - dive);
		float soar = glide * (0.07F + Mth.sin(t * 0.045F) * 0.025F);
		float lift = stroke + soar - land * 0.7F - dive * 0.1F;
		float openSweep = SWEEP + hover * 0.35F + land * 0.3F + dive * DIVE_SWEEP;
		float tipSweep = (TIP_SWEEP + dive * DIVE_TIP_SWEEP) * open;
		float tipUp = (Mth.sin(beat - TIP_LAG) * TIP_SWING * envelope * (1.0F - dive) - 0.05F + glide * 0.2F) * fly;
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
		this.tipL.yRot = tipSweep;
		this.tipL.zRot = tipZ;
		this.wingR.xRot = armX;
		this.wingR.yRot = -armY;
		this.wingR.zRot = -armZ;
		this.tipR.yRot = -tipSweep;
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
