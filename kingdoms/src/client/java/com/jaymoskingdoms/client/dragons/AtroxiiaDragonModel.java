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
 * (0.7.513) Atroxiia, our own model (blocks, as the Kingdoms beasts are; nothing of Saint's Dragons' art is used, see
 * {@code docs/SaintsDragons-Plan.md}): a tall, gaunt frost wyrm (Andy: "longer limbs and necks, and be taller, to be
 * imposing"). High shoulders on long straight forelegs, digitigrade hind legs, a serpent's neck rising in six segments
 * to a narrow head under swept-back horns (long, short, or long with a crown of thin brow tines, by its
 * {@link DragonLook}), cheek frills, five ice crystals down its spine (the middle three give way to the saddle), and a
 * long tail that arches up over its hips and ends in a spear of ice with a crossguard of shards. Texture 256x256
 * ({@code tools/gen_atroxiia.py} prints the body plan below from its own table, so the UVs match the paint).
 * <p>
 * Posed procedurally from its render state, every joint eased (Andy: "all the joints should move and blend smoothly"):
 * each move is a smoothstep envelope that starts and ends at rest, and the neck's and tail's segments each follow a
 * beat behind the one before. It walks with its diagonal legs together, its spine waving and its neck swaying against
 * it; in the water it tucks its legs back, stretches its neck out and sculls with its uncurled tail. Its moves
 * ({@link AtroxiiaDragon#action}): the slam rears and stamps a forefoot, the swipe swings a claw across, the precise
 * strike jabs the tail spear forward three times over its back, Gungnir coils the tail back and drives it forward
 * straight as a spear, the sweep swings the tail round low, and the Helheim quake rears twice and comes down with the
 * jaw wide. Crashed it sinks to its belly with its neck along the ground; sitting, it settles on its haunches.
 */
final class AtroxiiaDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How low it sinks crashed and sitting (model pixels). */
	static final float CRASH_DROP = 20.0F, SIT_DROP = 9.0F;
	static final int NECK = 6, TAIL = 7;

	private final ModelPart body, head, jaw, frillL, frillR;
	private final ModelPart[] neck = new ModelPart[NECK], tail = new ModelPart[TAIL];
	private final float[] neckRest = new float[NECK], tailRest = new float[TAIL];
	private final float headRest;
	private final ModelPart hornL, hornR, shortL, shortR, crownL, crownR;
	private final ModelPart[] spines = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final float[] upperRest = new float[4], lowerRest = new float[4];
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
		this.frillL = this.head.getChild("frill_l");
		this.frillR = this.head.getChild("frill_r");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.crownL = this.head.getChild("crown_l");
		this.crownR = this.head.getChild("crown_r");
		ModelPart parent = this.body;
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
		this.pad = this.body.getChild("pad");
		String[] legs = {"left_front", "right_front", "left_hind", "right_hind"};
		for (int i = 0; i < 4; i++) {
			this.upper[i] = this.body.getChild(legs[i] + "_leg");
			this.lower[i] = this.upper[i].getChild(legs[i] + "_shin");
			this.foot[i] = this.lower[i].getChild(legs[i] + "_foot");
			this.upperRest[i] = this.upper[i].xRot;
			this.lowerRest[i] = this.lower[i].xRot;
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
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -8.0F, -8.0F, -15.0F, 16, 16, 30), PartPose.offset(0.0F, -8.0F, 0.0F));
		body.addOrReplaceChild("chest", box(20, 46, -7.0F, 0.0F, -7.0F, 14, 4, 14), PartPose.offset(0.0F, 8.0F, -7.0F));
		body.addOrReplaceChild("belly", box(178, 65, -6.0F, 0.0F, -6.0F, 12, 2, 12), PartPose.offset(0.0F, 8.0F, 7.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(76, 46, -5.5F, -5.0F, -8.0F, 11, 10, 8), PartPose.offsetAndRotation(0.0F, -3.0F, -14.0F, -0.62F, 0.0F, 0.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(0, 65, -5.0F, -4.5F, -7.0F, 10, 9, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, -0.22F, 0.0F, 0.0F));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", box(66, 65, -4.5F, -4.0F, -7.0F, 9, 8, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, -0.12F, 0.0F, 0.0F));
		PartDefinition neck3 = neck2.addOrReplaceChild("neck3", box(98, 65, -4.0F, -4.0F, -7.0F, 8, 8, 7), PartPose.offset(0.0F, 0.0F, -6.0F));
		PartDefinition neck4 = neck3.addOrReplaceChild("neck4", box(70, 81, -4.0F, -3.5F, -6.0F, 8, 7, 6), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.12F, 0.0F, 0.0F));
		PartDefinition neck5 = neck4.addOrReplaceChild("neck5", box(98, 81, -3.5F, -3.5F, -6.0F, 7, 7, 6), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.2F, 0.0F, 0.0F));
		PartDefinition head = neck5.addOrReplaceChild("head", box(114, 46, -4.5F, -4.0F, -11.0F, 9, 7, 11), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.55F, 0.0F, 0.0F));
		head.addOrReplaceChild("snout", box(124, 81, -3.5F, -2.5F, -8.0F, 7, 5, 8), PartPose.offset(0.0F, -0.5F, -11.0F));
		head.addOrReplaceChild("jaw", box(146, 0, -3.5F, 0.0F, -18.0F, 7, 2, 18), PartPose.offset(0.0F, 3.0F, 0.0F));
		head.addOrReplaceChild("brow", box(128, 95, -5.0F, -1.0F, -2.0F, 10, 2, 4), PartPose.offset(0.0F, -4.0F, -8.0F));
		head.addOrReplaceChild("eye_l", box(192, 95, -0.5F, -1.0F, -1.0F, 1, 2, 2), PartPose.offset(4.5F, -2.0F, -8.0F));
		head.addOrReplaceChild("eye_r", box(192, 95, -0.5F, -1.0F, -1.0F, 1, 2, 2), PartPose.offset(-4.5F, -2.0F, -8.0F));
		head.addOrReplaceChild("horn_l", box(128, 65, -1.0F, -1.0F, 0.0F, 2, 2, 13), PartPose.offsetAndRotation(3.5F, -3.5F, -3.0F, 0.42F, 0.28F, 0.0F));
		head.addOrReplaceChild("horn_r", box(128, 65, -1.0F, -1.0F, 0.0F, 2, 2, 13), PartPose.offsetAndRotation(-3.5F, -3.5F, -3.0F, 0.42F, -0.28F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(82, 95, -1.0F, -1.0F, 0.0F, 2, 2, 6), PartPose.offsetAndRotation(3.5F, -3.5F, -3.0F, 0.5F, 0.28F, 0.0F));
		head.addOrReplaceChild("short_horn_r", box(82, 95, -1.0F, -1.0F, 0.0F, 2, 2, 6), PartPose.offsetAndRotation(-3.5F, -3.5F, -3.0F, 0.5F, -0.28F, 0.0F));
		head.addOrReplaceChild("crown_l", box(64, 95, -0.5F, -0.5F, 0.0F, 1, 1, 8), PartPose.offsetAndRotation(4.0F, -1.5F, -5.0F, 0.15F, 0.62F, 0.0F));
		head.addOrReplaceChild("crown_r", box(64, 95, -0.5F, -0.5F, 0.0F, 1, 1, 8), PartPose.offsetAndRotation(-4.0F, -1.5F, -5.0F, 0.15F, -0.62F, 0.0F));
		head.addOrReplaceChild("frill_l", box(200, 81, 0.0F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(4.5F, 1.0F, -4.0F, 0.0F, 0.35F, 0.0F));
		head.addOrReplaceChild("frill_r", mirrored(200, 81, -1.0F, -2.5F, 0.0F, 1, 5, 6), PartPose.offsetAndRotation(-4.5F, 1.0F, -4.0F, 0.0F, -0.35F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(154, 46, -4.5F, -4.5F, 0.0F, 9, 9, 9), PartPose.offsetAndRotation(0.0F, -3.0F, 14.0F, 0.22F, 0.0F, 0.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(190, 46, -4.0F, -4.0F, 0.0F, 8, 8, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.26F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(34, 65, -3.5F, -3.5F, 0.0F, 7, 7, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.3F, 0.0F, 0.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(226, 65, -3.0F, -3.0F, 0.0F, 6, 6, 8), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.34F, 0.0F, 0.0F));
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", box(154, 81, -2.5F, -2.5F, 0.0F, 5, 5, 8), PartPose.offsetAndRotation(0.0F, 0.3F, 7.0F, 0.36F, 0.0F, 0.0F));
		PartDefinition tail5 = tail4.addOrReplaceChild("tail5", box(214, 81, -2.0F, -2.0F, 0.0F, 4, 4, 7), PartPose.offsetAndRotation(0.0F, 0.3F, 7.0F, 0.34F, 0.0F, 0.0F));
		PartDefinition tail6 = tail5.addOrReplaceChild("tail6", box(236, 81, -1.5F, -1.5F, 0.0F, 3, 3, 7), PartPose.offsetAndRotation(0.0F, 0.3F, 6.0F, 0.3F, 0.0F, 0.0F));
		PartDefinition spear = tail6.addOrReplaceChild("spear", box(98, 95, -1.0F, -1.0F, 0.0F, 2, 2, 6), PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.1F, 0.0F, 0.0F));
		spear.addOrReplaceChild("blade", box(0, 81, -0.5F, -2.0F, 0.0F, 1, 4, 10), PartPose.offset(0.0F, 0.0F, 5.0F));
		spear.addOrReplaceChild("guard", box(198, 95, -3.5F, -0.5F, 0.0F, 7, 1, 2), PartPose.offset(0.0F, 0.0F, 4.0F));
		tail1.addOrReplaceChild("tail_shard0", box(120, 95, -0.5F, -4.0F, -1.5F, 1, 4, 3), PartPose.offsetAndRotation(0.0F, -3.5F, 4.0F, -0.3F, 0.0F, 0.0F));
		tail3.addOrReplaceChild("tail_shard1", box(120, 95, -0.5F, -4.0F, -1.5F, 1, 4, 3), PartPose.offsetAndRotation(0.0F, -2.5F, 4.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(22, 81, -6.0F, -2.0F, -6.0F, 12, 2, 12), PartPose.offset(0.0F, -8.0F, -4.0F));
		saddle.addOrReplaceChild("pommel", box(156, 95, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -6.0F));
		saddle.addOrReplaceChild("cantle", box(168, 95, -5.0F, -3.0F, 0.0F, 10, 3, 2), PartPose.offset(0.0F, -2.0F, 4.0F));
		body.addOrReplaceChild("strap", box(196, 0, -8.5F, -8.5F, -1.5F, 17, 17, 3), PartPose.offset(0.0F, 0.0F, -4.0F));
		body.addOrReplaceChild("pad", box(0, 95, -5.0F, -2.0F, -4.0F, 10, 2, 8), PartPose.offset(0.0F, -8.0F, 7.0F));
		body.addOrReplaceChild("spine0", box(180, 81, -1.0F, -9.0F, -1.5F, 2, 9, 3), PartPose.offsetAndRotation(0.0F, -7.0F, -11.0F, -0.32F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine1", box(158, 65, -1.0F, -12.0F, -1.5F, 2, 12, 3), PartPose.offsetAndRotation(0.0F, -7.0F, -5.5F, -0.32F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine2", box(224, 46, -1.0F, -14.0F, -1.5F, 2, 14, 3), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, -0.32F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine3", box(168, 65, -1.0F, -12.0F, -1.5F, 2, 12, 3), PartPose.offsetAndRotation(0.0F, -7.0F, 5.5F, -0.32F, 0.0F, 0.0F));
		body.addOrReplaceChild("spine4", box(190, 81, -1.0F, -9.0F, -1.5F, 2, 9, 3), PartPose.offsetAndRotation(0.0F, -7.0F, 11.0F, -0.32F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard_l", box(114, 95, -0.5F, -6.0F, -1.0F, 1, 6, 2), PartPose.offsetAndRotation(6.0F, -6.0F, -10.0F, -0.2F, 0.0F, 0.5F));
		body.addOrReplaceChild("shard_r", mirrored(114, 95, -0.5F, -6.0F, -1.0F, 1, 6, 2), PartPose.offsetAndRotation(-6.0F, -6.0F, -10.0F, -0.2F, 0.0F, -0.5F));
		PartDefinition leftfrontleg = body.addOrReplaceChild("left_front_leg", box(122, 0, -3.0F, -3.0F, -3.0F, 6, 15, 6), PartPose.offset(7.0F, 4.0F, -9.0F));
		PartDefinition leftfrontshin = leftfrontleg.addOrReplaceChild("left_front_shin", box(0, 46, -2.5F, 0.0F, -2.5F, 5, 14, 5), PartPose.offset(0.0F, 12.0F, 0.0F));
		leftfrontshin.addOrReplaceChild("left_front_foot", box(36, 95, -3.0F, 0.0F, -5.0F, 6, 2, 8), PartPose.offset(0.0F, 14.0F, 0.0F));
		PartDefinition rightfrontleg = body.addOrReplaceChild("right_front_leg", mirrored(122, 0, -3.0F, -3.0F, -3.0F, 6, 15, 6), PartPose.offset(-7.0F, 4.0F, -9.0F));
		PartDefinition rightfrontshin = rightfrontleg.addOrReplaceChild("right_front_shin", mirrored(0, 46, -2.5F, 0.0F, -2.5F, 5, 14, 5), PartPose.offset(0.0F, 12.0F, 0.0F));
		rightfrontshin.addOrReplaceChild("right_front_foot", mirrored(36, 95, -3.0F, 0.0F, -5.0F, 6, 2, 8), PartPose.offset(0.0F, 14.0F, 0.0F));
		PartDefinition lefthindleg = body.addOrReplaceChild("left_hind_leg", box(92, 0, -3.5F, -3.0F, -4.0F, 7, 15, 8), PartPose.offsetAndRotation(7.0F, 4.0F, 10.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition lefthindshin = lefthindleg.addOrReplaceChild("left_hind_shin", box(236, 0, -2.5F, 0.0F, -2.5F, 5, 15, 5), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.6F, 0.0F, 0.0F));
		lefthindshin.addOrReplaceChild("left_hind_foot", box(36, 95, -3.0F, 0.0F, -5.0F, 6, 2, 8), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition righthindleg = body.addOrReplaceChild("right_hind_leg", mirrored(92, 0, -3.5F, -3.0F, -4.0F, 7, 15, 8), PartPose.offsetAndRotation(-7.0F, 4.0F, 10.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition righthindshin = righthindleg.addOrReplaceChild("right_hind_shin", mirrored(236, 0, -2.5F, 0.0F, -2.5F, 5, 15, 5), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.6F, 0.0F, 0.0F));
		righthindshin.addOrReplaceChild("right_hind_foot", mirrored(36, 95, -3.0F, 0.0F, -5.0F, 6, 2, 8), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, -0.3F, 0.0F, 0.0F));

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
		float step = s.walkAnimationPos * 0.5F;
		float headYaw = Mth.clamp(s.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
		float x = AtroxiiaDragon.actionShare(s.action, s.actionAge);
		int a = s.action;

		// the moves, each a 0..1 envelope that begins and ends at rest
		float slam = a == AtroxiiaDragon.SLAM_MOVE ? rise(x, 0.0F, 0.5F) * (1.0F - rise(x, 0.5F, 0.65F)) : 0.0F;
		float slamDown = a == AtroxiiaDragon.SLAM_MOVE ? rise(x, 0.5F, 0.62F) * (1.0F - rise(x, 0.7F, 1.0F)) : 0.0F;
		float swipe = a == AtroxiiaDragon.SWIPE_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float swipeAcross = a == AtroxiiaDragon.SWIPE_MOVE ? rise(x, 0.25F, 0.65F) : 0.0F;
		// the precise strike: the tail raised over the back, then three jabs
		float strike = a == AtroxiiaDragon.STRIKE_MOVE ? rise(x, 0.0F, 0.2F) * (1.0F - rise(x, 0.8F, 1.0F)) : 0.0F;
		float jab = a == AtroxiiaDragon.STRIKE_MOVE ? bell(x, 0.18F, 0.36F) + bell(x, 0.41F, 0.59F) + bell(x, 0.64F, 0.82F) : 0.0F;
		// Gungnir: coiled back through the wind-up, then driven out straight, then drawn home
		float coil = a == AtroxiiaDragon.GUNGNIR_MOVE ? rise(x, 0.0F, 0.5F) * (1.0F - rise(x, 0.5F, 0.6F)) : 0.0F;
		float thrust = a == AtroxiiaDragon.GUNGNIR_MOVE ? rise(x, 0.5F, 0.6F) * (1.0F - rise(x, 0.72F, 1.0F)) : 0.0F;
		// the sweep: the tail swung round low and back
		float sweep = a == AtroxiiaDragon.SWEEP_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float sweepTurn = a == AtroxiiaDragon.SWEEP_MOVE ? Mth.sin(rise(x, 0.1F, 0.8F) * Mth.PI * 2.0F) : 0.0F;
		// the Helheim quake: reared and brought down twice, the jaw wide throughout
		float rear = a == AtroxiiaDragon.QUAKE_MOVE ? rise(x, 0.0F, 0.4F) * (1.0F - rise(x, 0.4F, 0.46F))
			+ rise(x, 0.5F, 0.68F) * (1.0F - rise(x, 0.68F, 0.74F)) : 0.0F;
		float roar = a == AtroxiiaDragon.QUAKE_MOVE ? rise(x, 0.0F, 0.15F) * (1.0F - rise(x, 0.8F, 1.0F)) : 0.0F;
		rear += slam * 0.6F;

		// the body: high and swaying, sunk crashed, settled back sitting, reared for the slam and the quake
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP + Math.abs(Mth.sin(step)) * 0.5F * walk + Mth.sin(t * 0.07F) * 0.2F
			- rear * 4.0F + slamDown * 1.5F + coil * 1.5F;
		this.body.xRot = -rear * 0.42F + slamDown * 0.08F - sit * 0.2F + coil * 0.08F - thrust * 0.06F;
		// (its spine waves as it walks, and sweeps round with the tail)
		this.body.yRot = Mth.sin(step) * 0.06F * walk + Mth.sin(t * 0.12F) * 0.08F * swim + sweepTurn * 0.35F;
		this.body.zRot = Mth.sin(step) * 0.03F * walk;

		// the neck: each segment a beat behind the one before, swaying against the spine
		float neckDown = crash * 1.8F + swim * 0.9F + sit * 0.1F - roar * 0.25F + slamDown * 0.3F + swipe * 0.2F + coil * 0.15F;
		for (int i = 0; i < NECK; i++) {
			float share = (NECK - i) / 21.0F;
			this.neck[i].xRot = this.neckRest[i] * (1.0F - crash * 0.8F) + neckDown * share
				+ Mth.sin(step * 2.0F - i * 0.5F) * 0.03F * walk + Mth.sin(t * 0.05F - i * 0.45F) * 0.02F;
			this.neck[i].yRot = headYaw * 0.11F - Mth.sin(step - i * 0.55F) * 0.05F * walk + Mth.sin(t * 0.12F - i * 0.5F) * 0.08F * swim
				- this.body.yRot * 0.25F;
		}
		this.head.xRot = this.headRest + headPitch * 0.5F - neckDown * 0.45F - roar * 0.3F;
		this.head.yRot = headYaw * 0.2F;
		this.jaw.xRot = 0.05F + roar * 0.8F + (0.5F - 0.5F * Mth.cos(t * 0.04F)) * 0.04F + swipe * 0.25F + thrust * 0.3F;
		this.frillL.yRot = 0.35F + roar * 0.4F + Mth.sin(t * 0.09F) * 0.04F;
		this.frillR.yRot = -this.frillL.yRot;

		// the tail: arched over the hips; it uncurls in the water and crashed, rises and jabs in the strike, coils and
		// drives out in Gungnir, and swings round low in the sweep (each segment a beat behind the one before)
		float uncurl = Math.max(swim * 0.85F, Math.max(crash * 0.75F, sweep * 0.6F));
		for (int i = 0; i < TAIL; i++) {
			float k = i / (float)(TAIL - 1);
			float rx = this.tailRest[i] * (1.0F - uncurl);
			// the strike lifts the root and the jabs throw the spear end forward
			rx += strike * (i < 2 ? 0.25F : 0.05F) + jab * (i < 3 ? 0.18F : -0.12F);
			// Gungnir: coiled tighter, then the root swung forward and the rest straightened into a spear
			rx += coil * 0.22F + thrust * (i == 0 ? 0.95F : i == 1 ? 0.5F : -this.tailRest[i] * 0.9F);
			rx += -walk * 0.03F + Mth.sin(t * 0.06F - i * 0.5F) * 0.02F;
			this.tail[i].xRot = rx;
			this.tail[i].yRot = Mth.sin(step - i * 0.6F) * 0.06F * walk + Mth.sin(t * 0.05F - i * 0.5F) * 0.04F
				+ Mth.sin(t * 0.25F - i * 0.7F) * (0.12F + k * 0.1F) * swim + sweep * 0.55F * (1.0F - k * 0.5F) - sweepTurn * 0.15F;
		}

		// legs: forelegs swing as pillars; the hind legs fold at the hock; the diagonal pairs move together
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.42F * walk;
			// (the knee folds as the leg swings forward and straightens as it plants: a smooth curve)
			float lift = (1.0F - Mth.sin(step + phase)) * 0.5F;
			float knee = lift * lift * 0.5F * walk;
			float up = this.upperRest[i] + swing, low = this.lowerRest[i] + (front ? knee : knee * 1.3F);
			if (front) {
				// reared: both forelegs lifted, the slam's left one stamped; the swipe's left one swung across
				float raise = left ? Math.max(rear, slam) : rear;
				up = Mth.lerp(raise, up, -0.9F);
				low = Mth.lerp(raise, low, 1.1F);
				if (left) {
					up = Mth.lerp(swipe, up, -1.25F);
					low = Mth.lerp(swipe, low, 0.5F);
				}
			} else {
				up = Mth.lerp(sit, up, -1.1F);
				low = Mth.lerp(sit, low, 1.7F);
				up = Mth.lerp(rear * 0.5F, up, this.upperRest[i] - 0.25F);
			}
			up = Mth.lerp(crash, up, front ? -1.0F : 0.9F);
			low = Mth.lerp(crash, low, front ? 1.4F : -0.2F);
			// swimming: tucked back along the body, paddling slowly
			up = Mth.lerp(swim, up, 1.1F + Mth.sin(t * 0.2F + phase) * 0.25F);
			low = Mth.lerp(swim, low, 0.4F + Mth.sin(t * 0.2F + phase - 0.5F) * 0.15F);
			this.upper[i].xRot = up;
			this.upper[i].zRot = (left ? -1.0F : 1.0F) * (crash * 0.55F + (front && left ? swipeAcross * swipe * 0.6F : 0.0F));
			this.lower[i].xRot = low;
			// the foot kept flat on the ground
			this.foot[i].xRot = -(up + low) * (1.0F - swim);
		}

		// its look: horns, a lost crystal; its tack
		DragonLook look = s.look;
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		this.hornL.visible = this.hornR.visible = !shortHorns;
		this.shortL.visible = this.shortR.visible = shortHorns;
		this.crownL.visible = this.crownR.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			boolean tack = s.saddled && i >= 1 && i <= 3;
			this.spines[i].visible = look.spike(i) && !tack;
		}
		this.saddle.visible = this.strap.visible = this.pad.visible = s.saddled;
	}
}
