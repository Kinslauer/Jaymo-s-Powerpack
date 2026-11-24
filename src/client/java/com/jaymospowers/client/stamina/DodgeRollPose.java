package com.jaymospowers.client.stamina;

import com.jaymospowers.client.KneeBend;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.jaymospowers.stamina.Dodge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.74) The dodge's roll ({@link Dodge}), for everyone to see: the body tucks into a ball (knees to the chest,
 * arms round them, chin down), drops low and tumbles one full turn the way the dodge went: a forward roll, a back
 * roll, or over the shoulder to the side. Combat Roll used to draw this; our own dodge only hopped until now.
 * <p>Your own roll draws at once from {@link DodgeClient}; everyone else's from the synced {@link Dodge#ROLLING}. In
 * first person the view tumbles with it ({@link #applyCamera}) and dips toward the ground; your own body drawn there
 * (First-person Model) stays upright rather than swinging through the camera.
 * <p>Andy (2026-11-22): "when players roll their bodies clip through the ground and their armor". The tumble turned the
 * standing body round its waist and dropped it, so upside down its head went half a block into the ground; and the back
 * rounded about the neck left the legs where they were, so the trousers and the chestplate ran through each other. Now
 * the body turns round the middle of the tucked body ({@link #BALL_CENTRE}) and drops, turn by turn, only as far as
 * keeps it on the ground: tucked, it's an oval longer head to hips ({@link #BALL_LONG}) than front to back
 * ({@link #BALL_DEEP}). The legs stay on the hips as the back rounds; the knees fold to the chest and the elbows round
 * the shins ({@link #bendLimbs}), with the arms outside the legs.
 * <p>The dash (a tap of the Dodge key on the ground, {@link Dodge#DASH_SPEED}) is drawn upright, the legs set from the
 * ground up ({@link #stance}): out to the side the feet plant wide, each leg straight to its own side; forward or back it's
 * a lunge, the leg ahead bent with its shin upright, the one behind straight. The hips drop exactly as far as the legs'
 * angle lowers them, so both feet stay on the ground. The upper body leans into the way about the hips
 * ({@link PoseBlend#bend}, {@link #DASH_LEAN}), both arms come up to a guard, and the head stays level, eyes on what was
 * dodged. Eased in fast and out slower over {@link Dodge#DASH_TICKS}. In first person the view dips and leans into a
 * side dash. (Checked in a scratch run of tools/pose_preview.py with the same knobs and formulas.)
 */
public final class DodgeRollPose {
	// ===== the knobs ================================================================================================
	/**
	 * The tucked body: its middle, which it turns round, and how far it reaches from there along the body (head to hips)
	 * and front to back, as shares of the body's height (measured with tools/pose_preview.py). Tucked, it rests on the
	 * ground: it drops till the oval's lowest point is there.
	 */
	static final float BALL_CENTRE = 0.72F, BALL_LONG = 0.42F, BALL_DEEP = 0.35F;
	/** Standing, the body's reach front to back from its middle (a share of its height). */
	static final float STANDING_DEEP = 0.12F;
	/**
	 * (Andy, 2026-11-23: "the combat dodge roll currently clips through the ground ... make it stay above ground for now")
	 * The drop stops this share of the body's height short of resting the ball on the ground: the arms, the armour and the
	 * half-tucked moments reach past the oval, and sank into the ground. It hovers a little at the middle of the roll instead.
	 */
	static final float GROUND_CLEARANCE = 0.15F;
	/**
	 * The tuck: thighs drawn up to the chest, arms forward round them (a little out, so they stay outside the legs), the
	 * back rounded and the chin down (radians); then the knees and elbows folded ({@link #bendLimbs}).
	 */
	static final float LEG_TUCK = -1.9F, ARM_TUCK = -1.0F, ARM_OUT = 0.12F, BODY_CURL = 0.45F, CHIN = 0.6F;
	/** How far the knees fold the shins back under the thighs, and the elbows bring the forearms round the shins (radians). */
	static final float KNEE_FOLD = 2.2F, ELBOW_FOLD = 1.1F;
	/** Where an arm folds: this many pixels down it from the shoulder (as SwingElbows). */
	static final float ELBOW = 4.0F;
	/** A leg's length (pixels): the hips move this far round the neck as the back rounds. */
	static final float LEG_TOP = 12.0F;
	/** Of the roll, the share spent tucking in at the start, and opening out at the end. */
	static final float TUCK_IN = 0.15F, TUCK_OUT = 0.3F;
	/** First person: how much of the full turn the view follows (1: all the way over), and how far it dips (blocks). */
	static final float VIEW_ROLL = 1.0F, VIEW_DIP = 0.35F;
	/** The flip in the air: how much of the roll's tuck the legs, the arms (the weapon stays out) and the back take. */
	static final float FLIP_LEGS = 0.7F, FLIP_ARMS = 0.25F, FLIP_CURL = 0.5F;
	/**
	 * The stretch dodge of Rubber-folk and Verdants ({@link Dodge#STRETCHY}), in place of the tumble: the body leans this
	 * far the way of the dodge from the feet (radians), is drawn out this much longer and this much thinner, and the
	 * arms are thrown this far up along it (radians).
	 */
	static final float STRETCH_LEAN = 0.95F, STRETCH_LONGER = 0.7F, STRETCH_THINNER = 0.18F, STRETCH_ARMS = -2.9F;
	/**
	 * The dash: the upper body's lean into its way about the hips (radians), a straight leg's angle out from under the body
	 * in the stance (radians; a bent leg ahead goes further, as far as keeps its foot on the ground), and the guard, the
	 * arms forward and a little out (radians).
	 */
	static final float DASH_LEAN = 0.3F, DASH_STANCE = 0.45F, DASH_ARM_UP = -0.9F, DASH_ARM_OUT = 0.25F;
	/** Of the dash, the share spent going into it, and coming out at the end. */
	static final float DASH_IN = 0.2F, DASH_OUT = 0.55F;
	/** First person, at the dash's fullest: how far the view dips (blocks) and leans into a side dash (radians). */
	static final float DASH_VIEW_DIP = 0.12F, DASH_VIEW_TILT = 0.07F;
	/** A leg's length (blocks): 12 pixels. */
	static final float LEG = 12.0F / 16.0F;
	// ================================================================================================================

	/** {@code t}: how far through the roll (0 to 1); {@code (x, z)}: the way it goes, a unit vector on the ground; {@code dash}: the upright dash, not a tumble. */
	public record Roll(float t, float x, float z, boolean flip, boolean stretch, boolean dash) {
	}

	public static final RenderStateDataKey<Roll> KEY = RenderStateDataKey.create(() -> "jaymospowers:dodge_roll");

	private DodgeRollPose() {
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Roll roll = roll(entity, partialTicks);
		state.setData(KEY, roll);
		if (roll != null && !roll.flip() && !roll.stretch() && !roll.dash()) {
			// the body is the low one for a roll (so it goes under a gap); the tumble draws it standing, not laid flat as a crawl is
			state.pose = Pose.STANDING;
			state.isVisuallySwimming = false;
			state.swimAmount = 0.0F;
			state.isCrouching = false;
		}
	}

	private static @Nullable Roll roll(Entity entity, float partialTicks) {
		if (!(entity instanceof Player player) || player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
			return null;
		}
		long now = player.level().getGameTime();
		long start;
		float x, z;
		boolean flip, extended, dash;
		boolean own = player == Minecraft.getInstance().player;
		if (own && DodgeClient.rollStart != Long.MIN_VALUE && DodgeClient.rollStart >= DodgeClient.dashDrawStart) {
			// our own roll: drawn the moment we hop, not when the server's copy comes back
			start = DodgeClient.rollStart;
			x = DodgeClient.rollX;
			z = DodgeClient.rollZ;
			flip = DodgeClient.rollFlip;
			extended = DodgeClient.rollLong;
			dash = false;
		} else if (own && DodgeClient.dashDrawStart != Long.MIN_VALUE) {
			// our own dash, likewise (the roll it may go on into starts later, and takes over above)
			start = DodgeClient.dashDrawStart;
			x = DodgeClient.rollX;
			z = DodgeClient.rollZ;
			flip = false;
			extended = false;
			dash = true;
		} else {
			Dodge.Rolling rolling = player.getAttached(Dodge.ROLLING);
			if (rolling == null) {
				return null;
			}
			start = rolling.start();
			x = rolling.x();
			z = rolling.z();
			flip = rolling.flip();
			extended = rolling.extended();
			dash = rolling.dash();
		}
		float elapsed = now - start + partialTicks;
		// (a long roll, the key held into it, turns slower over more ground: Dodge#rollShare)
		float t = dash ? elapsed / Dodge.DASH_TICKS : flip ? elapsed / Dodge.FLIP_TICKS : Dodge.rollShare(elapsed, extended);
		// (a flip in the air is a flip for everyone; Rubber-folk and Verdants dash by their stretch too)
		return t < 0.0F || t >= 1.0F ? null : new Roll(t, x, z, flip, !flip && Boolean.TRUE.equals(player.getAttached(Dodge.STRETCHY)), dash);
	}

	/** How far into the dash the body is (0 to 1): quickly in, slower out, eased at both ends. */
	private static float dashAmount(float t) {
		float e = Math.min(1.0F, Math.min(t / DASH_IN, (1.0F - t) / DASH_OUT));
		return e * e * (3.0F - 2.0F * e);
	}

	/**
	 * The dash's legs at {@code e} of its fullest, going {@code forward}, {@code right} in the body's frame (a unit vector):
	 * {xRot, zRot, knee} for the right leg, then the left. The leg on the side it goes leads (the right one straight ahead
	 * or behind), out along the way, the other back against it, both {@link #DASH_STANCE} from under the body; a leg whose
	 * foot goes ahead bends at the knee by its own forward swing (the shin stays upright) and swings further, enough to
	 * reach the ground as low as a straight leg does: {@code 6 cos a + 6 = 12 cos s}.
	 */
	private static float[] stance(float e, float forward, float right) {
		float s = DASH_STANCE * e;
		float bent = (float)Math.acos(Math.max(-1.0F, 2.0F * Mth.cos(s) - 1.0F));
		float lead = right >= -0.01F ? 1.0F : -1.0F;
		float[] out = new float[6];
		for (int leg = 0; leg < 2; leg++) {
			float sign = leg == 0 ? lead : -lead;
			float f = forward * sign, r = right * sign;
			float ahead = Math.max(0.0F, f);
			float a = s + (bent - s) * ahead;
			out[leg * 3] = -a * f;
			out[leg * 3 + 1] = a * r;
			out[leg * 3 + 2] = a * ahead;
		}
		return out;
	}

	/** The way {@code roll} goes in the body's (or view's) own frame, as {forward, right}, of unit length; null if it has none. */
	private static float @Nullable [] way(Roll roll, float yawDegrees) {
		float yaw = yawDegrees * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		float forward = -roll.x() * sin + roll.z() * cos;
		float right = -roll.x() * cos - roll.z() * sin;
		float length = Mth.sqrt(forward * forward + right * right);
		return length < 1.0E-4F ? null : new float[] {forward / length, right / length};
	}

	/** How tucked the body is (0 to 1): curling in at the start, opening out to land on its feet. */
	private static float tuck(float t) {
		return Math.min(1.0F, Math.min(t / TUCK_IN, (1.0F - t) / TUCK_OUT));
	}

	/** From setupRotations, after the body has been turned to face its way ({@code bodyRot}, degrees). */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		Roll roll = state.getData(KEY);
		if (roll == null || CombatAnimCompat.firstPersonPass(state) || ownFirstPerson(state)) {
			return;
		}
		// the dodge's way in the body's own frame (after YP(180 - bodyRot): forward is -Z, right is +X)
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		float forward = -roll.x() * sin + roll.z() * cos;
		float right = -roll.x() * cos - roll.z() * sin;
		float length = Mth.sqrt(forward * forward + right * right);
		if (length < 1.0E-4F) {
			return;
		}
		float t = roll.t();
		if (roll.stretch()) {
			// no tumble: out from the feet the way of the dodge, long and thin, and back
			float out = Mth.sin(Mth.PI * t);
			poseStack.rotateAround(new Quaternionf().rotateAxis(STRETCH_LEAN * out, -forward / length, 0.0F, -right / length), 0.0F, 0.0F, 0.0F);
			poseStack.scale(1.0F - STRETCH_THINNER * out, 1.0F + STRETCH_LONGER * out, 1.0F - STRETCH_THINNER * out);
			return;
		}
		if (roll.dash()) {
			// the hips down as far as the stance's straight legs lower them (the lean is the upper body's: applyModel)
			poseStack.translate(0.0F, -LEG * (1.0F - Mth.cos(DASH_STANCE * dashAmount(t))), 0.0F);
			return;
		}
		float turn = t * t * (3.0F - 2.0F * t) * Mth.TWO_PI;
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		float middle = height * BALL_CENTRE;
		if (!roll.flip()) {
			// down into a ball resting on the ground, and back up to the feet as it opens (the flip turns where it is, in the air)
			// (standing, the feet are as far below the middle as it is above the ground: no drop till the tuck comes on, and
			// the feet only leave the ground late in it, hence the square)
			float w = tuck(t);
			float along = Mth.lerp(w * w, BALL_CENTRE, BALL_LONG) * Mth.cos(turn), across = Mth.lerp(w, STANDING_DEEP, BALL_DEEP) * Mth.sin(turn);
			float reach = Mth.sqrt(along * along + across * across);
			poseStack.translate(0.0F, -height * Math.max(0.0F, BALL_CENTRE - reach - GROUND_CLEARANCE), 0.0F);
		}
		// the top tips toward the way of the dodge: about (up x way), round the body's middle
		poseStack.rotateAround(new Quaternionf().rotateAxis(turn, -forward / length, 0.0F, -right / length), 0.0F, middle, 0.0F);
	}

	/** First person, with the hurt bob (ScreenShakeMixin): the view turns over with our own roll and dips at its middle. */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		Roll roll = roll(minecraft.player, partialTicks);
		if (roll == null || roll.stretch()) {
			return;
		}
		if (roll.dash()) {
			// a dip with the knees, and a lean into a side dash (a turn about +Z leans the top of the view right)
			float[] way = way(roll, minecraft.player.getViewYRot(partialTicks));
			float e = dashAmount(roll.t());
			poseStack.translate(0.0F, DASH_VIEW_DIP * e, 0.0F);
			if (way != null) {
				poseStack.rotate(new Quaternionf().rotateZ(DASH_VIEW_TILT * way[1] * e));
			}
			return;
		}
		// the dodge's way in view space (forward is -Z, right is +X), from where we face
		float yaw = minecraft.player.getViewYRot(partialTicks) * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		float forward = -roll.x() * sin + roll.z() * cos;
		float right = -roll.x() * cos - roll.z() * sin;
		float length = Mth.sqrt(forward * forward + right * right);
		if (length < 1.0E-4F) {
			return;
		}
		float t = roll.t();
		float turn = t * t * (3.0F - 2.0F * t) * Mth.TWO_PI * VIEW_ROLL;
		// (the view turns the opposite way to the body: the world swings round the other way)
		if (!roll.flip()) {
			poseStack.translate(0.0F, VIEW_DIP * Mth.sin(Mth.PI * t), 0.0F);
		}
		poseStack.rotate(new Quaternionf().rotateAxis(-turn, -forward / length, 0.0F, -right / length));
	}

	/** Our own body drawn round the camera in first person: it stays upright (the view doesn't tumble). */
	private static boolean ownFirstPerson(AvatarRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && state.id == minecraft.player.getId() && FirstPersonCompat.bodyShown();
	}

	/** After the model animates: the tuck. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Roll roll = living.getData(KEY);
		if (roll == null) {
			return;
		}
		if (roll.dash() && !roll.stretch()) {
			float[] way = way(roll, living.bodyRot);
			float e = dashAmount(roll.t());
			float forward = way == null ? 0.0F : way[0], right = way == null ? 0.0F : way[1];
			// the stance, set outright (vanilla's walk would lift a foot off the ground; bendLimbs folds the knees)
			float[] legs = stance(e, forward, right);
			set(h.rightLeg, legs[0], 0.0F, legs[1], 1.0F);
			set(h.leftLeg, legs[3], 0.0F, legs[4], 1.0F);
			// the guard: both arms up in front, a little out (not while swinging or using something: a counter shows as it is)
			if (!(living instanceof AvatarRenderState avatar && (avatar.swingAnimation > 0.0F || avatar.isUsingItem))) {
				set(h.rightArm, DASH_ARM_UP, 0.0F, DASH_ARM_OUT, e);
				set(h.leftArm, DASH_ARM_UP, 0.0F, -DASH_ARM_OUT, e);
			}
			// the upper body leans into the way about the hips; the head turned back against it first, so it stays level, eyes
			// on what was dodged
			float pitch = DASH_LEAN * forward * e, tip = DASH_LEAN * right * e;
			h.head.xRot -= pitch;
			h.head.zRot -= tip;
			com.jaymospowers.client.PoseBlend.bend(h, pitch, 0.0F, tip, 0.0F, 0.0F);
			return;
		}
		if (roll.stretch()) {
			// drawn out: the arms thrown up along the body, the legs straight under it
			float out = Mth.sin(Mth.PI * roll.t());
			set(h.rightArm, STRETCH_ARMS, 0.0F, 0.1F, out);
			set(h.leftArm, STRETCH_ARMS, 0.0F, -0.1F, out);
			set(h.rightLeg, 0.0F, 0.0F, 0.02F, out);
			set(h.leftLeg, 0.0F, 0.0F, -0.02F, out);
			return;
		}
		float w = tuck(roll.t());
		// (the flip: knees up, the back barely rounded, and the arms left nearly as they are so the weapon stays out)
		float legs = roll.flip() ? w * FLIP_LEGS : w, arms = roll.flip() ? w * FLIP_ARMS : w, curl = roll.flip() ? w * FLIP_CURL : w;
		set(h.rightLeg, LEG_TUCK, 0.0F, 0.05F, legs);
		set(h.leftLeg, LEG_TUCK, 0.0F, -0.05F, legs);
		set(h.rightArm, ARM_TUCK, 0.0F, ARM_OUT, arms);
		set(h.leftArm, ARM_TUCK, 0.0F, -ARM_OUT, arms);
		h.body.xRot = Mth.lerp(curl, h.body.xRot, BODY_CURL);
		h.head.xRot = Mth.lerp(curl, h.head.xRot, CHIN);
		// the hips go where the rounded back takes them (it turns about the neck), so the legs stay on the body
		float back = h.body.xRot;
		h.rightLeg.y = Mth.lerp(legs, h.rightLeg.y, LEG_TOP * Mth.cos(back));
		h.rightLeg.z = Mth.lerp(legs, h.rightLeg.z, LEG_TOP * Mth.sin(back));
		h.leftLeg.y = Mth.lerp(legs, h.leftLeg.y, LEG_TOP * Mth.cos(back));
		h.leftLeg.z = Mth.lerp(legs, h.leftLeg.z, LEG_TOP * Mth.sin(back));
	}

	/** Last thing before the model is drawn: the tuck's knees folded to the chest and elbows round the shins. */
	public static void bendLimbs(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Roll roll = living.getData(KEY);
		if (roll == null || roll.stretch()) {
			return;
		}
		if (roll.dash()) {
			// the leg ahead's knee folds its shin back upright (stance)
			float[] way = way(roll, living.bodyRot);
			if (way != null) {
				float[] legs = stance(dashAmount(roll.t()), way[0], way[1]);
				KneeBend.bend(h.rightLeg, legs[2]);
				KneeBend.bend(h.leftLeg, legs[5]);
			}
			return;
		}
		float w = tuck(roll.t());
		float legs = roll.flip() ? w * FLIP_LEGS : w, arms = roll.flip() ? w * FLIP_ARMS : w;
		KneeBend.bend(h.rightLeg, KNEE_FOLD * legs);
		KneeBend.bend(h.leftLeg, KNEE_FOLD * legs);
		// (KneeBend's + swings the lower half back: a forearm comes forward on a -)
		KneeBend.bend(h.rightArm, -ELBOW_FOLD * arms, ELBOW, false);
		KneeBend.bend(h.leftArm, -ELBOW_FOLD * arms, ELBOW, false);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
