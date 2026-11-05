package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformClient;
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
 * (0.20.9, Ultimates patch 0) Bulwark's Ultimate, Titan: Bulwark's four-armed, thin-waisted body grown heavier and covered in iron-grey
 * stone plates (shoulders, forearms, shins, a back plate), a ridge of spikes down the spine, spiked knuckles on all four fists,
 * horns and tusks, and molten seams glowing between the plates (the skin's glow map, drawn fullbright by the layer). It is the same rig
 * as {@code XenoFormModels.Bulwark} and moves the same way (the swapping raised-pair guard, a wide walking swing, the carry and the leap),
 * only slower and with a heavier sway. The skin is {@code tools/gen_xeno_ultimates.py}'s {@code titan()}, 128x128; {@link #UV} here is the
 * same table that script prints ({@code gen_xeno_ultimates.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code right_lower_arm}, {@code left_lower_arm}, {@code pauldron} (the yoke across the
 * shoulders), {@code spine} and {@code head}: those names are parts of this mesh.
 */
public final class UltimateTitan extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The skeleton (pixels): where the hips (body pivot) and the hip joints sit, the leg's bones and the foot's thickness. */
	static final float HIP_Y = 12.5F, LEG_Y = 11.2F, THIGH = 6.0F, SHIN = 6.0F, FOOT = 2.0F, LEG_X = 3.0F;
	/** The resting knee (radians): the thigh forward, the shin folded back under it. */
	static final float KNEE_T = -0.4F, KNEE_S = 0.8F;
	/** The torso's forward lean (radians): at rest, per walking speed, and crouched. */
	static final float LEAN = 0.16F, LEAN_WALK = 0.07F, LEAN_CROUCH = 0.3F;
	/** The legs' stride (radians), the extra knee bend on the leg coming forward, and the toe curled up as it lifts. */
	static final float LEG_SWING = 0.6F, KNEE_LIFT = 0.7F, FOOT_TOE = 0.3F;
	/** The foot's reach (pixels): the toe forward of the ankle, the heel behind it. */
	static final float TOE = 5.0F, HEEL = 2.0F;
	/** The guard's swap: ticks for the pair to go up and come back (slower than Bulwark's), how long each stance dwells, and how many ticks the right side leads the left. */
	static final float SWAP_TICKS = 160.0F, DWELL = 1.8F, SKEW = 8.0F;
	/** The upper pair's guard, {low, high}: arm swing (x, + is back), splay out (z), forearm bend forward (x) and in (z). */
	static final float[] UP_X = {0.1F, -0.3F}, UP_Z = {1.5F, 2.2F}, UP_FX = {-0.5F, -0.9F}, UP_FZ = {0.25F, 0.6F};
	/** The lower pair's guard, {low, high}: always below the upper pair's so the arms never cross. */
	static final float[] LO_X = {0.15F, -0.35F}, LO_Z = {0.55F, 1.2F}, LO_FX = {-0.3F, -0.6F}, LO_FZ = {0.15F, 0.3F};
	/** Walking: how far the arms swing, and how much wider they are held out at a run (radians). */
	static final float ARM_SWING = 0.55F, ARM_WIDE = 0.2F;
	/** A punch: the arm's swing at full reach, its splay, and how far the other arm of the pair is drawn back. */
	static final float PUNCH_X = -1.75F, PUNCH_Z = 0.25F, PUNCH_RECOIL = 0.5F;
	/** Carrying (upper then lower pair): arm swing, splay, forearm bend forward and in; leaping: splay of each pair. */
	static final float[] CARRY_X = {-0.35F, 0.0F}, CARRY_Z = {2.5F, 2.0F}, CARRY_FX = {-0.4F, -0.4F}, CARRY_FZ = {1.0F, 0.6F};
	static final float[] LEAP_Z = {2.7F, 2.2F};
	/** Where the shoulders and the ribs' arms sit on the body (pixels): x out, y up from the hips. */
	static final float SHOULDER_X = 6.5F, SHOULDER_Y = 12.0F, RIB_X = 5.5F, RIB_Y = 6.5F;
	/** The sway of the heavy body: roll with each step, and the head's slow bob. */
	static final float ROLL = 0.07F, HEAD_BOB = 0.35F;
	/** The renderer draws a sneaking player this many pixels lower: crouched, the feet are planted that much higher to stay on the ground. */
	static final float CROUCH_SINK = 2.0F;

	/** The skin's patches (u, v) by box name: the table {@code tools/gen_xeno_ultimates.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0), uv("brow", 28, 0), uv("jaw", 54, 0), uv("tusk", 76, 0), uv("horn", 80, 0), uv("chest", 88, 0),
		uv("waist", 0, 15), uv("belt", 18, 15), uv("yoke", 42, 15), uv("backplate", 84, 15), uv("spine", 104, 15), uv("spike", 112, 15),
		uv("ua_up", 0, 26), uv("pauld", 20, 26), uv("fa_up", 48, 26), uv("fi_up", 72, 26), uv("ua_lo", 100, 26),
		uv("fa_lo", 0, 40), uv("fi_lo", 28, 40), uv("knuckle", 60, 40), uv("thigh", 64, 40), uv("shin", 80, 40), uv("greave", 96, 40),
		uv("foot", 0, 53));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	/** The four arms, forearms and fists: upper right, upper left, lower right, lower left. */
	private final ModelPart[] arms = new ModelPart[4], fores = new ModelPart[4], fists = new ModelPart[4];
	private final ModelPart rightShin, leftShin, rightFoot, leftFoot;
	private final float[] ax = new float[4], az = new float[4], fx = new float[4], fz = new float[4];

	public UltimateTitan() {
		super(XenoFormModels.bake(mesh(), 128, 128), "bulwark");
		this.arms[0] = this.rightArm;
		this.arms[1] = this.leftArm;
		this.arms[2] = this.body.getChild("right_lower_arm");
		this.arms[3] = this.body.getChild("left_lower_arm");
		for (int i = 0; i < 4; i++) {
			this.fores[i] = this.arms[i].getChild("forearm");
			this.fists[i] = this.fores[i].getChild("fist");
		}
		this.rightShin = this.rightLeg.getChild("shin");
		this.leftShin = this.leftLeg.getChild("shin");
		this.rightFoot = this.rightShin.getChild("foot");
		this.leftFoot = this.leftShin.getChild("foot");
		// flush on the front of the breastplate (12 wide, 9 tall)
		this.insigniaY = -9.5F;
		this.insigniaZ = -3.0F;
		this.insigniaSize = 1.1F;
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code TITAN_DIMS} in the painter). */
	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		return CubeListBuilder.create().texOffs(at[0], at[1]).addBox(x, y, z, w, h, d);
	}

	/** One arm: a forearm hung from the upper arm's elbow, a fist from the wrist, and three spikes on the knuckles. */
	private static void addArm(PartDefinition arm, String foreKey, int fw, int fh, String fistKey, int xw, int xh, float fd) {
		PartDefinition fore = arm.addOrReplaceChild("forearm", cube(foreKey, -fw / 2.0F, -0.5F, -fw / 2.0F, fw, fh, fw), PartPose.offset(0.0F, 6.0F, 0.0F));
		PartDefinition fist = fore.addOrReplaceChild("fist", cube(fistKey, -xw / 2.0F, 0.0F, -xw / 2.0F, xw, xh, xw), PartPose.offset(0.0F, 5.5F, 0.0F));
		for (int i = 0; i < 3; i++) {
			fist.addOrReplaceChild("knuckle_" + i, cube("knuckle", -0.5F, 0.0F, -0.5F, 1, 2, 1), PartPose.offset(-2.0F + 2.0F * i, xh, fd));
		}
	}

	private static void addLeg(PartDefinition root, String name, float x) {
		PartDefinition leg = root.addOrReplaceChild(name, cube("thigh", -2.0F, 0.0F, -2.0F, 4, 7, 4), PartPose.offset(x, LEG_Y, 0.0F));
		PartDefinition shin = leg.addOrReplaceChild("shin", cube("shin", -2.0F, -1.0F, -2.0F, 4, 7, 4), PartPose.offset(0.0F, 6.0F, 0.0F));
		shin.addOrReplaceChild("greave", cube("greave", -2.5F, 0.0F, -2.5F, 5, 4, 5), PartPose.ZERO);
		shin.addOrReplaceChild("foot", cube("foot", -2.5F, -1.0F, -5.0F, 5, 3, 7), PartPose.offset(0.0F, 6.0F, 0.0F));
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// a heavy head, forward of the shoulders: a deep brow, an underbite, two tusks and two swept-back horns
		PartDefinition head = root.addOrReplaceChild("head", cube("head", -3.5F, -7.0F, -3.5F, 7, 7, 7), PartPose.offset(0.0F, 0.8F, -1.0F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("brow", cube("brow", -4.5F, -6.5F, -5.0F, 9, 2, 4), PartPose.ZERO);
		head.addOrReplaceChild("jaw", cube("jaw", -3.5F, -2.5F, -5.0F, 7, 2, 4), PartPose.ZERO);
		head.addOrReplaceChild("tusk_r", cube("tusk", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offset(-2.8F, -0.5F, -4.3F));
		head.addOrReplaceChild("tusk_l", cube("tusk", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offset(2.8F, -0.5F, -4.3F));
		head.addOrReplaceChild("horn_r", cube("horn", -1.0F, -4.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(-3.0F, -6.5F, 0.5F, -0.5F, 0.0F, -0.45F));
		head.addOrReplaceChild("horn_l", cube("horn", -1.0F, -4.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(3.0F, -6.5F, 0.5F, -0.5F, 0.0F, 0.45F));
		// the torso: a wedge of a chest on a narrow waist and a belt; a yoke of stone across the shoulders, a plate and a ridge of spikes down the back
		PartDefinition body = root.addOrReplaceChild("body", cube("chest", -6.0F, -14.0F, -3.0F, 12, 9, 6), PartPose.offset(0.0F, HIP_Y, 0.0F));
		body.addOrReplaceChild("waist", cube("waist", -2.5F, -5.0F, -2.0F, 5, 5, 4), PartPose.ZERO);
		body.addOrReplaceChild("belt", cube("belt", -3.5F, -2.0F, -2.5F, 7, 2, 5), PartPose.ZERO);
		body.addOrReplaceChild("pauldron", cube("yoke", -7.0F, -15.5F, -3.5F, 14, 2, 7), PartPose.ZERO);
		body.addOrReplaceChild("backplate", cube("backplate", -4.5F, -12.5F, 3.0F, 9, 6, 1), PartPose.ZERO);
		PartDefinition spine = body.addOrReplaceChild("spine", cube("spine", -1.0F, -13.0F, 4.0F, 2, 9, 2), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			spine.addOrReplaceChild("spine_spike_" + i, cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1),
				PartPose.offsetAndRotation(0.0F, -11.5F + 2.6F * i, 5.0F, -0.7F, 0.0F, 0.0F));
		}
		// the upper pair, from the shoulders, each with a stone pauldron and a spike on it
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", cube("ua_up", -2.5F, -1.0F, -2.5F, 5, 8, 5),
			PartPose.offset(-SHOULDER_X, HIP_Y - SHOULDER_Y, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", cube("ua_up", -2.5F, -1.0F, -2.5F, 5, 8, 5),
			PartPose.offset(SHOULDER_X, HIP_Y - SHOULDER_Y, 0.0F));
		PartDefinition plateR = rightArm.addOrReplaceChild("pauldron_r", cube("pauld", -4.5F, -4.0F, -3.5F, 7, 4, 7), PartPose.ZERO);
		PartDefinition plateL = leftArm.addOrReplaceChild("pauldron_l", cube("pauld", -2.5F, -4.0F, -3.5F, 7, 4, 7), PartPose.ZERO);
		plateR.addOrReplaceChild("pauldron_spike", cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(-2.0F, -4.0F, 0.0F, 0.0F, 0.0F, -0.4F));
		plateL.addOrReplaceChild("pauldron_spike", cube("spike", -0.5F, -3.0F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(2.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.4F));
		addArm(rightArm, "fa_up", 6, 6, "fi_up", 7, 5, 0.0F);
		addArm(leftArm, "fa_up", 6, 6, "fi_up", 7, 5, 0.0F);
		// the lower pair, from the ribs (children of the body, so they lean with it): thicker, plated like gauntlets
		PartDefinition lowerRight = body.addOrReplaceChild("right_lower_arm", cube("ua_lo", -3.0F, -1.0F, -3.0F, 6, 8, 6), PartPose.offset(-RIB_X, -RIB_Y, 0.0F));
		PartDefinition lowerLeft = body.addOrReplaceChild("left_lower_arm", cube("ua_lo", -3.0F, -1.0F, -3.0F, 6, 8, 6), PartPose.offset(RIB_X, -RIB_Y, 0.0F));
		addArm(lowerRight, "fa_lo", 7, 6, "fi_lo", 8, 5, 0.0F);
		addArm(lowerLeft, "fa_lo", 7, 6, "fi_lo", 8, 5, 0.0F);
		// legs: a thigh, a shin folded back from the knee with a stone greave, a broad foot
		addLeg(root, "right_leg", -LEG_X);
		addLeg(root, "left_leg", LEG_X);
		return mesh;
	}

	/** The forearms and fists straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
	public void handPose() {
		for (int i = 0; i < 2; i++) {
			this.fores[i].resetPose();
			this.fists[i].resetPose();
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The guard's swap at {@code t} ticks: 0 with the upper pair low, 1 with it raised; it dwells in each stance. */
	private static float swap(float t) {
		return smooth(0.5F + Mth.clamp(Mth.sin(t * 2.0F * Mth.PI / SWAP_TICKS) * DWELL * 0.5F, -0.5F, 0.5F));
	}

	/**
	 * One leg's bones for the walk's phase ({@code c} the cosine, {@code w} the sine): the thigh swings, the knee folds on the leg
	 * coming forward, the foot is held level (toe curled up as it lifts). Returns how far below the hip joint its lowest point sits.
	 */
	private float leg(ModelPart thigh, ModelPart shin, ModelPart foot, float c, float w, float sp, float cr, float air) {
		float lift = Math.max(0.0F, w) * sp;
		float t = KNEE_T + c * LEG_SWING * sp - lift * 0.25F - cr * 0.35F - air * 0.3F;
		float s = KNEE_S + lift * KNEE_LIFT + cr * 0.7F + air * 0.4F;
		float f = -lift * FOOT_TOE;
		thigh.xRot = t;
		shin.xRot = s;
		foot.xRot = -(t + s) + f;
		// the foot's lowest corner: the toe or the heel
		float foot2 = FOOT * Mth.cos(f);
		return THIGH * Mth.cos(t) + SHIN * Mth.cos(t + s) + Math.max(foot2 + TOE * Mth.sin(f), foot2 - HEEL * Mth.sin(f));
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		XenoMotion.Read m = XenoMotion.read(state);
		String act = state.getData(XenoformClient.ACT);
		boolean carry = "carry".equals(act), leap = "leap".equals(act);
		float age = state.ageInTicks;
		float sp = Math.min(1.0F, state.walkAnimationSpeed);
		float p = state.walkAnimationPos * 0.6662F;
		float cc = Mth.cos(p), ww = Mth.sin(p);
		float air = m.air();
		float cr = state.isCrouching ? 1.0F : 0.0F;
		this.head.resetPose();
		this.body.resetPose();
		this.rightLeg.resetPose();
		this.leftLeg.resetPose();
		this.rightShin.resetPose();
		this.leftShin.resetPose();
		this.rightFoot.resetPose();
		this.leftFoot.resetPose();
		for (int i = 0; i < 4; i++) {
			this.arms[i].resetPose();
			this.fores[i].resetPose();
			this.fists[i].resetPose();
		}
		// the legs: walking solves the knees, then the whole body sinks until the lowest foot is on the ground
		float depthR = leg(this.rightLeg, this.rightShin, this.rightFoot, cc, ww, sp, cr, air);
		float depthL = leg(this.leftLeg, this.leftShin, this.leftFoot, -cc, -ww, sp, cr, air);
		float dy = (24.0F - CROUCH_SINK * cr - LEG_Y) - Math.max(depthR, depthL);
		// the torso leans into the walk and over when crouched; everything on it is placed through the lean
		float lean = LEAN + LEAN_WALK * sp + LEAN_CROUCH * cr + m.land() * 0.2F - Math.max(0.0F, m.fall()) * 0.08F * air;
		float ls = Mth.sin(lean), lc = Mth.cos(lean);
		this.body.y = HIP_Y + dy;
		this.body.xRot = lean;
		// heavy: it rolls over each planted foot
		this.body.zRot = cc * ROLL * sp;
		this.rightLeg.y = this.leftLeg.y = LEG_Y + dy;
		this.rightLeg.z = this.leftLeg.z = -1.3F * ls;
		// the head, on a short neck jutting forward of the shoulders, looking where the player looks
		this.head.y = this.body.y - 12.2F * lc + 1.0F * ls + Mth.sin(age * 0.05F) * 0.25F + Math.abs(cc) * HEAD_BOB * sp;
		this.head.z = -12.2F * ls - 1.0F * lc;
		this.head.yRot = Mth.clamp(state.yRot, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
		this.head.xRot = Mth.clamp(state.xRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD - lean * 0.7F;
		// the upper arms hang off the shoulders, which lean with the body
		float shy = this.body.y - SHOULDER_Y * lc;
		float shz = -SHOULDER_Y * ls;
		this.rightArm.setPos(-SHOULDER_X, shy, shz);
		this.leftArm.setPos(SHOULDER_X, shy, shz);

		// ---- the arms' targets: the swapping guard, the walk's swing, the air ----
		float uRight = swap(age + SKEW), uLeft = swap(age);
		float sw = cc * sp;
		for (int i = 0; i < 4; i++) {
			boolean upper = i < 2, rightSide = i % 2 == 0;
			float k = upper ? (rightSide ? uRight : uLeft) : 1.0F - (rightSide ? uRight : uLeft);
			float[] x = upper ? UP_X : LO_X, z = upper ? UP_Z : LO_Z, fxs = upper ? UP_FX : LO_FX, fzs = upper ? UP_FZ : LO_FZ;
			this.ax[i] = Mth.lerp(k, x[0], x[1]);
			this.az[i] = Mth.lerp(k, z[0], z[1]) + Mth.sin(age * 0.06F + i * 1.3F) * 0.05F;
			this.fx[i] = Mth.lerp(k, fxs[0], fxs[1]);
			this.fz[i] = Mth.lerp(k, fzs[0], fzs[1]);
			// walking: the upper pair swings against its own side's leg, the lower pair against the upper, all held wider
			float swing = (rightSide ? -sw : sw) * ARM_SWING * (upper ? 1.0F : -0.8F);
			this.ax[i] += swing;
			this.az[i] += ARM_WIDE * sp;
			// in the air they flare; a fall lifts them, a landing slams them down
			this.az[i] += air * 0.25F + Math.max(0.0F, m.fall()) * air * 0.4F - m.land() * 0.7F;
			if (carry) {
				int pair = upper ? 0 : 1;
				this.ax[i] = CARRY_X[pair] + swing * 0.3F;
				this.az[i] = CARRY_Z[pair] + Mth.sin(age * 0.6F + i) * 0.03F;
				this.fx[i] = CARRY_FX[pair];
				this.fz[i] = CARRY_FZ[pair];
			} else if (leap) {
				this.ax[i] = 0.25F;
				this.az[i] = LEAP_Z[upper ? 0 : 1];
				this.fx[i] = -0.2F;
				this.fz[i] = 0.2F;
			}
		}
		// a punch: (0.20.60) each swing is thrown by the next of the four arms in turn (XenoformClient.BULWARK_ARM);
		// without that, the main hand throws the upper arm on its side, the off hand the lower arm on the other side
		float sa = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		if (state.currentSwing != null && sa > 0.0F && !carry) {
			Integer turn = state.getData(com.jaymospowers.client.power.xenoform.XenoformClient.BULWARK_ARM);
			boolean off = state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND;
			boolean right = (state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) != off;
			int punch = turn != null ? turn : (off ? 2 : 0) + (right ? 0 : 1);
			int other = punch ^ 1;
			float pk = Mth.sin(sa * Mth.PI);
			this.ax[punch] = Mth.lerp(pk, this.ax[punch], PUNCH_X);
			this.az[punch] = Mth.lerp(pk, this.az[punch], PUNCH_Z);
			this.fx[punch] = Mth.lerp(pk, this.fx[punch], 0.0F);
			this.fz[punch] = Mth.lerp(pk, this.fz[punch], 0.0F);
			// the other arm of the pair is drawn back as the fist goes out
			this.ax[other] += PUNCH_RECOIL * pk;
			this.az[other] += 0.2F * pk;
			this.fx[other] -= 0.3F * pk;
		}
		for (int i = 0; i < 4; i++) {
			float sg = i % 2 == 0 ? 1.0F : -1.0F;
			this.arms[i].xRot = this.ax[i] + (i < 2 ? lean : 0.0F);
			this.arms[i].zRot = sg * this.az[i];
			this.fores[i].xRot = this.fx[i];
			this.fores[i].zRot = sg * this.fz[i];
			this.fists[i].xRot = -0.25F;
		}
	}
}
