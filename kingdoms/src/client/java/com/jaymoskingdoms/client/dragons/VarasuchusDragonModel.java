package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.VarasuchusDragon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.522) Varasuchus, our own model (blocks, as the Kingdoms beasts are; nothing of Saint's Dragons' art is used, see
 * {@code docs/SaintsDragons-Plan.md}): a crocodile dragon held high on long sprawling legs (Andy: "longer limbs ... and
 * be taller, to be imposing"). A broad armoured body under two rows of bony scutes (the middle three pairs give way to
 * the saddle), a thick neck, a long-jawed head with its eyes on its crown and horns swept up and back from it (long,
 * short, or long with a nose horn, by its {@link DragonLook}), and a great tail with a double crest that runs into one.
 * Texture 256x256 ({@code tools/gen_varasuchus.py} prints the body plan below from its own table, so the UVs match).
 * <p>
 * Posed procedurally from its render state, every joint eased (Andy: "all the joints should move and blend smoothly"):
 * each move is a smoothstep envelope that starts and ends at rest, and the tail's segments each follow a beat behind
 * the one before, so its sway rolls down it. It walks in a high crocodile's gait, body swaying and tail snaking; in the
 * water its legs fold flat back and the tail sculls. Its moves ({@link VarasuchusDragon#action}): a claw raked down,
 * the horns dipped and tossed, the dash low and fast with the tail lashed round at its end, a barrage of claws from
 * both forelegs, the phase shift's crouch, the tailguard curling the tail round before it (and snapping out on a
 * parry), and its rage, head flung up and jaws wide. Crashed it lies flat on its belly; sitting, it settles back.
 * <p>
 * (0.7.612, docs/SaintsDragons-Animation-Spec.md) The rest of a crocodile's day: the plain bite (drawn back, jaws wide,
 * snapped shut), the tail sweep (lashed round to one side and back, the body twisting into it), a gallop when it runs
 * (front and hind pairs together, the body rocking), a flinch at a blow, its death (legs locked out, jaw slack, tail
 * limp), and idle: now and then a hiss, a head shake, a tail swish or a slow sniff along the ground; sitting, it basks
 * with its jaws propped open; floating still, its legs tread slowly.
 */
final class VarasuchusDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How low it sinks crashed and sitting (model pixels). */
	static final float CRASH_DROP = 17.0F, SIT_DROP = 7.0F;
	static final int TAIL = 12;
	/** How the tail's bend is shared down it when it wraps round to the front (most at the root, so it runs along the flank). */
	static final float[] WRAP = {0.6F, 0.7F, 0.7F, 0.6F, 0.3F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F, 0.02F};
	/** Upright: how far the body tips back, and its hips (body-relative, model pixels) that stay put as it does. */
	static final float STAND_TILT = 1.05F, HIP_Y = 3.0F, HIP_Z = 13.0F;

	private final ModelPart body, neck0, neck1, head, jaw;
	private final float neck0Rest, neck1Rest, headRest;
	private final ModelPart[] tail = new ModelPart[TAIL];
	private final float[] tailRest = new float[TAIL];
	private final ModelPart hornL, hornR, tipL, tipR, shortL, shortR, noseHorn;
	private final ModelPart[] scutesL = new ModelPart[DragonLook.SPIKES], scutesR = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final float[] upperZ = new float[4], lowerZ = new float[4];
	private final float bodyY, bodyZ;

	VarasuchusDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		this.bodyZ = this.body.z;
		this.neck0 = this.body.getChild("neck0");
		this.neck1 = this.neck0.getChild("neck1");
		this.head = this.neck1.getChild("head");
		this.neck0Rest = this.neck0.xRot;
		this.neck1Rest = this.neck1.xRot;
		this.headRest = this.head.xRot;
		this.jaw = this.head.getChild("jaw");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.tipL = this.hornL.getChild("horn_tip_l");
		this.tipR = this.hornR.getChild("horn_tip_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.noseHorn = this.head.getChild("snout").getChild("nose_horn");
		ModelPart parent = this.body;
		for (int i = 0; i < TAIL; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			this.tailRest[i] = this.tail[i].xRot;
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.scutesL[i] = this.body.getChild("scute" + i + "_l");
			this.scutesR[i] = this.body.getChild("scute" + i + "_r");
		}
		this.saddle = this.body.getChild("saddle");
		this.strap = this.body.getChild("strap");
		this.pad = this.body.getChild("pad");
		String[] legs = {"left_front", "right_front", "left_hind", "right_hind"};
		for (int i = 0; i < 4; i++) {
			this.upper[i] = this.body.getChild(legs[i] + "_leg");
			this.lower[i] = this.upper[i].getChild(legs[i] + "_shin");
			this.foot[i] = this.lower[i].getChild(legs[i] + "_foot");
			this.upperZ[i] = this.upper[i].zRot;
			this.lowerZ[i] = this.lower[i].zRot;
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
		// (printed by tools/gen_varasuchus.py)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -11.0F, -7.0F, -20.0F, 22, 14, 40), PartPose.offset(0.0F, -9.0F, 0.0F));
		body.addOrReplaceChild("belly", box(0, 54, -9.0F, 0.0F, -16.0F, 18, 2, 32), PartPose.offset(0.0F, 7.0F, 0.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(0, 113, -8.0F, -6.0F, -8.0F, 16, 12, 8), PartPose.offsetAndRotation(0.0F, -1.0F, -19.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(190, 113, -7.5F, -5.5F, -7.0F, 15, 11, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, -0.1F, 0.0F, 0.0F));
		PartDefinition head = neck1.addOrReplaceChild("head", box(100, 54, -9.0F, -6.5F, -14.0F, 18, 12, 14), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.36F, 0.0F, 0.0F));
		PartDefinition snout = head.addOrReplaceChild("snout", box(164, 54, -7.0F, -3.5F, -18.0F, 14, 8, 18), PartPose.offset(0.0F, -0.5F, -14.0F));
		snout.addOrReplaceChild("snout_tip", box(180, 151, -7.5F, -4.0F, -4.0F, 15, 8, 4), PartPose.offset(0.0F, 0.0F, -15.0F));
		head.addOrReplaceChild("jaw", box(124, 0, -7.5F, 0.0F, -33.0F, 15, 4, 33), PartPose.offset(0.0F, 5.5F, -1.0F));
		head.addOrReplaceChild("brow", box(116, 166, -9.5F, -1.5F, -2.5F, 19, 3, 5), PartPose.offset(0.0F, -6.5F, -10.0F));
		head.addOrReplaceChild("cheek_l", box(170, 133, 0.0F, -3.5F, -4.5F, 3, 7, 9), PartPose.offset(9.0F, 1.0F, -6.0F));
		head.addOrReplaceChild("cheek_r", mirrored(170, 133, -3.0F, -3.5F, -4.5F, 3, 7, 9), PartPose.offset(-9.0F, 1.0F, -6.0F));
		head.addOrReplaceChild("eye_l", box(192, 166, -1.5F, -2.0F, -1.5F, 3, 2, 3), PartPose.offset(6.0F, -7.0F, -8.0F));
		head.addOrReplaceChild("eye_r", box(192, 166, -1.5F, -2.0F, -1.5F, 3, 2, 3), PartPose.offset(-6.0F, -7.0F, -8.0F));
		PartDefinition hornl = head.addOrReplaceChild("horn_l", box(0, 133, -2.0F, -2.0F, 0.0F, 4, 4, 14), PartPose.offsetAndRotation(6.5F, -5.5F, -3.0F, 0.62F, 0.38F, 0.0F));
		PartDefinition hornr = head.addOrReplaceChild("horn_r", box(0, 133, -2.0F, -2.0F, 0.0F, 4, 4, 14), PartPose.offsetAndRotation(-6.5F, -5.5F, -3.0F, 0.62F, -0.38F, 0.0F));
		hornl.addOrReplaceChild("horn_tip_l", box(26, 166, -1.5F, -1.5F, 0.0F, 3, 3, 7), PartPose.offsetAndRotation(0.0F, 0.0F, 13.0F, 0.4F, 0.0F, 0.0F));
		hornr.addOrReplaceChild("horn_tip_r", box(26, 166, -1.5F, -1.5F, 0.0F, 3, 3, 7), PartPose.offsetAndRotation(0.0F, 0.0F, 13.0F, 0.4F, 0.0F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(218, 151, -2.0F, -2.0F, 0.0F, 4, 4, 8), PartPose.offsetAndRotation(6.5F, -5.5F, -3.0F, 0.7F, 0.38F, 0.0F));
		head.addOrReplaceChild("short_horn_r", box(218, 151, -2.0F, -2.0F, 0.0F, 4, 4, 8), PartPose.offsetAndRotation(-6.5F, -5.5F, -3.0F, 0.7F, -0.38F, 0.0F));
		snout.addOrReplaceChild("nose_horn", box(164, 166, -1.5F, -5.0F, -1.5F, 3, 5, 3), PartPose.offsetAndRotation(0.0F, -3.5F, -13.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(38, 88, -8.5F, -6.0F, 0.0F, 17, 12, 10), PartPose.offsetAndRotation(0.0F, -2.0F, 19.0F, -0.2F, 0.0F, 0.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(144, 88, -8.0F, -5.5F, 0.0F, 16, 11, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, -0.1F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(48, 113, -7.5F, -5.0F, 0.0F, 15, 10, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, -0.08F, 0.0F, 0.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(98, 113, -7.0F, -4.5F, 0.0F, 14, 9, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, -0.05F, 0.0F, 0.0F));
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", box(146, 113, -6.0F, -4.5F, 0.0F, 12, 9, 10), PartPose.offset(0.0F, 0.3F, 9.0F));
		PartDefinition tail5 = tail4.addOrReplaceChild("tail5", box(36, 133, -5.5F, -4.0F, 0.0F, 11, 8, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.04F, 0.0F, 0.0F));
		PartDefinition tail6 = tail5.addOrReplaceChild("tail6", box(130, 133, -5.0F, -3.5F, 0.0F, 10, 7, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.05F, 0.0F, 0.0F));
		PartDefinition tail7 = tail6.addOrReplaceChild("tail7", box(194, 133, -4.5F, -3.0F, 0.0F, 9, 6, 10), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.05F, 0.0F, 0.0F));
		PartDefinition tail8 = tail7.addOrReplaceChild("tail8", box(0, 151, -4.0F, -3.0F, 0.0F, 8, 6, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 9.0F, 0.04F, 0.0F, 0.0F));
		PartDefinition tail9 = tail8.addOrReplaceChild("tail9", box(34, 151, -3.0F, -2.5F, 0.0F, 6, 5, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.03F, 0.0F, 0.0F));
		PartDefinition tail10 = tail9.addOrReplaceChild("tail10", box(116, 151, -2.5F, -2.0F, 0.0F, 5, 4, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.03F, 0.0F, 0.0F));
		PartDefinition tail11 = tail10.addOrReplaceChild("tail11", box(0, 166, -2.0F, -1.5F, 0.0F, 4, 3, 9), PartPose.offsetAndRotation(0.0F, 0.3F, 8.0F, 0.03F, 0.0F, 0.0F));
		tail1.addOrReplaceChild("crest1_l", box(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(6.0F, -5.5F, 1.5F));
		tail1.addOrReplaceChild("crest1_r", mirrored(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(-6.0F, -5.5F, 1.5F));
		tail2.addOrReplaceChild("crest2_l", box(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(5.5F, -5.0F, 1.5F));
		tail2.addOrReplaceChild("crest2_r", mirrored(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(-5.5F, -5.0F, 1.5F));
		tail3.addOrReplaceChild("crest3_l", box(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(5.0F, -4.5F, 1.5F));
		tail3.addOrReplaceChild("crest3_r", mirrored(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(-5.0F, -4.5F, 1.5F));
		tail4.addOrReplaceChild("crest4_l", box(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(4.0F, -4.5F, 1.5F));
		tail4.addOrReplaceChild("crest4_r", mirrored(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(-4.0F, -4.5F, 1.5F));
		tail5.addOrReplaceChild("crest5_l", box(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(3.5F, -4.0F, 1.5F));
		tail5.addOrReplaceChild("crest5_r", mirrored(46, 166, -0.5F, -3.0F, 0.0F, 1, 3, 7), PartPose.offset(-3.5F, -4.0F, 1.5F));
		tail6.addOrReplaceChild("crest6", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -3.5F, 1.5F));
		tail7.addOrReplaceChild("crest7", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -3.0F, 1.5F));
		tail8.addOrReplaceChild("crest8", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -3.0F, 1.5F));
		tail9.addOrReplaceChild("crest9", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -2.5F, 1.5F));
		tail10.addOrReplaceChild("crest10", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -2.0F, 1.5F));
		tail11.addOrReplaceChild("crest11", box(102, 166, -0.5F, -3.0F, 0.0F, 1, 3, 6), PartPose.offset(0.0F, -1.5F, 1.5F));
		body.addOrReplaceChild("scute0_l", box(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(4.0F, -7.0F, -15.0F));
		body.addOrReplaceChild("scute0_r", mirrored(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(-4.0F, -7.0F, -12.0F));
		body.addOrReplaceChild("scute1_l", box(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(4.0F, -7.0F, -7.5F));
		body.addOrReplaceChild("scute1_r", mirrored(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(-4.0F, -7.0F, -4.5F));
		body.addOrReplaceChild("scute2_l", box(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(4.0F, -7.0F, 0.0F));
		body.addOrReplaceChild("scute2_r", mirrored(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(-4.0F, -7.0F, 3.0F));
		body.addOrReplaceChild("scute3_l", box(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(4.0F, -7.0F, 7.5F));
		body.addOrReplaceChild("scute3_r", mirrored(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(-4.0F, -7.0F, 10.5F));
		body.addOrReplaceChild("scute4_l", box(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(4.0F, -7.0F, 15.0F));
		body.addOrReplaceChild("scute4_r", mirrored(176, 166, -1.5F, -3.0F, -2.5F, 3, 3, 5), PartPose.offset(-4.0F, -7.0F, 18.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(64, 151, -7.0F, -2.0F, -6.0F, 14, 2, 12), PartPose.offset(0.0F, -7.0F, -6.0F));
		saddle.addOrReplaceChild("pommel", box(204, 166, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -6.0F));
		saddle.addOrReplaceChild("cantle", box(216, 166, -6.0F, -3.0F, 0.0F, 12, 3, 2), PartPose.offset(0.0F, -2.0F, 4.0F));
		body.addOrReplaceChild("strap", box(78, 133, -11.5F, -7.5F, -1.5F, 23, 15, 3), PartPose.offset(0.0F, 0.0F, -6.0F));
		body.addOrReplaceChild("pad", box(62, 166, -6.0F, -2.0F, -4.0F, 12, 2, 8), PartPose.offset(0.0F, -7.0F, 7.0F));
		PartDefinition leftfrontleg = body.addOrReplaceChild("left_front_leg", box(92, 88, -3.5F, -2.0F, -3.5F, 7, 15, 7), PartPose.offsetAndRotation(9.5F, 3.0F, -13.0F, 0.0F, 0.0F, -0.35F));
		PartDefinition leftfrontshin = leftfrontleg.addOrReplaceChild("left_front_shin", box(196, 88, -2.5F, 0.0F, -2.5F, 5, 16, 5), PartPose.offsetAndRotation(0.0F, 12.7F, 0.0F, 0.0F, 0.0F, 0.35F));
		leftfrontshin.addOrReplaceChild("left_front_foot", box(144, 151, -4.0F, 0.0F, -7.0F, 8, 3, 10), PartPose.offset(0.0F, 15.0F, 0.0F));
		PartDefinition rightfrontleg = body.addOrReplaceChild("right_front_leg", mirrored(92, 88, -3.5F, -2.0F, -3.5F, 7, 15, 7), PartPose.offsetAndRotation(-9.5F, 3.0F, -13.0F, 0.0F, 0.0F, 0.35F));
		PartDefinition rightfrontshin = rightfrontleg.addOrReplaceChild("right_front_shin", mirrored(196, 88, -2.5F, 0.0F, -2.5F, 5, 16, 5), PartPose.offsetAndRotation(0.0F, 12.7F, 0.0F, 0.0F, 0.0F, -0.35F));
		rightfrontshin.addOrReplaceChild("right_front_foot", mirrored(144, 151, -4.0F, 0.0F, -7.0F, 8, 3, 10), PartPose.offset(0.0F, 15.0F, 0.0F));
		PartDefinition lefthindleg = body.addOrReplaceChild("left_hind_leg", box(0, 88, -4.5F, -2.0F, -5.0F, 9, 15, 10), PartPose.offsetAndRotation(9.5F, 3.0F, 13.0F, 0.0F, 0.0F, -0.35F));
		PartDefinition lefthindshin = lefthindleg.addOrReplaceChild("left_hind_shin", box(120, 88, -3.0F, 0.0F, -3.0F, 6, 16, 6), PartPose.offsetAndRotation(0.0F, 12.7F, 0.0F, 0.0F, 0.0F, 0.35F));
		lefthindshin.addOrReplaceChild("left_hind_foot", box(144, 151, -4.0F, 0.0F, -7.0F, 8, 3, 10), PartPose.offset(0.0F, 15.0F, 0.0F));
		PartDefinition righthindleg = body.addOrReplaceChild("right_hind_leg", mirrored(0, 88, -4.5F, -2.0F, -5.0F, 9, 15, 10), PartPose.offsetAndRotation(-9.5F, 3.0F, 13.0F, 0.0F, 0.0F, 0.35F));
		PartDefinition righthindshin = righthindleg.addOrReplaceChild("right_hind_shin", mirrored(120, 88, -3.0F, 0.0F, -3.0F, 6, 16, 6), PartPose.offsetAndRotation(0.0F, 12.7F, 0.0F, 0.0F, 0.0F, -0.35F));
		righthindshin.addOrReplaceChild("right_hind_foot", mirrored(144, 151, -4.0F, 0.0F, -7.0F, 8, 3, 10), PartPose.offset(0.0F, 15.0F, 0.0F));

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
		float dead = ease(s.deathTime / 20.0F);
		float crash = ease(s.crash), swim = ease(s.swim) * (1.0F - crash);
		float sit = ease(s.sit) * (1.0F - crash) * (1.0F - swim);
		int a = s.action;
		float x = VarasuchusDragon.actionShare(a, s.actionAge);
		float age = s.actionAge;
		float dash = a == VarasuchusDragon.DASH_MOVE ? rise(x, 0.0F, 0.12F) * (1.0F - rise(x, 0.62F, 0.8F)) : 0.0F;
		float walk = Mth.clamp(s.walkAnimationSpeed + dash * 0.6F, 0.0F, 1.0F) * (1.0F - crash) * (1.0F - sit) * (1.0F - swim) * (1.0F - dead);
		// (0.7.612) at a run it gallops, as crocodiles can: the front pair and the hind pair each together, the body rocking
		float run = ease((s.walkAnimationSpeed + dash * 0.6F - 0.55F) / 0.35F) * walk;
		float step = s.walkAnimationPos * 0.5F;
		float headYaw = Mth.clamp(s.yRot, -45.0F, 45.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;

		// the moves, each a 0..1 envelope that begins and ends at rest
		float claw = a == VarasuchusDragon.CLAW_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float clawDown = a == VarasuchusDragon.CLAW_MOVE ? rise(x, 0.3F, 0.6F) : 0.0F;
		float dip = a == VarasuchusDragon.HORN_MOVE ? rise(x, 0.0F, 0.35F) * (1.0F - rise(x, 0.35F, 0.55F)) : 0.0F;
		float toss = a == VarasuchusDragon.HORN_MOVE ? bell(x, 0.35F, 1.0F) : 0.0F;
		// the barrage: both forelegs raking in turn, the turns eased (a cosine, no kinks)
		float barrage = a == VarasuchusDragon.BARRAGE_MOVE ? rise(x, 0.0F, 0.08F) * (1.0F - rise(x, 0.9F, 1.0F)) : 0.0F;
		float rakeL = barrage * (0.5F - 0.5F * Mth.cos(age * Mth.PI / 6.0F));
		float rakeR = barrage * (0.5F + 0.5F * Mth.cos(age * Mth.PI / 6.0F));
		float crouch = a == VarasuchusDragon.SHIFT_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		// the tailguard: the tail curled round before it (eased in through the startup, held, eased out)
		float guard = a == VarasuchusDragon.GUARD_MOVE ? rise(x, 0.0F, 0.23F) * (1.0F - rise(x, 0.85F, 1.0F)) : 0.0F;
		float parry = a == VarasuchusDragon.PARRY_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		float rage = a == VarasuchusDragon.RAGE_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		// (0.7.612) the bite: drawn back with the jaws wide (to 0.35), then snapped forward and shut
		float biteBack = a == VarasuchusDragon.BITE_MOVE ? rise(x, 0.0F, 0.35F) * (1.0F - rise(x, 0.35F, 0.5F)) : 0.0F;
		float biteIn = a == VarasuchusDragon.BITE_MOVE ? bell(x, 0.35F, 1.0F) : 0.0F;
		// (0.7.612) the tail sweep: the tail swung round to its left and back, the body twisting into it
		float sweep = a == VarasuchusDragon.SWEEP_MOVE ? bell(x, 0.0F, 1.0F) : 0.0F;
		// (0.7.612) a blow: it jerks aside, head tossed back, jaw open, and rights itself
		float flinch = s.hurt <= 0.0F ? 0.0F : bell(1.0F - s.hurt, 0.0F, 1.0F) * (1.0F - dead);
		// (0.7.612) idle, stood still and doing nothing: every 300 ticks one of a hiss, a head shake, a tail swish or a sniff
		float idle = (1.0F - Math.min(s.walkAnimationSpeed * 4.0F, 1.0F)) * (1.0F - sit) * (1.0F - crash) * (1.0F - swim) * (1.0F - dead)
			* (a == VarasuchusDragon.NONE ? 1.0F : 0.0F) * (1.0F - ease(s.stand));
		float cycle = (t + 140.0F) / 300.0F;
		float into = (cycle - Mth.floor(cycle)) * 300.0F;
		int kind = Math.floorMod(Mth.floor(cycle), 4);
		float fidget = idle * bell(into, 0.0F, kind == 3 ? 90.0F : 50.0F);
		float hiss = kind == 0 ? fidget : 0.0F;
		float shake = kind == 1 ? fidget * Mth.sin(into * 0.6F) : 0.0F;
		float swish = kind == 2 ? fidget : 0.0F;
		float sniff = kind == 3 ? fidget : 0.0F;
		float sniffTurn = sniff * Mth.sin(into * Mth.PI / 45.0F);
		// (0.7.612) sitting it basks, jaws propped open, breathing slow
		float bask = sit * sit;
		// upright (raging): tipped back onto its hind legs about the hips, which stay where they were
		float stand = ease(s.stand) * (1.0F - crash) * (1.0F - swim);
		float tilt = -STAND_TILT * stand;
		float hipY = HIP_Y * Mth.cos(tilt) - HIP_Z * Mth.sin(tilt), hipZ = HIP_Y * Mth.sin(tilt) + HIP_Z * Mth.cos(tilt);
		this.body.z = this.bodyZ + (HIP_Z - hipZ);

		// the body: held high, swaying in the walk, low in the dash and the shift's crouch, flat crashed
		this.body.y = (HIP_Y - hipY) + this.bodyY + crash * CRASH_DROP + sit * SIT_DROP + Math.abs(Mth.sin(step)) * 0.5F * walk + Mth.sin(t * 0.06F) * 0.15F
			+ dash * 2.0F + crouch * 4.0F - rage * 2.0F + Math.abs(Mth.sin(step)) * 1.5F * run + dead * 6.0F + biteIn * 1.0F;
		this.body.xRot = tilt + dash * 0.06F - sit * 0.14F - rage * 0.18F + dip * 0.05F + Mth.sin(step) * 0.12F * run - biteBack * 0.06F
			+ biteIn * 0.05F;
		this.body.yRot = Mth.sin(step) * 0.08F * walk * (1.0F - run) + Mth.sin(t * 0.14F) * 0.07F * swim - guard * 0.22F - parry * 0.1F + sweep * 0.3F;
		this.body.zRot = Mth.sin(step) * 0.025F * walk * (1.0F - run) + flinch * 0.14F;

		// the neck and head: level with the back; swaying against the body, dipped and tossed for the gore, flung up raging
		float down = crash * 0.45F + swim * 0.2F + dip * 0.35F - toss * 0.45F - rage * 0.5F + dash * 0.15F - biteBack * 0.35F + biteIn * 0.3F
			+ hiss * 0.15F + sniff * 0.55F - flinch * 0.4F + bask * 0.3F + dead * 0.6F;
		this.neck0.xRot = this.neck0Rest + down * 0.55F + Mth.sin(step * 2.0F) * 0.02F * walk - tilt * 0.55F;
		this.neck1.xRot = this.neck1Rest + down * 0.45F + Mth.sin(step * 2.0F - 0.5F) * 0.02F * walk - tilt * 0.3F;
		float lookYaw = headYaw * (1.0F - fidget) * (1.0F - dead);
		this.neck0.yRot = lookYaw * 0.3F - this.body.yRot * 0.5F + sniffTurn * 0.3F + shake * 0.12F;
		this.neck1.yRot = lookYaw * 0.3F - Mth.sin(step - 0.5F) * 0.04F * walk + sniffTurn * 0.3F + shake * 0.18F;
		this.head.xRot = this.headRest + headPitch * 0.5F - down * 0.3F - toss * 0.25F - crash * 0.2F;
		this.head.yRot = lookYaw * 0.3F + shake * 0.25F;
		this.head.zRot = shake * 0.2F + dead * 0.3F;
		// (a crocodile's gape at rest, wide raging and in the dash)
		this.jaw.xRot = 0.04F + (0.5F - 0.5F * Mth.cos(t * 0.03F)) * 0.12F * (1.0F - walk) + rage * 0.85F + dash * 0.55F + toss * 0.2F
			+ s.breath * 0.4F + biteBack * 0.95F + flinch * 0.5F + hiss * 0.75F + bask * (0.75F + Mth.sin(t * 0.03F) * 0.05F) + dead * 0.4F;

		// the tail: snaking behind, each segment a beat behind; sculling in the water; curled round in the tailguard
		for (int i = 0; i < TAIL; i++) {
			float k = i / (float)(TAIL - 1);
			float lag = i * 0.7F;
			float sway = Mth.sin(step - lag) * (0.07F + k * 0.05F) * walk + Mth.sin(t * 0.05F - lag) * 0.04F
				+ Mth.sin(t * 0.28F - lag) * (0.14F + k * 0.12F) * swim;
			// the lash at the dash's end and the parry's snap: rolling down the tail
			// (0.7.522, Andy: "a lot longer so it can realistically wrap around to the front for the parry/attacks") the
			// tailguard curls it round its flank to before its head; the dash's lash and the parry's snap swing it round
			// to the front and back, rolling down it a beat per segment
			float whip = (a == VarasuchusDragon.DASH_MOVE ? bell(x, 0.55F + k * 0.15F, 0.85F + k * 0.15F) * 0.8F : 0.0F)
				+ (a == VarasuchusDragon.PARRY_MOVE ? bell(x, k * 0.25F, 0.6F + k * 0.4F) * 0.6F : 0.0F);
			float wrap = Math.max(guard, whip);
			// (0.7.612) the sweep rolls down the tail a beat a segment; the swish flicks its last half; dead it lies limp
			float lash = a == VarasuchusDragon.SWEEP_MOVE ? bell(x, k * 0.25F, 0.65F + k * 0.35F) : 0.0F;
			float flick = swish * Mth.sin(t * 0.5F - i * 0.6F) * k * 0.3F;
			this.tail[i].yRot = (sway * (1.0F - wrap) + wrap * WRAP[i] + this.body.yRot * -0.3F - lash * 0.45F + flick) * (1.0F - dead)
				+ dead * 0.06F;
			// (wrapped round, it's held level off the ground; upright, its root bends so it still lies along the ground)
			this.tail[i].xRot = this.tailRest[i] * (1.0F - Math.max(swim * 0.7F, wrap * 0.85F)) - dash * 0.03F + crash * (i == 0 ? 0.08F : 0.0F)
				- sit * (i == 0 ? 0.1F : 0.0F) - (i == 0 ? tilt * 0.85F : 0.0F);
		}

		// legs: a high, sprawling walk with the diagonal pairs together; folded flat back swimming; splayed crashed
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			// (0.7.612) the gallop: the pairs fall out of the walk's diagonal into front-together, hind-together
			float phase = Mth.lerp(run, front == left ? 0.0F : Mth.PI, front ? (left ? 0.0F : 0.35F) : (left ? Mth.PI * 0.9F : Mth.PI * 1.25F));
			float swing = Mth.cos(step + phase) * (0.45F + run * 0.2F) * walk;
			float lift = (1.0F - Mth.sin(step + phase)) * 0.5F;
			float knee = lift * lift * 0.5F * walk;
			float up = swing, low = knee;
			if (!front) {
				// upright: the hind legs stay under it, walking on their own (left then right)
				up -= tilt;
			} else {
				// upright: the forelegs held out before it as arms, the claws ready, swinging a little as it walks
				up = Mth.lerp(stand, up, -0.25F + Mth.cos(step + phase) * 0.15F * walk);
				low = Mth.lerp(stand, low, -0.9F);
			}
			if (front) {
				float rake = left ? Math.max(claw, rakeL) : rakeR;
				float strike = left ? clawDown * claw : 0.0F;
				up = Mth.lerp(rake, up, -1.1F + strike * 0.6F);
				low = Mth.lerp(rake, low, 0.7F - strike * 0.3F);
				up = Mth.lerp(rage, up, -0.4F);
			} else {
				up = Mth.lerp(sit, up, -0.9F);
				low = Mth.lerp(sit, low, 1.4F);
			}
			up = Mth.lerp(crouch, up, front ? -0.3F : 0.3F);
			low = Mth.lerp(crouch, low, front ? 0.6F : 0.3F);
			// swimming: laid flat back along its flanks
			// (0.7.612) floating still, they tread slowly; under way, the strokes quicken
			float stroke = t * (0.08F + Math.min(s.walkAnimationSpeed, 1.0F) * 0.14F);
			up = Mth.lerp(swim, up, 1.3F + Mth.sin(stroke + phase) * 0.12F);
			// (0.7.612) dead: locked out stiff
			up = Mth.lerp(dead, up, front ? -0.35F : 0.35F);
			low = Mth.lerp(dead, low, 0.0F);
			low = Mth.lerp(swim, low, 0.2F);
			float side = left ? -1.0F : 1.0F;
			this.upper[i].xRot = up;
			this.upper[i].zRot = this.upperZ[i] * (1.0F - swim * 0.6F) + side * crash * 0.9F + side * dead * 0.5F;
			this.lower[i].xRot = low;
			this.lower[i].zRot = this.lowerZ[i] * (1.0F - swim * 0.6F) - side * crash * 0.6F;
			// the foot kept flat on the ground (upright, the forefeet held as hands)
			this.foot[i].xRot = -(up + low + (front ? 0.0F : tilt)) * (1.0F - swim) * (front ? 1.0F - stand : 1.0F) + (front ? stand * 0.4F : 0.0F);
		}

		// its look: horns, a lost scute; its tack
		DragonLook look = s.look;
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		this.hornL.visible = this.hornR.visible = !shortHorns;
		this.tipL.visible = this.tipR.visible = !shortHorns;
		this.shortL.visible = this.shortR.visible = shortHorns;
		this.noseHorn.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			boolean tack = s.saddled && i >= 1 && i <= 3;
			this.scutesL[i].visible = look.spike(i) && !tack;
			this.scutesR[i].visible = !tack;
		}
		this.saddle.visible = this.strap.visible = this.pad.visible = s.saddled;
	}
}
