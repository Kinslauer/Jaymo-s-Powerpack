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
 * (0.21.37, Ultimates patch 2) Gorger's Ultimate, Worldmaw: Gorger's own build and gait (a squat armadillo on four short legs that
 * scurries in diagonal pairs, the six bands of its shell bobbing one after another, a head that nods a beat behind the step, a tail
 * dragging behind), bigger and plated: every shell band is wider and taller and carries a row of three short bone spikes, a stone brow
 * sits over the eyes, each leg wears a guard, the tail's rings are stone with a spike on each, the snout is wider with a hinged jaw,
 * and the horn on it is forked into two. The seams between the bands, the cracks in the plates and the tips of the spikes and horns
 * glow ember orange (the skin's glow map, drawn fullbright by the layer). Rolled up (B) it closes into a larger spiked ball of the six
 * bands; charging (V) it drops its head with the horn levelled; inhaling (C, act {@code "inhale"}) it plants its feet, throws the head
 * up and unhinges the jaw wide.
 * <p>
 * The skin is {@code tools/ultimates/gorger.py}, 128x128; {@link #UV} here is the table that script prints
 * ({@code gorger.py layout}). The change-in film zooms on, in order, {@code shell0..shell5} (the bands), {@code tail} and
 * {@code tail2}, {@code snout} and {@code horn_r}/{@code horn_l}: those names are parts of this mesh.
 */
public final class UltimateWorldmaw extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** How fast its legs scurry against the walk, how far they swing and how high a foot lifts (pixels). */
	static final float GAIT = 0.6662F * 2.2F, STEP = 0.6F, LIFT = 1.0F;
	/** (charge) the legs scrabble this much faster, the head drops and the tail rises. */
	static final float CHARGE_GAIT = 1.4F, CHARGE_HEAD_DROP = 1.2F, CHARGE_HEAD_PITCH = 0.55F, CHARGE_TAIL = 0.35F;
	/** (inhale) the head is thrown up this far (radians), the jaw opens this wide, and it rocks back on its hind legs this much (pixels). */
	static final float INHALE_HEAD_UP = 0.45F, INHALE_JAW = 1.05F, INHALE_SHUDDER = 0.04F, INHALE_BACK = 0.6F;
	/** The jaw: how far a bite opens it (radians). */
	static final float BITE_JAW = 0.6F;
	/** The body's dip at the widest stride (pixels), its side sway, a band's bob (pixels) and the bob's wave along the bands. */
	static final float BOB = 0.6F, SWAY = 0.3F, BAND_BOB = 0.4F, BAND_WAVE = 0.9F;
	/** How far it drops when sneaking (pixels). */
	static final float CROUCH_DROP = 1.5F;
	/** The attack: the head butts down (radians) with a little push forward (pixels). */
	static final float LUNGE = 0.4F, LUNGE_PITCH = 0.7F;
	/** Airborne, the legs reach out this far (radians); landing squashes it down this far (pixels). */
	static final float AIR_REACH = 0.5F, LAND_SQUASH = 1.0F;
	/** How far the head can look up and down (radians); its snout stops at the ground, however far it looks down. */
	static final float HEAD_UP = 0.9F, HEAD_DOWN = 1.2F;
	/** The tail at rest (radians, + is up). */
	static final float TAIL_REST = 0.15F;
	/** Rolled up: how far the ball turns for each unit of walk, and its size (the flat sides' distance from the middle). */
	static final float ROLL_PER_POS = 0.5F, BALL_APOTHEM = 8.5F, BALL_WIDTH = 1.15F;
	/** Each band of the ball a hair narrower than the last, so their flat sides don't flicker through each other. */
	static final float BALL_STEP = 0.02F;
	/** The ball is a hexagon of the six bands: 60 degrees a band, a corner this far (apothem / cos 30) from the middle. */
	private static final float SEGMENT = (float)Math.PI / 3.0F;
	private static final float BALL_CORNER = BALL_APOTHEM / 0.8660254F;
	private static final float BALL_SIDE = BALL_APOTHEM * 1.1547005F;
	/** The shell, front to back: where each band sits (z, and y of its bottom), how tall and thick it is, and its width. */
	private static final float[] SHELL_Z = {-4.5F, -2.0F, 0.0F, 2.0F, 4.0F, 6.5F};
	private static final float[] SHELL_Y = {20.0F, 18.0F, 18.0F, 18.0F, 18.0F, 20.0F};
	private static final float[] SHELL_H = {9.0F, 8.0F, 9.0F, 9.0F, 8.0F, 9.0F};
	private static final float[] SHELL_D = {3.0F, 2.0F, 2.0F, 2.0F, 2.0F, 3.0F};
	private static final String[] SHELL_UV = {"band_f", "band_b", "band_c", "band_c", "band_b", "band_r"};
	private static final float SHELL_W = 11.0F;
	/** Spikes along each band's top edge: how many, how far apart (pixels), and how far they lean back (radians). */
	private static final int SPIKES = 3;
	private static final float SPIKE_GAP = 3.5F, SPIKE_LEAN = -0.25F;
	/** Front and hind legs rest here (x either side, z, y of the hip), are this long and their feet this deep (half). */
	private static final float LEG_X = 2.5F, FRONT_Z = -3.5F, HIND_Z = 4.5F, HIP_Y = 19.0F, LEG_LEN = 5.0F, FOOT_HALF = 2.0F;
	/** The head's pivot, and how far below it the snout's tip reaches at most (its lowest corner: reach * sin(pitch + angle)). */
	private static final float HEAD_Y = 17.0F, HEAD_Z = -5.5F, SNOUT_REACH = 6.32F, SNOUT_ANGLE = 0.32F;
	/** The tail's root (in the rump shield), how far below the root its tip reaches as it hangs down, and its half-thickness. */
	private static final float TAIL_Y = 20.0F, TAIL_Z = 7.0F, TAIL_REACH = 8.5F, TAIL_HALF = 1.5F;
	/** The hidden body stands here (y), at the back of the rump shield (z), facing back. */
	private static final float PLATE_Y = 15.5F, PLATE_Z = 6.0F;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/gorger.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0), uv("brow", 16, 0), uv("snout", 32, 0), uv("jaw", 46, 0), uv("horn", 60, 0), uv("ear", 64, 0), uv("torso", 70, 0),
		uv("band_f", 0, 17), uv("band_b", 28, 17), uv("band_c", 54, 17), uv("band_r", 80, 17), uv("spike", 108, 17), uv("leg", 112, 17),
		uv("greave", 0, 29), uv("arm", 20, 29), uv("tail", 36, 29), uv("tail2", 52, 29));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart torso, snout, jaw, tail, tail2, rightEar, leftEar, hindRight, hindLeft;
	private final ModelPart[] shell = new ModelPart[6];
	private final ModelPart[][] spikes = new ModelPart[6][SPIKES];

	public UltimateWorldmaw() {
		super(XenoFormModels.bake(mesh(), 128, 128), "gorger");
		ModelPart root = this.root();
		this.torso = root.getChild("torso");
		this.snout = this.head.getChild("snout");
		this.jaw = this.snout.getChild("jaw");
		this.rightEar = this.head.getChild("right_ear");
		this.leftEar = this.head.getChild("left_ear");
		this.tail = root.getChild("tail");
		this.tail2 = this.tail.getChild("tail2");
		this.hindRight = root.getChild("hind_right_leg");
		this.hindLeft = root.getChild("hind_left_leg");
		for (int i = 0; i < 6; i++) {
			this.shell[i] = root.getChild("shell" + i);
			for (int j = 0; j < SPIKES; j++) {
				this.spikes[i][j] = this.shell[i].getChild("spike_" + i + "_" + j);
			}
		}
		// on the back of the rump (the body, hidden, stands there facing back), the plate flat on its face, above the tail
		this.insigniaY = 0.0F;
		this.insigniaZ = -2.0F;
		this.insigniaSize = 0.9F;
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		return CubeListBuilder.create().texOffs(at[0], at[1]).addBox(x, y, z, w, h, d);
	}

	private static void addLeg(PartDefinition root, String name, float x, float z) {
		PartDefinition leg = root.addOrReplaceChild(name, cube("leg", -2.0F, 0.0F, -2.0F, 4, 5, 4), PartPose.offset(x, HIP_Y, z));
		leg.addOrReplaceChild("greave", cube("greave", -2.5F, -0.5F, -2.5F, 5, 2, 5), PartPose.ZERO);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// the head: a small wedge under a stone brow, a wide snout with a hinged jaw and a horn forked in two, ears at the back
		PartDefinition head = root.addOrReplaceChild("head", cube("head", -2.5F, -2.5F, -3.0F, 5, 5, 3), PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("brow", cube("brow", -2.5F, -3.5F, -3.0F, 5, 1, 3), PartPose.ZERO);
		PartDefinition snout = head.addOrReplaceChild("snout", cube("snout", -2.0F, -1.5F, -3.0F, 4, 3, 3), PartPose.offset(0.0F, 0.5F, -3.0F));
		snout.addOrReplaceChild("jaw", cube("jaw", -2.0F, 0.0F, -3.0F, 4, 1, 3), PartPose.offset(0.0F, 1.5F, 0.0F));
		snout.addOrReplaceChild("horn_r", cube("horn", -0.5F, -3.0F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(-1.0F, -1.2F, -2.0F, 0.9F, 0.0F, -0.4F));
		snout.addOrReplaceChild("horn_l", cube("horn", -0.5F, -3.0F, -0.5F, 1, 3, 1), PartPose.offsetAndRotation(1.0F, -1.2F, -2.0F, 0.9F, 0.0F, 0.4F));
		head.addOrReplaceChild("right_ear", cube("ear", -1.0F, -2.0F, -0.5F, 2, 2, 1), PartPose.offsetAndRotation(-2.0F, -2.3F, -0.5F, 0.0F, 0.0F, -0.35F));
		head.addOrReplaceChild("left_ear", cube("ear", -1.0F, -2.0F, -0.5F, 2, 2, 1), PartPose.offsetAndRotation(2.0F, -2.3F, -0.5F, 0.0F, 0.0F, 0.35F));
		// the soft belly and flanks under the shell
		root.addOrReplaceChild("torso", cube("torso", -4.0F, -2.5F, -6.0F, 8, 5, 12), PartPose.offset(0.0F, 17.5F, 1.0F));
		// the shell: shoulder shield, four bands, rump shield (centred in z so it can close into a ball), a row of spikes on each
		for (int i = 0; i < 6; i++) {
			PartDefinition band = root.addOrReplaceChild("shell" + i, cube(SHELL_UV[i], -SHELL_W / 2.0F, -SHELL_H[i], -SHELL_D[i] / 2.0F, (int)SHELL_W, (int)SHELL_H[i], (int)SHELL_D[i]),
				PartPose.offset(0.0F, SHELL_Y[i], SHELL_Z[i]));
			for (int j = 0; j < SPIKES; j++) {
				band.addOrReplaceChild("spike_" + i + "_" + j, cube("spike", -0.5F, -2.0F, -0.5F, 1, 2, 1),
					PartPose.offsetAndRotation((j - (SPIKES - 1) / 2.0F) * SPIKE_GAP, -SHELL_H[i], 0.0F, SPIKE_LEAN, 0.0F, 0.0F));
			}
		}
		// the legs: the man's legs are its front ones, the hind pair new; each wears a guard
		addLeg(root, "right_leg", -LEG_X, FRONT_Z);
		addLeg(root, "left_leg", LEG_X, FRONT_Z);
		addLeg(root, "hind_right_leg", -LEG_X, HIND_Z);
		addLeg(root, "hind_left_leg", LEG_X, HIND_Z);
		// the arms are only the first-person arm (hidden on the body)
		root.addOrReplaceChild("right_arm", cube("arm", -2.0F, -2.0F, -2.0F, 4, 12, 4), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", cube("arm", -2.0F, -2.0F, -2.0F, 4, 12, 4), PartPose.offset(5.0F, 2.0F, 0.0F));
		// the tail: two stone-ringed joints dragging behind, low, its root set into the rump shield, a spike on each
		PartDefinition tail = root.addOrReplaceChild("tail", cube("tail", -1.5F, -1.5F, 0.0F, 3, 3, 5), PartPose.offset(0.0F, TAIL_Y, TAIL_Z));
		tail.addOrReplaceChild("tail_spike", cube("spike", -0.5F, -2.0F, -0.5F, 1, 2, 1), PartPose.offsetAndRotation(0.0F, -1.5F, 2.5F, SPIKE_LEAN, 0.0F, 0.0F));
		PartDefinition tail2 = tail.addOrReplaceChild("tail2", cube("tail2", -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offset(0.0F, 0.0F, 4.5F));
		tail2.addOrReplaceChild("tail2_spike", cube("spike", -0.5F, -2.0F, -0.5F, 1, 2, 1), PartPose.offsetAndRotation(0.0F, -1.0F, 2.0F, SPIKE_LEAN, 0.0F, 0.0F));
		return mesh;
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	private static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
		for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft}) {
			part.resetPose();
		}
		String act = state.getData(XenoformClient.ACT);
		if ("roll".equals(act)) {
			this.ball(state);
		} else {
			this.walk(state, "charge".equals(act), "inhale".equals(act));
		}
	}

	private void show(boolean out) {
		this.head.visible = this.torso.visible = this.tail.visible = out;
		this.rightLeg.visible = this.leftLeg.visible = this.hindRight.visible = this.hindLeft.visible = out;
	}

	/** Walking, standing, charging, inhaling: the quadruped. */
	private void walk(AvatarRenderState state, boolean charge, boolean inhale) {
		this.show(true);
		XenoMotion.Read m = XenoMotion.read(state);
		float age = state.ageInTicks;
		// inhaling it plants its feet and only shudders
		float speed = inhale ? 0.0F : Math.min(1.0F, state.walkAnimationSpeed);
		float p = state.walkAnimationPos * GAIT * (charge ? CHARGE_GAIT : 1.0F);
		float crouch = state.isCrouching ? CROUCH_DROP : 0.0F;
		float air = Mth.clamp(m.air(), 0.0F, 1.0F);
		float shudder = inhale ? Mth.sin(age * 1.7F) * INHALE_SHUDDER : 0.0F;
		// the body dips at the widest stride, and squashes on landing
		float drop = crouch + Mth.abs(Mth.cos(p)) * BOB * speed * (1.0F - air) + Math.max(0.0F, m.land()) * LAND_SQUASH;
		float sway = Mth.sin(p) * SWAY * speed + shudder * 6.0F;
		// the shell: the bands bob in a wave along its back; it breathes a little
		float breath = Mth.sin(age * 0.08F) * 0.12F;
		for (int i = 0; i < 6; i++) {
			ModelPart band = this.shell[i];
			float lift = (i == 0 || i == 5) ? 0.0F : Math.max(0.0F, Mth.sin(p * 2.0F - i * BAND_WAVE)) * BAND_BOB * speed + breath * 0.5F + 0.12F;
			band.x = sway;
			band.y = SHELL_Y[i] + drop - lift;
			band.z = SHELL_Z[i];
			band.xRot = band.yRot = band.zRot = 0.0F;
			band.xScale = band.yScale = band.zScale = 1.0F;
			for (int j = 0; j < SPIKES; j++) {
				ModelPart spike = this.spikes[i][j];
				spike.xScale = spike.yScale = spike.zScale = 1.0F;
			}
		}
		this.torso.x = sway;
		this.torso.y = 17.5F + drop;
		this.torso.z = 1.0F;
		this.torso.xRot = this.torso.yRot = this.torso.zRot = 0.0F;
		this.torso.xScale = this.torso.yScale = this.torso.zScale = 1.0F;
		// the insignia: the hidden body stands at the rump facing back, its plate on the shield's back, above the tail
		this.body.x = sway;
		this.body.y = PLATE_Y + drop;
		this.body.z = PLATE_Z;
		this.body.yRot = (float)Math.PI;
		// the head: where it looks, nodding a beat behind the step, a sniff when idle, a snap on the attack; however far
		// it looks down, its snout stops at the ground
		float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
		float sniff = Mth.sin(age * 0.35F) * 0.05F * (1.0F - speed);
		this.head.x = sway;
		this.head.y = HEAD_Y + drop + (charge ? CHARGE_HEAD_DROP : 0.0F);
		this.head.z = HEAD_Z - swing * LUNGE + (inhale ? INHALE_BACK : 0.0F);
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.3F, 1.3F);
		float room = (24.0F - this.head.y) / SNOUT_REACH;
		float down = room >= 1.0F ? HEAD_DOWN : Math.min(HEAD_DOWN, (float)Math.asin(Math.max(0.0F, room)) - SNOUT_ANGLE);
		float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -HEAD_UP, HEAD_UP) + Mth.sin(p * 2.0F - 0.8F) * 0.06F * speed + sniff
			+ swing * LUNGE_PITCH + (charge ? CHARGE_HEAD_PITCH : 0.0F) - (inhale ? INHALE_HEAD_UP : 0.0F);
		this.head.xRot = Math.max(-HEAD_UP - (inhale ? INHALE_HEAD_UP : 0.0F), Math.min(down, pitch));
		this.snout.xRot = sniff * 0.8F;
		// the jaw: shut, a snap on the bite, flung wide while it inhales (juddering as it pulls)
		this.jaw.xRot = inhale ? INHALE_JAW + shudder * 3.0F : swing * BITE_JAW;
		// the ears prick up and flick now and then
		float flick = Math.max(0.0F, Mth.sin(age * 0.11F + 1.0F) - 0.85F) * 2.0F;
		this.rightEar.zRot = -0.35F - flick - m.trail() * 0.1F;
		this.leftEar.zRot = 0.35F + flick + m.trail() * 0.1F;
		// the tail: drags low, sways with the walk and the turn, lifts when it falls or charges; it never dips through
		// the ground (landing or sneaking would whip it down)
		this.tail.x = sway;
		this.tail.y = TAIL_Y + drop;
		float floor = -(float)Math.asin(Mth.clamp((24.0F - this.tail.y - TAIL_HALF) / TAIL_REACH, 0.0F, 1.0F));
		this.tail.xRot = Math.max(floor, TAIL_REST + tailLift(m) + (charge ? CHARGE_TAIL : 0.0F));
		this.tail.yRot = Mth.sin(age * 0.07F) * 0.12F - Mth.sin(p) * 0.25F * speed - m.turn() * 0.5F;
		this.tail2.xRot = 0.1F;
		this.tail2.yRot = Mth.sin(age * 0.07F - 0.8F) * 0.15F - m.turn() * 0.3F;
		// the legs: diagonal pairs together; each pivots at the hip, which sinks or rises as the leg swings so the
		// lowest corner of its foot meets the ground, and rises as a foot lifts through its forward swing; in the air
		// the hips stay put (in the body) and the legs reach out
		float leg = (LEG_LEN - crouch) / LEG_LEN;
		float len = LEG_LEN * leg;
		ModelPart[] legs = {this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft};
		float[] phase = {0.0F, (float)Math.PI, (float)Math.PI, 0.0F};
		for (int i = 0; i < 4; i++) {
			ModelPart foot = legs[i];
			float theta = Mth.cos(p + phase[i]) * STEP * speed + (i < 2 ? -AIR_REACH : AIR_REACH) * air;
			float up = Math.max(0.0F, Mth.sin(p + phase[i])) * LIFT * speed * (1.0F - air);
			foot.xRot = theta;
			foot.yScale = leg;
			foot.y = HIP_Y + crouch + (len * (1.0F - Mth.cos(theta)) - FOOT_HALF * Mth.abs(Mth.sin(theta))) * (1.0F - air) - up;
		}
	}

	/** Rolled up: head, legs and tail tucked away, the six bands closed into a hexagonal ball of spikes that turns as it moves. */
	private void ball(AvatarRenderState state) {
		this.show(false);
		float roll = state.walkAnimationPos * ROLL_PER_POS;
		float rem = roll - (float)Math.floor(roll / SEGMENT) * SEGMENT;
		// resting on a flat side, a corner lifts it as it turns over
		float cy = 24.0F - BALL_CORNER * Mth.cos(rem - SEGMENT * 0.5F);
		for (int i = 0; i < 6; i++) {
			ModelPart band = this.shell[i];
			band.x = 0.0F;
			band.y = cy;
			band.z = 1.0F;
			band.xRot = roll + i * SEGMENT;
			band.yRot = band.zRot = 0.0F;
			band.xScale = BALL_WIDTH - i * BALL_STEP;
			band.yScale = BALL_APOTHEM / SHELL_H[i];
			band.zScale = BALL_SIDE / SHELL_D[i];
			// the spikes keep their own size on the stretched band
			for (int j = 0; j < SPIKES; j++) {
				ModelPart spike = this.spikes[i][j];
				spike.xScale = 1.0F / band.xScale;
				spike.yScale = 1.0F / band.yScale;
				spike.zScale = 1.0F / band.zScale;
			}
		}
		// the plate sits inside the ball, out of sight
		this.body.x = 0.0F;
		this.body.y = cy;
		this.body.z = 1.0F;
		this.body.yRot = (float)Math.PI;
	}
}
