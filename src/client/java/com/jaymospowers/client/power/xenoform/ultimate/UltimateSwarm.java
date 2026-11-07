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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * (0.20.21, Ultimates patch 1) Gremlin's Ultimate, Swarm: Gremlin's own build and gait (the hunched imp on its knuckles, the big
 * bobbing skull, the floppy ears, every limb solved onto the ground with two-bone IK), drawn at 0.45 of a player instead of 0.3.
 * The skull gets a plated brow with a crest of five small horns, the fists get knuckle plates, the shoulders pauldrons, the
 * forearms and shins guards, the flanks plates and the back six spines. The thin tail grows barbs and curls up over the back in
 * three joints, ending in a stinger. Green seams glow along the plates (the skin's glow map, drawn fullbright by the layer). It
 * scurries a little faster than Gremlin lopes. The skin is {@code tools/ultimates/gremlin.py}, 128x128; {@link #UV} here is the
 * same table that script prints ({@code gremlin.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code right_fist+left_fist} (the knuckle plates), {@code tail}, {@code right_ear} and
 * {@code head}: those names are parts of this mesh.
 */
public final class UltimateSwarm extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The torso's centre (model y, z), its resting pitch (negative: shoulders up, hips low), and how far it drops when sneaking. */
	static final float TORSO_Y = 11.0F, TORSO_Z = 1.5F, TORSO_PITCH = -0.30F, CROUCH_DROP = 2.5F;
	/** Where the shoulders and hips sit on the torso (local x, y, z; the right side is negative x). */
	static final float SHOULDER_X = 4.6F, SHOULDER_Y = -0.5F, SHOULDER_Z = -3.0F;
	static final float HIP_X = 3.2F, HIP_Y = 1.0F, HIP_Z = 3.0F;
	/** The neck: where the skull is hung on the torso's front (local y, z) and how far below it the pivot sits. */
	static final float NECK_Y = -1.5F, NECK_Z = -4.0F, NECK_DROP = 1.0F;
	/** Bone lengths: arm upper and lower, leg upper and lower; how far a plated fist and a foot reach below their joint. */
	static final float ARM_UPPER = 7.0F, ARM_LOWER = 7.0F, LEG_UPPER = 5.0F, LEG_LOWER = 6.0F;
	static final float FIST_DROP = 3.0F, FOOT_DROP = 1.5F;
	/** Where the fists and feet plant (model z) at rest, how far a stride sweeps, and how high a swinging limb lifts. */
	static final float HAND_Z = -5.0F, FOOT_Z = 3.5F, HAND_STRIDE = 6.0F, FOOT_STRIDE = 6.0F, HAND_LIFT = 3.0F, FOOT_LIFT = 2.5F;
	/** The share of a cycle a limb is planted, and how fast the cycle runs against the walk (Gremlin's 1.25, quicker). */
	static final float DUTY = 0.6F, GAIT = 0.6662F * 1.5F;
	/** Where each limb is in the cycle: the arms alternate, the legs a beat behind the opposite arm. */
	static final float RIGHT_ARM_AT = 0.0F, LEFT_LEG_AT = 0.12F, LEFT_ARM_AT = 0.5F, RIGHT_LEG_AT = 0.62F;
	/** The body's bob (pixels) and rock (radians) a step. */
	static final float BOB = 0.9F, ROCK = 0.07F;
	/** The big skull's bob (pixels) and nod (radians) each footfall, and the phase it lags the body by. */
	static final float SKULL_BOB = 1.3F, SKULL_NOD = 0.2F, SKULL_LAG = 0.9F;
	/** The skull's resting pitch (negative: held up) and how far a bite darts it forward (pixels; keep under 2.5 so the skull stays on the torso). */
	static final float HEAD_PITCH = -0.1F, BITE_DART = 2.0F;
	/** The jaw: how wide a bite opens it, and the idle chatter. */
	static final float JAW_BITE = 0.85F, JAW_IDLE = 0.04F;
	/** A swipe: how high and how far forward the right fist goes. */
	static final float SWIPE_LIFT = 7.0F, SWIPE_REACH = 4.0F;
	/** A leap: how high the fists and feet are drawn up, and how far forward the fists reach. */
	static final float AIR_HAND_LIFT = 8.0F, AIR_HAND_REACH = 3.0F, AIR_FOOT_LIFT = 5.0F;
	/** The ears' resting droop (zRot; right ear, negative is outward), their sweep back at a run, and their flop. */
	static final float EAR_REST = -0.2F, EAR_SWEEP = 0.45F, EAR_FLOP = 0.25F;
	/** The tail's resting rise at its root, how far its middle and its sting curl on from there (radians), and how far they flick. */
	static final float TAIL_REST = 0.5F, TAIL_MID_CURL = 0.85F, TAIL_TIP_CURL = 0.9F, TAIL_FLICK = 0.12F;
	/** The insignia's size on the back. */
	static final float PLATE_SIZE = 0.85F;
	/** How far the horn crest sways against the skull (radians) and how fast. */
	static final float CREST_SWAY = 0.05F, CREST_RATE = 0.2F;
	/** The horn crest: how many, where across the brow (x pixels) and how tall each is (0 short, 1 tall). */
	static final int CREST = 5;
	private static final float[] CREST_X = {-4.0F, -2.0F, 0.0F, 2.0F, 4.0F};
	private static final boolean[] CREST_TALL = {false, true, true, true, false};
	/** The back's spines: where along the torso (z) each pair sits. */
	private static final float[] SPINE_Z = {-3.0F, 0.0F, 3.0F};

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/gremlin.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("skull", 0, 0), uv("brow", 40, 0), uv("jaw", 70, 0), uv("ear", 96, 0), uv("torso", 0, 20), uv("flank", 32, 20),
		uv("spike", 46, 20), uv("uarm", 50, 20), uv("farm", 62, 20), uv("fist", 70, 20), uv("fplate", 84, 20), uv("pauld", 102, 20),
		uv("guard", 0, 35), uv("thigh", 12, 35), uv("shin", 24, 35), uv("foot", 32, 35), uv("tail", 48, 35), uv("stinger", 60, 35));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart torso, tail, tailMid, tailTip, jaw, rightEar, leftEar;
	private final ModelPart rightLower, leftLower, rightFist, leftFist, rightShin, leftShin, rightFoot, leftFoot;
	private final ModelPart[] crest = new ModelPart[CREST];
	/** The foot a limb is aimed at (set by {@link #aim}). */
	private float aimY, aimZ;

	public UltimateSwarm() {
		super(XenoFormModels.bake(mesh(), 128, 128), "gremlin");
		this.insigniaY = 0.0F;
		this.insigniaZ = -3.0F;
		this.insigniaSize = PLATE_SIZE;
		ModelPart root = this.root();
		this.torso = root.getChild("torso");
		this.tail = root.getChild("tail");
		this.tailMid = this.tail.getChild("tail_mid");
		this.tailTip = this.tailMid.getChild("tail_tip");
		this.jaw = this.head.getChild("jaw");
		this.rightEar = this.head.getChild("right_ear");
		this.leftEar = this.head.getChild("left_ear");
		this.rightLower = this.rightArm.getChild("forearm");
		this.leftLower = this.leftArm.getChild("forearm");
		this.rightFist = this.rightLower.getChild("right_fist");
		this.leftFist = this.leftLower.getChild("left_fist");
		this.rightShin = this.rightLeg.getChild("shin");
		this.leftShin = this.leftLeg.getChild("shin");
		this.rightFoot = this.rightShin.getChild("foot");
		this.leftFoot = this.leftShin.getChild("foot");
		ModelPart brow = this.head.getChild("brow");
		for (int i = 0; i < CREST; i++) {
			this.crest[i] = brow.getChild("crest_" + i);
		}
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d, float grow) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d, new CubeDeformation(grow));
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// the skull is the head: big, held up and forward of the neck pivot, with a hinged jaw, two great ears, a plated brow and a crest of horns
		PartDefinition head = root.addOrReplaceChild("head", cube("skull", false, -5.0F, -9.0F, -8.0F, 10, 10, 10, 0.0F), PartPose.offset(0.0F, 8.0F, -4.0F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("jaw", cube("jaw", false, -3.5F, 0.0F, -6.5F, 7, 3, 6, 0.0F), PartPose.offset(0.0F, 1.0F, -1.5F));
		head.addOrReplaceChild("right_ear", cube("ear", false, -5.0F, -6.0F, -0.5F, 5, 6, 1, 0.0F),
			PartPose.offsetAndRotation(-4.5F, -5.5F, -3.0F, 0.0F, 0.0F, EAR_REST));
		head.addOrReplaceChild("left_ear", cube("ear", true, 0.0F, -6.0F, -0.5F, 5, 6, 1, 0.0F),
			PartPose.offsetAndRotation(4.5F, -5.5F, -3.0F, 0.0F, 0.0F, -EAR_REST));
		PartDefinition brow = head.addOrReplaceChild("brow", cube("brow", false, -5.0F, -9.0F, -8.0F, 10, 2, 5, 0.4F), PartPose.ZERO);
		for (int i = 0; i < CREST; i++) {
			brow.addOrReplaceChild("crest_" + i, cube("spike", i > 2, -0.5F, CREST_TALL[i] ? -3.0F : -2.0F, -0.5F, 1, CREST_TALL[i] ? 3 : 2, 1, 0.0F),
				PartPose.offsetAndRotation(CREST_X[i], -9.2F, -5.5F, -0.45F, 0.0F, CREST_X[i] * 0.05F));
		}
		// the man's body is hidden (no cubes): it only carries the insignia, laid on the back of the real torso
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, TORSO_Y, TORSO_Z));
		PartDefinition torso = root.addOrReplaceChild("torso", cube("torso", false, -3.5F, -3.0F, -4.5F, 7, 6, 9, 0.0F),
			PartPose.offsetAndRotation(0.0F, TORSO_Y, TORSO_Z, TORSO_PITCH, 0.0F, 0.0F));
		// plates down both flanks, and a pair of spines at each of three places along the back
		torso.addOrReplaceChild("right_flank", cube("flank", false, -4.5F, -2.5F, -3.0F, 1, 4, 6, 0.0F), PartPose.ZERO);
		torso.addOrReplaceChild("left_flank", cube("flank", true, 3.5F, -2.5F, -3.0F, 1, 4, 6, 0.0F), PartPose.ZERO);
		for (int i = 0; i < SPINE_Z.length * 2; i++) {
			boolean left = (i & 1) == 1;
			torso.addOrReplaceChild("spine" + i, cube("spike", left, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
				PartPose.offsetAndRotation(left ? 2.9F : -2.9F, -2.6F, SPINE_Z[i / 2], 0.4F, 0.0F, left ? 0.25F : -0.25F));
		}
		// the tail curls up over the back in three joints: root, middle (a barb on each) and a stinger
		PartDefinition tail = root.addOrReplaceChild("tail", cube("tail", false, -0.5F, -0.5F, 0.0F, 1, 1, 5, 0.0F),
			PartPose.offsetAndRotation(0.0F, 11.0F, 6.0F, TAIL_REST, 0.0F, 0.0F));
		tail.addOrReplaceChild("barb_0", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F), PartPose.offsetAndRotation(0.0F, -0.4F, 2.0F, -0.3F, 0.0F, 0.0F));
		PartDefinition mid = tail.addOrReplaceChild("tail_mid", cube("tail", false, -0.5F, -0.5F, 0.0F, 1, 1, 5, 0.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, TAIL_MID_CURL, 0.0F, 0.0F));
		mid.addOrReplaceChild("barb_1", cube("spike", true, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F), PartPose.offsetAndRotation(0.0F, -0.4F, 1.5F, -0.3F, 0.0F, 0.0F));
		mid.addOrReplaceChild("barb_2", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F), PartPose.offsetAndRotation(0.0F, -0.4F, 3.5F, -0.3F, 0.0F, 0.0F));
		PartDefinition tip = mid.addOrReplaceChild("tail_tip", cube("stinger", false, -0.5F, -0.5F, 0.0F, 1, 1, 3, 0.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, TAIL_TIP_CURL, 0.0F, 0.0F));
		tip.addOrReplaceChild("barb_3", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F), PartPose.offsetAndRotation(0.0F, -0.4F, 1.0F, -0.3F, 0.0F, 0.0F));
		// long arms (upper with a pauldron, lower with a guard, plated knuckled fist) and short bowed legs (thigh, shin with a guard, foot)
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			String s = left ? "left" : "right";
			float sx = left ? SHOULDER_X : -SHOULDER_X;
			float hx = left ? HIP_X : -HIP_X;
			PartDefinition arm = root.addOrReplaceChild(s + "_arm", cube("uarm", left, -1.5F, -1.0F, -1.5F, 3, 8, 3, 0.0F), PartPose.offset(sx, 9.6F, -1.2F));
			arm.addOrReplaceChild("pauldron", cube("pauld", left, -2.5F, -2.0F, -2.5F, 5, 2, 5, 0.0F), PartPose.ZERO);
			PartDefinition fore = arm.addOrReplaceChild("forearm", cube("farm", left, -1.0F, -1.0F, -1.0F, 2, 8, 2, 0.0F), PartPose.offset(0.0F, ARM_UPPER, 0.0F));
			fore.addOrReplaceChild("bracer", cube("guard", left, -1.5F, 1.0F, -1.5F, 3, 3, 3, 0.0F), PartPose.ZERO);
			PartDefinition fist = fore.addOrReplaceChild(s + "_fist", cube("fist", left, -1.5F, -0.5F, -2.5F, 3, 3, 4, 0.0F), PartPose.offset(0.0F, ARM_LOWER, 0.0F));
			fist.addOrReplaceChild("knuckles", cube("fplate", left, -2.0F, -1.0F, -3.0F, 4, 4, 5, 0.0F), PartPose.ZERO);
			PartDefinition leg = root.addOrReplaceChild(s + "_leg", cube("thigh", left, -1.5F, -1.0F, -1.5F, 3, 6, 3, 0.0F), PartPose.offset(hx, 12.8F, 4.0F));
			PartDefinition shin = leg.addOrReplaceChild("shin", cube("shin", left, -1.0F, -1.0F, -1.0F, 2, 7, 2, 0.0F), PartPose.offset(0.0F, LEG_UPPER, 0.0F));
			shin.addOrReplaceChild("greave", cube("guard", left, -1.5F, 1.0F, -1.5F, 3, 3, 3, 0.0F), PartPose.ZERO);
			shin.addOrReplaceChild("foot", cube("foot", left, -1.5F, -0.5F, -3.5F, 3, 2, 5, 0.0F), PartPose.offset(0.0F, LEG_LOWER, 0.0F));
		}
		return mesh;
	}

	/** Where in its cycle (0-1) a limb is, given the walk clock and its offset. */
	private static float cycle(float walk, float at) {
		float u = walk / (2.0F * (float)Math.PI) + at;
		return u - (float)Math.floor(u);
	}

	/**
	 * Aims a limb's foot for a point in the cycle: planted and sweeping back for {@link #DUTY}, then lifted and swinging forward. Idle
	 * (speed 0) it stands on its rest spot.
	 */
	private void aim(float u, float speed, float groundY, float restZ, float stride, float lift) {
		float s = stride * speed;
		if (u < DUTY) {
			this.aimY = groundY;
			this.aimZ = restZ - s * 0.5F + s * (u / DUTY);
		} else {
			float q = (u - DUTY) / (1.0F - DUTY);
			this.aimY = groundY - lift * speed * Mth.sin(q * (float)Math.PI);
			this.aimZ = restZ + s * 0.5F - s * q;
		}
	}

	/**
	 * Two-bone IK in the side plane: swings {@code upper} (hung at {@code hy, hz}) and {@code lower} so the end of {@code end} reaches
	 * (ty, tz), or as near as the bones allow. {@code bend} is which way the joint folds (+1 back, like an elbow; -1 forward, like a
	 * knee); {@code tilt} is the end part's pitch from level.
	 */
	private static void solve(ModelPart upper, ModelPart lower, ModelPart end, float hy, float hz, float ty, float tz, float l1, float l2,
		float bend, float tilt) {
		float dy = ty - hy;
		float dz = tz - hz;
		float d = Math.max(1.0E-3F, (float)Math.sqrt(dy * dy + dz * dz));
		float reach = Mth.clamp(d, Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.05F);
		dy *= reach / d;
		dz *= reach / d;
		float phi = (float)Math.atan2(dz, dy);
		float cos = Mth.clamp((l1 * l1 + reach * reach - l2 * l2) / (2.0F * l1 * reach), -1.0F, 1.0F);
		float a1 = phi + bend * (float)Math.acos(cos);
		float a2 = (float)Math.atan2(dz - l1 * Mth.sin(a1), dy - l1 * Mth.cos(a1));
		upper.y = hy;
		upper.z = hz;
		upper.xRot = a1;
		lower.xRot = a2 - a1;
		end.xRot = tilt - a2;
	}

	/** The model-space y of a point (local y, z) on the torso at this centre height and pitch. */
	private static float worldY(float cy, float pitch, float ly, float lz) {
		return cy + ly * Mth.cos(pitch) - lz * Mth.sin(pitch);
	}

	private static float worldZ(float pitch, float ly, float lz) {
		return TORSO_Z + ly * Mth.sin(pitch) + lz * Mth.cos(pitch);
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	private static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	/** The forearm and fist straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
	public void handPose() {
		this.rightLower.xRot = 0.0F;
		this.leftLower.xRot = 0.0F;
		this.rightFist.xRot = 0.0F;
		this.leftFist.xRot = 0.0F;
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
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float walk = state.walkAnimationPos * GAIT;
		float age = state.ageInTicks;
		float air = m.air();
		float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		float bite = Mth.sin(swing * (float)Math.PI);
		float swipe = bite;
		boolean low = m.crouching();
		// the body: bobs with the lope, rocks nose-down on a bite, stretches in a leap and squashes on landing
		float cy = TORSO_Y + (low ? CROUCH_DROP : 0.0F) - BOB * Mth.abs(Mth.sin(walk)) * speed
			+ Mth.sin(age * 0.09F) * 0.15F + Math.max(0.0F, m.land()) * 1.5F - air * 0.5F;
		float pitch = TORSO_PITCH + ROCK * Mth.sin(walk * 2.0F + 0.4F) * speed + bite * 0.12F - air * 0.2F + m.land() * 0.1F;
		this.torso.y = cy;
		this.torso.xRot = pitch;
		// the hidden body lies face-up on the torso's back so the insignia plate sits flush there
		this.body.y = cy;
		this.body.z = TORSO_Z;
		this.body.xRot = pitch - (float)Math.PI * 0.5F;
		// limbs: arms alternate, the legs a beat behind the opposite arm; planted fists and feet reach the ground
		float shoulderY = worldY(cy, pitch, SHOULDER_Y, SHOULDER_Z);
		float shoulderZ = worldZ(pitch, SHOULDER_Y, SHOULDER_Z);
		float hipY = worldY(cy, pitch, HIP_Y, HIP_Z);
		float hipZ = worldZ(pitch, HIP_Y, HIP_Z);
		float handY = 24.0F - FIST_DROP;
		float footY = 24.0F - FOOT_DROP;
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			ModelPart arm = left ? this.leftArm : this.rightArm;
			ModelPart lower = left ? this.leftLower : this.rightLower;
			ModelPart fist = left ? this.leftFist : this.rightFist;
			ModelPart leg = left ? this.leftLeg : this.rightLeg;
			ModelPart shin = left ? this.leftShin : this.rightShin;
			ModelPart foot = left ? this.leftFoot : this.rightFoot;
			this.aim(cycle(walk, left ? LEFT_ARM_AT : RIGHT_ARM_AT), speed, handY, HAND_Z, HAND_STRIDE, HAND_LIFT);
			float ty = this.aimY;
			float tz = this.aimZ;
			if (!left) {
				ty -= swipe * SWIPE_LIFT;
				tz -= swipe * SWIPE_REACH;
			}
			ty = Mth.lerp(air, ty, handY - AIR_HAND_LIFT);
			tz = Mth.lerp(air, tz, HAND_Z - AIR_HAND_REACH);
			arm.x = left ? SHOULDER_X : -SHOULDER_X;
			solve(arm, lower, fist, shoulderY, shoulderZ, ty, tz, ARM_UPPER, ARM_LOWER, 1.0F, (handY - ty) * 0.05F);
			this.aim(cycle(walk, left ? LEFT_LEG_AT : RIGHT_LEG_AT), speed, footY, FOOT_Z, FOOT_STRIDE, FOOT_LIFT);
			ty = Mth.lerp(air, this.aimY, footY - AIR_FOOT_LIFT);
			tz = Mth.lerp(air, this.aimZ, FOOT_Z - 1.0F);
			leg.x = left ? HIP_X : -HIP_X;
			solve(leg, shin, foot, hipY, hipZ, ty, tz, LEG_UPPER, LEG_LOWER, -1.0F, (footY - ty) * 0.08F);
		}
		// the big skull: hung on the front of the torso, bobbing and nodding each footfall a beat behind the body
		float lag = walk * 2.0F - SKULL_LAG;
		float bob = Mth.sin(lag) * SKULL_BOB * speed + Mth.sin(age * 0.07F) * 0.25F;
		this.head.y = worldY(cy, pitch, NECK_Y, NECK_Z) + NECK_DROP + bob - bite * 0.5F;
		this.head.z = worldZ(pitch, NECK_Y, NECK_Z) - bite * BITE_DART;
		this.head.xRot = HEAD_PITCH + state.xRot * Mth.DEG_TO_RAD + Mth.sin(lag + 0.6F) * SKULL_NOD * speed
			- (pitch - TORSO_PITCH) * 0.8F + bite * 0.25F + m.land() * 0.25F - air * 0.2F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.zRot = Mth.sin(walk) * 0.05F * speed;
		this.jaw.xRot = JAW_IDLE + Mth.sin(age * 0.23F) * JAW_IDLE * (0.5F + speed) + Mth.sin(Math.min(1.0F, swing * 1.3F) * (float)Math.PI) * JAW_BITE
			+ Math.max(0.0F, m.fall()) * 0.2F * air;
		// the horn crest rattles a little on each footfall
		for (int i = 0; i < CREST; i++) {
			this.crest[i].zRot = Mth.sin(age * CREST_RATE + i * 1.3F) * CREST_SWAY * (0.4F + speed);
		}
		// the ears bounce with each step, flop down on landing (and lift as it falls), blow back at a run and lag a turn
		float twitch = Mth.sin(age * 0.3F) * 0.1F + speed * 0.1F;
		float flop = m.land() * 0.7F - Math.max(0.0F, m.fall()) * 0.4F * air;
		float step = Mth.sin(lag - 0.6F) * EAR_FLOP * speed;
		float sweep = EAR_SWEEP * speed + Math.max(0.0F, m.fall()) * 0.3F * air;
		this.rightEar.zRot = EAR_REST + twitch - flop - step - m.trail() * 0.25F;
		this.leftEar.zRot = -EAR_REST - twitch + flop + step - m.trail() * 0.25F;
		this.rightEar.yRot = sweep;
		this.leftEar.yRot = -sweep;
		// the tail hangs off the back of the torso and flicks; its middle and sting curl on over the back and trail a beat behind
		this.tail.y = worldY(cy, pitch, -0.5F, 4.5F);
		this.tail.z = worldZ(pitch, -0.5F, 4.5F);
		this.tail.yRot = Mth.sin(age * 0.2F) * 0.5F + Mth.cos(walk) * 0.6F * speed - m.trail() * 0.8F;
		this.tail.xRot = TAIL_REST + tailLift(m);
		this.tailMid.xRot = TAIL_MID_CURL + Mth.sin(age * 0.2F - 0.8F) * TAIL_FLICK + bite * 0.3F;
		this.tailMid.yRot = Mth.sin(age * 0.2F - 1.2F) * 0.25F;
		this.tailTip.xRot = TAIL_TIP_CURL + Mth.sin(age * 0.2F - 1.6F) * TAIL_FLICK * 1.5F + bite * 0.4F;
		this.tailTip.yRot = Mth.sin(age * 0.2F - 2.0F) * 0.3F;
	}
}
