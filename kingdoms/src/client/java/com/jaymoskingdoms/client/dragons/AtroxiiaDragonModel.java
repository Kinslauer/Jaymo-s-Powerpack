package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.AtroxiiaDragon;
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
 * (0.7.522) Atroxiia, our own model (blocks, as the Kingdoms beasts are; nothing of Saint's Dragons' art is used, see
 * {@code docs/SaintsDragons-Plan.md}). Andy: "atroxiia does NOT look right": it is a serpent with two great arms and no
 * hind legs. Its chest is held high on the arms (broad shoulders, long bowed upper arms, long forearms with gold cuffs,
 * three-clawed hands and a thumb), the serpent body runs back down to the ground and along it as a long tail, its
 * third support, rising at the end into a spear of ice bound in gold. A short raised neck carries a head under a
 * headdress of striped hide: a cap and gold brow band, two lappets hanging behind (long or short by its
 * {@link DragonLook}; a brow crystal on some) and a braid, dark lines round its eyes, ice crystals down its back (the
 * first gives way to the pillion pad). Texture 256x256 ({@code tools/gen_atroxiia.py} prints the body plan below).
 * <p>
 * Posed procedurally, every joint eased (Andy: "all the joints should move and blend smoothly"): each move is a
 * smoothstep envelope that starts and ends at rest, and the neck's and the tail's segments each follow a beat behind
 * the one before. It walks on its arms in turn, shoulders rolling, the serpent body snaking behind; in the water the
 * arms fold back and the whole body undulates. Its moves ({@link AtroxiiaDragon#action}): the slam raises an arm
 * overhead and brings it down, the swipe rakes a hand across, the precise strike jabs with both hands in turn,
 * Gungnir arches the tail up over its back and drives the spear forward, the sweep swings the tail round, and the
 * Helheim quake rears the chest up twice and brings both arms down with the jaw wide.
 */
final class AtroxiiaDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How low its chest sinks crashed and sitting (model pixels). */
	static final float CRASH_DROP = 20.0F, SIT_DROP = 8.0F;
	static final int NECK = 3, TAIL = 11;

	private final ModelPart body, head, jaw, braid;
	private final ModelPart[] neck = new ModelPart[NECK], tail = new ModelPart[TAIL];
	private final float[] neckRest = new float[NECK], tailRest = new float[TAIL];
	private final float headRest;
	private final ModelPart nemesL, nemesR, shortL, shortR, uraeus;
	private final ModelPart[] spines = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap, pad;
	/** The arms, left then right: upper arm, forearm, hand, its three fingers and thumb. */
	private final ModelPart[] upper = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
	private final ModelPart[][] fingers = new ModelPart[2][4];
	private final float[] upperX = new float[2], upperZ = new float[2], foreX = new float[2], foreZ = new float[2];
	private final float bodyY;

	AtroxiiaDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart up = this.body;
		for (int i = 0; i < NECK; i++) {
			this.neck[i] = up.getChild("neck" + i);
			this.neckRest[i] = this.neck[i].xRot;
			up = this.neck[i];
		}
		this.head = up.getChild("head");
		this.headRest = this.head.xRot;
		this.jaw = this.head.getChild("jaw");
		this.braid = this.head.getChild("braid");
		this.nemesL = this.head.getChild("nemes_l");
		this.nemesR = this.head.getChild("nemes_r");
		this.shortL = this.head.getChild("short_nemes_l");
		this.shortR = this.head.getChild("short_nemes_r");
		this.uraeus = this.head.getChild("uraeus");
		ModelPart parent = this.body;
		for (int i = 0; i < TAIL; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			this.tailRest[i] = this.tail[i].xRot;
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spines[i] = this.tail[i].getChild("spine" + i);
		}
		this.saddle = this.body.getChild("saddle");
		this.strap = this.body.getChild("strap");
		this.pad = this.tail[0].getChild("pad");
		String[] sides = {"left", "right"};
		for (int i = 0; i < 2; i++) {
			this.upper[i] = this.body.getChild(sides[i] + "_arm");
			this.fore[i] = this.upper[i].getChild(sides[i] + "_forearm");
			this.hand[i] = this.fore[i].getChild(sides[i] + "_hand");
			for (int f = 0; f < 3; f++) {
				this.fingers[i][f] = this.hand[i].getChild(sides[i] + "_finger" + f);
			}
			this.fingers[i][3] = this.hand[i].getChild(sides[i] + "_thumb");
			this.upperX[i] = this.upper[i].xRot;
			this.upperZ[i] = this.upper[i].zRot;
			this.foreX[i] = this.fore[i].xRot;
			this.foreZ[i] = this.fore[i].zRot;
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
		// (printed by tools/gen_atroxiia.py)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -9.0F, -8.0F, -10.0F, 18, 16, 20), PartPose.offset(0.0F, -14.0F, -6.0F));
		body.addOrReplaceChild("chest", box(118, 83, -8.0F, 0.0F, -8.0F, 16, 3, 14), PartPose.offset(0.0F, 8.0F, -1.0F));
		body.addOrReplaceChild("collar", box(208, 118, -9.5F, -2.0F, -1.5F, 19, 4, 3), PartPose.offset(0.0F, -5.0F, -9.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(0, 83, -5.5F, -5.5F, -8.0F, 11, 11, 8), PartPose.offsetAndRotation(0.0F, -4.0F, -9.0F, -0.75F, 0.0F, 0.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(178, 83, -5.0F, -5.0F, -7.0F, 10, 10, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, -0.25F, 0.0F, 0.0F));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", box(34, 102, -4.5F, -4.5F, -6.0F, 9, 9, 6), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.15F, 0.0F, 0.0F));
		PartDefinition head = neck2.addOrReplaceChild("head", box(150, 61, -6.0F, -5.0F, -11.0F, 12, 9, 11), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.75F, 0.0F, 0.0F));
		head.addOrReplaceChild("snout", box(64, 102, -4.5F, -3.0F, -9.0F, 9, 6, 9), PartPose.offset(0.0F, 0.0F, -11.0F));
		head.addOrReplaceChild("jaw", box(0, 61, -4.5F, 0.0F, -19.0F, 9, 3, 19), PartPose.offset(0.0F, 3.5F, 0.0F));
		head.addOrReplaceChild("eye_l", box(28, 131, -0.5F, -1.0F, -1.5F, 1, 2, 3), PartPose.offset(6.0F, -2.0F, -8.0F));
		head.addOrReplaceChild("eye_r", box(28, 131, -0.5F, -1.0F, -1.5F, 1, 2, 3), PartPose.offset(-6.0F, -2.0F, -8.0F));
		head.addOrReplaceChild("cap", box(82, 118, -6.5F, -1.0F, -5.0F, 13, 2, 10), PartPose.offset(0.0F, -5.0F, -5.5F));
		head.addOrReplaceChild("band", box(36, 131, -6.5F, -1.0F, -1.5F, 13, 2, 3), PartPose.offset(0.0F, -5.0F, -10.0F));
		head.addOrReplaceChild("nemes_l", box(208, 0, 0.0F, 0.0F, 0.0F, 1, 14, 11), PartPose.offsetAndRotation(6.0F, -4.0F, -6.0F, 0.32F, 0.22F, -0.18F));
		head.addOrReplaceChild("nemes_r", mirrored(208, 0, -1.0F, 0.0F, 0.0F, 1, 14, 11), PartPose.offsetAndRotation(-6.0F, -4.0F, -6.0F, 0.32F, -0.22F, 0.18F));
		head.addOrReplaceChild("short_nemes_l", box(212, 83, 0.0F, 0.0F, 0.0F, 1, 8, 9), PartPose.offsetAndRotation(6.0F, -4.0F, -6.0F, 0.32F, 0.22F, -0.18F));
		head.addOrReplaceChild("short_nemes_r", mirrored(212, 83, -1.0F, 0.0F, 0.0F, 1, 8, 9), PartPose.offsetAndRotation(-6.0F, -4.0F, -6.0F, 0.32F, -0.22F, 0.18F));
		head.addOrReplaceChild("braid", box(138, 118, -2.0F, 0.0F, 0.0F, 4, 9, 2), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.35F, 0.0F, 0.0F));
		head.addOrReplaceChild("uraeus", box(200, 118, -1.0F, -6.0F, -1.0F, 2, 6, 2), PartPose.offsetAndRotation(0.0F, -5.5F, -9.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(152, 0, -8.0F, -7.0F, 0.0F, 16, 14, 12), PartPose.offsetAndRotation(0.0F, -2.0F, 9.0F, -0.5F, 0.0F, 0.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(0, 36, -7.5F, -6.5F, 0.0F, 15, 13, 12), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, -0.55F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(54, 36, -7.0F, -6.0F, 0.0F, 14, 12, 12), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, -0.45F, 0.0F, 0.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(106, 36, -6.5F, -5.5F, 0.0F, 13, 11, 12), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.9F, 0.0F, 0.0F));
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", box(56, 61, -6.0F, -5.0F, 0.0F, 12, 10, 12), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.5F, 0.0F, 0.0F));
		PartDefinition tail5 = tail4.addOrReplaceChild("tail5", box(104, 61, -5.5F, -4.5F, 0.0F, 11, 9, 12), PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.15F, 0.0F, 0.0F));
		PartDefinition tail6 = tail5.addOrReplaceChild("tail6", box(38, 83, -5.0F, -4.0F, 0.0F, 10, 8, 11), PartPose.offset(0.0F, 0.0F, 11.0F));
		PartDefinition tail7 = tail6.addOrReplaceChild("tail7", box(80, 83, -4.0F, -3.5F, 0.0F, 8, 7, 11), PartPose.offset(0.0F, 0.0F, 10.0F));
		PartDefinition tail8 = tail7.addOrReplaceChild("tail8", box(0, 102, -3.5F, -3.0F, 0.0F, 7, 6, 10), PartPose.offsetAndRotation(0.0F, 0.0F, 10.0F, 0.22F, 0.0F, 0.0F));
		PartDefinition tail9 = tail8.addOrReplaceChild("tail9", box(100, 102, -3.0F, -2.5F, 0.0F, 6, 5, 10), PartPose.offsetAndRotation(0.0F, 0.0F, 9.0F, 0.3F, 0.0F, 0.0F));
		PartDefinition tail10 = tail9.addOrReplaceChild("tail10", box(204, 102, -2.0F, -2.0F, 0.0F, 4, 4, 9), PartPose.offsetAndRotation(0.0F, 0.0F, 9.0F, 0.35F, 0.0F, 0.0F));
		PartDefinition spear = tail10.addOrReplaceChild("spear", box(0, 131, -1.5F, -1.5F, 0.0F, 3, 3, 4), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.1F, 0.0F, 0.0F));
		spear.addOrReplaceChild("blade", box(156, 36, -1.0F, -3.5F, 0.0F, 2, 7, 16), PartPose.offset(0.0F, 0.0F, 3.5F));
		spear.addOrReplaceChild("guard", box(104, 131, -5.0F, -1.0F, 0.0F, 10, 2, 2), PartPose.offset(0.0F, 0.0F, 2.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(142, 102, -7.0F, -2.0F, -6.0F, 14, 2, 12), PartPose.offset(0.0F, -8.0F, 1.0F));
		saddle.addOrReplaceChild("pommel", box(68, 131, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -6.0F));
		saddle.addOrReplaceChild("cantle", box(80, 131, -5.0F, -3.0F, 0.0F, 10, 3, 2), PartPose.offset(0.0F, -2.0F, 4.0F));
		body.addOrReplaceChild("strap", box(196, 61, -9.5F, -8.5F, -1.5F, 19, 17, 3), PartPose.offset(0.0F, 0.0F, 1.0F));
		tail0.addOrReplaceChild("pad", box(150, 118, -6.0F, -2.0F, -4.0F, 12, 2, 8), PartPose.offset(0.0F, -7.0F, 6.0F));
		tail0.addOrReplaceChild("spine0", box(230, 102, -1.0F, -10.0F, -1.5F, 2, 10, 3), PartPose.offsetAndRotation(0.0F, -6.0F, 6.0F, -0.4F, 0.0F, 0.0F));
		tail1.addOrReplaceChild("spine1", box(132, 102, -1.0F, -12.0F, -1.5F, 2, 12, 3), PartPose.offsetAndRotation(0.0F, -5.5F, 6.0F, -0.4F, 0.0F, 0.0F));
		tail2.addOrReplaceChild("spine2", box(194, 102, -1.0F, -11.0F, -1.5F, 2, 11, 3), PartPose.offsetAndRotation(0.0F, -5.0F, 6.0F, -0.4F, 0.0F, 0.0F));
		tail3.addOrReplaceChild("spine3", box(128, 118, -1.0F, -9.0F, -1.5F, 2, 9, 3), PartPose.offsetAndRotation(0.0F, -4.5F, 6.0F, -0.4F, 0.0F, 0.0F));
		tail4.addOrReplaceChild("spine4", box(190, 118, -1.0F, -7.0F, -1.5F, 2, 7, 3), PartPose.offsetAndRotation(0.0F, -4.0F, 6.0F, -0.4F, 0.0F, 0.0F));
		PartDefinition leftarm = body.addOrReplaceChild("left_arm", box(76, 0, -5.0F, -3.0F, -5.0F, 10, 21, 10), PartPose.offsetAndRotation(10.0F, -3.0F, -5.0F, -0.1F, 0.0F, -0.45F));
		leftarm.addOrReplaceChild("left_shoulder", box(192, 36, -6.5F, -5.0F, -6.5F, 13, 10, 13), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition leftforearm = leftarm.addOrReplaceChild("left_forearm", box(116, 0, -4.5F, 0.0F, -4.5F, 9, 22, 9), PartPose.offsetAndRotation(0.0F, 17.5F, 0.0F, 0.1F, 0.0F, 0.45F));
		leftforearm.addOrReplaceChild("left_cuff", box(0, 118, -5.0F, 0.0F, -5.0F, 10, 3, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
		PartDefinition lefthand = leftforearm.addOrReplaceChild("left_hand", box(40, 118, -5.5F, 0.0F, -7.0F, 11, 3, 10), PartPose.offset(0.0F, 22.3F, 0.0F));
		lefthand.addOrReplaceChild("left_finger0", box(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(-3.6F, 1.0F, -7.0F, 0.0F, 0.15F, 0.0F));
		lefthand.addOrReplaceChild("left_finger1", box(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offset(0.0F, 1.0F, -7.0F));
		lefthand.addOrReplaceChild("left_finger2", box(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(3.6F, 1.0F, -7.0F, 0.0F, -0.15F, 0.0F));
		lefthand.addOrReplaceChild("left_thumb", box(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(-5.5F, 1.0F, -3.0F, 0.0F, 0.7F, 0.0F));
		PartDefinition rightarm = body.addOrReplaceChild("right_arm", mirrored(76, 0, -5.0F, -3.0F, -5.0F, 10, 21, 10), PartPose.offsetAndRotation(-10.0F, -3.0F, -5.0F, -0.1F, 0.0F, 0.45F));
		rightarm.addOrReplaceChild("right_shoulder", mirrored(192, 36, -6.5F, -5.0F, -6.5F, 13, 10, 13), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition rightforearm = rightarm.addOrReplaceChild("right_forearm", mirrored(116, 0, -4.5F, 0.0F, -4.5F, 9, 22, 9), PartPose.offsetAndRotation(0.0F, 17.5F, 0.0F, 0.1F, 0.0F, -0.45F));
		rightforearm.addOrReplaceChild("right_cuff", mirrored(0, 118, -5.0F, 0.0F, -5.0F, 10, 3, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
		PartDefinition righthand = rightforearm.addOrReplaceChild("right_hand", mirrored(40, 118, -5.5F, 0.0F, -7.0F, 11, 3, 10), PartPose.offset(0.0F, 22.3F, 0.0F));
		righthand.addOrReplaceChild("right_finger0", mirrored(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(-3.6F, 1.0F, -7.0F, 0.0F, 0.15F, 0.0F));
		righthand.addOrReplaceChild("right_finger1", mirrored(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offset(0.0F, 1.0F, -7.0F));
		righthand.addOrReplaceChild("right_finger2", mirrored(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(3.6F, 1.0F, -7.0F, 0.0F, -0.15F, 0.0F));
		righthand.addOrReplaceChild("right_thumb", mirrored(14, 131, -1.0F, 0.0F, -5.0F, 2, 2, 5), PartPose.offsetAndRotation(5.5F, 1.0F, -3.0F, 0.0F, -0.7F, 0.0F));

		return LayerDefinition.create(mesh, 256, 256);
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
		float crash = ease(s.crash), swim = ease(s.swim) * (1.0F - crash);
		float sit = ease(s.sit) * (1.0F - crash) * (1.0F - swim);
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - crash) * (1.0F - sit) * (1.0F - swim);
		float step = s.walkAnimationPos * 0.45F;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
		float x = AtroxiiaDragon.actionShare(s.action, s.actionAge);
		int a = s.action;

		// the moves, each a 0..1 envelope that begins and ends at rest
		float slamUp = a == AtroxiiaDragon.SLAM_MOVE ? rise(x, 0.0F, 0.5F) * (1.0F - rise(x, 0.5F, 0.62F)) : 0.0F;
		float slamDown = a == AtroxiiaDragon.SLAM_MOVE ? rise(x, 0.5F, 0.62F) * (1.0F - rise(x, 0.7F, 1.0F)) : 0.0F;
		float swipe = a == AtroxiiaDragon.SWIPE_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float swipeAcross = a == AtroxiiaDragon.SWIPE_MOVE ? rise(x, 0.2F, 0.7F) : 0.0F;
		// the precise strike: a jab of the left hand, the right, the left
		float jabL = a == AtroxiiaDragon.STRIKE_MOVE ? bell(x, 0.14F, 0.38F) + bell(x, 0.62F, 0.86F) : 0.0F;
		float jabR = a == AtroxiiaDragon.STRIKE_MOVE ? bell(x, 0.38F, 0.62F) : 0.0F;
		// Gungnir: the tail arched up over its back through the wind-up, then the spear driven forward, then drawn home
		float arch = a == AtroxiiaDragon.GUNGNIR_MOVE ? rise(x, 0.0F, 0.45F) * (1.0F - rise(x, 0.75F, 1.0F)) : 0.0F;
		float thrust = a == AtroxiiaDragon.GUNGNIR_MOVE ? rise(x, 0.5F, 0.6F) * (1.0F - rise(x, 0.7F, 0.95F)) : 0.0F;
		// the sweep: the tail swung round and back
		float sweep = a == AtroxiiaDragon.SWEEP_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		// the Helheim quake: the chest reared and brought down twice, the jaw wide throughout
		float rear = a == AtroxiiaDragon.QUAKE_MOVE ? rise(x, 0.0F, 0.4F) * (1.0F - rise(x, 0.4F, 0.46F))
			+ rise(x, 0.5F, 0.68F) * (1.0F - rise(x, 0.68F, 0.74F)) : 0.0F;
		float roar = a == AtroxiiaDragon.QUAKE_MOVE ? rise(x, 0.0F, 0.15F) * (1.0F - rise(x, 0.8F, 1.0F)) : 0.0F;

		// the chest: high on the arms, rolling with each step, sunk crashed, lower sitting, reared for the quake
		float drop = crash * CRASH_DROP + sit * SIT_DROP + swim * 6.0F - rear * 6.0F + slamDown * 1.5F + thrust * 1.5F;
		this.body.y = this.bodyY + drop + Math.abs(Mth.sin(step)) * 0.8F * walk + Mth.sin(t * 0.07F) * 0.25F;
		this.body.xRot = -rear * 0.45F - slamUp * 0.12F + slamDown * 0.08F + thrust * 0.06F + swim * 0.15F;
		this.body.zRot = Mth.sin(step) * 0.06F * walk;
		this.body.yRot = Mth.sin(step) * 0.07F * walk + Mth.sin(t * 0.12F) * 0.06F * swim - swipeAcross * swipe * 0.15F;

		// the neck: each segment a beat behind the one before
		float neckDown = crash * 1.0F + swim * 0.6F - roar * 0.25F + slamDown * 0.25F + thrust * 0.2F;
		for (int i = 0; i < NECK; i++) {
			float share = (NECK - i) / 6.0F;
			this.neck[i].xRot = this.neckRest[i] + neckDown * share - this.body.xRot * (i == 0 ? 0.6F : 0.0F)
				+ Mth.sin(step * 2.0F - i * 0.5F) * 0.03F * walk + Mth.sin(t * 0.05F - i * 0.45F) * 0.02F;
			this.neck[i].yRot = headYaw * 0.2F - this.body.yRot * (i == 0 ? 0.6F : 0.0F) - Mth.sin(step - i * 0.6F) * 0.04F * walk;
		}
		this.head.xRot = this.headRest + headPitch * 0.5F - neckDown * 0.4F - roar * 0.3F;
		this.head.yRot = headYaw * 0.3F;
		this.jaw.xRot = 0.04F + roar * 0.8F + (0.5F - 0.5F * Mth.cos(t * 0.04F)) * 0.04F + swipe * 0.25F + thrust * 0.3F + s.breath * 0.4F;
		this.braid.xRot = 0.35F + Mth.sin(t * 0.08F) * 0.04F + walk * 0.1F - this.head.xRot * 0.3F;
		this.nemesL.zRot = -0.18F - Mth.sin(t * 0.07F) * 0.02F - roar * 0.12F;
		this.nemesR.zRot = -this.nemesL.zRot;
		this.shortL.zRot = this.nemesL.zRot;
		this.shortR.zRot = this.nemesR.zRot;

		// the serpent body and tail: the root keeps its run down to the ground as the chest sinks or rears; the rest
		// lies along the ground, snaking as it goes (each segment a beat behind), sculling in the water; it arches up
		// over its back for Gungnir and swings round for the sweep
		float lift = drop / CRASH_DROP;
		for (int i = 0; i < TAIL; i++) {
			float k = i / (float)(TAIL - 1);
			float rx = this.tailRest[i];
			if (i <= 2) {
				// (the chest lower: the root runs down less steeply; reared: more)
				rx += lift * 0.45F - (i == 0 ? this.body.xRot : 0.0F);
			} else if (i == 3) {
				rx -= lift * 1.2F;
			}
			// Gungnir: from the ground up into a scorpion's coil (eased), then the whole arc swung forward past its head
			if (i >= 3) {
				rx += arch * 0.27F + thrust * (i == 3 ? 0.7F : i == 4 ? 0.3F : -0.12F);
			}
			rx += Mth.sin(t * 0.06F - i * 0.5F) * 0.015F;
			this.tail[i].xRot = rx;
			this.tail[i].yRot = Mth.sin(step * 1.2F - i * 0.55F) * (0.04F + k * 0.08F) * walk + Mth.sin(t * 0.05F - i * 0.5F) * 0.03F
				+ Mth.sin(t * 0.22F - i * 0.6F) * (0.08F + k * 0.1F) * swim + sweep * 0.32F * (1.0F - k * 0.3F) * (i >= 2 ? 1.0F : 0.3F)
				- (i == 0 ? this.body.yRot : 0.0F);
		}

		// the arms: walking on them in turn (the hand lifts and the elbow folds as it swings forward, a smooth curve)
		for (int i = 0; i < 2; i++) {
			boolean left = i == 0;
			float side = left ? 1.0F : -1.0F;
			float phase = left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.45F * walk;
			float raise = (1.0F - Mth.sin(step + phase)) * 0.5F;
			float elbow = raise * raise * 0.6F * walk;
			float up = this.upperX[i] + swing, low = this.foreX[i] + elbow;
			float uz = this.upperZ[i], lz = this.foreZ[i];
			// the slam (left): raised high, then brought down; the swipe (right): swung out and raked across
			if (left) {
				up = Mth.lerp(slamUp, up, -2.4F);
				low = Mth.lerp(slamUp, low, -0.2F);
				up = Mth.lerp(slamDown, up, -0.6F);
				low = Mth.lerp(slamDown, low, 0.3F);
			} else {
				up = Mth.lerp(swipe, up, -1.3F);
				low = Mth.lerp(swipe, low, 0.4F);
				uz = Mth.lerp(swipe, uz, this.upperZ[i] + side * (0.8F - swipeAcross * 1.4F));
			}
			// the strike's jabs: straight ahead
			float jab = left ? jabL : jabR;
			up = Mth.lerp(jab, up, -1.4F);
			low = Mth.lerp(jab, low, -0.1F);
			// the quake: both raised as it rears, slammed as it lands
			up = Mth.lerp(rear, up, -1.9F);
			low = Mth.lerp(rear, low, 0.2F);
			// sitting: folded before it; crashed: splayed out flat; swimming: swept back along its flanks
			up = Mth.lerp(sit, up, -0.5F);
			low = Mth.lerp(sit, low, 1.1F);
			up = Mth.lerp(crash, up, -0.4F);
			uz = Mth.lerp(crash, uz, -side * 1.2F);
			low = Mth.lerp(crash, low, 0.2F);
			lz = Mth.lerp(crash, lz, side * 0.9F);
			up = Mth.lerp(swim, up, 1.2F + Mth.sin(t * 0.2F + phase) * 0.2F);
			low = Mth.lerp(swim, low, 0.3F);
			this.upper[i].xRot = up;
			this.upper[i].zRot = uz;
			this.fore[i].xRot = low;
			this.fore[i].zRot = lz;
			// the hand kept flat on the ground as it walks; it opens raised and in the strikes, and grips crashed
			float flat = 1.0F - Math.max(Math.max(slamUp * (left ? 1.0F : 0.0F), jab), swim);
			this.hand[i].xRot = -(up + low) * flat + (1.0F - flat) * -0.3F;
			this.hand[i].zRot = -(uz + lz) * flat;
			float curl = elbow * 0.6F + crash * 0.3F + (left ? slamDown : swipe) * 0.5F - jab * 0.2F;
			for (int f = 0; f < 4; f++) {
				this.fingers[i][f].xRot = curl * (f == 3 ? 0.6F : 1.0F);
			}
		}

		// its look: the headdress, a lost crystal; its tack
		DragonLook look = s.look;
		boolean shortNemes = look.horns() == DragonLook.SHORT_HORNS;
		this.nemesL.visible = this.nemesR.visible = !shortNemes;
		this.shortL.visible = this.shortR.visible = shortNemes;
		this.uraeus.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.spines[i].visible = look.spike(i) && !(s.saddled && i == 0);
		}
		this.saddle.visible = this.strap.visible = this.pad.visible = s.saddled;
	}
}
