package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.AetherionDragon;
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
 * (0.7.522) Aetherion, the star dragon of the Aether: our own model (blocks; {@code tools/gen_aetherion.py} prints the body
 * plan below from its own table, so the UVs match; texture 512x512). Andy sent an Aetherion (a public-domain GeckoLib
 * dragon) and said "the wings and head might need upgrading": its body plan is kept (a four-legged flier, a four-segment
 * neck rising in an S, a seven-segment tail ending in a crossed fan) and its wings and head drawn anew. Each wing is an
 * arm, a forearm and a hand of three fanned finger bones with a thumb claw, a membrane stretched behind each bone with a
 * scalloped trailing edge; the head has a heavy brow, two great horns swept back and up (short, or with a second pair
 * beneath, by its {@link DragonLook}), cheek frills and a jaw that opens. Crystal spines run down its neck, back and
 * tail; its markings (stars, constellations on the membranes, starlit veins, rims) glow in the dark.
 * <p>
 * Posed procedurally, every joint eased (Andy: "all the joints should move and blend smoothly especially for the ones
 * with wings"): the wingbeat rolls out from the shoulder, the forearm a beat behind the arm and the hand a beat behind
 * that, the fingers spreading on the downstroke; gliding they're held open with a little lift at the tips; landed they
 * fold (arm raised, forearm folded down along it, the hand up again, the fingers closed) and the fold eases in and out.
 * The neck straightens in flight and the legs fold away flat under the belly, drawn in so small it looks legless in
 * the air; on the ground it walks in a long, high gait, the tail swaying a
 * beat per segment. Its moves ({@link AetherionDragon#action}): the bite, the neck darting in and the jaws snapping; and
 * the Cataclysm, rearing up with its wings flung high and coming down with them beating as the stars fall.
 */
final class AetherionDragonModel extends EntityModel<WindDragonRenderer.State> {
	static final int NECK = 4, TAIL = 7, FINGERS = 3;
	/** How low it sinks sitting and crashed (model pixels). */
	static final float SIT_DROP = 9.0F, CRASH_DROP = 20.0F;
	/** The wing folded, as offsets from its spread rest for the left wing (the right's are mirrored): the arm (y, z), the forearm (y, z), the hand (z), and how far the fingers close. */
	static final float FOLD_ARM_Y = -0.35F, FOLD_ARM_Z = -1.25F, FOLD_FORE_Y = 0.2F, FOLD_FORE_Z = 2.75F, FOLD_HAND_Z = -2.55F, FOLD_CLOSE = 0.8F;
	/** The wingbeat: how far the arm swings up and down about a slight lift, and how much each later joint adds, a beat behind. */
	/** In flight the legs fold away: the thigh swung flat back, the shin folded up against it, the foot along it; drawn this much smaller and drawn up this far into the body (model pixels). */
	static final float TUCK_FRONT_UP = 1.45F, TUCK_FRONT_LOW = -2.8F, TUCK_HIND_UP = 1.5F, TUCK_HIND_LOW = -2.6F, TUCK_FOOT = 1.25F;
	static final float TUCK_SHRINK = 0.55F, TUCK_RAISE = 3.0F;
	static final float BEAT_ARM = 0.65F, BEAT_LIFT = 0.1F, BEAT_FORE = 0.32F, BEAT_HAND = 0.28F, BEAT_LAG = 0.7F;

	private final ModelPart body, head, jaw;
	private final ModelPart[] neck = new ModelPart[NECK];
	private final float[] neckRest = new float[NECK];
	private final float headRest;
	private final ModelPart[] tail = new ModelPart[TAIL];
	private final float[] tailRest = new float[TAIL];
	private final ModelPart hornL, hornR, tipL, tipR, lowL, lowR;
	private final ModelPart[] spines = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final float[] upperRest = new float[4], lowerRest = new float[4], footRest = new float[4], upperY = new float[4];
	/** The wings, left then right: arm, forearm, hand, and the fingers with their spread. */
	private final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
	private final ModelPart[][] finger = new ModelPart[2][FINGERS];
	private final float[][] fingerRest = new float[2][FINGERS];
	private final float bodyY;

	AetherionDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart parent = this.body;
		for (int i = 0; i < NECK; i++) {
			this.neck[i] = parent.getChild("neck" + i);
			this.neckRest[i] = this.neck[i].xRot;
			parent = this.neck[i];
		}
		this.head = parent.getChild("head");
		this.headRest = this.head.xRot;
		this.jaw = this.head.getChild("jaw");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.tipL = this.hornL.getChild("horn_tip_l");
		this.tipR = this.hornR.getChild("horn_tip_r");
		this.lowL = this.head.getChild("low_horn_l");
		this.lowR = this.head.getChild("low_horn_r");
		parent = this.body;
		for (int i = 0; i < TAIL; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			this.tailRest[i] = this.tail[i].xRot;
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spines[i] = this.body.getChild("spine" + i);
		}
		this.saddle = this.body.getChild("saddle");
		this.strap = this.body.getChild("strap");
		String[] legs = {"left_front", "right_front", "left_hind", "right_hind"};
		for (int i = 0; i < 4; i++) {
			this.upper[i] = this.body.getChild(legs[i] + "_leg");
			this.lower[i] = this.upper[i].getChild(legs[i] + "_shin");
			this.foot[i] = this.lower[i].getChild(legs[i] + "_foot");
			this.upperRest[i] = this.upper[i].xRot;
			this.upperY[i] = this.upper[i].y;
			this.lowerRest[i] = this.lower[i].xRot;
			this.footRest[i] = this.foot[i].xRot;
		}
		String[] sides = {"l", "r"};
		for (int w = 0; w < 2; w++) {
			this.arm[w] = this.body.getChild("wing_" + sides[w]);
			this.fore[w] = this.arm[w].getChild("wing_fore_" + sides[w]);
			this.hand[w] = this.fore[w].getChild("wing_hand_" + sides[w]);
			for (int f = 0; f < FINGERS; f++) {
				this.finger[w][f] = this.hand[w].getChild("finger" + f + "_" + sides[w]);
				this.fingerRest[w][f] = this.finger[w][f].yRot;
			}
		}
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
		// (printed by tools/gen_aetherion.py)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -10.0F, -8.0F, -17.0F, 20, 16, 34), PartPose.offset(0.0F, -15.0F, 0.0F));
		body.addOrReplaceChild("chest", box(232, 50, -9.0F, -6.0F, -6.0F, 18, 13, 12), PartPose.offsetAndRotation(0.0F, 2.0F, -12.0F, 0.2F, 0.0F, 0.0F));
		body.addOrReplaceChild("belly", box(0, 50, -8.0F, 0.0F, -14.0F, 16, 2, 28), PartPose.offset(0.0F, 8.0F, 0.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(48, 80, -6.0F, -5.5F, -9.0F, 12, 11, 9), PartPose.offsetAndRotation(0.0F, -3.0F, -16.0F, -0.7F, 0.0F, 0.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(364, 80, -5.5F, -5.0F, -8.0F, 11, 10, 8), PartPose.offsetAndRotation(0.0F, 0.0F, -8.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", box(440, 80, -5.0F, -4.5F, -8.0F, 10, 9, 8), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, -0.05F, 0.0F, 0.0F));
		PartDefinition neck3 = neck2.addOrReplaceChild("neck3", box(82, 101, -4.5F, -4.0F, -7.0F, 9, 8, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.3F, 0.0F, 0.0F));
		PartDefinition head = neck3.addOrReplaceChild("head", box(416, 50, -7.0F, -6.0F, -12.0F, 14, 10, 12), PartPose.offsetAndRotation(0.0F, -0.5F, -5.0F, 0.45F, 0.0F, 0.0F));
		PartDefinition snout = head.addOrReplaceChild("snout", box(182, 80, -5.0F, -3.5F, -12.0F, 10, 7, 12), PartPose.offsetAndRotation(0.0F, -0.5F, -11.0F, 0.06F, 0.0F, 0.0F));
		snout.addOrReplaceChild("nose", box(396, 101, -4.0F, -2.5F, -3.0F, 8, 5, 3), PartPose.offsetAndRotation(0.0F, -1.0F, -11.5F, 0.1F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", box(292, 50, -4.5F, 0.0F, -22.0F, 9, 3, 22), PartPose.offset(0.0F, 4.0F, -1.5F));
		head.addOrReplaceChild("brow_l", box(198, 101, -1.5F, -1.5F, -5.0F, 3, 3, 10), PartPose.offsetAndRotation(5.5F, -6.0F, -7.0F, 0.12F, 0.12F, 0.0F));
		head.addOrReplaceChild("brow_r", mirrored(198, 101, -1.5F, -1.5F, -5.0F, 3, 3, 10), PartPose.offsetAndRotation(-5.5F, -6.0F, -7.0F, 0.12F, -0.12F, 0.0F));
		head.addOrReplaceChild("eye_l", box(92, 118, -1.0F, -1.0F, -1.5F, 2, 2, 3), PartPose.offset(6.5F, -3.0F, -8.0F));
		head.addOrReplaceChild("eye_r", mirrored(92, 118, -1.0F, -1.0F, -1.5F, 2, 2, 3), PartPose.offset(-6.5F, -3.0F, -8.0F));
		PartDefinition hornl = head.addOrReplaceChild("horn_l", box(476, 80, -2.0F, -2.0F, 0.0F, 4, 4, 13), PartPose.offsetAndRotation(4.5F, -6.0F, -1.0F, 0.95F, 0.3F, 0.0F));
		PartDefinition hornr = head.addOrReplaceChild("horn_r", mirrored(476, 80, -2.0F, -2.0F, 0.0F, 4, 4, 13), PartPose.offsetAndRotation(-4.5F, -6.0F, -1.0F, 0.95F, -0.3F, 0.0F));
		hornl.addOrReplaceChild("horn_tip_l", box(224, 101, -1.5F, -1.5F, 0.0F, 3, 3, 10), PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, -0.25F, 0.0F, 0.0F));
		hornr.addOrReplaceChild("horn_tip_r", mirrored(224, 101, -1.5F, -1.5F, 0.0F, 3, 3, 10), PartPose.offsetAndRotation(0.0F, 0.0F, 12.0F, -0.25F, 0.0F, 0.0F));
		head.addOrReplaceChild("low_horn_l", box(378, 101, -1.0F, -1.0F, 0.0F, 2, 2, 7), PartPose.offsetAndRotation(6.5F, -1.0F, 0.0F, 0.2F, 0.55F, 0.0F));
		head.addOrReplaceChild("low_horn_r", mirrored(378, 101, -1.0F, -1.0F, 0.0F, 2, 2, 7), PartPose.offsetAndRotation(-6.5F, -1.0F, 0.0F, 0.2F, -0.55F, 0.0F));
		head.addOrReplaceChild("frill_l", box(0, 101, 0.0F, -4.0F, 0.0F, 1, 8, 9), PartPose.offsetAndRotation(7.0F, 1.0F, -3.0F, 0.35F, 0.5F, 0.0F));
		head.addOrReplaceChild("frill_r", mirrored(0, 101, -1.0F, -4.0F, 0.0F, 1, 8, 9), PartPose.offsetAndRotation(-7.0F, 1.0F, -3.0F, 0.35F, -0.5F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(0, 80, -7.0F, -5.5F, 0.0F, 14, 11, 10), PartPose.offsetAndRotation(0.0F, -4.0F, 16.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(90, 80, -6.0F, -5.0F, 0.0F, 12, 10, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, -0.08F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(226, 80, -5.5F, -4.5F, 0.0F, 11, 9, 10), PartPose.offset(0.0F, 0.3F, 9.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(402, 80, -4.5F, -4.0F, 0.0F, 9, 8, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.04F, 0.0F, 0.0F));
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", box(20, 101, -4.0F, -3.5F, 0.0F, 8, 7, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.05F, 0.0F, 0.0F));
		PartDefinition tail5 = tail4.addOrReplaceChild("tail5", box(114, 101, -3.0F, -2.5F, 0.0F, 6, 5, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.06F, 0.0F, 0.0F));
		PartDefinition tail6 = tail5.addOrReplaceChild("tail6", box(250, 101, -2.5F, -2.0F, 0.0F, 5, 4, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.06F, 0.0F, 0.0F));
		PartDefinition fan = tail6.addOrReplaceChild("fan", box(278, 101, -8.0F, -0.5F, 0.0F, 16, 1, 12), PartPose.offset(0.0F, 0.0F, 7.0F));
		fan.addOrReplaceChild("fan_tip", box(478, 101, -3.0F, -0.5F, 0.0F, 6, 1, 6), PartPose.offset(0.0F, 0.0F, 11.0F));
		tail6.addOrReplaceChild("fan_crest", box(56, 101, -0.5F, -5.0F, 0.0F, 1, 5, 12), PartPose.offset(0.0F, -1.0F, 5.0F));
		neck0.addOrReplaceChild("nspine0", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -5.5F, -4.5F, -0.4F, 0.0F, 0.0F));
		neck1.addOrReplaceChild("nspine1", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -5.0F, -4.0F, -0.4F, 0.0F, 0.0F));
		neck2.addOrReplaceChild("nspine2", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -4.5F, -4.0F, -0.4F, 0.0F, 0.0F));
		neck3.addOrReplaceChild("nspine3", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -4.0F, -3.5F, -0.4F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine0", box(334, 101, -1.5F, -7.0F, -2.0F, 3, 7, 4), PartPose.offsetAndRotation(0.0F, -8.0F, -13.0F, -0.45F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine1", box(334, 101, -1.5F, -7.0F, -2.0F, 3, 7, 4), PartPose.offsetAndRotation(0.0F, -8.0F, -6.5F, -0.45F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine2", box(334, 101, -1.5F, -7.0F, -2.0F, 3, 7, 4), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.45F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine3", box(334, 101, -1.5F, -7.0F, -2.0F, 3, 7, 4), PartPose.offsetAndRotation(0.0F, -8.0F, 6.5F, -0.45F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine4", box(334, 101, -1.5F, -7.0F, -2.0F, 3, 7, 4), PartPose.offsetAndRotation(0.0F, -8.0F, 13.0F, -0.45F, 0.0F, 0.0F));
		tail0.addOrReplaceChild("tspine0", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -5.5F, 5.0F, -0.5F, 0.0F, 0.0F));
		tail1.addOrReplaceChild("tspine1", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -5.0F, 5.0F, -0.5F, 0.0F, 0.0F));
		tail2.addOrReplaceChild("tspine2", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -4.5F, 5.0F, -0.5F, 0.0F, 0.0F));
		tail3.addOrReplaceChild("tspine3", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -4.0F, 5.0F, -0.5F, 0.0F, 0.0F));
		tail4.addOrReplaceChild("tspine4", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -3.5F, 5.0F, -0.5F, 0.0F, 0.0F));
		tail5.addOrReplaceChild("tspine5", box(502, 101, -1.0F, -4.0F, -2.0F, 2, 4, 3), PartPose.offsetAndRotation(0.0F, -2.5F, 5.0F, -0.5F, 0.0F, 0.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(146, 101, -7.0F, -2.0F, -6.0F, 14, 2, 12), PartPose.offset(0.0F, -8.0F, -3.0F));
		saddle.addOrReplaceChild("pommel", box(102, 118, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -6.0F));
		saddle.addOrReplaceChild("cantle", box(114, 118, -6.0F, -3.0F, 0.0F, 12, 3, 2), PartPose.offset(0.0F, -2.0F, 4.0F));
		body.addOrReplaceChild("strap", box(134, 80, -10.5F, -8.5F, -1.5F, 21, 17, 3), PartPose.offset(0.0F, 0.0F, -3.0F));
		PartDefinition leftfrontleg = body.addOrReplaceChild("left_front_leg", box(390, 50, -3.0F, -2.0F, -3.5F, 6, 16, 7), PartPose.offsetAndRotation(7.5F, 6.0F, -11.0F, 0.1F, 0.0F, 0.0F));
		PartDefinition leftfrontshin = leftfrontleg.addOrReplaceChild("left_front_shin", box(468, 50, -2.5F, 0.0F, -2.5F, 5, 17, 5), PartPose.offsetAndRotation(0.0F, 13.5F, 0.0F, -0.2F, 0.0F, 0.0F));
		leftfrontshin.addOrReplaceChild("left_front_foot", box(348, 101, -3.5F, 0.0F, -6.0F, 7, 3, 8), PartPose.offsetAndRotation(0.0F, 16.5F, 0.0F, 0.1F, 0.0F, 0.0F));
		PartDefinition rightfrontleg = body.addOrReplaceChild("right_front_leg", mirrored(390, 50, -3.0F, -2.0F, -3.5F, 6, 16, 7), PartPose.offsetAndRotation(-7.5F, 6.0F, -11.0F, 0.1F, 0.0F, 0.0F));
		PartDefinition rightfrontshin = rightfrontleg.addOrReplaceChild("right_front_shin", mirrored(468, 50, -2.5F, 0.0F, -2.5F, 5, 17, 5), PartPose.offsetAndRotation(0.0F, 13.5F, 0.0F, -0.2F, 0.0F, 0.0F));
		rightfrontshin.addOrReplaceChild("right_front_foot", mirrored(348, 101, -3.5F, 0.0F, -6.0F, 7, 3, 8), PartPose.offsetAndRotation(0.0F, 16.5F, 0.0F, 0.1F, 0.0F, 0.0F));
		PartDefinition lefthindleg = body.addOrReplaceChild("left_hind_leg", box(354, 50, -4.0F, -3.0F, -5.0F, 8, 15, 10), PartPose.offsetAndRotation(7.5F, 5.0F, 12.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition lefthindshin = lefthindleg.addOrReplaceChild("left_hind_shin", box(212, 50, -2.5F, 0.0F, -2.5F, 5, 21, 5), PartPose.offsetAndRotation(0.0F, 11.5F, 1.5F, 0.55F, 0.0F, 0.0F));
		lefthindshin.addOrReplaceChild("left_hind_foot", box(348, 101, -3.5F, 0.0F, -6.0F, 7, 3, 8), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, -0.25F, 0.0F, 0.0F));
		PartDefinition righthindleg = body.addOrReplaceChild("right_hind_leg", mirrored(354, 50, -4.0F, -3.0F, -5.0F, 8, 15, 10), PartPose.offsetAndRotation(-7.5F, 5.0F, 12.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition righthindshin = righthindleg.addOrReplaceChild("right_hind_shin", mirrored(212, 50, -2.5F, 0.0F, -2.5F, 5, 21, 5), PartPose.offsetAndRotation(0.0F, 11.5F, 1.5F, 0.55F, 0.0F, 0.0F));
		righthindshin.addOrReplaceChild("right_hind_foot", mirrored(348, 101, -3.5F, 0.0F, -6.0F, 7, 3, 8), PartPose.offsetAndRotation(0.0F, 20.0F, 0.0F, -0.25F, 0.0F, 0.0F));
		PartDefinition wingl = body.addOrReplaceChild("wing_l", box(418, 101, 0.0F, -2.0F, -2.0F, 26, 4, 4), PartPose.offset(9.0F, -6.0F, -8.0F));
		wingl.addOrReplaceChild("wing_mem_l", box(108, 0, 0.0F, -0.5F, 1.0F, 26, 1, 34), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition wingforel = wingl.addOrReplaceChild("wing_fore_l", box(14, 118, 0.0F, -1.5F, -1.5F, 30, 3, 3), PartPose.offset(25.5F, 0.0F, 0.0F));
		wingforel.addOrReplaceChild("wing_fmem_l", box(228, 0, 0.0F, -0.5F, 1.0F, 30, 1, 34), PartPose.offset(0.0F, 0.0F, 0.0F));
		wingforel.addOrReplaceChild("wing_thumb_l", box(0, 118, 0.0F, -1.0F, -1.0F, 2, 2, 5), PartPose.offsetAndRotation(29.5F, 0.0F, -1.0F, 0.0F, -0.9F, 0.0F));
		PartDefinition winghandl = wingforel.addOrReplaceChild("wing_hand_l", box(80, 118, 0.0F, -1.5F, -1.5F, 3, 3, 3), PartPose.offset(29.5F, 0.0F, 0.0F));
		PartDefinition finger0l = winghandl.addOrReplaceChild("finger0_l", box(142, 118, 0.0F, -1.0F, -1.0F, 42, 2, 2), PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.0F, -0.2F, 0.0F));
		finger0l.addOrReplaceChild("fmem0_l", box(356, 0, 0.0F, -0.5F, 1.0F, 42, 1, 30), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition finger1l = winghandl.addOrReplaceChild("finger1_l", box(230, 118, 0.0F, -1.0F, -1.0F, 36, 2, 2), PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F));
		finger1l.addOrReplaceChild("fmem1_l", box(88, 50, 0.0F, -0.5F, 1.0F, 36, 1, 26), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition finger2l = winghandl.addOrReplaceChild("finger2_l", box(306, 118, 0.0F, -1.0F, -1.0F, 30, 2, 2), PartPose.offsetAndRotation(1.5F, 0.0F, 0.0F, 0.0F, -1.75F, 0.0F));
		finger2l.addOrReplaceChild("fmem2_l", box(268, 80, 0.0F, -0.5F, 1.0F, 30, 1, 18), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition wingr = body.addOrReplaceChild("wing_r", mirrored(418, 101, -26.0F, -2.0F, -2.0F, 26, 4, 4), PartPose.offset(-9.0F, -6.0F, -8.0F));
		wingr.addOrReplaceChild("wing_mem_r", mirrored(108, 0, -26.0F, -0.5F, 1.0F, 26, 1, 34), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition wingforer = wingr.addOrReplaceChild("wing_fore_r", mirrored(14, 118, -30.0F, -1.5F, -1.5F, 30, 3, 3), PartPose.offset(-25.5F, 0.0F, 0.0F));
		wingforer.addOrReplaceChild("wing_fmem_r", mirrored(228, 0, -30.0F, -0.5F, 1.0F, 30, 1, 34), PartPose.offset(0.0F, 0.0F, 0.0F));
		wingforer.addOrReplaceChild("wing_thumb_r", mirrored(0, 118, -2.0F, -1.0F, -1.0F, 2, 2, 5), PartPose.offsetAndRotation(-29.5F, 0.0F, -1.0F, 0.0F, 0.9F, 0.0F));
		PartDefinition winghandr = wingforer.addOrReplaceChild("wing_hand_r", mirrored(80, 118, -3.0F, -1.5F, -1.5F, 3, 3, 3), PartPose.offset(-29.5F, 0.0F, 0.0F));
		PartDefinition finger0r = winghandr.addOrReplaceChild("finger0_r", mirrored(142, 118, -42.0F, -1.0F, -1.0F, 42, 2, 2), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.0F, 0.2F, 0.0F));
		finger0r.addOrReplaceChild("fmem0_r", mirrored(356, 0, -42.0F, -0.5F, 1.0F, 42, 1, 30), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition finger1r = winghandr.addOrReplaceChild("finger1_r", mirrored(230, 118, -36.0F, -1.0F, -1.0F, 36, 2, 2), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F));
		finger1r.addOrReplaceChild("fmem1_r", mirrored(88, 50, -36.0F, -0.5F, 1.0F, 36, 1, 26), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition finger2r = winghandr.addOrReplaceChild("finger2_r", mirrored(306, 118, -30.0F, -1.0F, -1.0F, 30, 2, 2), PartPose.offsetAndRotation(-1.5F, 0.0F, 0.0F, 0.0F, 1.75F, 0.0F));
		finger2r.addOrReplaceChild("fmem2_r", mirrored(268, 80, -30.0F, -0.5F, 1.0F, 30, 1, 18), PartPose.offset(0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 512, 512);
	}

	/** Smoothstep, clamped: eases a 0..1 share in and out. */
	static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Eases up from 0 to 1 between {@code a} and {@code b}. */
	static float rise(float x, float a, float b) {
		return ease((x - a) / (b - a));
	}

	/** A smooth bump over {@code a}..{@code b}: 0 at both ends (with no slope there), 1 in the middle. */
	static float bell(float x, float a, float b) {
		if (x <= a || x >= b) {
			return 0.0F;
		}
		float s = Mth.sin((x - a) / (b - a) * Mth.PI);
		return s * s;
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float fly = ease(s.fly), land = ease(s.land) * (1.0F - fly);
		float crash = ease(s.crash) * (1.0F - fly), sit = ease(s.sit) * (1.0F - crash) * (1.0F - fly) * (1.0F - land);
		float glide = ease(s.glide) * fly;
		float aloft = Math.max(fly, land);
		float onGround = 1.0F - aloft;
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * onGround * (1.0F - sit) * (1.0F - crash);
		float step = s.walkAnimationPos * 0.45F;
		float tuck = fly;
		float headYaw = Mth.clamp(s.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
		float breath = ease(s.breath);

		// the moves, each a 0..1 envelope that begins and ends at rest
		int a = s.action;
		float x = AetherionDragon.actionShare(a, s.actionAge);
		float lunge = a == AetherionDragon.BITE_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float snap = a == AetherionDragon.BITE_MOVE ? rise(x, 0.0F, 0.35F) * (1.0F - rise(x, 0.45F, 0.6F)) : 0.0F;
		// the Cataclysm: reared up and roaring with the wings flung high (to 0.55), then down with them beating
		float rear = a == AetherionDragon.CATACLYSM_MOVE ? rise(x, 0.0F, 0.3F) * (1.0F - rise(x, 0.55F, 0.75F)) : 0.0F;
		float slam = a == AetherionDragon.CATACLYSM_MOVE ? bell(x, 0.55F, 1.0F) : 0.0F;
		float roar = a == AetherionDragon.CATACLYSM_MOVE ? bell(x, 0.15F, 0.9F) : 0.0F;

		// the body: high on its legs, swaying a little as it walks; pitched with its climb in the air; reared up for the Cataclysm
		this.body.y = this.bodyY + sit * SIT_DROP + crash * CRASH_DROP + Math.abs(Mth.sin(step)) * 0.6F * walk + Mth.sin(t * 0.07F) * 0.2F * onGround
			- rear * 4.0F;
		this.body.xRot = s.pitch * fly - sit * 0.12F - rear * 0.55F + slam * 0.1F;
		this.body.zRot = Mth.sin(step) * 0.02F * walk;
		this.body.yRot = Mth.sin(step) * 0.04F * walk;

		// the neck: its S on the ground, straightened along its line of flight in the air, stretched out to breathe and to
		// bite, thrown up roaring; each segment follows a beat behind the one before
		float straight = Math.max(aloft * 0.7F, Math.max(breath * 0.5F, lunge * 0.6F));
		for (int i = 0; i < NECK; i++) {
			float lag = i * 0.6F;
			float sway = Mth.sin(t * 0.05F - lag) * 0.03F * onGround + Mth.sin(step * 2.0F - lag) * 0.02F * walk;
			this.neck[i].xRot = this.neckRest[i] * (1.0F - straight) + sway - (i < 2 ? lunge * 0.15F : -lunge * 0.1F) - roar * (i < 2 ? 0.2F : 0.05F)
				- s.pitch * fly * 0.25F + crash * 0.12F + sit * 0.05F;
			this.neck[i].yRot = headYaw * 0.2F - this.body.yRot * 0.4F;
		}
		this.head.xRot = this.headRest * (1.0F - straight * 0.6F) + headPitch * 0.5F + lunge * 0.25F - roar * 0.35F - crash * 0.2F;
		this.head.yRot = headYaw * 0.2F;
		this.jaw.xRot = 0.03F + (0.5F - 0.5F * Mth.cos(t * 0.04F)) * 0.05F + breath * 0.65F + snap * 0.75F + roar * 0.85F;

		// the tail: swaying behind, a beat per segment; streaming out level in the air
		for (int i = 0; i < TAIL; i++) {
			float k = i / (float)(TAIL - 1);
			float lag = i * 0.65F;
			float sway = Mth.sin(t * 0.06F - lag) * (0.03F + k * 0.04F) * (1.0F - aloft * 0.5F) + Mth.sin(step - lag) * (0.06F + k * 0.04F) * walk;
			this.tail[i].yRot = sway - this.body.yRot * 0.3F;
			this.tail[i].xRot = this.tailRest[i] * (1.0F - aloft * 0.8F) - s.pitch * fly * (i == 0 ? 0.5F : 0.0F) + Mth.sin(t * 0.1F - lag) * 0.03F * aloft
				+ (i == 0 ? rear * 0.5F + sit * 0.15F : 0.0F);
		}

		// legs: a long diagonal gait on the ground; tucked back in the air; folded sitting; splayed crashed
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.4F * walk;
			float lift = (1.0F - Mth.sin(step + phase)) * 0.5F;
			float knee = lift * lift * 0.45F * walk;
			float up = this.upperRest[i] + swing, low = this.lowerRest[i] + (front ? -knee : knee);
			if (front) {
				// (hovering to land, they hang down ready for the ground)
				up = Mth.lerp(land, up, 0.3F);
				low = Mth.lerp(land, low, -0.2F);
				up = Mth.lerp(rear, up, -0.6F);
				low = Mth.lerp(rear, low, -0.9F);
			} else {
				up = Mth.lerp(land, up, 0.2F);
				low = Mth.lerp(land, low, 0.4F);
				up = Mth.lerp(sit, up, -0.9F);
				low = Mth.lerp(sit, low, 1.5F);
				up += rear * 0.5F;
			}
			up = Mth.lerp(crash, up, front ? -0.6F : 0.8F);
			// (Andy: "its legs should collapse very close to its body in flight, so much so it looks like it doesn't have any
			// in the air") flying, each leg folds flat back under the belly, the shin folded up against it, and draws in
			// small into the body
			up = Mth.lerp(tuck, up, front ? TUCK_FRONT_UP : TUCK_HIND_UP);
			low = Mth.lerp(tuck, low, front ? TUCK_FRONT_LOW : TUCK_HIND_LOW);
			float legScale = 1.0F - tuck * TUCK_SHRINK;
			this.upper[i].xScale = this.upper[i].yScale = this.upper[i].zScale = legScale;
			this.upper[i].y = this.upperY[i] - tuck * TUCK_RAISE;
			this.upper[i].xRot = up;
			this.upper[i].zRot = (left ? -1.0F : 1.0F) * crash * 0.7F;
			this.lower[i].xRot = low;
			// the foot kept flat on the ground, pointed back in the air
			float flat = -(up + low - this.upperRest[i] - this.lowerRest[i]);
			this.foot[i].xRot = Mth.lerp(tuck, this.footRest[i] + flat * onGround * (1.0F - crash) + land * 0.4F, TUCK_FOOT);
		}

		// the wings: beating in time with its stroke, each joint a beat behind the one before it (held open gliding);
		// folded on the ground; flung up for the Cataclysm and beating as it comes down
		float beat = fly * (1.0F - glide) + land + slam;
		float open = Math.max(aloft, Math.max(rear, slam));
		float fold = (1.0F - open) * (1.0F - crash * 0.5F);
		float p = s.flap + (slam > 0.0F ? t * 0.5F : 0.0F);
		for (int w = 0; w < 2; w++) {
			float sx = w == 0 ? 1.0F : -1.0F;
			float armUp = BEAT_LIFT + BEAT_ARM * Mth.sin(p) * beat + glide * 0.05F + rear * 1.0F;
			float foreUp = BEAT_FORE * Mth.sin(p - BEAT_LAG) * beat + glide * 0.04F - rear * 0.25F;
			float handUp = BEAT_HAND * Mth.sin(p - BEAT_LAG * 2.0F) * beat + glide * 0.06F - rear * 0.2F;
			// (crashed, they lie spread and limp on the ground)
			armUp -= crash * 0.35F;
			this.arm[w].zRot = -sx * Mth.lerp(fold, armUp, -FOLD_ARM_Z);
			this.arm[w].yRot = sx * Mth.lerp(fold, Mth.cos(p) * 0.14F * beat - rear * 0.2F, FOLD_ARM_Y);
			this.fore[w].zRot = -sx * Mth.lerp(fold, foreUp, -FOLD_FORE_Z);
			this.fore[w].yRot = sx * fold * FOLD_FORE_Y;
			this.hand[w].zRot = -sx * Mth.lerp(fold, handUp, -FOLD_HAND_Z);
			// the fingers spread a little on the downstroke, closed folded
			float spread = 1.0F + Mth.cos(p - BEAT_LAG) * 0.08F * beat;
			for (int f = 0; f < FINGERS; f++) {
				this.finger[w][f].yRot = this.fingerRest[w][f] * Mth.lerp(fold, spread, 1.0F - FOLD_CLOSE);
			}
		}

		// its look: horns, a lost spine; its tack
		DragonLook look = s.look;
		this.tipL.visible = this.tipR.visible = look.horns() != DragonLook.SHORT_HORNS;
		this.lowL.visible = this.lowR.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spines[i].visible = look.spike(i) && !(s.saddled && i >= 1 && i <= 3);
		}
		this.saddle.visible = this.strap.visible = s.saddled;
	}
}
