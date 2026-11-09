package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import java.util.HashMap;
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
 * (0.21.37, Ultimates patch 2) Lurk's Ultimate, Nightfall: Lurk's own build and gait (the tall stalker that drops onto all fours when it
 * sneaks or fades, every foot and hand solved onto the ground, a pounce when it swings), grown. Its arms are longer, the forearms sit
 * under bone-black bracers and the shoulders under plates, a ridge of thin spikes runs down its back, and its tall bulb head has four eye
 * domes (the new pair above the old). On all fours it is longer and lower. Violet seams (0x6E52B0) run through the hide and between
 * the plates: the skin's glow map, drawn fullbright by the layer, breathing slowly ({@link #submitExtras}). It is
 * {@code XenoFormModels.Lurk}'s rig, copied, since that class is not open to other packages; the skin is {@code tools/ultimates/lurk.py},
 * 128x128, and {@link #UV} here is the table that script prints ({@code lurk.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code right_forearm+left_forearm+right_hand+left_hand}, {@code torso}, {@code neck},
 * {@code head+right_eye+left_eye} and {@code right_eye_top+left_eye_top}: those names are parts of this mesh.
 */
public final class UltimateNightfall extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	// ---- tuning ----
	/** The long bones (model pixels): upper arm and forearm (the hand runs {@link #HAND_LONG} past the wrist), thigh and shin (down to the ankle, the middle of a flat foot). */
	static final float UPPER_ARM = 7.0F, FOREARM = 9.0F, THIGH = 7.0F, SHIN = 7.0F;
	/** In the torso's space (its pivot is the hips; the box runs from NECK_Y to a pixel below): the neck's base, the shoulders (down, out), the chest's middle (the insignia) and how far out the hips are. */
	static final float NECK_Y = -10.0F, SHOULDER_Y = -9.0F, SHOULDER_X = 3.0F, CHEST_Y = -5.0F, HIP_X = 1.5F;
	/** Standing and on all fours: how high the hips are above the ground, how far the torso leans forward (radians) and how far back the hips are (z). */
	static final float STAND_HIP_UP = 13.6F, FOURS_HIP_UP = 7.4F, STAND_PITCH = 0.1F, FOURS_PITCH = 1.2F, FOURS_HIP_Z = 9.0F;
	/** How long the change of stance takes (ticks), and how far the renderer draws a sneaking player lower (the ground is that much higher in model space). */
	static final float STANCE_TICKS = 9.0F, CROUCH_SINK = 2.0F;
	/** An ankle (and a flat wrist) sits this far above the ground: the foot and the hand are 2 thick. The hand runs this far past the wrist. */
	static final float ANKLE_UP = 1.0F, HAND_LONG = 5.0F;
	/** The head: how far it pokes forward of the neck's base (standing, on all fours), how far it drops there, its tilt (radians), how much it tips forward standing, and how much of the look it follows on all fours. */
	static final float STAND_HEAD_FWD = 0.5F, FOURS_HEAD_FWD = 5.0F, FOURS_HEAD_DROP = 0.5F, STAND_TILT = 0.18F, FOURS_TILT = 0.08F, STAND_DIP = 0.15F, FOURS_LOOK = 0.6F;
	/** The gaits: the cycle against the walk (standing, on all fours), and the walking speed that is a full step. */
	static final float STAND_GAIT = 0.6662F, FOURS_GAIT = 0.5F, STEP_FULL = 0.3F;
	/** Standing: how far the legs stride (half a stride), how high a foot lifts, how much of the step it is down, how far the hips dip and how far a landing squats. */
	static final float STAND_STRIDE = 4.0F, STAND_LIFT = 2.6F, STAND_DUTY = 0.6F, STAND_BOB = 0.6F, STAND_DIP_LAND = 1.5F;
	/** On all fours: where the hind and fore feet rest (z), how far they stride, how high they lift, how much of the step they are down, the hips' bob, the landing squat and the sway of the spine (radians). */
	static final float HIND_REST_Z = 10.5F, FORE_REST_Z = -9.0F, HIND_STRIDE = 2.0F, FORE_STRIDE = 2.5F, HIND_LIFT = 2.0F, FORE_LIFT = 3.0F;
	static final float FOURS_DUTY = 0.75F, FOURS_BOB = 0.5F, FOURS_DIP_LAND = 1.0F, SWAY = 0.1F;
	/** A run stretches the stride by this much. */
	static final float RUN_STRETCH = 0.35F;
	/** The walking part of the vanilla arm swing that is kept (the arms are long): the rest is taken out. */
	static final float ARM_KEEP = 0.45F;
	/** Standing, a swing leans the torso this far forward. */
	static final float STAND_LUNGE = 0.2F;
	/** The pounce (a swing on all fours): how far the torso rises (radians), the hips rise and lunge (pixels), the head thrusts and dips, how far the forepaws reach, rise and drag back, how far the fingers claw down, and how much of it the left hand does. */
	static final float RISE_PITCH = 0.6F, RISE_UP = 2.5F, LUNGE = 3.0F, THRUST = 2.0F, STRIKE_DIP = 0.2F;
	static final float STRIKE_REACH = 6.0F, STRIKE_UP = 8.0F, STRIKE_DRAG = 4.0F, CLAW = 1.0F, OFF_HAND = 0.6F;
	/** In the air: how far the feet lift and trail (hind, standing then on all fours) and how far the forepaws reach forward and up. */
	static final float AIR_LIFT_STAND = 2.5F, AIR_LIFT_FOURS = 4.5F, AIR_BACK_STAND = 0.5F, AIR_BACK_FOURS = 3.0F, AIR_FORE_FWD = 3.0F, AIR_FORE_UP = 3.5F, FALL_TILT = 0.25F;
	/** How far the torso breathes at rest (a fraction of its length). */
	static final float BREATHE = 0.012F;
	private static final float TAU = (float)Math.PI * 2.0F;
	private static final float HALF_PI = (float)Math.PI * 0.5F;
	/** Each foot's place in the cycle on all fours (hind right, fore right, hind left, fore left): a lateral walk. */
	private static final float[] FOURS_PHASE = {0.0F, 0.25F, 0.5F, 0.75F};

	/** The seams' breath: ticks per cycle, how far the glow dips below full (0-1) and the extra flare as the eyes blink. */
	static final float BREATH_TICKS = 90.0F, BREATH_DIP = 0.55F;
	static final int FULL_BRIGHT = 0xF000F0;
	/** The spine's spikes: how many, the height of the first (torso y) and the gap between them, how far each leans back (radians). */
	static final int SPINE_SPIKES = 6;
	static final float SPIKE_Y = NECK_Y + 1.5F, SPIKE_GAP = 1.8F, SPIKE_LEAN = -0.9F;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/lurk.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0),
		uv("eye", 24, 0),
		uv("dome", 32, 0),
		uv("neck", 38, 0),
		uv("torso", 50, 0),
		uv("spine", 66, 0),
		uv("spike", 72, 0),
		uv("arm", 76, 0),
		uv("pauldron", 84, 0),
		uv("fore", 100, 0),
		uv("bracer", 108, 0),
		uv("hand", 0, 14),
		uv("thigh", 10, 14),
		uv("shin", 18, 14),
		uv("foot", 26, 14));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart torso, neck, rightEye, leftEye, rightEyeTop, leftEyeTop;
	private final ModelPart rightForearm, leftForearm, rightHand, leftHand, rightShin, leftShin, rightFoot, leftFoot;
	private final ModelPart[] moving;
	/** Each drawn entity's stance (0 standing, 1 on all fours) and the age it was last stepped at. */
	private final Map<Integer, float[]> stances = new HashMap<>();
	/** Scratch: a point in the torso's space made world, a foot's path, a limb's solved angles. */
	private final float[] point = new float[3];
	private final float[] step = new float[2];
	private final float[] angles = new float[2];

	public UltimateNightfall() {
		super(XenoFormModels.bake(mesh(), 128, 128), "lurk");
		ModelPart root = this.root();
		this.torso = root.getChild("torso");
		this.neck = root.getChild("neck");
		this.rightEye = this.head.getChild("right_eye");
		this.leftEye = this.head.getChild("left_eye");
		this.rightEyeTop = this.head.getChild("right_eye_top");
		this.leftEyeTop = this.head.getChild("left_eye_top");
		this.rightForearm = this.rightArm.getChild("right_forearm");
		this.leftForearm = this.leftArm.getChild("left_forearm");
		this.rightHand = this.rightForearm.getChild("right_hand");
		this.leftHand = this.leftForearm.getChild("left_hand");
		this.rightShin = this.rightLeg.getChild("shin");
		this.leftShin = this.leftLeg.getChild("shin");
		this.rightFoot = this.rightShin.getChild("foot");
		this.leftFoot = this.leftShin.getChild("foot");
		this.moving = new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.torso, this.neck,
			this.rightEye, this.leftEye, this.rightEyeTop, this.leftEyeTop, this.rightForearm, this.leftForearm, this.rightHand, this.leftHand,
			this.rightShin, this.leftShin, this.rightFoot, this.leftFoot};
		// the hidden body carries the plate: on the chest standing, on the back on all fours (see pose)
		this.insigniaY = 0.0F;
		this.insigniaZ = -1.5F;
		this.insigniaSize = 0.7F;
	}

	/** A box of {@code key}'s patch of the skin (its size {@code w x h x d} as in {@code DIMS} in the painter), mirrored for a left-hand part. */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d, CubeDeformation grow) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d, grow);
	}

	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d) {
		return cube(key, mirror, x, y, z, w, h, d, CubeDeformation.NONE);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		float hipY = 24.0F - STAND_HIP_UP;
		// the head: a tall bulb, four domes of eyes on its face (the lower pair large, the new pair above them smaller), a neck (a root part, so it can stretch from the torso to the head)
		PartDefinition head = root.addOrReplaceChild("head", cube("head", false, -3.0F, -7.0F, -3.0F, 6, 7, 6), PartPose.offset(0.0F, hipY + NECK_Y, -STAND_HEAD_FWD));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("right_eye", cube("eye", false, -1.5F, -1.5F, -0.5F, 3, 3, 1), PartPose.offset(-1.5F, -2.8F, -3.5F));
		head.addOrReplaceChild("left_eye", cube("eye", true, -1.5F, -1.5F, -0.5F, 3, 3, 1), PartPose.offset(1.5F, -2.8F, -3.5F));
		head.addOrReplaceChild("right_eye_top", cube("dome", false, -1.0F, -1.0F, -0.5F, 2, 2, 1), PartPose.offset(-1.3F, -5.6F, -3.5F));
		head.addOrReplaceChild("left_eye_top", cube("dome", true, -1.0F, -1.0F, -0.5F, 2, 2, 1), PartPose.offset(1.3F, -5.6F, -3.5F));
		root.addOrReplaceChild("neck", cube("neck", false, -1.5F, -3.0F, -1.5F, 3, 4, 3), PartPose.offset(0.0F, hipY + SHOULDER_Y, 0.0F));
		// the torso: thin, pivoting at the hips, a plated ridge down its back with a row of thin spikes leaning back off it; the man's body stays hidden, to carry the insignia
		PartDefinition torso = root.addOrReplaceChild("torso", cube("torso", false, -2.5F, NECK_Y, -1.5F, 5, 11, 3),
			PartPose.offsetAndRotation(0.0F, hipY, 0.0F, STAND_PITCH, 0.0F, 0.0F));
		PartDefinition spine = torso.addOrReplaceChild("spine", cube("spine", false, -1.0F, NECK_Y, 1.5F, 2, 11, 1), PartPose.ZERO);
		for (int i = 0; i < SPINE_SPIKES; i++) {
			spine.addOrReplaceChild("spine_spike_" + i, cube("spike", false, -0.5F, -4.0F, -0.5F, 1, 4, 1),
				PartPose.offsetAndRotation(0.0F, SPIKE_Y + SPIKE_GAP * i, 2.5F, SPIKE_LEAN, 0.0F, 0.0F));
		}
		// the arms: a plated shoulder, an upper arm, a forearm under a bracer, a long hand; every box runs a pixel past its joints
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			String s = left ? "left_" : "right_";
			PartDefinition arm = root.addOrReplaceChild(s + "arm", cube("arm", left, -1.0F, -1.0F, -1.0F, 2, 9, 2, new CubeDeformation(-0.1F, 0.0F, -0.1F)),
				PartPose.offset((left ? 1.0F : -1.0F) * SHOULDER_X, 24.0F - STAND_HIP_UP + SHOULDER_Y, 0.0F));
			arm.addOrReplaceChild(s + "pauldron", cube("pauldron", left, -2.0F, -2.0F, -2.0F, 4, 2, 4), PartPose.ZERO);
			PartDefinition fore = arm.addOrReplaceChild(s + "forearm", cube("fore", left, -1.0F, -1.0F, -1.0F, 2, 10, 2, new CubeDeformation(-0.3F, 0.0F, -0.3F)),
				PartPose.offset(0.0F, UPPER_ARM, 0.0F));
			fore.addOrReplaceChild(s + "bracer", cube("bracer", left, -1.5F, 1.0F, -1.5F, 3, 5, 3), PartPose.ZERO);
			fore.addOrReplaceChild(s + "hand", cube("hand", left, -1.5F, -1.0F, -1.0F, 3, 6, 2), PartPose.offset(0.0F, FOREARM, 0.0F));
		}
		// the legs: a thigh, a shin a hair thinner, a flat foot with the toes forward
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			PartDefinition leg = root.addOrReplaceChild(left ? "left_leg" : "right_leg", cube("thigh", left, -1.0F, -1.0F, -1.0F, 2, 9, 2, new CubeDeformation(-0.1F, 0.0F, -0.1F)),
				PartPose.offset((left ? 1.0F : -1.0F) * HIP_X, 24.0F - STAND_HIP_UP, 0.0F));
			PartDefinition shin = leg.addOrReplaceChild("shin", cube("shin", left, -1.0F, -1.0F, -1.0F, 2, 8, 2, new CubeDeformation(-0.3F, 0.0F, -0.3F)),
				PartPose.offset(0.0F, THIGH, 0.0F));
			shin.addOrReplaceChild("foot", cube("foot", left, -1.5F, -1.0F, -4.0F, 3, 2, 5), PartPose.offset(0.0F, SHIN, 0.0F));
		}
		return mesh;
	}

	/** The seams breathe slowly over the layer's glow (a full glow most of the time, dipping low between breaths). */
	@Override
	public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float breath = 0.5F + 0.5F * Mth.sin(state.ageInTicks * (Mth.TWO_PI / BREATH_TICKS));
		float flare = 1.0F - BREATH_DIP * (1.0F - breath);
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			(int)(flare * 255.0F) << 24 | 0xFFFFFF, null, 0);
	}

	/** 0-1 eased. */
	private static float smooth(float s) {
		return s * s * (3.0F - 2.0F * s);
	}

	/** The fraction of {@code x}. */
	private static float frac(float x) {
		return x - (float)Math.floor(x);
	}

	/** {@code state}'s entity's stance this frame, 0 standing to 1 on all fours: it moves toward its target over {@link #STANCE_TICKS}, stepped by the entity's age so a second draw in the same frame changes nothing. */
	private float stance(AvatarRenderState state, boolean fours) {
		float now = state.ageInTicks;
		float[] s = this.stances.get(state.id);
		if (s == null) {
			if (this.stances.size() > 64) {
				this.stances.clear();
			}
			s = new float[] {fours ? 1.0F : 0.0F, now};
			this.stances.put(state.id, s);
		}
		if (now < s[1] || now - s[1] > 100.0F) {
			// seen again after a while (or its age restarted): no slow change from where it was
			s[0] = fours ? 1.0F : 0.0F;
			s[1] = now;
		}
		float dt = Math.min(now - s[1], 3.0F);
		if (dt > 0.0F) {
			s[1] = now;
			s[0] = fours ? Math.min(1.0F, s[0] + dt / STANCE_TICKS) : Math.max(0.0F, s[0] - dt / STANCE_TICKS);
		}
		return s[0];
	}

	/**
	 * A point in the torso's space made world, into {@link #point}: the torso leans forward by {@code pitch} about the
	 * hips (at {@code py}, {@code pz}), then yaws by {@code yaw} about the upright through them.
	 */
	private void at(float py, float pz, float pitch, float yaw, float lx, float ly, float lz) {
		float y1 = ly * Mth.cos(pitch) - lz * Mth.sin(pitch);
		float z1 = ly * Mth.sin(pitch) + lz * Mth.cos(pitch);
		this.point[0] = lx * Mth.cos(yaw) + z1 * Mth.sin(yaw);
		this.point[1] = py + y1;
		this.point[2] = pz - lx * Mth.sin(yaw) + z1 * Mth.cos(yaw);
	}

	/**
	 * A foot's path against the body for a point in the cycle, into {@link #step}: on the ground it slides back at a steady
	 * pace, then it swings forward in an arc. {fore-aft offset (+ is back), height off the ground}.
	 */
	private void path(float p, float duty, float stride, float lift) {
		if (p < duty) {
			this.step[0] = -stride + 2.0F * stride * (p / duty);
			this.step[1] = 0.0F;
			return;
		}
		float v = (p - duty) / (1.0F - duty);
		this.step[0] = stride * Mth.cos((float)Math.PI * v);
		this.step[1] = lift * Mth.sin((float)Math.PI * v);
	}

	/**
	 * Two bones from a hip to an ankle in the side plane (z along the body, y down): the upper bone's and the lower bone's
	 * angles from straight down about x (+ swings the end back), into {@link #angles}. {@code bend} is +1 when the middle
	 * joint points back, -1 when it points forward. A target out of reach is stretched to the nearest reachable point.
	 */
	private void solve(float hipZ, float hipY, float targetZ, float targetY, float upper, float lower, float bend) {
		float dz = targetZ - hipZ;
		float dy = targetY - hipY;
		float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(upper - lower) + 0.05F, upper + lower - 0.001F);
		float toTarget = (float)Math.atan2(dz, dy);
		float cos = Mth.clamp((upper * upper + dist * dist - lower * lower) / (2.0F * upper * dist), -1.0F, 1.0F);
		float t1 = toTarget + bend * (float)Math.acos(cos);
		float kneeZ = hipZ + upper * Mth.sin(t1);
		float kneeY = hipY + upper * Mth.cos(t1);
		this.angles[0] = t1;
		this.angles[1] = (float)Math.atan2(targetZ - kneeZ, targetY - kneeY);
	}

	/** The first-person arm (see XenoformHand): the forearm and hand straight, as they hang. */
	public void handPose() {
		for (ModelPart part : new ModelPart[] {this.rightForearm, this.leftForearm, this.rightHand, this.leftHand}) {
			part.resetPose();
		}
		this.rightForearm.xRot = this.leftForearm.xRot = -0.15F;
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		// what vanilla made of the arms (held poses, swings, the idle sway) and of the body's twist in a swing: kept while it stands
		float twist = this.body.yRot;
		float rightX = this.rightArm.xRot, rightY = this.rightArm.yRot, rightZ = this.rightArm.zRot;
		float leftX = this.leftArm.xRot, leftY = this.leftArm.yRot, leftZ = this.leftArm.zRot;
		if (state.isCrouching) {
			// (vanilla tips a sneaking man's arms forward: the stance does that here, so a sneak doesn't jerk the arms first)
			rightX -= 0.4F;
			leftX -= 0.4F;
		}
		this.hat.visible = this.body.visible = false;
		for (ModelPart part : this.moving) {
			part.resetPose();
		}
		XenoMotion.Read m = XenoMotion.read(state);
		float age = state.ageInTicks;
		boolean sneaking = state.isCrouching;
		// sneaking, or faded ("fade", Lurk.faded on the server): all fours. (The shed skin kneels the same way.)
		float q = smooth(this.stance(state, sneaking || "fade".equals(state.getData(XenoformClient.ACT))));
		// a sneaking player is drawn CROUCH_SINK lower (a mob, the shed skin, is not): the ground, in model y, is that much higher
		float ground = 24.0F - (sneaking && !Boolean.TRUE.equals(state.getData(XenoformClient.SHED)) ? CROUCH_SINK : 0.0F);
		float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		float amp = Mth.clamp(speed / STEP_FULL, 0.0F, 1.0F);
		float stretch = 1.0F + RUN_STRETCH * Mth.clamp((speed - STEP_FULL) / (1.0F - STEP_FULL), 0.0F, 1.0F);
		float air = Mth.clamp(m.air(), 0.0F, 1.0F);
		float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		float sw = Mth.sin(swing * (float)Math.PI);
		float rise = sw * q;
		float walk = state.walkAnimationPos;
		float phS = walk * STAND_GAIT;
		float phF = walk * FOURS_GAIT;
		float cycS = phS / TAU;
		float cycF = phF / TAU;

		// the hips: they dip with each step, squat on a landing, and rise and lunge into a pounce
		float bobS = STAND_BOB * amp * (0.5F - 0.5F * Mth.cos(2.0F * phS));
		float bobF = FOURS_BOB * amp * (0.5F - 0.5F * Mth.cos(4.0F * phF));
		float hipUp = Mth.lerp(q, STAND_HIP_UP - bobS - m.land() * STAND_DIP_LAND, FOURS_HIP_UP - bobF - m.land() * FOURS_DIP_LAND) + RISE_UP * rise;
		float hipY = ground - hipUp;
		float hipZ = Mth.lerp(q, 0.0F, FOURS_HIP_Z) - LUNGE * rise;
		// the torso leans forward (and further into a standing swing), rears into a pounce, pitches with a fall; on all fours it sways like a spine
		float pitch = Mth.lerp(q, STAND_PITCH + STAND_LUNGE * sw, FOURS_PITCH) - RISE_PITCH * rise + m.fall() * air * FALL_TILT;
		float yaw = Mth.lerp(q, twist, Mth.sin(phF - 0.6F) * SWAY * amp);
		this.torso.setPos(0.0F, hipY, hipZ);
		this.torso.xRot = pitch;
		this.torso.yRot = yaw;
		this.torso.yScale = 1.0F + BREATHE * m.breath() * (1.0F - amp);

		// the hidden body lies on the torso: its front on the chest standing, turned over onto the back on all fours (it turns
		// over at once halfway through the change, so the plate is never half inside the torso)
		this.at(hipY, hipZ, pitch, yaw, 0.0F, CHEST_Y, 0.0F);
		this.body.setPos(this.point[0], this.point[1], this.point[2]);
		this.body.xRot = pitch + (q > 0.5F ? (float)Math.PI : 0.0F);
		this.body.yRot = yaw;

		// the head: on the torso's end, forward of it, looking where you look (level on all fours, and never into the ground)
		this.at(hipY, hipZ, pitch, yaw, 0.0F, NECK_Y, 0.0F);
		float headX = this.point[0];
		float headY = this.point[1] + Mth.lerp(q, 0.0F, FOURS_HEAD_DROP);
		float headZ = this.point[2] - Mth.lerp(q, STAND_HEAD_FWD, FOURS_HEAD_FWD) - THRUST * rise;
		float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
		float lookFours = Mth.clamp(look * FOURS_LOOK, -0.5F, 0.4F);
		this.head.setPos(headX, headY, headZ);
		this.head.xRot = Mth.lerp(q, look + STAND_DIP, lookFours) + STRIKE_DIP * rise + m.land() * 0.15F;
		float gaze = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
		this.head.yRot = gaze - yaw * 0.5F * q;
		this.head.zRot = Mth.lerp(q, STAND_TILT, FOURS_TILT) + Mth.sin(age * 0.03F) * 0.06F - m.turn() * 0.06F;
		// the eyes blink together and swell and shrink a little as if they were looking at something
		float blink = Math.max(0.0F, 1.0F - Math.abs(age % 110.0F - 2.0F) * 0.5F);
		float gleam = 1.0F + 0.05F * Mth.sin(age * 0.11F);
		this.rightEye.yScale = this.leftEye.yScale = Mth.lerp(blink, gleam, 0.1F);
		this.rightEye.xScale = this.leftEye.xScale = gleam;
		// the new pair above them blinks a beat after, a little smaller
		this.rightEyeTop.yScale = this.leftEyeTop.yScale = Mth.lerp(Math.max(0.0F, 1.0F - Math.abs(age % 110.0F - 6.0F) * 0.5F), gleam, 0.1F);
		this.rightEyeTop.xScale = this.leftEyeTop.xScale = gleam;
		// the neck is stretched from the torso's end to the head's pivot, so it never leaves either
		this.at(hipY, hipZ, pitch, yaw, 0.0F, SHOULDER_Y + 1.0F, 0.0F);
		float neckY = this.point[1];
		float neckZ = this.point[2];
		// (aimed two pixels up into the head, so it joins the head's middle rather than its underside)
		float dy = headY - 2.0F * Mth.cos(this.head.xRot) - neckY;
		float dz = headZ - 2.0F * Mth.sin(this.head.xRot) - neckZ;
		this.neck.setPos(this.point[0], neckY, neckZ);
		this.neck.xRot = (float)Math.atan2(-dz, -dy);
		this.neck.yScale = ((float)Math.sqrt(dy * dy + dz * dz) + 0.5F) / 3.0F;

		// the arms: standing, the vanilla pose (the walking swing mostly taken out, the forearm folding as the arm swings forward);
		// on all fours, forelegs solved to the ground, a pounce raising and clawing them; changing stance, the wrist goes from
		// where the standing arm holds it to its place on the ground and the arm is solved to reach it, kept high enough that
		// the hand (turning from hanging to flat) never goes into the ground
		float armWalk = Mth.cos(walk * 0.6662F) * speed * (1.0F - ARM_KEEP);
		rightX += armWalk;
		leftX -= armWalk;
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			float sign = left ? 1.0F : -1.0F;
			ModelPart arm = left ? this.leftArm : this.rightArm;
			ModelPart fore = left ? this.leftForearm : this.rightForearm;
			ModelPart hand = left ? this.leftHand : this.rightHand;
			this.at(hipY, hipZ, pitch, yaw, sign * SHOULDER_X, SHOULDER_Y, 0.0F);
			float shoulderX = this.point[0];
			float shoulderY = this.point[1];
			float shoulderZ = this.point[2];
			arm.setPos(shoulderX, shoulderY, shoulderZ);
			float standX = left ? leftX : rightX;
			float standFore = -(0.12F + 0.5F * Math.max(0.0F, -standX));
			float standHand = -0.2F * standX;
			// the forelegs
			float strike = sw * (left ? OFF_HAND : 1.0F);
			float stride = FORE_STRIDE * amp * stretch;
			float liftMax = FORE_LIFT * amp;
			this.path(frac(cycF + FOURS_PHASE[side * 2 + 1]), FOURS_DUTY, stride, liftMax);
			float lift = this.step[1];
			float wristZ = FORE_REST_Z + this.step[0] - STRIKE_REACH * strike + STRIKE_DRAG * (swing - 0.4F) * strike - AIR_FORE_FWD * air;
			float wristY = ground - ANKLE_UP - lift - STRIKE_UP * strike - AIR_FORE_UP * air;
			// the hand lies flat on the ground, its fingers forward; lifted, the fingers droop, and a pounce claws them down
			float flat = -HALF_PI + CLAW * strike + 0.5F * (liftMax > 0.01F ? lift / liftMax : 0.0F) * amp + 0.4F * air;
			arm.yRot = Mth.lerp(q, left ? leftY : rightY, 0.0F);
			arm.zRot = Mth.lerp(q, left ? leftZ : rightZ, 0.0F);
			if (q <= 0.0F) {
				arm.xRot = standX;
				fore.xRot = standFore;
				hand.xRot = standHand;
				continue;
			}
			float handAngle = Mth.lerp(q, standX + standFore + standHand, flat);
			float standWristZ = shoulderZ + UPPER_ARM * Mth.sin(standX) + FOREARM * Mth.sin(standX + standFore);
			float standWristY = shoulderY + UPPER_ARM * Mth.cos(standX) + FOREARM * Mth.cos(standX + standFore);
			// how far below the wrist the hand reaches at that angle (its length one way, a pixel the other, a pixel thick)
			float below = Math.max(HAND_LONG * Mth.cos(handAngle), -Mth.cos(handAngle)) + Math.abs(Mth.sin(handAngle));
			float toY = Math.min(Mth.lerp(q, standWristY, wristY), ground - below);
			this.solve(shoulderZ, shoulderY, Mth.lerp(q, standWristZ, wristZ), toY, UPPER_ARM, FOREARM, 1.0F);
			arm.xRot = this.angles[0];
			fore.xRot = this.angles[1] - this.angles[0];
			hand.xRot = handAngle - this.angles[1];
		}

		// the legs: the ankle goes where a foot would be (a stride standing, a lateral walk on all fours) and the thigh and shin are
		// solved to reach it with the knee forward, so the leg never breaks at a joint; the foot stays flat, toes down as it lifts
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			float sign = left ? 1.0F : -1.0F;
			ModelPart leg = left ? this.leftLeg : this.rightLeg;
			ModelPart shin = left ? this.leftShin : this.rightShin;
			ModelPart foot = left ? this.leftFoot : this.rightFoot;
			float liftMax = Mth.lerp(q, STAND_LIFT, HIND_LIFT) * amp;
			this.path(frac(cycS + side * 0.5F), STAND_DUTY, STAND_STRIDE * amp * stretch, STAND_LIFT * amp);
			float standZ = this.step[0];
			float standLift = this.step[1];
			this.path(frac(cycF + FOURS_PHASE[side * 2]), FOURS_DUTY, HIND_STRIDE * amp * stretch, HIND_LIFT * amp);
			float footZ = Mth.lerp(q, standZ, HIND_REST_Z + this.step[0]) + air * Mth.lerp(q, AIR_BACK_STAND, AIR_BACK_FOURS);
			float lift = Mth.lerp(q, standLift, this.step[1]);
			lift = Mth.lerp(air, lift, Mth.lerp(q, AIR_LIFT_STAND, AIR_LIFT_FOURS));
			this.solve(hipZ, hipY, footZ, ground - ANKLE_UP - lift, THIGH, SHIN, -1.0F);
			float thigh = this.angles[0];
			float shinAngle = this.angles[1];
			float tip = 0.35F * (liftMax > 0.01F ? Mth.clamp(lift / liftMax, 0.0F, 1.0F) : 0.0F) * amp + 0.3F * air;
			leg.setPos(sign * HIP_X, hipY, hipZ);
			leg.xRot = thigh;
			shin.xRot = shinAngle - thigh;
			foot.xRot = tip - shinAngle;
		}
	}
}
