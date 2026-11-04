package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
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
 * (0.20.21, Ultimates patch 1) Knell's Ultimate, The Tolling: Knell's own build and gait (the hunched beast on its fists and bowed legs,
 * every foot and fist solved onto the ground, the head slung low on a thick neck, the same stances: "sense", "toll", "burrow") grown
 * heavier. Plates of sculk-black stone sit on the hump and over the forearms, a ridge of sculk spikes runs down the spine, the horns are
 * longer with a third curl, and the rib plates are wider and swing open on every toll. Teal seams run through the hide and between the
 * plates (the skin's glow map, drawn fullbright by the layer) and beat like a heart ({@link #submitExtras}). It lumbers slower and
 * heavier than Knell does. It is {@code XenoFormModels.Knell}'s rig, copied, since that class is not open to other packages; the
 * skin is {@code tools/ultimates/knell.py}, 128x128, and {@link #UV} here is the table that script prints ({@code knell.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code right_forearm+left_forearm}, {@code hump+spine}, {@code right_rib+left_rib} and
 * {@code right_horn+left_horn}: those names are parts of this mesh.
 */
public final class UltimateTolling extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The pelvis, the body's pivot (model y, z), the back's forward lean about it at rest (radians), its breath and how far a swing rears it back. */
	static final float PELVIS_Y = 14.5F, PELVIS_Z = 3.0F, LEAN = 0.62F, LEAN_BREATH = 0.025F, BOOM_REAR = 0.14F;
	/** How far a sneaking renderer sinks the model (pixels), and how far a landing and a crouch dip the pelvis. */
	static final float CROUCH_SINK = 2.0F / 0.9375F, LAND_DIP = 2.0F, CROUCH_SQUAT = 1.5F, CROUCH_LEAN = 0.12F;
	/** The gait: radians of step cycle per unit of walkAnimationPos (slower than Knell's 0.9), how much walk speed it takes to be at full stride, the fraction of the cycle a foot is down. */
	static final float GAIT = 0.6662F * 0.78F, SPEED_GAIN = 1.25F, DUTY = 0.64F;
	/** The lumber: how far the body shifts (pixels) and rolls (radians) over the planted foot, how far it bobs (pixels), how far a turn twists it. */
	static final float ROCK_SHIFT = 1.2F, ROCK = 0.09F, BOB = 0.9F, TURN_TWIST = 0.1F;
	/** The legs: a hip's offset from the middle and its rest place (model y, z), the thigh's and the shin's length, the ankle's height over the sole. */
	static final float HIP_X = 3.8F, HIP_Y = 16.0F, HIP_Z = 3.6F, THIGH = 5.0F, SHIN = 5.0F, ANKLE = 1.0F;
	/** The legs' steps: half a stride and the lift of a swinging foot (pixels), how far ahead of its hip a foot rests, and the bow and the toe-out (radians). */
	static final float STRIDE = 3.4F, LIFT = 1.8F, FOOT_AHEAD = -0.6F, BOW = 0.2F, TOE_OUT = 0.12F;
	/** The shoulders (in the body's space: out from the middle, y, z), the arms' two bones, the height of the wrist over the fist's sole and the fist's half length and half width. */
	static final float SHOULDER_X = 7.5F, SHOULDER_Y = -10.5F, SHOULDER_Z = -0.5F, UPPER = 8.0F, FORE = 8.0F, FIST = 3.0F, FIST_HALF = 3.5F, FIST_SIDE = 3.0F;
	/** The fists' steps: half a stride and the lift of a swinging fist (pixels), how far ahead of its shoulder a fist rests, and how high the wrist rests over the ground. */
	static final float ARM_STRIDE = 3.8F, ARM_LIFT = 1.6F, ARM_AHEAD = 0.5F, WRIST_Y = 3.4F;
	/** The neck's base on the body (y, z), its length and its resting pitch (radians, absolute, - is forward), and how far a swing throws it out (pixels). */
	static final float NECK_Y = -11.0F, NECK_Z = -1.0F, NECK = 7.0F, NECK_PITCH = -0.95F, THRUST = 3.2F;
	/** The head: how far it hangs down at rest, how far a swing throws it up (radians), and how far the jaw hangs shut and opens on a swing. */
	static final float HEAD_DROP = 0.22F, BOOM_LIFT = 0.4F, JAW_REST = 0.08F, JAW_BOOM = 0.9F;
	/** The rib plates: their hinge's height and distance out on the trunk (below the hump's overhang, which ends at y = -8) and how far they swing open on a swing and in the toll (radians). */
	static final float RIB_Y = -4.0F, RIB_X = 6.0F, RIB_OPEN = 1.2F;
	/** Tremor Sense: how much lower the back leans, how far the pelvis squats and how far ahead the fists are put. */
	static final float SENSE_LEAN = 0.3F, SENSE_SQUAT = 2.0F, SENSE_REACH = 3.0F;
	/** The Tolling: ticks between tolls ({@code Tolling.TOLL_EVERY}), the back's lean at rest and reared, how far the pelvis squats, how high the fists rise over the shoulder. */
	static final float TOLL_TICKS = 40.0F, TOLL_LEAN = 0.35F, TOLL_REAR = 0.25F, TOLL_SQUAT = 2.0F, TOLL_RISE = 6.0F;
	/** Burrowing: how much lower the back leans, how far the pelvis squats, how fast the fists scoop and how far they reach, how far the head bows. */
	static final float BURROW_LEAN = 0.4F, BURROW_SQUAT = 4.0F, SCOOP_RATE = 0.7F, SCOOP_REACH = 4.0F, BURROW_BOW = 0.1F;
	/**
	 * The change-back tap: the fist comes in flat across the chest, its face on the insignia: how far in front of the
	 * plate its middle stops (its half depth, 3.5, and a hair), how far further out and up it is poised, and how far
	 * down the fist its middle is (from the wrist).
	 */
	static final float TAP_FIST = 4.0F, TAP_POISE = 3.0F, TAP_POISE_UP = 0.5F, TAP_FIST_MID = 1.0F;
	/** Which way the tapping elbow points (model space before the body's yaw: out to the right side, down and forward). */
	static final float TAP_POLE_X = -1.0F, TAP_POLE_Y = 1.0F, TAP_POLE_Z = -1.0F;
	/** The heartbeat of the seams: ticks per beat, the second beat's place in it, how wide each beat is, and how bright the seams flare on a beat (0-1). */
	static final float BEAT_TICKS = 34.0F, BEAT_SECOND = 0.34F, BEAT_WIDTH = 0.1F, BEAT_FLARE = 0.9F;
	/** The toll's flare: how fast the seams fade after each toll (of the toll's beat). */
	static final float TOLL_FLARE_DECAY = 3.0F;
	/** The spine's spikes and the horns' curl: how far they sway (radians). */
	static final float SPIKE_SWAY = 0.05F, CURL_SWAY = 0.04F;
	static final int FULL_BRIGHT = 0xF000F0;
	private static final float PI = (float)Math.PI;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/knell.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0),
		uv("brow", 32, 0),
		uv("jaw", 56, 0),
		uv("tendril", 82, 0),
		uv("horn_b", 86, 0),
		uv("horn_t", 94, 0),
		uv("horn_c", 102, 0),
		uv("neck", 0, 14),
		uv("trunk", 24, 14),
		uv("hump", 60, 14),
		uv("hplate", 0, 37),
		uv("rib", 42, 37),
		uv("growth", 54, 37),
		uv("spine", 66, 37),
		uv("spike", 74, 37),
		uv("arm", 78, 37),
		uv("fore", 98, 37),
		uv("fist", 0, 53),
		uv("bracer", 26, 53),
		uv("thigh", 54, 53),
		uv("shin", 74, 53),
		uv("foot", 90, 53));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart neck, jaw, ribRight, ribLeft;
	/** Right horn, its tip, left horn, its tip, then the two curls. */
	private final ModelPart[] horns = new ModelPart[6], growths = new ModelPart[2], tendrils = new ModelPart[3], spikes = new ModelPart[SPINE_SPIKES];
	/** Index 0 right, 1 left. */
	private final ModelPart[] fore = new ModelPart[2], fist = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
	private final ModelPart[] posed;
	private final float[] at = new float[3], gait = new float[3], bones = new float[2], tapAt = new float[3], tapRot = new float[4], rot = new float[3];

	/** The spikes down the spine: how many, the height of the first (body y) and the gap between them, how far each leans back (radians). */
	static final int SPINE_SPIKES = 5;
	static final float SPIKE_Y = -11.5F, SPIKE_GAP = 3.0F, SPIKE_LEAN = -0.8F;

	public UltimateTolling() {
		super(XenoFormModels.bake(mesh(), 128, 128), "knell");
		this.ownsTap = true;
		ModelPart root = this.root();
		this.neck = root.getChild("neck");
		this.jaw = this.head.getChild("jaw");
		this.ribRight = this.body.getChild("right_rib");
		this.ribLeft = this.body.getChild("left_rib");
		this.horns[0] = this.head.getChild("right_horn");
		this.horns[1] = this.horns[0].getChild("right_tip");
		this.horns[4] = this.horns[1].getChild("right_curl");
		this.horns[2] = this.head.getChild("left_horn");
		this.horns[3] = this.horns[2].getChild("left_tip");
		this.horns[5] = this.horns[3].getChild("left_curl");
		this.growths[0] = this.body.getChild("right_growth");
		this.growths[1] = this.body.getChild("left_growth");
		ModelPart ridge = this.body.getChild("spine");
		for (int i = 0; i < SPINE_SPIKES; i++) {
			this.spikes[i] = ridge.getChild("spine_spike_" + i);
		}
		for (int i = 0; i < 3; i++) {
			this.tendrils[i] = this.jaw.getChild("tendril_" + i);
		}
		this.fore[0] = this.rightArm.getChild("right_forearm");
		this.fore[1] = this.leftArm.getChild("left_forearm");
		this.fist[0] = this.fore[0].getChild("right_fist");
		this.fist[1] = this.fore[1].getChild("left_fist");
		this.shin[0] = this.rightLeg.getChild("shin");
		this.shin[1] = this.leftLeg.getChild("shin");
		this.foot[0] = this.shin[0].getChild("foot");
		this.foot[1] = this.shin[1].getChild("foot");
		this.posed = new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.neck};
		// the plate lies in the gap between the rib plates, flush on the trunk's front (the trunk's front face is at z = -3.5),
		// below the hump's overhang (a whole skin row: the ribs' middle is a row's edge)
		this.insigniaY = RIB_Y + 0.5F;
		this.insigniaZ = -3.5F;
		this.insigniaSize = 0.6F;
	}

	/** A box of {@code key}'s patch of the skin (its size {@code w x h x d} as in {@code DIMS} in the painter), mirrored for a left-hand part. */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		float[] p = new float[3];
		// the trunk (the man's body): the back, leaning forward about the pelvis; a hump of shoulders under a sculk-black plate, rib plates hinged over the glowing chest, spurs of sculk
		PartDefinition body = root.addOrReplaceChild("body", cube("trunk", false, -5.5F, -13.0F, -3.5F, 11, 16, 7), PartPose.offsetAndRotation(0.0F, PELVIS_Y, PELVIS_Z, LEAN, 0.0F, 0.0F));
		PartDefinition hump = body.addOrReplaceChild("hump", cube("hump", false, -7.5F, -14.0F, -4.5F, 15, 6, 9), PartPose.ZERO);
		hump.addOrReplaceChild("hump_plate", cube("hplate", false, -6.5F, -15.5F, -4.0F, 13, 2, 8), PartPose.ZERO);
		body.addOrReplaceChild("right_rib", cube("rib", false, 0.0F, -4.0F, -1.0F, 5, 10, 1), PartPose.offset(-RIB_X, RIB_Y, -3.5F));
		body.addOrReplaceChild("left_rib", cube("rib", true, -5.0F, -4.0F, -1.0F, 5, 10, 1), PartPose.offset(RIB_X, RIB_Y, -3.5F));
		body.addOrReplaceChild("right_growth", cube("growth", false, -1.5F, -5.0F, -1.5F, 3, 5, 3), PartPose.offsetAndRotation(-5.5F, -13.5F, 1.0F, 0.0F, 0.0F, -0.35F));
		body.addOrReplaceChild("left_growth", cube("growth", true, -1.5F, -5.0F, -1.5F, 3, 5, 3), PartPose.offsetAndRotation(5.5F, -13.5F, 1.0F, 0.0F, 0.0F, 0.35F));
		// a ridge down the back with a row of sculk spikes leaning back off it
		PartDefinition spine = body.addOrReplaceChild("spine", cube("spine", false, -1.0F, -12.0F, 3.0F, 2, 13, 2), PartPose.ZERO);
		for (int i = 0; i < SPINE_SPIKES; i++) {
			spine.addOrReplaceChild("spine_spike_" + i, cube("spike", false, -0.5F, -4.0F, -0.5F, 1, 4, 1),
				PartPose.offsetAndRotation(0.0F, SPIKE_Y + SPIKE_GAP * i, 5.0F, SPIKE_LEAN, 0.0F, 0.0F));
		}
		// the neck, thick, from the hump forward and down; the head on its end
		place(0.0F, NECK_Y, NECK_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
		root.addOrReplaceChild("neck", cube("neck", false, -3.0F, -3.0F, -3.0F, 6, 11, 6), PartPose.offsetAndRotation(p[0], p[1], p[2], NECK_PITCH, 0.0F, 0.0F));
		place(0.0F, NECK, 0.0F, NECK_PITCH, 0.0F, 0.0F, p[0], p[1], p[2], p);
		PartDefinition head = root.addOrReplaceChild("head", cube("head", false, -4.0F, -5.0F, -7.0F, 8, 6, 8), PartPose.offsetAndRotation(p[0], p[1], p[2], HEAD_DROP, 0.0F, 0.0F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("brow", cube("brow", false, -4.5F, -6.0F, -8.0F, 9, 2, 3), PartPose.ZERO);
		PartDefinition jaw = head.addOrReplaceChild("jaw", cube("jaw", false, -3.5F, -0.5F, -6.5F, 7, 3, 6), PartPose.offsetAndRotation(0.0F, 0.5F, -0.5F, JAW_REST, 0.0F, 0.0F));
		// sculk tendrils hanging from the chin
		for (int i = 0; i < 3; i++) {
			jaw.addOrReplaceChild("tendril_" + i, cube("tendril", false, -0.5F, 0.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(-2.5F + 2.5F * i, 2.5F, -5.5F, 0.15F, 0.0F, 0.2F - 0.2F * i));
		}
		// the horns curl out and up, then in and forward, then further in: longer than Knell's, with a third joint
		PartDefinition rightHorn = head.addOrReplaceChild("right_horn", cube("horn_b", false, -1.0F, -7.0F, -1.0F, 2, 7, 2), PartPose.offsetAndRotation(-3.6F, -4.5F, -2.5F, 0.0F, 0.0F, -0.7F));
		PartDefinition rightTip = rightHorn.addOrReplaceChild("right_tip", cube("horn_t", false, -1.0F, -5.0F, -1.0F, 2, 6, 2), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, -0.5F, 0.0F, 1.1F));
		rightTip.addOrReplaceChild("right_curl", cube("horn_c", false, -1.0F, -3.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, -0.45F, 0.0F, 0.9F));
		PartDefinition leftHorn = head.addOrReplaceChild("left_horn", cube("horn_b", true, -1.0F, -7.0F, -1.0F, 2, 7, 2), PartPose.offsetAndRotation(3.6F, -4.5F, -2.5F, 0.0F, 0.0F, 0.7F));
		PartDefinition leftTip = leftHorn.addOrReplaceChild("left_tip", cube("horn_t", true, -1.0F, -5.0F, -1.0F, 2, 6, 2), PartPose.offsetAndRotation(0.0F, -7.0F, 0.0F, -0.5F, 0.0F, -1.1F));
		leftTip.addOrReplaceChild("left_curl", cube("horn_c", true, -1.0F, -3.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, -0.45F, 0.0F, -0.9F));
		// the arms (the man's): upper arm, forearm bent forward at rest under a sculk-black bracer, fist; the shoulders where the hump's ends are
		place(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", cube("arm", false, -2.5F, -2.0F, -2.5F, 5, 11, 5), PartPose.offset(p[0], p[1], p[2]));
		PartDefinition rightFore = rightArm.addOrReplaceChild("right_forearm", cube("fore", false, -3.0F, -1.0F, -3.0F, 6, 10, 6), PartPose.offsetAndRotation(0.0F, UPPER, 0.0F, -0.6F, 0.0F, 0.0F));
		rightFore.addOrReplaceChild("right_bracer", cube("bracer", false, -3.5F, 1.5F, -3.5F, 7, 5, 7), PartPose.ZERO);
		rightFore.addOrReplaceChild("right_fist", cube("fist", false, -3.0F, -1.0F, -3.5F, 6, 4, 7), PartPose.offsetAndRotation(0.0F, FORE, 0.0F, 0.3F, 0.0F, 0.0F));
		place(SHOULDER_X, SHOULDER_Y, SHOULDER_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", cube("arm", true, -2.5F, -2.0F, -2.5F, 5, 11, 5), PartPose.offset(p[0], p[1], p[2]));
		PartDefinition leftFore = leftArm.addOrReplaceChild("left_forearm", cube("fore", true, -3.0F, -1.0F, -3.0F, 6, 10, 6), PartPose.offsetAndRotation(0.0F, UPPER, 0.0F, -0.6F, 0.0F, 0.0F));
		leftFore.addOrReplaceChild("left_bracer", cube("bracer", true, -3.5F, 1.5F, -3.5F, 7, 5, 7), PartPose.ZERO);
		leftFore.addOrReplaceChild("left_fist", cube("fist", true, -3.0F, -1.0F, -3.5F, 6, 4, 7), PartPose.offsetAndRotation(0.0F, FORE, 0.0F, 0.3F, 0.0F, 0.0F));
		// the legs (the man's): thigh, shin, flat foot; knees forward at rest
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			PartDefinition leg = root.addOrReplaceChild(left ? "left_leg" : "right_leg", cube("thigh", left, -2.5F, -2.0F, -2.5F, 5, 8, 5),
				PartPose.offsetAndRotation(left ? HIP_X : -HIP_X, HIP_Y, HIP_Z, -0.8F, 0.0F, 0.0F));
			PartDefinition shin = leg.addOrReplaceChild("shin", cube("shin", left, -2.0F, -1.5F, -2.0F, 4, 7, 4), PartPose.offsetAndRotation(0.0F, THIGH, 0.0F, 1.6F, 0.0F, 0.0F));
			shin.addOrReplaceChild("foot", cube("foot", left, -2.5F, -1.0F, -5.0F, 5, 2, 7), PartPose.offsetAndRotation(0.0F, SHIN, 0.0F, -0.8F, 0.0F, 0.0F));
		}
		return mesh;
	}

	/** The seams' heartbeat at {@code age} ticks (0-1): a lub and a dub, each a short swell. */
	private static float heartbeat(float age) {
		float u = (age % BEAT_TICKS) / BEAT_TICKS;
		float lub = Math.max(0.0F, 1.0F - Math.abs(u) / BEAT_WIDTH);
		float dub = Math.max(0.0F, 1.0F - Math.abs(u - BEAT_SECOND) / BEAT_WIDTH) * 0.7F;
		return Math.max(lub, dub);
	}

	/** The seams flare over the layer's slow glow with the heartbeat, and with each toll's strike. */
	@Override
	public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float flare = heartbeat(state.ageInTicks) * BEAT_FLARE;
		if ("toll".equals(state.getData(XenoformClient.ACT))) {
			net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
			float clock = level != null ? (level.getGameTime() % (long)TOLL_TICKS) + fract(state.ageInTicks) : state.ageInTicks;
			// the strike lands as the fists come down (the end of the beat), then the light drains away
			flare = Math.max(flare, Mth.clamp(1.0F - fract(clock / TOLL_TICKS) * TOLL_FLARE_DECAY, 0.0F, 1.0F));
		}
		if (flare < 0.04F) {
			return;
		}
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			(int)(flare * 255.0F) << 24 | 0xFFFFFF, null, 0);
	}

	/** A point of the body's space put where the body is: turned about x (the lean), y, then z, as a part is, then moved to the pelvis. */
	private static void place(float x, float y, float z, float xr, float yr, float zr, float px, float py, float pz, float[] out) {
		float y1 = y * Mth.cos(xr) - z * Mth.sin(xr);
		float z1 = y * Mth.sin(xr) + z * Mth.cos(xr);
		float x2 = x * Mth.cos(yr) + z1 * Mth.sin(yr);
		float z2 = -x * Mth.sin(yr) + z1 * Mth.cos(yr);
		out[0] = px + x2 * Mth.cos(zr) - y1 * Mth.sin(zr);
		out[1] = py + x2 * Mth.sin(zr) + y1 * Mth.cos(zr);
		out[2] = pz + z2;
	}

	/** A step's phase at {@code u} (0-1 through a cycle) into {@link #gait}: the foot's place (-1 forward to 1 back), its lift (0-1), 1 while it swings. */
	private void step(float u) {
		if (u < DUTY) {
			this.gait[0] = -1.0F + 2.0F * u / DUTY;
			this.gait[1] = 0.0F;
			this.gait[2] = 0.0F;
		} else {
			float t = (u - DUTY) / (1.0F - DUTY);
			float ease = t * t * (3.0F - 2.0F * t);
			this.gait[0] = 1.0F - 2.0F * ease;
			this.gait[1] = Mth.sin(t * PI);
			this.gait[2] = 1.0F;
		}
	}

	/** Two bones from (sy, sz) to (ty, tz), the joint on the side {@code bend} (-1 forward, 1 back), into {@link #bones}: each bone's angle from straight down (x rotation). */
	private void ik(float sy, float sz, float ty, float tz, float l1, float l2, float bend) {
		float dz = tz - sz;
		float dy = ty - sy;
		float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.01F);
		float to = (float)Math.atan2(dz, dy);
		float cos = Mth.clamp((l1 * l1 + dist * dist - l2 * l2) / (2.0F * l1 * dist), -1.0F, 1.0F);
		float t1 = to + bend * (float)Math.acos(cos);
		float jz = sz + l1 * Mth.sin(t1);
		float jy = sy + l1 * Mth.cos(t1);
		this.bones[0] = t1;
		this.bones[1] = (float)Math.atan2(sz + dist * Mth.sin(to) - jz, sy + dist * Mth.cos(to) - jy);
	}

	/**
	 * (0.19.84) The right arm from the shoulder (sx, sy, sz) to put the fist's middle on (tx, ty, tz), a 3D two-bone
	 * reach with the elbow toward the pole (px, py, pz) and the wrist straight: into {@link #tapRot} the upper arm's
	 * x, y, z angles (the parts' Z*Y*X order) and the forearm's hinge.
	 */
	private void tapArm(float sx, float sy, float sz, float tx, float ty, float tz, float px, float py, float pz) {
		float dx = tx - sx, dy = ty - sy, dz = tz - sz;
		float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.1F) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		// the second bone runs on through the straight wrist to the fist's middle
		float lower = FORE + TAP_FIST_MID;
		float reach = Mth.clamp(len, Math.abs(UPPER - lower) + 0.05F, UPPER + lower - 0.01F);
		float along = (UPPER * UPPER + reach * reach - lower * lower) / (2.0F * reach);
		float out = (float)Math.sqrt(Math.max(0.0F, UPPER * UPPER - along * along));
		// the pole, made square to the reach
		float qx = px, qy = py, qz = pz;
		float dot = qx * dx + qy * dy + qz * dz;
		qx -= dot * dx;
		qy -= dot * dy;
		qz -= dot * dz;
		float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
		if (ql < 1.0E-4F) {
			return;
		}
		qx /= ql;
		qy /= ql;
		qz /= ql;
		// the elbow, and the upper arm (local y) and forearm directions
		float ex = dx * along + qx * out, ey = dy * along + qy * out, ez = dz * along + qz * out;
		float yx = ex / UPPER, yy = ey / UPPER, yz = ez / UPPER;
		float fx = (dx * reach - ex) / lower, fy = (dy * reach - ey) / lower, fz = (dz * reach - ez) / lower;
		// the hinge (local x) is square to both, turned so the elbow bends the way the arm's rest pose does (a
		// negative turn about x), so nothing flips over as the tap starts; local z completes the frame
		float xx = yz * fy - yy * fz, xy = yx * fz - yz * fx, xz = yy * fx - yx * fy;
		float xl = (float)Math.sqrt(xx * xx + xy * xy + xz * xz);
		if (xl < 1.0E-4F) {
			return;
		}
		xx /= xl;
		xy /= xl;
		xz /= xl;
		float zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
		// R = [x y z] as columns = Rz * Ry * Rx
		this.tapRot[0] = (float)Math.atan2(yz, zz);
		this.tapRot[1] = (float)Math.asin(Mth.clamp(-xz, -1.0F, 1.0F));
		this.tapRot[2] = (float)Math.atan2(xy, xx);
		// the forearm's hinge: its direction in the upper arm's frame, (0, cos, sin) of the turn about x
		float fore = (float)Math.atan2(fx * zx + fy * zy + fz * zz, fx * yx + fy * yy + fz * yz);
		this.tapRot[3] = fore;
	}

	/** (0.19.84) {@code (x, y, z)} turned as a part is (Z*Y*X by {@code xr, yr, zr}), into {@link #rot}. */
	private void turnPart(float x, float y, float z, float xr, float yr, float zr) {
		float y1 = y * Mth.cos(xr) - z * Mth.sin(xr), z1 = y * Mth.sin(xr) + z * Mth.cos(xr);
		float x2 = x * Mth.cos(yr) + z1 * Mth.sin(yr), z2 = -x * Mth.sin(yr) + z1 * Mth.cos(yr);
		this.rot[0] = x2 * Mth.cos(zr) - y1 * Mth.sin(zr);
		this.rot[1] = x2 * Mth.sin(zr) + y1 * Mth.cos(zr);
		this.rot[2] = z2;
	}

	/**
	 * The forearms and fists straight, and the shoulders back over the player's, for the first-person arm (the model is
	 * shared, so they hold whoever was drawn last, and the arms' rest place is out on the hump, ahead of the chest).
	 */
	public void handPose() {
		this.rightArm.z = 0.0F;
		this.leftArm.z = 0.0F;
		for (int side = 0; side < 2; side++) {
			this.fore[side].xRot = 0.0F;
			this.fist[side].xRot = 0.0F;
		}
	}

	private static float fract(float v) {
		return v - (float)Math.floor(v);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		this.hat.visible = false;
		for (ModelPart part : this.posed) {
			part.resetPose();
		}
		XenoMotion.Read m = XenoMotion.read(state);
		String act = state.getData(XenoformClient.ACT);
		boolean burrow = "burrow".equals(act);
		boolean toll = "toll".equals(act);
		boolean sense = "sense".equals(act);
		float age = state.ageInTicks;
		boolean crouch = state.isCrouching;
		boolean low = crouch || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK));
		float ground = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
		float air = Mth.clamp(m.air(), 0.0F, 1.0F);
		// a swing is a Sonic Boom, a Shriek or a blow; burrowing swings too as it starts digging, but that is the scoop's to show
		float swing = burrow ? 0.0F : Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * PI);
		float breath = m.breath();
		float speed = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F) * (1.0F - air) * (toll ? 0.0F : burrow ? 0.25F : 1.0F);
		float w = state.walkAnimationPos * GAIT;
		// the Tolling's beat: the fists rise over the toll and slam down on it (the server tolls when the level's game
		// time is a multiple of the interval; the client's game time follows the server's, an entity's age does not)
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		float clock = level != null ? (level.getGameTime() % (long)TOLL_TICKS) + fract(age) : age;
		float beat = fract(clock / TOLL_TICKS);
		float rise = beat < 0.7F ? Mth.sin(beat / 0.7F * PI * 0.5F) : 1.0F - (float)Math.pow((beat - 0.7F) / 0.3F, 3.0);
		if (!toll) {
			rise = 0.0F;
		}
		// (0.19.84) the change-back tap: the right fist comes up off the ground onto the insignia (XenoformSlap leaves that arm to us)
		XenoformSlap.Pose slap = state.getData(XenoformSlap.KEY);
		float tap = slap != null && slap.chest() && !XenoformSlap.ownBody(state) ? slap.raise() : 0.0F;
		float tapHit = slap != null ? slap.strike() : 0.0F;

		// ---- the body: the back leans over the pelvis, rocking over the planted foot ----
		// (the crouch's own squat and lean only when no ability already has it low)
		boolean crouchPose = low && !sense && !burrow && !toll;
		float lean = LEAN + breath * LEAN_BREATH - swing * BOOM_REAR + (crouchPose ? CROUCH_LEAN : 0.0F);
		float squat = (crouchPose ? CROUCH_SQUAT : 0.0F) + (burrow ? 0.0F : m.land() * LAND_DIP);
		float wide = 0.0F;
		if (sense) {
			lean += SENSE_LEAN;
			squat += SENSE_SQUAT;
			wide = 0.8F;
		}
		if (burrow) {
			lean += BURROW_LEAN;
			squat += BURROW_SQUAT;
			wide = 0.8F;
		}
		if (toll) {
			lean = TOLL_LEAN - TOLL_REAR * rise;
			squat += TOLL_SQUAT;
			wide = 1.0F;
		}
		float rock = Mth.cos(w - DUTY * PI) * speed;
		float bob = Mth.cos(2.0F * (w - DUTY * PI)) * speed * BOB;
		float px = -rock * ROCK_SHIFT;
		float py = PELVIS_Y - (crouch ? CROUCH_SINK : 0.0F) + squat - bob;
		float pz = PELVIS_Z;
		float yaw = m.turn() * TURN_TWIST;
		float roll = -rock * ROCK;
		this.body.setPos(px, py, pz);
		this.body.xRot = lean;
		this.body.yRot = yaw;
		this.body.zRot = roll;
		// the rib plates: open on a swing (the chest glows) and while it tolls
		float open = Mth.clamp(swing * 1.1F + rise * 0.9F + (toll ? 0.25F : 0.0F), 0.0F, 1.0F) * RIB_OPEN;
		this.ribRight.yRot = open;
		this.ribLeft.yRot = -open;
		float spur = Mth.sin(age * 0.1F) * 0.05F;
		this.growths[0].zRot = -0.35F + spur - m.trail() * 0.1F;
		this.growths[1].zRot = 0.35F - spur - m.trail() * 0.1F;

		// ---- the legs: planted flat on the ground, the knees forward ----
		float hipY = py + (HIP_Y - PELVIS_Y);
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			float sgn = left ? 1.0F : -1.0F;
			ModelPart leg = left ? this.leftLeg : this.rightLeg;
			float hx = sgn * (HIP_X + wide) + px * 0.5F;
			this.step(fract((w + (left ? PI : 0.0F)) / (2.0F * PI)));
			float tz = HIP_Z + FOOT_AHEAD + this.gait[0] * STRIDE * speed - air * 1.0F;
			float ty = ground - ANKLE - this.gait[1] * LIFT * speed - air * 3.5F;
			this.ik(hipY, HIP_Z, ty, tz, THIGH, SHIN, -1.0F);
			leg.setPos(hx, hipY, HIP_Z);
			leg.xRot = this.bones[0];
			leg.yRot = -sgn * TOE_OUT;
			leg.zRot = -sgn * (BOW + wide * 0.15F);
			this.shin[side].xRot = this.bones[1] - this.bones[0];
			this.shin[side].zRot = sgn * (BOW + wide * 0.15F) * 1.5F;
			// the foot lies flat on the ground, the toes dropping as it swings
			float lifted = this.gait[1] * LIFT * speed;
			this.foot[side].xRot = (float)Math.min(0.3F, Math.asin(Math.min(1.0F, lifted / 5.0F))) - this.bones[1];
			// and level across: the bow's tilt of the shin taken back out at the ankle, so the sole lies flat
			this.foot[side].zRot = sgn * (BOW + wide * 0.15F) * 0.5F;
		}

		// ---- the arms: the fists plant on the ground ahead of the shoulders, swinging against the legs ----
		float splay = sense || burrow ? 0.3F : toll ? 0.12F : 0.05F;
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			float sgn = left ? 1.0F : -1.0F;
			ModelPart arm = left ? this.leftArm : this.rightArm;
			place(sgn * SHOULDER_X, SHOULDER_Y, SHOULDER_Z, lean, yaw, roll, px, py, pz, this.at);
			float sx = this.at[0];
			float sy = this.at[1];
			float sz = this.at[2];
			// an arm swings with the opposite leg
			this.step(fract((w + (left ? 0.0F : PI)) / (2.0F * PI)));
			float tz = sz + ARM_AHEAD + this.gait[0] * ARM_STRIDE * speed + (sense ? SENSE_REACH : 0.0F);
			float ty = ground - WRIST_Y - this.gait[1] * ARM_LIFT * speed;
			float fistAbs = this.gait[2] * this.gait[1] * 0.35F;
			if (burrow) {
				// scooping the ground back, one fist and then the other
				float scoop = Mth.sin(age * SCOOP_RATE + (left ? PI : 0.0F));
				tz = sz + 2.0F - scoop * SCOOP_REACH;
				ty = ground - WRIST_Y - Math.max(0.0F, Mth.cos(age * SCOOP_RATE + (left ? PI : 0.0F))) * 3.0F;
				fistAbs = scoop * 0.4F;
			}
			if (toll) {
				// up over the shoulders and down onto the ground
				tz = Mth.lerp(rise, sz + ARM_AHEAD + 1.0F, sz - 3.0F);
				ty = Mth.lerp(rise, ground - WRIST_Y, sy - TOLL_RISE);
				fistAbs = -rise * 0.5F;
			}
			if (!left && swing > 0.0F && !toll) {
				// the swing: the right fist slams forward and up
				tz = Mth.lerp(swing, tz, sz - 11.0F);
				ty = Mth.lerp(swing, ty, sy + 2.0F);
				fistAbs = -swing * 0.4F;
			}
			// a tipped fist (pitched or rolled with the arm) is lifted so its lowest edge stays where its flat sole would be
			float fistRoll = -sgn * splay + roll;
			ty -= FIST * (Mth.cos(fistAbs) - 1.0F) + FIST_HALF * Math.abs(Mth.sin(fistAbs)) + FIST_SIDE * Math.abs(Mth.sin(fistRoll));
			// the arm stands splayed out, which shortens it by the splay's cosine
			float flat = Mth.cos(splay);
			this.ik(sy, sz, sy + (ty - sy) / flat, tz, UPPER, FORE, 1.0F);
			arm.setPos(sx, sy, sz);
			arm.xRot = this.bones[0];
			arm.yRot = yaw;
			arm.zRot = fistRoll;
			this.fore[side].xRot = this.bones[1] - this.bones[0];
			this.fist[side].xRot = fistAbs - this.bones[1];
			float tapped = left ? 0.0F : tap;
			if (tapped > 0.0F) {
				// (0.19.84) the change-back tap: the forearm comes in across the chest with the wrist straight, the fist's
				// face flat on the insignia (poised a little further out), the elbow out to the side clear of the chest and
				// the neck. The fist's middle and the elbow's side are eased over from the walking arm's, not its angles,
				// so the arm stays solved (never sweeping through the trunk) the whole way up.
				place(0.0F, this.insigniaY - TAP_POISE_UP * (1.0F - tapHit), this.insigniaZ - TAP_FIST - TAP_POISE * (1.0F - tapHit), lean, yaw, roll, px, py,
					pz, this.tapAt);
				this.turnPart(0.0F, UPPER, 0.0F, arm.xRot, arm.yRot, arm.zRot);
				float ex = this.rot[0], ey = this.rot[1], ez = this.rot[2];
				// the walking fist's middle: down the forearm, then down the fist at its own bend
				float fa = this.fore[side].xRot, fb = fa + this.fist[side].xRot;
				this.turnPart(0.0F, FORE * Mth.cos(fa) + TAP_FIST_MID * Mth.cos(fb), FORE * Mth.sin(fa) + TAP_FIST_MID * Mth.sin(fb), arm.xRot, arm.yRot,
					arm.zRot);
				float wx = sx + ex + this.rot[0], wy = sy + ey + this.rot[1], wz = sz + ez + this.rot[2];
				float pole = (float)Math.sqrt(TAP_POLE_X * TAP_POLE_X + TAP_POLE_Y * TAP_POLE_Y + TAP_POLE_Z * TAP_POLE_Z);
				float qx = (TAP_POLE_X * Mth.cos(yaw) + TAP_POLE_Z * Mth.sin(yaw)) / pole, qy = TAP_POLE_Y / pole;
				float qz = (-TAP_POLE_X * Mth.sin(yaw) + TAP_POLE_Z * Mth.cos(yaw)) / pole;
				this.tapRot[0] = arm.xRot;
				this.tapRot[1] = arm.yRot;
				this.tapRot[2] = arm.zRot;
				this.tapRot[3] = this.fore[side].xRot;
				this.tapArm(sx, sy, sz, Mth.lerp(tapped, wx, this.tapAt[0]), Mth.lerp(tapped, wy, this.tapAt[1]), Mth.lerp(tapped, wz, this.tapAt[2]),
					Mth.lerp(tapped, ex / UPPER, qx), Mth.lerp(tapped, ey / UPPER, qy), Mth.lerp(tapped, ez / UPPER, qz));
				arm.xRot = this.tapRot[0];
				arm.yRot = this.tapRot[1];
				arm.zRot = this.tapRot[2];
				this.fore[side].xRot = this.tapRot[3];
				// the wrist straightens as it comes up
				this.fist[side].xRot *= 1.0F - tapped;
			}
		}

		// ---- the neck and head: slung low and forward; a swing throws them out ----
		float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.9F, 0.9F);
		float neckPitch = NECK_PITCH + (lean - LEAN) * -0.6F + look * 0.2F;
		float neckYaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F) * 0.35F;
		place(0.0F, NECK_Y, NECK_Z, lean, yaw, roll, px, py, pz, this.at);
		this.neck.setPos(this.at[0], this.at[1], this.at[2]);
		this.neck.xRot = neckPitch;
		this.neck.yRot = yaw + neckYaw;
		this.neck.zRot = roll;
		// a swing throws the head out: the neck stretches with it, so the skull never leaves it
		this.neck.yScale = (NECK + 1.0F + THRUST * swing) / (NECK + 1.0F);
		place(0.0F, NECK + THRUST * swing, 0.0F, this.neck.xRot, this.neck.yRot, this.neck.zRot, this.at[0], this.at[1], this.at[2], this.at);
		this.head.setPos(this.at[0], this.at[1], this.at[2]);
		float listen = sense ? 0.35F + Mth.sin(age * 0.06F) * 0.1F : 0.0F;
		float headPitch = HEAD_DROP + look * (burrow ? 0.4F : 0.85F) - (neckPitch - NECK_PITCH) * 0.5F - swing * BOOM_LIFT + listen - rise * 0.8F + (burrow ? BURROW_BOW : 0.0F)
			+ m.land() * 0.2F + Mth.sin(w * 2.0F) * 0.04F * speed + breath * 0.01F;
		this.head.xRot = headPitch;
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) + (sense ? Mth.sin(age * 0.045F) * 0.3F : 0.0F);
		this.head.zRot = roll * 1.5F;
		this.jaw.xRot = JAW_REST + Mth.sin(age * 0.09F) * 0.03F + swing * JAW_BOOM + rise * 0.9F + Math.max(0.0F, m.fall()) * 0.2F * air;
		// the tendrils hang straight down whichever way the head turns, sway on their own and swing out of a turn
		for (int i = 0; i < 3; i++) {
			float own = Mth.sin(age * 0.11F + i * 1.7F);
			this.tendrils[i].xRot = 0.1F - (this.head.xRot + this.jaw.xRot) * 0.9F + own * 0.08F + m.speed() * 0.25F + m.land() * 0.4F;
			this.tendrils[i].zRot = 0.2F - 0.2F * i + own * 0.06F - m.trail() * 0.4F;
		}
		// the horns twitch with the head
		float flick = Mth.sin(age * 0.07F) * 0.03F + m.land() * 0.15F;
		this.horns[0].zRot = -0.7F + flick;
		this.horns[2].zRot = 0.7F - flick;
		this.horns[1].zRot = 1.1F + flick;
		this.horns[3].zRot = -1.1F - flick;
		this.horns[4].zRot = 0.9F + flick * 1.5F + Mth.sin(age * 0.05F) * CURL_SWAY;
		this.horns[5].zRot = -0.9F - flick * 1.5F - Mth.sin(age * 0.05F) * CURL_SWAY;
		// the spine's spikes lean back a little further with each breath and trail the body's turn
		for (int i = 0; i < SPINE_SPIKES; i++) {
			this.spikes[i].xRot = SPIKE_LEAN + Mth.sin(age * 0.08F + i * 0.9F) * SPIKE_SWAY - m.speed() * 0.15F;
		}
	}
}
