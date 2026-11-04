package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * (0.20.21, Ultimates patch 1) Velox's Ultimate, Standstill: the same forward-leaning two-legged raptor (the S-neck, tucked clawed
 * forearms, the long balancing tail, the same digitigrade gait) with blue-black plates down the spine and the tail, the swept-back crest
 * fanned into blade quills, a blade on the tail's end, a plated sliding visor and a sickle claw on each foot. The skin's seams glow the
 * light blue of Velocity's streak (the glow map, drawn fullbright by the layer) and brighten with speed ({@link #submitExtras}). At a run
 * the tail straightens as Velox's does and the quills also flatten back. It is the same rig as {@code XenoFormModels.Velox} (built bigger by
 * the player's scale, not the mesh), with its own 128x128 skin from {@code tools/ultimates/blur.py}; {@link #UV} is the table
 * {@code blur.py layout} prints.
 * <p>
 * The change-in film zooms on {@code sickle_r+sickle_l}, {@code tail4} (with its blade), {@code crest} (with its quills) and
 * {@code visor} (with its plate): parts of this mesh.
 */
public final class UltimateStandstill extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The hip's height off the ground (model y) at rest, how far it drops at full speed, when sneaking, and on a landing. */
	static final float HIP_Y = 8.0F, RUN_DROP = 1.0F, CROUCH_DROP = 2.6F, LAND_DROP = 2.0F;
	/** How far apart the hips are from the middle, and how far the feet stand from the hip's line when still (front/back). */
	static final float HIP_X = 2.5F, FOOT_Z = -1.0F, IDLE_STAGGER = 0.8F;
	/** Leg segments (pixels): thigh (hip to knee), shin (knee to ankle), tarsus (ankle to toes). */
	static final float THIGH = 5.5F, SHIN = 7.0F, TARSUS = 5.0F;
	/** The foot's pivot is this far below its top; its toes reach this far forward of it (the lowest point of a pitched foot). */
	static final float TOE_REACH = 4.0F;
	/** The gait: steps per unit of the walk (a full stride cycle), the stride's half-length and the swing's lift at full speed (pixels). */
	static final float GAIT = 0.2F, MAX_STRIDE = 5.2F, MAX_LIFT = 2.4F;
	/** The share of a stride a foot is on the ground: walking, and flat out (under half is a flight). */
	static final float DUTY_WALK = 0.62F, DUTY_RUN = 0.42F;
	/** How far the hip bounces over a stride at full speed (pixels), and how much the body pitches (radians: + is nose down). */
	static final float BOB = 0.9F, REST_PITCH = 0.1F, RUN_PITCH = 0.17F, CROUCH_PITCH = 0.22F;
	/** The neck's two segments' lengths (pixels) and their angles from the vertical (radians, + is forward): resting, and at full run. */
	static final float NECK1 = 4.0F, NECK2 = 4.0F;
	static final float NECK1_REST = 0.85F, NECK2_REST = 0.05F, NECK1_RUN = 1.15F, NECK2_RUN = 0.7F;
	/** Where the neck starts on the body (y, z in the body's space). */
	static final float NECK_Y = -1.0F, NECK_Z = -7.0F;
	/** The arms: where they hang off the chest (body space), their tucked angle and the hand's bend (radians). */
	static final float ARM_X = 2.5F, ARM_Y = 0.5F, ARM_Z = -6.0F, ARM_REST = -0.95F, HAND_REST = 0.7F, CLAW_REST = 0.4F;
	/** The slash: how far the arm winds up and how far it rakes down (radians). */
	static final float SLASH_UP = -1.25F, SLASH_DOWN = 1.0F;
	/** The tail: how far each segment sways at rest and at a run (radians), and how fast at rest. */
	static final float TAIL_IDLE_SWAY = 0.15F, TAIL_RUN_SWAY = 0.05F, TAIL_IDLE_RATE = 0.07F;
	/** The lowest the tail droops (radians of tailLift): a hard landing while sneaking would otherwise drive its tip into the ground. */
	static final float TAIL_LOW = -0.35F;
	/** The visor's slide: how far up on the brow, and down over the eyes (model y), its tilt at each. */
	static final float VISOR_UP_Y = -2.9F, VISOR_DOWN_Y = -0.9F, VISOR_UP_X = -0.4F, VISOR_DOWN_X = 0.0F;
	/** The quill fan: how far each pair spreads (radians, yaw) at rest, how far they rise at rest and lie back at a run, and their ruffle. */
	static final float QUILL_SPREAD = 0.3F, QUILL_RISE = 0.55F, QUILL_FLAT = -0.12F, QUILL_RUFFLE = 0.04F;
	/** How much brighter the seams get at full speed (the share of an extra glow pass), and the speed it takes to read as full. */
	static final float SEAM_SPEED_GAIN = 0.7F, SEAM_FULL_SPEED = 0.8F;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** The skin's patches by box name: (u, v, w, h, d), the table {@code tools/ultimates/blur.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("skull", 0, 0, 4, 4, 6), uv("snout", 20, 0, 3, 2, 5), uv("jaw", 36, 0, 3, 1, 7), uv("visor", 56, 0, 5, 2, 4),
		uv("vplate", 74, 0, 6, 1, 5), uv("crest", 96, 0, 1, 2, 4), uv("sweep", 106, 0, 1, 2, 6), uv("quill", 0, 10, 1, 1, 6),
		uv("torso", 14, 10, 5, 5, 8), uv("chest", 40, 10, 4, 4, 5), uv("splate", 58, 10, 3, 1, 2), uv("spike", 68, 10, 1, 2, 1),
		uv("neck1", 72, 10, 3, 5, 3), uv("neck2", 84, 10, 2, 5, 2), uv("tail1", 92, 10, 4, 3, 7), uv("tail2", 0, 23, 3, 2, 7),
		uv("tail3", 20, 23, 2, 2, 6), uv("tail4", 36, 23, 2, 1, 4), uv("tp1", 48, 23, 4, 1, 5), uv("tp2", 66, 23, 3, 1, 5),
		uv("tp3", 82, 23, 2, 1, 4), uv("blade", 94, 23, 1, 3, 6), uv("arm", 108, 23, 2, 5, 2), uv("hand", 116, 23, 2, 3, 2),
		uv("claw", 124, 23, 1, 2, 1), uv("aplate", 0, 32, 3, 2, 3), uv("thigh", 12, 32, 3, 7, 4), uv("tplate", 26, 32, 4, 3, 5),
		uv("shin", 44, 32, 2, 8, 3), uv("tarsus", 54, 32, 2, 6, 2), uv("foot", 62, 32, 4, 2, 5), uv("sickle", 80, 32, 1, 1, 4));

	private static Map.Entry<String, int[]> uv(String name, int u, int v, int w, int h, int d) {
		return Map.entry(name, new int[] {u, v, w, h, d});
	}

	private final ModelPart torso, neck1, neck2, tail1, tail2, tail3, tail4;
	private final ModelPart rightShin, rightTarsus, rightFoot, leftShin, leftTarsus, leftFoot;
	private final ModelPart rightHand, rightClaw, leftHand, leftClaw;
	private final ModelPart visor, jaw, crest, sweep;
	private final ModelPart[] quills = new ModelPart[4];
	private final float[] solved = new float[6];

	public UltimateStandstill() {
		super(XenoFormModels.bake(mesh(), 128, 128), "blur");
		this.insigniaY = -0.5F;
		this.insigniaZ = -2.5F;
		this.insigniaSize = 0.9F;
		this.torso = this.root().getChild("torso");
		this.neck1 = this.torso.getChild("neck1");
		this.neck2 = this.neck1.getChild("neck2");
		this.tail1 = this.torso.getChild("tail1");
		this.tail2 = this.tail1.getChild("tail2");
		this.tail3 = this.tail2.getChild("tail3");
		this.tail4 = this.tail3.getChild("tail4");
		this.rightShin = this.rightLeg.getChild("shin");
		this.rightTarsus = this.rightShin.getChild("tarsus");
		this.rightFoot = this.rightTarsus.getChild("foot");
		this.leftShin = this.leftLeg.getChild("shin");
		this.leftTarsus = this.leftShin.getChild("tarsus");
		this.leftFoot = this.leftTarsus.getChild("foot");
		this.rightHand = this.rightArm.getChild("hand");
		this.rightClaw = this.rightHand.getChild("claw");
		this.leftHand = this.leftArm.getChild("hand");
		this.leftClaw = this.leftHand.getChild("claw");
		this.visor = this.head.getChild("visor");
		this.jaw = this.head.getChild("jaw");
		this.crest = this.head.getChild("crest");
		this.sweep = this.crest.getChild("sweep");
		for (int i = 0; i < 4; i++) {
			this.quills[i] = this.crest.getChild("quill_" + i);
		}
	}

	/** A box of {@code key}'s patch of the skin, at {@code (x, y, z)}, its size the patch's own. */
	private static CubeListBuilder cube(String key, float x, float y, float z) {
		int[] at = UV.get(key);
		return CubeListBuilder.create().texOffs(at[0], at[1]).addBox(x, y, z, at[2], at[3], at[4]);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// the head (at the end of the neck, set each frame): a skull, a long snout, a jaw that opens, the fanned crest, the plated visor
		PartDefinition head = root.addOrReplaceChild("head", cube("skull", -2.0F, -2.0F, -5.0F), PartPose.offset(0.0F, 4.0F, -10.0F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("snout", cube("snout", -1.5F, -1.0F, -4.0F), PartPose.offset(0.0F, 0.5F, -5.0F));
		head.addOrReplaceChild("jaw", cube("jaw", -1.5F, 0.0F, -7.0F), PartPose.offset(0.0F, 1.5F, -1.5F));
		PartDefinition crest = head.addOrReplaceChild("crest", cube("crest", -0.5F, -2.0F, -1.5F), PartPose.offset(0.0F, -1.5F, -1.5F));
		crest.addOrReplaceChild("sweep", cube("sweep", -0.5F, -1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -1.3F, 2.5F, -0.3F, 0.0F, 0.0F));
		// the fan: two quills a side, each pivoting at the crest's root (set each frame)
		for (int i = 0; i < 4; i++) {
			float side = i < 2 ? -1.0F : 1.0F;
			float far = i % 2 == 0 ? 1.0F : 2.0F;
			crest.addOrReplaceChild("quill_" + i, cube("quill", -0.5F, -0.5F, 0.0F), PartPose.offsetAndRotation(side * 0.45F * far, -1.6F, 1.5F,
				QUILL_RISE, side * QUILL_SPREAD * far, 0.0F));
		}
		PartDefinition visor = head.addOrReplaceChild("visor", cube("visor", -2.5F, 0.0F, -4.0F),
			PartPose.offsetAndRotation(0.0F, VISOR_UP_Y, -1.5F, VISOR_UP_X, 0.0F, 0.0F));
		visor.addOrReplaceChild("visor_plate", cube("vplate", -3.0F, -0.9F, -4.5F), PartPose.ZERO);
		// the man's body is hidden; it lies face-up on the torso (the insignia sits on its back)
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, HIP_Y, 0.0F));
		PartDefinition torso = root.addOrReplaceChild("torso", cube("torso", -2.5F, -2.5F, -4.0F), PartPose.offsetAndRotation(0.0F, HIP_Y, 0.0F, REST_PITCH, 0.0F, 0.0F));
		torso.addOrReplaceChild("chest", cube("chest", -2.0F, -2.0F, -4.0F), PartPose.offset(0.0F, 0.5F, -4.0F));
		// the plates down the spine, each with a swept-back spike
		for (int i = 0; i < 4; i++) {
			float z = -3.5F + 2.0F * i;
			torso.addOrReplaceChild("spine_plate_" + i, cube("splate", -1.5F, -3.4F, z), PartPose.ZERO);
			torso.addOrReplaceChild("spine_spike_" + i, cube("spike", -0.5F, -2.0F, -0.5F), PartPose.offsetAndRotation(0.0F, -3.0F, z + 1.0F, 0.7F, 0.0F, 0.0F));
		}
		PartDefinition neck1 = torso.addOrReplaceChild("neck1", cube("neck1", -1.5F, -4.0F, -1.5F), PartPose.offsetAndRotation(0.0F, NECK_Y, NECK_Z, 0.75F, 0.0F, 0.0F));
		neck1.addOrReplaceChild("neck2", cube("neck2", -1.0F, -4.0F, -1.0F), PartPose.offsetAndRotation(0.0F, -NECK1, 0.0F, -0.8F, 0.0F, 0.0F));
		// the balancing tail: four segments, thinning out, plated on top, ending in a blade
		PartDefinition tail1 = torso.addOrReplaceChild("tail1", cube("tail1", -2.0F, -1.5F, -1.0F), PartPose.offset(0.0F, -0.5F, 4.0F));
		tail1.addOrReplaceChild("tail_plate_1", cube("tp1", -2.0F, -2.4F, 0.0F), PartPose.ZERO);
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", cube("tail2", -1.5F, -1.0F, -1.0F), PartPose.offset(0.0F, 0.0F, 6.0F));
		tail2.addOrReplaceChild("tail_plate_2", cube("tp2", -1.5F, -1.9F, 0.0F), PartPose.ZERO);
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", cube("tail3", -1.0F, -1.0F, -1.0F), PartPose.offset(0.0F, 0.0F, 6.0F));
		tail3.addOrReplaceChild("tail_plate_3", cube("tp3", -1.0F, -1.9F, 0.0F), PartPose.ZERO);
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", cube("tail4", -1.0F, -0.5F, -1.0F), PartPose.offset(0.0F, 0.0F, 5.0F));
		tail4.addOrReplaceChild("tail_blade", cube("blade", -0.5F, -1.5F, 2.5F), PartPose.ZERO);
		// the little arms: an upper arm with a plate, a hand bent down off it, a hooked claw
		arm(root, "right_arm", -1);
		arm(root, "left_arm", 1);
		// the legs: thigh (plated), shin, tarsus and toes (a sickle claw with a hooked tip held up off each foot)
		leg(root, "right_leg", -1, "sickle_r");
		leg(root, "left_leg", 1, "sickle_l");
		return mesh;
	}

	private static void arm(PartDefinition root, String name, int side) {
		PartDefinition arm = root.addOrReplaceChild(name, cube("arm", -1.0F, -1.0F, -1.0F), PartPose.offset(side * 5.0F, 2.0F, 0.0F));
		arm.addOrReplaceChild("arm_plate", cube("aplate", -1.5F, -0.5F, -1.5F), PartPose.ZERO);
		PartDefinition hand = arm.addOrReplaceChild("hand", cube("hand", -1.0F, 0.0F, -1.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, HAND_REST, 0.0F, 0.0F));
		hand.addOrReplaceChild("claw", cube("claw", -0.5F, 0.0F, -0.5F), PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, CLAW_REST, 0.0F, 0.0F));
	}

	private static void leg(PartDefinition root, String name, int side, String sickleName) {
		PartDefinition thigh = root.addOrReplaceChild(name, cube("thigh", -1.5F, -1.5F, -2.0F), PartPose.offset(side * HIP_X, HIP_Y, 0.0F));
		thigh.addOrReplaceChild("thigh_plate", cube("tplate", -2.0F, -1.0F, -2.5F), PartPose.ZERO);
		PartDefinition shin = thigh.addOrReplaceChild("shin", cube("shin", -1.0F, -1.0F, -1.5F), PartPose.offset(0.0F, THIGH, 0.0F));
		PartDefinition tarsus = shin.addOrReplaceChild("tarsus", cube("tarsus", -1.0F, -1.0F, -1.0F), PartPose.offset(0.0F, SHIN, 0.0F));
		PartDefinition foot = tarsus.addOrReplaceChild("foot", cube("foot", -2.0F, -1.0F, -4.0F), PartPose.offset(0.0F, TARSUS, 0.0F));
		PartDefinition sickle = foot.addOrReplaceChild(sickleName, cube("sickle", -0.5F, -0.5F, -4.0F), PartPose.offsetAndRotation(0.0F, -1.2F, -1.0F, -0.5F, 0.0F, 0.0F));
		sickle.addOrReplaceChild(sickleName + "_tip", cube("claw", -0.5F, 0.0F, -0.5F), PartPose.offsetAndRotation(0.0F, -0.3F, -3.8F, 0.4F, 0.0F, 0.0F));
	}

	/** The seams glow brighter the faster it goes: an extra pass of the glow map over the layer's own, as strong as the speed. */
	@Override
	public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float speed = Mth.clamp(state.walkAnimationSpeed / SEAM_FULL_SPEED, 0.0F, 1.0F);
		if (speed < 0.05F) {
			return;
		}
		int alpha = (int)(speed * SEAM_SPEED_GAIN * 255.0F);
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			alpha << 24 | 0xFFFFFF, null, 0);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		XenoMotion.Read m = XenoMotion.read(state);
		for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
			part.resetPose();
		}
		this.hat.visible = false;
		this.body.visible = false;
		float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		// the legs only really stride once it is going; a still raptor stands with its feet flat
		float blend = Mth.clamp(speed * 4.0F, 0.0F, 1.0F);
		float air = m.air();
		float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		float bite = Mth.sin(swing * (float)Math.PI);
		float ticks = state.ageInTicks;
		boolean crouch = state.isCrouching;
		// ---- the gait: right leg on the clock, left half a stride behind ----
		float cycle = state.walkAnimationPos * GAIT;
		float duty = Mth.lerp(speed, DUTY_WALK, DUTY_RUN);
		float stride = MAX_STRIDE * Math.min(1.0F, speed * 1.1F);
		float lift = MAX_LIFT * Math.min(1.0F, speed * 1.2F);
		// the hip dips mid-stance (a spring) and rises in flight, twice a cycle
		float bob = BOB * speed * 0.5F * (1.0F + Mth.cos((cycle - duty * 0.5F) * 4.0F * (float)Math.PI));
		float hipY = HIP_Y + RUN_DROP * speed + (crouch ? CROUCH_DROP : 0.0F) + bob + m.land() * LAND_DROP + m.breath() * 0.12F * (1.0F - speed);
		float pitch = REST_PITCH + RUN_PITCH * speed + (crouch ? CROUCH_PITCH : 0.0F) + bite * 0.1F
			+ Mth.sin((cycle - duty * 0.5F) * 4.0F * (float)Math.PI) * 0.03F * speed + Math.max(0.0F, m.fall()) * air * 0.2F;
		this.legs(this.rightLeg, this.rightShin, this.rightTarsus, this.rightFoot, hipY, cycle, stride, lift, duty, FOOT_Z + IDLE_STAGGER * (1.0F - blend), blend, air, 2.0F);
		this.legs(this.leftLeg, this.leftShin, this.leftTarsus, this.leftFoot, hipY, cycle + 0.5F, stride, lift, duty, FOOT_Z - IDLE_STAGGER * (1.0F - blend), blend, air, -2.0F);
		// ---- the body: near horizontal, pitched over the hips ----
		this.torso.x = 0.0F;
		this.torso.y = hipY;
		this.torso.z = 0.0F;
		this.torso.xRot = pitch;
		this.torso.yRot = this.torso.zRot = 0.0F;
		// the hidden body lies face-up on the torso's back, for the insignia
		this.body.x = 0.0F;
		this.body.y = hipY;
		this.body.z = 0.0F;
		this.body.xRot = pitch - (float)Math.PI * 0.5F;
		this.body.yRot = this.body.zRot = 0.0F;
		// ---- the neck: an S, reaching forward as it runs, the head held level on the end of it ----
		float reach = Mth.clamp(speed * 1.3F, 0.0F, 1.0F);
		float n1 = Mth.lerp(reach, NECK1_REST, NECK1_RUN) + bite * 0.35F + (crouch ? 0.2F : 0.0F) + m.breath() * 0.03F * (1.0F - speed);
		float n2 = Mth.lerp(reach, NECK2_REST, NECK2_RUN) - bite * 0.15F;
		this.neck1.xRot = n1 - pitch;
		this.neck2.xRot = n2 - n1;
		float cos = Mth.cos(pitch);
		float sin = Mth.sin(pitch);
		this.head.x = 0.0F;
		this.head.y = hipY + NECK_Y * cos - NECK_Z * sin - NECK1 * Mth.cos(n1) - NECK2 * Mth.cos(n2);
		this.head.z = NECK_Y * sin + NECK_Z * cos - NECK1 * Mth.sin(n1) - NECK2 * Mth.sin(n2);
		float look = Mth.sin(ticks * 0.05F) * 0.1F * (1.0F - speed);
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD + look;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD + 0.1F - 0.1F * speed + bite * 0.25F;
		this.head.zRot = 0.0F;
		this.jaw.xRot = 0.05F + bite * 0.7F + Math.max(0.0F, Mth.sin(ticks * 0.11F + 1.0F) - 0.9F) * 2.0F * (1.0F - speed);
		// the plated visor slides down over the eyes as it picks up speed and rides up on the brow when it slows
		float down = Mth.clamp((speed - 0.35F) / 0.4F, 0.0F, 1.0F);
		down = down * down * (3.0F - 2.0F * down);
		this.visor.y = Mth.lerp(down, VISOR_UP_Y, VISOR_DOWN_Y);
		this.visor.xRot = Mth.lerp(down, VISOR_UP_X, VISOR_DOWN_X);
		// ---- the quills: fanned up off the crest at rest, ruffling; lying back along the head at a run ----
		float flat = Mth.clamp(speed * 1.4F, 0.0F, 1.0F);
		for (int i = 0; i < 4; i++) {
			float side = i < 2 ? -1.0F : 1.0F;
			float far = i % 2 == 0 ? 1.0F : 2.0F;
			float ruffle = Mth.sin(ticks * 0.13F + i * 1.7F) * QUILL_RUFFLE * (1.0F - flat);
			this.quills[i].xRot = Mth.lerp(flat, QUILL_RISE, QUILL_FLAT) + ruffle;
			this.quills[i].yRot = side * QUILL_SPREAD * far * (1.0F - 0.6F * flat);
			this.quills[i].zRot = 0.0F;
		}
		this.sweep.xRot = Mth.lerp(flat, -0.3F, 0.05F);
		// ---- the arms: tucked at the chest, clawing in a slash, the left a beat behind ----
		this.arm(this.rightArm, this.rightHand, this.rightClaw, -1, hipY, pitch, slash(swing), speed, ticks);
		this.arm(this.leftArm, this.leftHand, this.leftClaw, 1, hipY, pitch, slash(Mth.clamp((swing - 0.12F) / 0.88F, 0.0F, 1.0F)), speed, ticks);
		// ---- the tail: out behind, countering the body, swaying when idle, steady at a run ----
		float rise = Math.max(TAIL_LOW, tailLift(m));
		float idle = 1.0F - speed;
		float wave = ticks * TAIL_IDLE_RATE;
		float stepWave = cycle * 2.0F * (float)Math.PI;
		this.tail1.xRot = -pitch + 0.04F * idle + rise;
		this.tail2.xRot = -0.1F * idle + rise * 0.5F;
		this.tail3.xRot = -0.08F * idle + 0.03F * speed + rise * 0.4F;
		this.tail4.xRot = -0.06F * idle + 0.05F * speed + rise * 0.3F;
		ModelPart[] tail = {this.tail1, this.tail2, this.tail3, this.tail4};
		for (int i = 0; i < 4; i++) {
			float idleSway = Mth.sin(wave - i * 0.9F) * TAIL_IDLE_SWAY * (0.6F + 0.3F * i);
			float runSway = -Mth.sin(stepWave - i * 0.7F) * TAIL_RUN_SWAY;
			tail[i].yRot = idleSway * idle + runSway * speed + (i == 0 ? -m.trail() * 0.3F : (m.turn() - m.trail()) * 0.12F);
			tail[i].zRot = 0.0F;
		}
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up): Velox's own. */
	private static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	/** The slash's arm angle added to the tuck, for the attack's progress (0-1): wind up, rake down, come back. */
	private static float slash(float swing) {
		if (swing <= 0.0F) {
			return 0.0F;
		}
		if (swing < 0.25F) {
			return Mth.lerp(swing / 0.25F, 0.0F, SLASH_UP);
		}
		if (swing < 0.55F) {
			float k = (swing - 0.25F) / 0.3F;
			return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_UP, SLASH_DOWN);
		}
		float k = (swing - 0.55F) / 0.45F;
		return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_DOWN, 0.0F);
	}

	private void arm(ModelPart arm, ModelPart hand, ModelPart claw, int side, float hipY, float pitch, float slash, float speed, float ticks) {
		float cos = Mth.cos(pitch);
		float sin = Mth.sin(pitch);
		arm.x = side * ARM_X;
		arm.y = hipY + ARM_Y * cos - ARM_Z * sin;
		arm.z = ARM_Y * sin + ARM_Z * cos;
		// held tucked, a little twitch at rest, drawn in tighter at a run
		float tuck = ARM_REST - 0.15F * speed + Mth.sin(ticks * 0.09F + side) * 0.04F * (1.0F - speed);
		arm.xRot = pitch + tuck + slash;
		arm.zRot = -side * 0.2F;
		hand.xRot = HAND_REST + slash * 0.5F + 0.2F * speed;
		claw.xRot = CLAW_REST + slash * 0.3F;
	}

	/** One leg: its foot's path through the stride, then the leg's angles from it ({@link #solveLeg}), kept on the ground while it's down. */
	private void legs(ModelPart thigh, ModelPart shin, ModelPart tarsus, ModelPart foot, float hipY, float cycle, float stride, float lift,
		float duty, float footZ, float blend, float air, float tuckZ) {
		thigh.x = Math.signum(thigh.x) * HIP_X;
		thigh.y = hipY;
		thigh.z = 0.0F;
		solveLeg(hipY, cycle, stride, lift, duty, footZ, blend, air, tuckZ, this.solved);
		thigh.xRot = this.solved[0];
		shin.xRot = this.solved[1] - this.solved[0];
		tarsus.xRot = this.solved[2] - this.solved[1];
		foot.xRot = this.solved[3] - this.solved[2];
	}

	/** How far below its pivot a foot's lowest point is when pitched {@code pitch} (+ toes down): the sole, or the toe tips. */
	static float toeDrop(float pitch) {
		float sin = (float)Math.sin(pitch);
		return (float)Math.cos(pitch) + (pitch >= 0.0F ? TOE_REACH * sin : -sin);
	}

	/**
	 * Solves one leg into {@code out}: the thigh, shin, tarsus and foot angles from the vertical (radians, + swings the bottom backward),
	 * then the foot pivot's y and z from the hip. Velox's own solver: a foot is on the ground for {@code duty} of the cycle, then swings
	 * back through the air, lifted; {@code air} tucks it for a leap; {@code blend} (0-1) fades the stride's foot angles out for a raptor
	 * standing still. The thigh and tarsus stay parallel (a bird's leg), so the knee is always forward and the ankle behind.
	 */
	static void solveLeg(float hipY, float cycle, float stride, float lift, float duty, float footZ, float blend, float air, float tuckZ, float[] out) {
		float t = cycle - (float)Math.floor(cycle);
		float fz;
		float rise = 0.0F;
		float toes;
		if (t < duty) {
			float u = t / duty;
			fz = footZ + stride * (2.0F * u - 1.0F);
			// down flat on the heel's first touch, flat through the stance, rolling onto the toes to push off
			toes = u < 0.2F ? Mth.lerp(u / 0.2F, -0.15F, 0.0F) : u > 0.7F ? Mth.lerp((u - 0.7F) / 0.3F, 0.0F, 0.55F) : 0.0F;
		} else {
			float u = (t - duty) / (1.0F - duty);
			float s = u * u * (3.0F - 2.0F * u);
			fz = footZ + stride * (1.0F - 2.0F * s);
			rise = lift * (float)Math.sin(Math.PI * u);
			toes = Mth.lerp(s, 0.55F, -0.15F) + 0.2F * (float)Math.sin(Math.PI * u);
		}
		toes *= blend;
		// a leap: the feet come up and tuck, one back and one forward
		rise = Math.max(rise, 2.4F * air);
		fz = Mth.lerp(air, fz, footZ + tuckZ);
		toes = Mth.lerp(air, toes, 0.5F);
		float dy = 24.0F - toeDrop(toes) - rise - hipY;
		float dz = fz;
		float reach = (float)Math.sqrt(dy * dy + dz * dz);
		float paired = THIGH + TARSUS;
		float min = paired - SHIN + 0.3F;
		float max = paired + SHIN - 0.05F;
		float d = Mth.clamp(reach, min, max);
		if (reach > 1.0E-4F && d != reach) {
			dy *= d / reach;
			dz *= d / reach;
		}
		float psi = (float)Math.atan2(dz, dy);
		float cosA = Mth.clamp((paired * paired + d * d - SHIN * SHIN) / (2.0F * paired * d), -1.0F, 1.0F);
		// the thigh (and the tarsus with it) leans forward of the hip-to-foot line: knee ahead, ankle back
		float lean = psi - (float)Math.acos(cosA);
		float cosL = (float)Math.cos(lean);
		float sinL = (float)Math.sin(lean);
		float kneeY = THIGH * cosL;
		float kneeZ = THIGH * sinL;
		float ankleY = dy - TARSUS * cosL;
		float ankleZ = dz - TARSUS * sinL;
		out[0] = lean;
		out[1] = (float)Math.atan2(ankleZ - kneeZ, ankleY - kneeY);
		out[2] = lean;
		out[3] = toes;
		out[4] = hipY + dy;
		out[5] = dz;
	}
}
