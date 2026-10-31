package com.jaymospowers.client.power.xenoform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * The slam of the band as you see it from your own eyes (the mixin {@code XenoformFirstPersonMixin} draws the two arms with
 * these placements). In the view's own space (x right, y up, -z ahead):
 * <ol>
 * <li><b>raise</b>: the left forearm swings up from below into a bar across the lower middle of the view, the wrist
 * turned so the band's face looks up at you (it overshoots a little, then settles);</li>
 * <li><b>held</b>: it breathes, the band's face plate lifts and grows, the dialled alien's silhouette dark on its glowing
 * face and its hologram over it ({@link XenoHologram}), and the right hand waits up and off to the right, clear of it;</li>
 * <li><b>strike</b>: the hand comes straight down (fast, accelerating) and covers the dial; on contact the left forearm
 * dips and springs back;</li>
 * <li>then both arms drop away.</li>
 * </ol>
 * The clocks come from {@link XenoformSlap.Pose}. The same functions are replicated in the offline preview, so the numbers
 * here were tuned against pictures: change them together.
 */
public final class XenoformSlamView {
	/** The raise overshoots by this much (ease-out-back), and the face plate lifts over the last of it. */
	static final float OVERSHOOT = 1.4F, POP_FROM = 0.6F;
	/** How fast the raised face's glow pulses (radians a tick). */
	static final float PULSE_SPEED = 0.5F;
	/** The dip on contact: how fast it dies away and how fast it swings (per tick), and its size at the peak. */
	static final float DIP_DAMP = 0.45F, DIP_FREQ = 1.4F, DIP_GAIN = 1.6F;
	/** The breathing while held: radians a tick. */
	static final float BOB_SPEED = 0.12F;
	/** The arm parts' rest placement (AvatarRenderer.renderHand sets their lean): pivot, in model pixels, and the lean in degrees. */
	static final float PIVOT_X = 5.0F, PIVOT_Y = 2.0F, LEAN_DEGREES = 0.1F * Mth.RAD_TO_DEG;

	/** The left wrist's centre (model pixels in the arm: the middle of the band) goes here: from below, and raised. */
	static final float[] L_ANCHOR = {1.0F, 7.5F, 0.0F};
	static final float[] L_POS_DOWN = {-0.15F, -1.1F, -0.5F}, L_POS_UP = {0.08F, -0.33F, -1.0F};
	/** The forearm's lean in the view's plane, its swing in and out, and its roll about its own length, in degrees (the roll turns the dial toward you). */
	static final float L_TILT_DOWN = 70.0F, L_TILT = 8.0F, L_YAW_DOWN = 0.0F, L_YAW = 8.0F, L_ROLL_DOWN = -117.0F, L_ROLL = -120.0F;
	/** Breathing and impact: how far (blocks) the wrist bobs and dips, and the roll and pitch they add (degrees). */
	static final float L_BOB_Y = 0.008F, L_BOB_ROLL = 1.0F, L_DIP_Y = 0.05F, L_DIP_PITCH = 6.0F;

	/**
	 * The right hand's end (the middle of the end of the arm, in model pixels): from low and right, poised, and down on the
	 * dial. The yaw and tilt lay the forearm back toward the camera and down off the bottom-right edge, so only the hand and a
	 * short wrist show. Poised, it waits up and off to the right, clear of the hologram over the dial.
	 */
	static final float[] R_ANCHOR = {-1.0F, 10.0F, 0.0F};
	static final float[] R_POS_DOWN = {1.1F, -0.9F, -0.7F}, R_POS_POISED = {0.46F, 0.2F, -0.9F}, R_POS_STRUCK = {0.0F, -0.22F, -0.95F};
	static final float R_TILT_DOWN = -20.0F, R_TILT = -50.0F, R_TILT_STRUCK = -40.0F;
	static final float R_YAW_DOWN = -30.0F, R_YAW = -50.0F, R_YAW_STRUCK = -50.0F;
	static final float R_ROLL_DOWN = 0.0F, R_ROLL = 0.0F, R_ROLL_STRUCK = 0.0F;
	static final float R_BOB_Y = 0.01F, R_BOB_ROLL = 2.0F;

	/**
	 * (0.18.86) An alien's tap on the insignia on its chest (it wears no band): no wrist comes up; the right hand rises
	 * into the lower right of the view, then presses down and in toward you, onto the chest just under the view.
	 */
	static final float[] C_POS_POISED = {0.38F, -0.42F, -0.8F}, C_POS_STRUCK = {0.06F, -0.78F, -0.42F};
	static final float C_TILT = -35.0F, C_TILT_STRUCK = -10.0F, C_YAW = -55.0F, C_YAW_STRUCK = -75.0F;

	/** While the left arm is drawn: how far the face plate is lifted (0-1) and the pulse of its glow (0-1), for {@link XenoBand}. */
	static float pop, glow;

	private XenoformSlamView() {
	}

	/** One moment of the slam: the raise (overshooting), the strike, the dip on contact and the breathing. */
	private record Phase(float u, float e, float dip, float bob) {
	}

	private static Phase phase(XenoformSlap.Pose pose, float time) {
		float u = easeOutBack(pose.rise()) * (1.0F - easeIn(pose.fall()));
		float e = easeIn(pose.hit());
		float since = pose.since();
		float dip = since < 0.0F ? 0.0F : (float)Math.exp(-DIP_DAMP * since) * Mth.sin(DIP_FREQ * since) * DIP_GAIN;
		float bob = Mth.sin(time * BOB_SPEED) * Mth.clamp(u, 0.0F, 1.0F) * (1.0F - pose.hit());
		return new Phase(u, e, dip, bob);
	}

	/** The left forearm with the band: the pose stack is left carrying its placement, ready to draw the arm. */
	public static void left(PoseStack poseStack, XenoformSlap.Pose pose, float time) {
		Phase p = phase(pose, time);
		poseStack.translate(Mth.lerp(p.u, L_POS_DOWN[0], L_POS_UP[0]), Mth.lerp(p.u, L_POS_DOWN[1], L_POS_UP[1]) + p.bob * L_BOB_Y - p.dip * L_DIP_Y,
			Mth.lerp(p.u, L_POS_DOWN[2], L_POS_UP[2]));
		poseStack.rotateDegrees(Axis.ZP, Mth.lerp(p.u, L_TILT_DOWN, L_TILT));
		poseStack.rotateDegrees(Axis.YP, Mth.lerp(p.u, L_YAW_DOWN, L_YAW));
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(p.u, L_ROLL_DOWN, L_ROLL) + p.dip * L_DIP_PITCH + p.bob * L_BOB_ROLL);
		// the arm's length (its local y, toward the hand) along the view's x
		poseStack.rotateDegrees(Axis.ZP, -90.0F);
		anchorArm(poseStack, PIVOT_X, -LEAN_DEGREES, L_ANCHOR);
	}

	/** The right hand: the pose stack is left carrying its placement. */
	public static void right(PoseStack poseStack, XenoformSlap.Pose pose, float time) {
		Phase p = phase(pose, time);
		float x = Mth.lerp(p.u, R_POS_DOWN[0], Mth.lerp(p.e, R_POS_POISED[0], R_POS_STRUCK[0]));
		float y = Mth.lerp(p.u, R_POS_DOWN[1], Mth.lerp(p.e, R_POS_POISED[1], R_POS_STRUCK[1]));
		float z = Mth.lerp(p.u, R_POS_DOWN[2], Mth.lerp(p.e, R_POS_POISED[2], R_POS_STRUCK[2]));
		// hovering it drifts a little; coming down it rides the left wrist's dip
		poseStack.translate(x, y + p.bob * R_BOB_Y - L_DIP_Y * p.dip * p.e, z);
		poseStack.rotateDegrees(Axis.ZP, Mth.lerp(p.u, R_TILT_DOWN, Mth.lerp(p.e, R_TILT, R_TILT_STRUCK)));
		poseStack.rotateDegrees(Axis.YP, Mth.lerp(p.u, R_YAW_DOWN, Mth.lerp(p.e, R_YAW, R_YAW_STRUCK)));
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(p.u, R_ROLL_DOWN, Mth.lerp(p.e, R_ROLL, R_ROLL_STRUCK)) + p.bob * R_BOB_ROLL);
		// the arm's length toward the hand runs along the view's -x
		poseStack.rotateDegrees(Axis.ZP, 90.0F);
		anchorArm(poseStack, -PIVOT_X, LEAN_DEGREES, R_ANCHOR);
	}

	/** An alien's right hand, tapping its chest: the pose stack is left carrying its placement. */
	public static void chest(PoseStack poseStack, XenoformSlap.Pose pose, float time) {
		Phase p = phase(pose, time);
		float x = Mth.lerp(p.u, R_POS_DOWN[0], Mth.lerp(p.e, C_POS_POISED[0], C_POS_STRUCK[0]));
		float y = Mth.lerp(p.u, R_POS_DOWN[1], Mth.lerp(p.e, C_POS_POISED[1], C_POS_STRUCK[1]));
		float z = Mth.lerp(p.u, R_POS_DOWN[2], Mth.lerp(p.e, C_POS_POISED[2], C_POS_STRUCK[2]));
		// on contact it jolts with the same dip the wrist would have taken
		poseStack.translate(x, y + p.bob * R_BOB_Y - L_DIP_Y * p.dip * p.e, z);
		poseStack.rotateDegrees(Axis.ZP, Mth.lerp(p.u, R_TILT_DOWN, Mth.lerp(p.e, C_TILT, C_TILT_STRUCK)));
		poseStack.rotateDegrees(Axis.YP, Mth.lerp(p.u, R_YAW_DOWN, Mth.lerp(p.e, C_YAW, C_YAW_STRUCK)));
		poseStack.rotateDegrees(Axis.XP, p.bob * R_BOB_ROLL);
		poseStack.rotateDegrees(Axis.ZP, 90.0F);
		anchorArm(poseStack, -PIVOT_X, LEAN_DEGREES, R_ANCHOR);
	}

	/**
	 * Puts the arm part's own point {@code at} (model pixels) on the stack's origin: undoes what the part will do itself
	 * (translate to its pivot, lean by {@code lean} degrees) and then moves the point there.
	 */
	private static void anchorArm(PoseStack poseStack, float pivotX, float lean, float[] at) {
		poseStack.rotateDegrees(Axis.ZP, lean);
		poseStack.translate(-at[0] / 16.0F, -at[1] / 16.0F, -at[2] / 16.0F);
		poseStack.rotateDegrees(Axis.ZP, -lean);
		poseStack.translate(-pivotX / 16.0F, -PIVOT_Y / 16.0F, 0.0F);
	}

	/** Before the left arm is drawn: the face plate's lift and its glow for this moment ({@link XenoBand} reads them). */
	public static void dial(XenoformSlap.Pose pose, float time) {
		Phase p = phase(pose, time);
		float up = smooth((p.u - POP_FROM) / (1.0F - POP_FROM));
		// it stands as the arm arrives, sinks as the hand closes on it, and is flat for the drop
		pop = up * (1.0F - smooth(p.e * 1.6F)) * (1.0F - smooth(pose.fall() * 2.0F));
		glow = 0.7F + 0.3F * Mth.sin(time * PULSE_SPEED);
	}

	public static void dialDone() {
		pop = 0.0F;
		glow = 0.0F;
	}

	private static float easeOutBack(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
		return 1.0F + (OVERSHOOT + 1.0F) * t * t * t + OVERSHOOT * t * t;
	}

	private static float easeIn(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * t;
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
