package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.power.web.WebWallRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The wall run as everyone sees it ({@link WebWallRun#WALL}), after the Prince of Persia's: feet on the wall and the
 * body slanted out from it, pitched into the run, head held up to look where you're going. Runs after
 * {@link WebBodyPose}, so it has the body for as long as the run lasts; a kick-off's cartwheel takes over the moment it
 * ends. Your own run doesn't show in first person. A freerunner's run ({@code Freerun#WALL}) looks the same.
 * <p>Andy: "the climbing and wall run animation need work". Redrawn as a real run:
 * <ul>
 * <li>The body faces the way you run along the wall whatever way you look (the head still turns to your look, as far
 * as a neck turns), and which side the wall is on comes from that, not from where the body happened to face.</li>
 * <li>The stride follows the ground covered ({@link #STRIDE_LENGTH} a full stride), so a fast run takes long quick
 * strides and the feet don't slide; the knees bend (each leg folds high as it swings through and stays nearly
 * straight pushing off), the body bobs with each step and the shoulders twist against the hips.</li>
 * <li>The outer arm pumps with the opposite leg, elbow bent like a sprinter's; the arm by the wall reaches down to it,
 * the palm pressed flat and skimming it, instead of sticking straight out through it.</li>
 * <li>Coming off the wall the pose eases out over {@link #EASE_OUT} ticks instead of snapping back (unless a wall kick
 * or another move takes the body).</li>
 * </ul>
 */
public final class WebWallRunPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** How far the body slants out from the wall, and pitches forward into the run (degrees). */
	static final float LEAN = 24.0F, PITCH = 10.0F;
	/** How far the feet sit over towards the wall (blocks), so they land on it rather than in the air beside it. */
	static final float FOOT_SHIFT = 0.16F;
	/** Blocks run along the wall for one full stride (a step with each foot). */
	static final float STRIDE_LENGTH = 2.4F;
	/**
	 * The legs: the thigh's middle swing and how far it swings each way (radians, - forward); the knee's bend pushing off,
	 * how much more it folds swinging through, and how much it bends as the foot reaches ahead to land.
	 */
	static final float THIGH_MID = -0.3F, THIGH_SWING = 1.0F, KNEE_PUSH = 0.2F, KNEE_FOLD = 1.6F, KNEE_REACH = 0.25F;
	/** The leg by the wall turned out to it, the other drawn in a little (radians). */
	static final float LEG_TO_WALL = 0.18F, LEG_IN = 0.06F;
	/** The outer arm: its middle swing, how far it pumps each way, its elbow (radians: - folds the forearm forward), how far out. */
	static final float ARM_MID = -0.15F, ARM_PUMP = 0.85F, ARM_ELBOW = -1.35F, ARM_OUT = 0.12F;
	/** The arm by the wall: out and down to it, back a little, sweeping with the stride, its elbow bent to set the palm flat on it (radians). */
	static final float WALL_ARM_OUT = 0.7F, WALL_ARM_BACK = 0.2F, WALL_ARM_SWEEP = 0.15F, WALL_ARM_ELBOW = 0.5F;
	/** The shoulders' twist against the hips (radians), and the body's bob with each step (blocks). */
	static final float TWIST = 0.18F, BOB = 0.05F;
	/** How far the head turns from the run's way to your look at most (radians). */
	static final float HEAD_TURN_MAX = 1.3F;
	/** Ticks the pose takes to settle in after stepping onto the wall, and to let go after leaving it. */
	static final float EASE_IN = 3.0F, EASE_OUT = 4.0F;
	// ================================================================================================================

	/**
	 * {@code side}: +1 with the wall on your right, -1 on your left; {@code stride}: the stride's phase (radians);
	 * {@code in}: how settled into the pose (0-1); {@code turn}: degrees the body turns from its own way to the run's.
	 */
	public record Run(int side, float stride, float in, float turn) {
	}

	public static final RenderStateDataKey<Run> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_wall_run");

	/** Per entity id: the run in progress (or easing out). */
	private static final java.util.Map<Integer, Track> TRACKS = new java.util.HashMap<>();

	private static final class Track {
		float started;
		float ended = Float.NaN;
		double x, z, run;
		float runYaw;
		int side = 1;
		boolean aimed;

		Track(float time, double x, double z) {
			this.started = time;
			this.x = x;
			this.z = z;
		}
	}

	private WebWallRunPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer face = entity.getAttached(WebWallRun.WALL);
		if (face == null) {
			// (0.14.3) a freerunner's wall run (Wall Runner) looks the same
			face = entity.getAttached(com.jaymospowers.freerun.Freerun.WALL);
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		boolean onWall = face != null && face >= 0 && face < Direction.values().length;
		float time = entity.level().getGameTime() + partialTicks;
		double x = Mth.lerp(partialTicks, entity.xo, entity.getX()), z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		Track track = TRACKS.get(entity.getId());
		if (onWall) {
			if (track == null || !Float.isNaN(track.ended)) {
				track = new Track(time, x, z);
				TRACKS.put(entity.getId(), track);
			}
			Direction wall = Direction.from3DDataValue(face);
			// the run's way: along the wall, whichever way the body is going (kept while it stands still)
			double ax = -wall.getStepZ(), az = wall.getStepX();
			double along = (x - track.x) * ax + (z - track.z) * az;
			if (Math.abs(along) > 0.01 || !track.aimed) {
				double sign = Math.abs(along) > 0.01 ? Math.signum(along) : forwardSign(entity, ax, az);
				track.runYaw = (float) (Mth.atan2(-ax * sign, az * sign) * Mth.RAD_TO_DEG);
				track.aimed = true;
			}
			float yaw = track.runYaw * Mth.DEG_TO_RAD;
			// the body's right, as it runs: the wall is on that side or the other
			double right = -Mth.cos(yaw) * wall.getStepX() - Mth.sin(yaw) * wall.getStepZ();
			track.side = right >= 0.0 ? 1 : -1;
		} else if (track != null && Float.isNaN(track.ended)) {
			track.ended = time;
		}
		if (track == null) {
			state.setData(KEY, null);
			return;
		}
		// the ground covered (a jump of a block or more is a teleport, not a stride)
		double step = Math.sqrt(Mth.square(x - track.x) + Mth.square(z - track.z));
		if (step < 1.0) {
			track.run = (track.run + step) % 4096.0;
		}
		track.x = x;
		track.z = z;
		float weight;
		if (onWall) {
			weight = smooth(Mth.clamp((time - track.started) / EASE_IN, 0.0F, 1.0F));
		} else {
			// off the wall: ease out, unless a wall kick or another move has the body
			float out = (time - track.ended) / EASE_OUT;
			if (out >= 1.0F || out < 0.0F || entity.getAttached(WebParkour.MOVE) != null
				|| entity.getAttached(com.jaymospowers.freerun.Freerun.MOVE) != null) {
				TRACKS.remove(entity.getId());
				state.setData(KEY, null);
				return;
			}
			weight = 1.0F - smooth(out);
		}
		if (ownView || entity.isPassenger()) {
			state.setData(KEY, null);
			return;
		}
		float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		float turn = Mth.clamp(Mth.wrapDegrees(bodyYaw - track.runYaw), -90.0F, 90.0F);
		state.setData(KEY, new Run(track.side, (float) (Mth.TWO_PI * track.run / STRIDE_LENGTH), weight, turn));
	}

	/** Standing still on the wall with no way yet: +1 if the body faces along {@code (ax, az)}, else -1. */
	private static double forwardSign(LivingEntity entity, double ax, double az) {
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		return -Mth.sin(yaw) * ax + Mth.cos(yaw) * az >= 0.0 ? 1.0 : -1.0;
	}

	/** After the body faces its way: turned to the run, slanted out from the wall about the feet, pitched into the run. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Run run = state.getData(KEY);
		if (run == null || state.deathTime > 0.0F) {
			return;
		}
		// (here +X is the body's right, +Y up, +Z behind it; a turn about +Z tips the top over to the left)
		float e = run.in();
		poseStack.rotateDegrees(Axis.YP, run.turn() * e);
		float bob = BOB * (0.5F - 0.5F * Mth.cos(2.0F * run.stride()));
		poseStack.translate(run.side() * FOOT_SHIFT * e, bob * e, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, run.side() * LEAN * e);
		poseStack.rotateDegrees(Axis.XP, -PITCH * e);
	}

	/** After the model animates: the stride, the arms, the head. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Run run = living.getData(KEY);
		if (run == null) {
			return;
		}
		float e = run.in(), psi = run.stride(), s = Mth.sin(psi);
		boolean wallRight = run.side() > 0;
		// the legs: right leg at psi, left half a stride on; the one by the wall turned out to it
		set(h.rightLeg, thigh(psi), 0.0F, wallRight ? LEG_TO_WALL : LEG_IN, e);
		set(h.leftLeg, thigh(psi + Mth.PI), 0.0F, wallRight ? -LEG_IN : -LEG_TO_WALL, e);
		// the outer arm pumps with the opposite leg; the arm by the wall reaches down to it, palm on it, sweeping
		ModelPart inner = wallRight ? h.rightArm : h.leftArm;
		ModelPart outer = wallRight ? h.leftArm : h.rightArm;
		float outerPsi = wallRight ? psi : psi + Mth.PI;
		set(outer, ARM_MID - ARM_PUMP * Mth.sin(outerPsi), 0.0F, -run.side() * ARM_OUT, e);
		set(inner, WALL_ARM_BACK + WALL_ARM_SWEEP * s, 0.0F, run.side() * WALL_ARM_OUT, e);
		// the shoulders twist against the hips
		h.body.yRot = Mth.lerp(e, h.body.yRot, TWIST * s);
		// head held level against the slant, looking ahead, still turning to the look (the body was turned to the run)
		float look = Mth.clamp(h.head.yRot + run.turn() * Mth.DEG_TO_RAD, -HEAD_TURN_MAX, HEAD_TURN_MAX);
		h.head.yRot = Mth.lerp(e, h.head.yRot, look - TWIST * s);
		h.head.zRot = Mth.lerp(e, h.head.zRot, -run.side() * LEAN * 0.6F * Mth.DEG_TO_RAD);
		h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - 0.1F);
	}

	/** Right before the model is drawn (after {@link #applyModel}): the knees and elbows bent for this one draw. */
	public static void bendLimbs(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h) || living.deathTime > 0.0F) {
			return;
		}
		Run run = living.getData(KEY);
		if (run == null) {
			return;
		}
		float e = run.in(), psi = run.stride();
		com.jaymospowers.client.KneeBend.bend(h.rightLeg, knee(psi) * e);
		com.jaymospowers.client.KneeBend.bend(h.leftLeg, knee(psi + Mth.PI) * e);
		boolean wallRight = run.side() > 0;
		// a sprinter's elbow; the wall arm's bent out to lay the palm flat (a right arm's + sideways bend swings it out)
		com.jaymospowers.client.KneeBend.bend(wallRight ? h.leftArm : h.rightArm, ARM_ELBOW * e, WebParkourPose.ELBOW, false);
		com.jaymospowers.client.KneeBend.bend(wallRight ? h.rightArm : h.leftArm, -run.side() * WALL_ARM_ELBOW * e, WebParkourPose.ELBOW, true);
	}

	/** A thigh's swing at stride phase {@code psi}: furthest forward at a quarter, furthest back at three quarters. */
	static float thigh(float psi) {
		return THIGH_MID - THIGH_SWING * Mth.sin(psi);
	}

	/** A knee's bend at stride phase {@code psi}: folded high as the leg swings through, reaching to land, nearly straight pushing off. */
	static float knee(float psi) {
		float swing = Math.max(0.0F, Mth.cos(psi));
		return KNEE_PUSH + KNEE_FOLD * swing * (float) Math.sqrt(swing) + KNEE_REACH * Math.max(0.0F, Mth.sin(psi));
	}

	private static void set(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
