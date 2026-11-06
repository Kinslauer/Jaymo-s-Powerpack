package com.jaymospowers.client;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Kneel;
import com.jaymospowers.core.Stance;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Tending;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import com.jaymospowers.power.xenoform.XenoformState;

/**
 * (0.17.7) Andy: "shift should now animate a crouch, the players gets low, hands out to the side, swaying as they move, ready
 * to draw a weapon and fire more comfortably on one knee when they stop moving. Not moving and crouching will kneel when
 * tending to players as well, or for executions". Drawn for everyone, over vanilla's crouch:
 * <ul>
 * <li>moving: lower still ({@link #LOW}), arms held out from the sides, the body swaying side to side with the stride;</li>
 * <li>still for {@link Kneel#KNEEL_AFTER} ticks: down on the left knee, right foot planted, upright, right hand by the hip
 * ready to draw, left forearm over the raised knee (the server steadies a bow from here, {@link Kneel}). So the hold of
 * a sneak + left-click execution is knelt through, and tending someone kneels as it always did ({@code TendPose}).</li>
 * </ul>
 * The arms are left alone while drawing a bow, aiming, blocking or swinging, and the whole pose stands aside for the other
 * full-body poses (tending, a finisher, carrying, crawling).
 * <p>(0.17.18) Andy: "crouching looks like a split right now, the back leg is fine, but the front leg should bend at the
 * knee, and stay bent while firing ... same for all ranged weapons". The front thigh now goes out level and the shin bends
 * down to the foot ({@link KneeBend}); the legs hold that whatever is in the hands, bow, crossbow, gun or thrown.
 * <p>(0.18.57) The low crouch from the stance wheel ({@link Stance}): both thighs forward and both knees bent so the shins
 * stand straight ({@link KneeBend}), the body sunk {@link #LOW_DROP} lower to keep the feet on the ground.
 * <p>(0.18.66) Andy: "the second to last option should be our new sneak, the third option the default vanilla sneak ...
 * use a smoother animation like the one from MGSV". The arms out and the sway are now the low crouch's alone; a plain
 * sneak is vanilla's, and only the low crouch goes onto one knee when still (the firing stance). And every change eases: vanilla snaps in and out of
 * its crouch, so the body is blended between standing and crouched over {@link #BEND_IN} ticks ({@link #bendBody}),
 * the low crouch and the arms over {@link #LOW_IN} and {@link #READY_IN}, all on a smootherstep curve.
 * <p>(0.19.30) Andy: "the crouch walk looks like tippy toeing". The stride swung the thighs while the body stayed at one
 * height, so the legs came up short and the feet hovered or stood on their toes. Now each knee bends just enough to put its
 * foot on the ground wherever its thigh is ({@link #groundKnee}), the shin leaning forward over the foot as in a real crouch
 * walk; the thighs come further forward, the steps are longer and slower, only the leg coming through lifts, and the upper body leans
 * forward over the knees ({@link #LOW_LEAN}).
 * <p>(0.21.28) Andy: "the kneel we have on crouch right now needs a revamp". The back leg was one straight stick out
 * behind and the body sat between the legs like a split; now it kneels as every kneel does ({@link KneelLegs}): the
 * left thigh straight down to the knee on the ground and the shin flat behind, the right thigh level and the shin straight
 * down to the planted foot, the hips one thigh's length down.
 * <p>(0.21.37) Andy: "the newest kneel has both knees bent in front of the character, it should look more like a
 * traditional kneel, posing with a weapon in hand like a hand on the sword's pommel, blade in the ground, similar with an
 * axe, or a low ready with a rifle or bow". A genuflect: the knee on the ground is now well behind the hip ({@link #BACK_LEG}),
 * the shin flat behind it, the front knee raised a touch above the hip with the shin straight down, the hips sunk to match
 * ({@link #SINK}). And the hands pose with what's held ({@link Style}): a sword or axe planted point or head down in front
 * of the knee, both hands stacked on the pommel or haft end; a spear or staff stood upright; a bow or a gun at a low
 * ready, muzzle or arrow toward the ground ahead. The weapon is turned in the hand for it ({@link #grip}). Worked out on
 * preview renders of the model ({@code art-overhaul/kneel-climb}).
 */
public final class CrouchPose {
	/** Arms out, and down onto the knee or back up (ticks). */
	static final float READY_IN = 8.0F, KNEEL_IN = 7.0F, GET_UP = 5.0F;
	/** Moving: how much lower than vanilla's crouch (blocks). On the knee: how far below vanilla's crouch the body sits. */
	static final float LOW = 0.08F, SINK = 6.47F / 16.0F - 0.125F, HIP = 0.75F;
	/** The arms out from the sides (radians), and a little forward. */
	static final float ARMS_OUT = 0.55F, ARMS_FORWARD = -0.35F;
	/** The sway with the stride: the body's twist and roll (radians at full walking speed), and how fast it goes against the stride. */
	static final float SWAY_TWIST = 0.14F, SWAY_ROLL = 0.08F, SWAY_RATE = 0.25F;
	/** On the knee: a slight lean forward (degrees). */
	static final float KNEEL_LEAN = 4.0F;
	/**
	 * On the knee: the front (right) thigh out level from the hip, the knee bent so the shin stands on the ground
	 * (radians), and the back (left) leg trailing to the knee on the ground. Drawn while aiming and firing too.
	 */
	static final float FRONT_THIGH = -1.65F, FRONT_KNEE = 1.65F, BACK_LEG = 0.40F, BACK_KNEE = 1.09F;
	/** (0.21.37) The legs turned a little out (radians): the front one, the back one. */
	static final float FRONT_OUT = 0.12F, BACK_OUT = -0.08F;
	/**
	 * (0.21.37) The arms on the knee for each {@link Style} (radians, xRot / yRot / zRot, the main hand's then the other's,
	 * for a right-handed body; mirrored for a left-handed one), and how far the weapon is turned in the main hand (degrees,
	 * + toward the ground). Solved on the model's preview so the blade stands straight with its point on the ground.
	 */
	static final float[] EMPTY_MAIN = {-0.25F, 0.0F, 0.12F}, EMPTY_OFF = {-0.75F, 0.25F, -0.05F};
	static final float[] PLANT_MAIN = {-1.13F, -0.53F, -0.01F}, PLANT_OFF = {-1.85F, 0.55F, 0.0F};
	static final float[] UPRIGHT_MAIN = {-0.55F, 0.0F, 0.18F};
	static final float[] BOW_MAIN = {-0.75F, -0.15F, 0.05F}, BOW_OFF = {-0.95F, 0.55F, 0.0F};
	static final float[] RIFLE_MAIN = {-0.85F, -0.3F, 0.0F}, RIFLE_OFF = {-1.15F, 0.6F, 0.0F};
	static final float PLANT_GRIP = 151.0F, UPRIGHT_GRIP = -55.0F, BOW_GRIP = 75.0F, RIFLE_GRIP = 85.0F;

	/** (0.21.37) How the hands pose on the knee, by what's in the main hand. */
	public enum Style {
		/** Nothing held, or nothing with a pose of its own: the hand by the hip, the other forearm over the knee. */
		EMPTY,
		/** A sword, greatsword, axe, hammer or club: planted point (or head) down ahead of the knee, hands on the pommel. */
		PLANT,
		/** A spear, polearm or staff: stood upright beside the raised knee. */
		UPRIGHT,
		/** A bow: low ready, the arrow toward the ground ahead, the other hand at the string. */
		BOW,
		/** A gun or crossbow: low ready, muzzle down ahead, the other hand under the barrel. */
		RIFLE;

		static Style of(net.minecraft.world.item.ItemStack stack) {
			if (stack.isEmpty()) {
				return EMPTY;
			}
			return switch (Finishers.kind(stack)) {
				case SWORD, GREATSWORD, AXE, BLUNT -> PLANT;
				case SPEAR, POLEARM, STAFF -> UPRIGHT;
				case BOW -> BOW;
				case CROSSBOW, GUN -> RIFLE;
				default -> EMPTY;
			};
		}
	}
	/**
	 * The low crouch: both thighs this far forward (radians), and the body sunk this much more (blocks). (0.19.30) Thighs further forward;
	 * the knees bend to reach the ground from there ({@link #groundKnee}).
	 */
	static final float LOW_THIGH = 1.3F, LOW_DROP = 0.22F;
	/** (0.19.30) The low crouch: the upper body leaned forward over the knees (radians, past vanilla's crouch), the head kept up. */
	static final float LOW_LEAN = 0.2F;
	/**
	 * (0.18.85) The low crouch's stride, a Skyrim sneak: how far each thigh swings either side of {@link #LOW_THIGH}
	 * (radians), how quickly it's at full stride with walking speed (vanilla's 0..1, times this), and how far the knee bends
	 * more to lift the foot as it comes through (radians).
	 */
	static final float LOW_STRIDE = 0.35F, STRIDE_GAIN = 2.5F, LOW_LIFT = 0.5F;
	/** The stride's beat (radians per unit walked; vanilla's walk is 0.6662). (0.19.30) Slower: longer, heavier steps. */
	static final float STRIDE_BEAT = 0.5F;
	/** Into and out of the low crouch (ticks). */
	static final float LOW_IN = 9.0F;
	/** Standing into vanilla's crouch and back (ticks). */
	static final float BEND_IN = 7.0F;
	/** Vanilla's crouch, as it lays it on the model: body pitch, arms' pitch, legs back, head / body / arms down (pixels), and the
	 * whole body down (blocks, before scale). */
	static final float V_BODY = 0.5F, V_ARMS = 0.4F, V_LEGS_Z = 4.0F, V_HEAD_Y = 4.2F, V_BODY_Y = 3.2F, V_OFFSET = 0.125F;
	/** Under this walking speed (vanilla's 0..1) the body counts as still. */
	static final float STILL_SPEED = 0.05F;

	/**
	 * {@code ready}: 0 to 1, arms out; {@code kneel}: 0 to 1, on the knee; {@code pos}, {@code speed}: the stride's;
	 * {@code low}: 0 to 1, in the low crouch; {@code bend}: 0 to 1, how far into vanilla's crouch the body is drawn.
	 */
	public record Crouch(float ready, float kneel, float pos, float speed, float low, float bend, Style style, boolean offFree) {
	}

	public static final RenderStateDataKey<Crouch> KEY = RenderStateDataKey.create(() -> "jaymospowers:crouch");

	private static final class Track {
		float ready;
		float kneel;
		float low;
		float bend;
		float stillSince = -1.0F;
		float last = -1.0F;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private CrouchPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (ownCrouch(entity)) {
			// (0.19.84) an alien that poses its own crouch: vanilla's drop only, none of the low crouch, kneel or eased bend
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		Track track = TRACKS.get(entity.getId());
		boolean crouched = entity.isCrouching() && entity.onGround() && !entity.isPassenger() && !entity.isSwimming() && !entity.isFallFlying()
			&& !entity.isSpectator() && !otherwise(entity);
		boolean bent = entity.isCrouching() && !entity.isPassenger() && !entity.isSwimming() && !entity.isFallFlying() && !entity.isSpectator()
			&& !otherwise(entity);
		if (!crouched && !bent && track == null) {
			state.setData(KEY, null);
			return;
		}
		if (track == null) {
			track = new Track();
			TRACKS.put(entity.getId(), track);
		}
		float now = entity.level().getGameTime() + partialTicks;
		float dt = track.last < 0.0F ? 0.0F : Mth.clamp(now - track.last, 0.0F, 5.0F);
		track.last = now;
		boolean still = crouched && state.walkAnimationSpeed < STILL_SPEED;
		if (!still) {
			track.stillSince = -1.0F;
		} else if (track.stillSince < 0.0F) {
			track.stillSince = now;
		}
		// (0.18.66) onto the knee only from the low crouch; a plain sneak stays vanilla's
		boolean kneel = still && entity instanceof Player p && Stance.low(p) && now - track.stillSince >= Kneel.KNEEL_AFTER;
		boolean low = crouched && entity instanceof Player player && Stance.low(player);
		// (0.18.66) arms out and swaying only in the low crouch; a plain sneak is vanilla's
		track.ready = approach(track.ready, low ? 1.0F : 0.0F, dt / READY_IN);
		track.kneel = approach(track.kneel, kneel ? 1.0F : 0.0F, dt / (kneel ? KNEEL_IN : GET_UP));
		track.low = approach(track.low, low ? 1.0F : 0.0F, dt / LOW_IN);
		track.bend = approach(track.bend, bent ? 1.0F : 0.0F, dt / BEND_IN);
		if (!crouched && !bent && track.ready <= 0.0F && track.kneel <= 0.0F && track.low <= 0.0F && track.bend <= 0.0F || entity.isRemoved()) {
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		// (0.21.37) the hands' pose on the knee, by what's held (only looked up while kneeling)
		Style style = track.kneel > 0.0F ? Style.of(entity.getMainHandItem()) : Style.EMPTY;
		state.setData(KEY, new Crouch(smooth(track.ready), smooth(track.kneel), state.walkAnimationPos, state.walkAnimationSpeed,
			smooth(track.low), smooth(track.bend), style, entity.getOffhandItem().isEmpty()));
	}

	/**
	 * (0.19.84) {@code entity} is an alien whose body solves its own legs and crouch (Bulwark, Knell, Rime, Shade, ...):
	 * the low crouch's thighs, the kneel's vanilla places and the eased bend would pull it apart, so they stay off it
	 * (the low stance's height still holds; it just doesn't settle onto a knee).
	 */
	public static boolean ownCrouch(net.minecraft.world.entity.Entity entity) {
		return entity instanceof Player player && XenoformState.ownCrouch(player);
	}

	/** Another full-body pose has them: tending, a finisher, carrying or being carried, crawling, downed. */
	private static boolean otherwise(Avatar entity) {
		return entity.hasAttached(Tending.TEND) || Finishers.of(entity) != null || Carry.isCarrying(entity) || Carry.isCarried(entity)
			|| Downed.isDowned(entity);
	}

	/** After the body faces its way: lower while moving, sunk onto the knee when still. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Crouch c = state.getData(KEY);
		if (c == null || state.deathTime > 0.0F) {
			return;
		}
		// (0.18.66) vanilla drops a crouching body V_OFFSET at once: eased in and out instead
		float drop = V_OFFSET * state.scale;
		if (!state.isCrouching) {
			poseStack.translate(0.0F, -drop * c.bend(), 0.0F);
			return;
		}
		poseStack.translate(0.0F, drop * (1.0F - c.bend()), 0.0F);
		poseStack.translate(0.0F, -(LOW * c.ready() + LOW_DROP * c.low()) * (1.0F - c.kneel()) - SINK * c.kneel(), 0.0F);
		if (c.kneel() > 0.0F) {
			poseStack.translate(0.0F, HIP, 0.0F);
			poseStack.rotate(Axis.XP.rotationDegrees(-KNEEL_LEAN * c.kneel()));
			poseStack.translate(0.0F, -HIP, 0.0F);
		}
	}

	/** After the model animates (crouched by vanilla): arms out and swaying, or up out of the crouch onto one knee. */
	public static void applyModel(Model<?> model, Object renderState) {
		bendBody(model, renderState);
		Crouch c = posed(model, renderState);
		if (c == null) {
			return;
		}
		AvatarRenderState state = (AvatarRenderState) renderState;
		HumanoidModel<?> h = (HumanoidModel<?>) model;
		boolean armsFree = !state.isUsingItem && state.swingAnimation <= 0.0F && free(state.rightArmPose) && free(state.leftArmPose);
		float walk = (1.0F - c.kneel()) * c.ready();
		if (walk > 0.0F) {
			// the sway: the shoulders roll and twist against the stride
			float phase = c.pos() * SWAY_RATE;
			float sway = Mth.clamp(c.speed(), 0.0F, 1.0F) * walk;
			h.body.yRot += Mth.sin(phase) * SWAY_TWIST * sway;
			h.body.zRot += Mth.cos(phase) * SWAY_ROLL * sway;
			h.head.yRot -= Mth.sin(phase) * SWAY_TWIST * 0.5F * sway;
			if (armsFree) {
				float bob = Mth.cos(phase) * 0.08F * sway;
				blend(h.rightArm, ARMS_FORWARD + bob, h.body.yRot, ARMS_OUT, walk);
				blend(h.leftArm, ARMS_FORWARD - bob, h.body.yRot, -ARMS_OUT, walk);
			}
		}
		float k = c.kneel();
		float low = c.low() * (1.0F - k);
		if (low > 0.0F) {
			// the low crouch: thighs forward, stepping through a full slow stride (0.18.85: a Skyrim sneak; it was half
			// vanilla's, too small at a sneak's pace to see), knees bent at bendKnee
			float swing = Mth.cos(c.pos() * STRIDE_BEAT) * LOW_STRIDE * stride(c);
			h.rightLeg.xRot = Mth.lerp(low, h.rightLeg.xRot, -LOW_THIGH + swing);
			h.leftLeg.xRot = Mth.lerp(low, h.leftLeg.xRot, -LOW_THIGH - swing);
			// (0.19.30) leaning forward over the knees, eyes still ahead
			PoseBlend.bend(h, LOW_LEAN * low, 0.0F, 0.0F, 0.0F, 0.0F);
			h.head.xRot -= LOW_LEAN * low;
		}
		if (k <= 0.0F) {
			return;
		}
		// up out of vanilla's crouch: the body upright, the parts back where they stand
		float pitch = KNEEL_LEAN * Mth.DEG_TO_RAD;
		h.body.xRot = Mth.lerp(k, h.body.xRot, 0.0F);
		h.body.y = Mth.lerp(k, h.body.y, 0.0F);
		h.head.y = Mth.lerp(k, h.head.y, 0.0F);
		h.rightArm.y = Mth.lerp(k, h.rightArm.y, 2.0F);
		h.leftArm.y = Mth.lerp(k, h.leftArm.y, 2.0F);
		h.rightLeg.y = Mth.lerp(k, h.rightLeg.y, 12.0F);
		h.leftLeg.y = Mth.lerp(k, h.leftLeg.y, 12.0F);
		h.rightLeg.z = Mth.lerp(k, h.rightLeg.z, 0.0F);
		h.leftLeg.z = Mth.lerp(k, h.leftLeg.z, 0.0F);
		// right thigh out ahead (a touch above level), bent at the knee (KneeBend) down to the planted foot; the left knee
		// down behind the hip, its shin flat along the ground (0.21.37)
		blend(h.rightLeg, FRONT_THIGH - pitch, FRONT_OUT, 0.0F, k);
		blend(h.leftLeg, BACK_LEG - pitch, BACK_OUT, 0.0F, k);
		if (armsFree) {
			// (0.21.37) posed with what's held: by the hip and over the knee empty-handed, a sword planted, a gun at a low ready
			boolean right = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
			ModelPart main = right ? h.rightArm : h.leftArm, off = right ? h.leftArm : h.rightArm;
			float side = right ? 1.0F : -1.0F;
			float[] mainPose = switch (c.style()) {
				case PLANT -> PLANT_MAIN;
				case UPRIGHT -> UPRIGHT_MAIN;
				case BOW -> BOW_MAIN;
				case RIFLE -> RIFLE_MAIN;
				default -> EMPTY_MAIN;
			};
			float[] offPose = !c.offFree() ? EMPTY_OFF : switch (c.style()) {
				case PLANT -> PLANT_OFF;
				case BOW -> BOW_OFF;
				case RIFLE -> RIFLE_OFF;
				default -> EMPTY_OFF;
			};
			// (held weapons are laid out upright, so the lean is taken back off the arms)
			float lean = c.style() == Style.EMPTY ? 0.0F : pitch;
			blend(main, mainPose[0] - lean, mainPose[1] * side, mainPose[2] * side, k);
			blend(off, offPose[0] - lean, offPose[1] * side, offPose[2] * side, k);
		} else {
			// (aiming or swinging from the knee: the arms are vanilla's, only no longer crouched)
			h.rightArm.xRot -= 0.4F * k;
			h.leftArm.xRot -= 0.4F * k;
		}
	}

	/**
	 * (0.18.66) Vanilla lays its crouch on (or takes it off) in one frame; here the body is drawn only {@code bend} of the
	 * way into it, so going down and getting up both ease (MGSV's smooth stance changes).
	 */
	private static void bendBody(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		Crouch c = state.getData(KEY);
		if (c == null || otherPose(state)) {
			return;
		}
		// crouched: back out by what isn't bent yet; standing: in by what's still bent
		float u = state.isCrouching ? c.bend() - 1.0F : c.bend();
		if (u == 0.0F) {
			return;
		}
		h.body.xRot += V_BODY * u;
		h.rightArm.xRot += V_ARMS * u;
		h.leftArm.xRot += V_ARMS * u;
		h.rightLeg.z += V_LEGS_Z * u;
		h.leftLeg.z += V_LEGS_Z * u;
		h.head.y += V_HEAD_Y * u;
		h.body.y += V_BODY_Y * u;
		h.rightArm.y += V_BODY_Y * u;
		h.leftArm.y += V_BODY_Y * u;
	}

	/**
	 * Right before the model is drawn (after {@link #applyModel}): on the knee, the front leg bent at the knee for this one
	 * draw ({@link KneeBend#restore} once it's drawn).
	 */
	public static void bendKnee(Model<?> model, Object renderState) {
		Crouch c = posed(model, renderState);
		if (c == null) {
			return;
		}
		HumanoidModel<?> h = (HumanoidModel<?>) model;
		// (0.18.57) in the low crouch each knee bends as far as its thigh is forward, so the shin stands straight
		float low = c.low() * (1.0F - c.kneel());
		// (0.18.85) and more as the leg comes through, lifting the foot off the ground
		float lift = Mth.sin(c.pos() * STRIDE_BEAT) * LOW_LIFT * stride(c) * low;
		// (0.19.30) each foot on the ground wherever its thigh is (or lifted, coming through)
		float right = Mth.lerp(c.kneel(), groundKnee(-h.rightLeg.xRot) * c.low() + Math.max(0.0F, lift), FRONT_KNEE);
		// (0.21.28) and on the knee, the left shin laid flat behind (KneelLegs)
		float left = groundKnee(-h.leftLeg.xRot) * low + Math.max(0.0F, -lift) + BACK_KNEE * c.kneel();
		if (right > 0.0F) {
			KneeBend.bend(h.rightLeg, right);
		}
		if (left > 0.0F) {
			KneeBend.bend(h.leftLeg, left);
		}
	}

	/**
	 * (0.21.37) From the held-item layer, just before the main hand's item is drawn (the pose stack at the grip): on the
	 * knee, the weapon turned in the hand for its {@link Style} (a sword point down into the ground, a gun's muzzle down).
	 * Not while the arms are busy (aiming, drawing, swinging), when they're vanilla's.
	 */
	public static void grip(Object renderState, net.minecraft.world.entity.HumanoidArm arm, PoseStack poseStack) {
		if (!(renderState instanceof AvatarRenderState state) || arm != state.mainArm || !state.isCrouching || state.deathTime > 0.0F) {
			return;
		}
		Crouch c = state.getData(KEY);
		if (c == null || c.kneel() <= 0.0F || c.style() == Style.EMPTY || otherPose(state) || state.isUsingItem || state.swingAnimation > 0.0F
			|| !free(state.rightArmPose) || !free(state.leftArmPose)) {
			return;
		}
		float degrees = switch (c.style()) {
			case PLANT -> PLANT_GRIP;
			case UPRIGHT -> UPRIGHT_GRIP;
			case BOW -> BOW_GRIP;
			case RIFLE -> RIFLE_GRIP;
			default -> 0.0F;
		};
		// (about the hand's own side-to-side axis, as FinisherPose turns a weapon)
		poseStack.rotateDegrees(Axis.XP, -degrees * c.kneel());
	}

	/** The crouch to lay over this model, or null when it isn't a crouching body or another full-body pose has it. */
	private static Crouch posed(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?>) || state.deathTime > 0.0F || !state.isCrouching) {
			return null;
		}
		Crouch c = state.getData(KEY);
		return c == null || otherPose(state) ? null : c;
	}

	/** Whether another full-body pose is drawn on this body (tending, a finisher, carrying, crawling). */
	private static boolean otherPose(AvatarRenderState state) {
		return state.getData(com.jaymospowers.client.wounds.TendPose.KEY) != null
			|| state.getData(com.jaymospowers.client.finisher.FinisherPose.KEY) != null || state.getData(CarryPose.CARRIER_KEY) != null
			|| state.getData(CrawlPose.KEY) != null
			// (0.20.38) sitting down while still holding Sneak: the sit's own legs, not the crouch's
			|| state.getData(MeditatePose.KEY) != null;
	}

	/** An arm with nothing to do (empty, or just holding something), free to be held out or rested. */
	private static boolean free(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
	}

	/** (0.18.85) How far into a full stride the low crouch is stepping, by walking speed (0 stood still). */
	/**
	 * (0.19.30) In the low crouch, how far a knee bends so the foot of a thigh raised {@code thigh} radians forward is on the
	 * ground, the body sunk {@link #LOW} + {@link #LOW_DROP}: the shin leans back from the knee to the foot (the knee over the
	 * toes). A thigh too far forward to reach just hangs its shin straight down.
	 */
	static float groundKnee(float thigh) {
		if (thigh <= 0.0F) {
			return 0.0F;
		}
		float reach = (12.0F - (LOW + LOW_DROP) * 16.0F) / 6.0F - Mth.cos(thigh);
		float shin = reach >= 1.0F ? 0.0F : (float) Math.acos(Mth.clamp(reach, -1.0F, 1.0F));
		return thigh + shin;
	}

	private static float stride(Crouch c) {
		return Mth.clamp(c.speed() * STRIDE_GAIN, 0.0F, 1.0F);
	}

	private static float approach(float from, float to, float step) {
		return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
	}

	/** (0.18.66) Smootherstep: starts and settles with no jolt at either end. */
	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
