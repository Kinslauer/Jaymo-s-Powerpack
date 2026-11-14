package com.jaymospowers.client.freerun;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.freerun.Freerun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.67) The vertical wall run as everyone sees it ({@link Freerun#upRunning}; it was the climbing pose): a Naruto
 * run, leaning hard into the wall, both arms swept straight back and the legs sprinting up it. Stopping on the wall
 * (Andy: "you should slide when you stop moving") it becomes a slide down: crouched, knees to the wall, one hand
 * dragging on it and the other out for balance. Worked out from the synced move and whether the body is going down,
 * so it shows on every player in view.
 * <p>Andy: "the climbing and wall run animation need work". The lean into the wall is gentler and turns about the
 * shoulders, the bend at the hips halved, so the head and chest no longer sink into the wall (they did, by a third of a
 * block) while the feet still reach it; the
 * legs stride with the height climbed ({@link #STEP_HEIGHT} a full stride) instead of the clock, each knee folding as
 * it swings up and setting the foot flat on the wall under it, so the feet plant on the wall instead of kicking
 * through it.
 */
public final class UpRunPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Running: the lean into the wall (degrees), turning about this height on the body (blocks), and the bend at the hips (radians). */
	static final float LEAN_DEGREES = 12.0F, LEAN_PIVOT = 1.5F, HIP_BEND = 0.15F;
	/** Running: the arms swept back (radians from hanging down) and a little out; the head tipped back to look up. */
	static final float ARMS_BACK = 1.25F, ARMS_OUT = 0.2F, HEAD_UP = -0.7F;
	/** Running: blocks climbed for one full stride (a step with each foot). */
	static final float STEP_HEIGHT = 1.3F;
	/** Running: the thigh's middle swing and how far it swings each way (radians, - up toward the wall). */
	static final float THIGH_MID = -0.85F, THIGH_SWING = 0.8F;
	/** Running: the knee pushing off, folding as it swings up, and bent to set the foot flat on the wall under it (radians). */
	static final float KNEE_PUSH = 0.3F, KNEE_FOLD = 1.4F, KNEE_PLANT = 1.1F;
	/** Sliding: leaning back off the wall (degrees), the knees up to it, the dragging hand and the balancing one (radians). */
	static final float SLIDE_LEAN = -8.0F, SLIDE_KNEE = -1.1F, SLIDE_KNEE_OTHER = -0.6F, DRAG_ARM = -1.9F, BALANCE_ARM = -0.6F, BALANCE_OUT = 0.6F;
	/** Sliding: the dragging hand's judder against the wall (radians) at this beat (per tick). */
	static final float JUDDER = 0.05F, JUDDER_BEAT = 2.3F;
	// ================================================================================================================

	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:freerun_up_run");
	/** The stride's phase (radians), from the height climbed. */
	public static final RenderStateDataKey<Float> STRIDE = RenderStateDataKey.create(() -> "jaymospowers:freerun_up_run_stride");

	/** Per entity id: {height last frame, height climbed}. */
	private static final java.util.Map<Integer, double[]> CLIMBED = new java.util.HashMap<>();

	private static final PoseBlend.Channel RUN = PoseBlend.channel("freerun_up_run");
	private static final PoseBlend.Channel SLIDE = PoseBlend.channel("freerun_up_slide");

	private UpRunPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		boolean on = !ownView && Freerun.upRunning(player);
		boolean sliding = on && entity.getY() < entity.yo - 0.01;
		state.setData(KEY, on ? Boolean.TRUE : null);
		RUN.track(player.getId(), on && !sliding, 0.0F, 2.0F);
		SLIDE.track(player.getId(), sliding, 0.0F, 1.5F);
		// the stride follows the height climbed, and holds while the pose eases out
		double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
		double[] climbed = CLIMBED.get(player.getId());
		if (on && climbed == null) {
			climbed = new double[] {y, 0.0};
			CLIMBED.put(player.getId(), climbed);
		}
		if (climbed == null || !on && RUN.peek(player.getId()) == null) {
			CLIMBED.remove(player.getId());
			state.setData(STRIDE, null);
			return;
		}
		double rise = y - climbed[0];
		if (on && rise > 0.0 && rise < 1.0) {
			climbed[1] = (climbed[1] + rise) % 4096.0;
		}
		climbed[0] = y;
		state.setData(STRIDE, (float) (Mth.TWO_PI * climbed[1] / STEP_HEIGHT));
	}

	/** After the body faces its way: the lean into the wall (or back off it, sliding). */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		if (state.deathTime > 0.0F || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		PoseBlend.Track run = RUN.peek(avatar.id);
		PoseBlend.Track slide = SLIDE.peek(avatar.id);
		float degrees = (run != null ? LEAN_DEGREES * run.weight() : 0.0F) + (slide != null ? SLIDE_LEAN * slide.weight() : 0.0F);
		if (degrees != 0.0F) {
			// about the shoulders, so the head doesn't sink into the wall and the feet still reach it
			poseStack.translate(0.0F, LEAN_PIVOT, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -degrees);
			poseStack.translate(0.0F, -LEAN_PIVOT, 0.0F);
		}
	}

	/** After the model animates: the arms back and the sprinting legs, or the slide's crouch. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		PoseBlend.Track run = RUN.peek(state.id);
		if (run != null) {
			PoseBlend.Frame f = new PoseBlend.Frame(h);
			Float phase = state.getData(STRIDE);
			float psi = phase != null ? phase : 0.0F;
			f.set(PoseBlend.HIP_PITCH, HIP_BEND);
			f.set(PoseBlend.HEAD, HEAD_UP, f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
			f.set(PoseBlend.RIGHT_ARM, ARMS_BACK, 0.0F, ARMS_OUT);
			f.set(PoseBlend.LEFT_ARM, ARMS_BACK, 0.0F, -ARMS_OUT);
			// the thighs swing up toward the wall in turn (the knees: bendLimbs)
			f.set(PoseBlend.RIGHT_LEG, THIGH_MID - THIGH_SWING * Mth.sin(psi), 0.0F, 0.0F);
			f.set(PoseBlend.LEFT_LEG, THIGH_MID - THIGH_SWING * Mth.sin(psi + Mth.PI), 0.0F, 0.0F);
			run.apply(h, f);
		}
		PoseBlend.Track slide = SLIDE.peek(state.id);
		if (slide != null) {
			PoseBlend.Frame f = new PoseBlend.Frame(h);
			f.set(PoseBlend.HIP_PITCH, 0.25F);
			f.set(PoseBlend.HEAD, -0.35F, f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
			f.set(PoseBlend.RIGHT_LEG, SLIDE_KNEE, 0.0F, 0.05F);
			f.set(PoseBlend.LEFT_LEG, SLIDE_KNEE_OTHER, 0.0F, -0.1F);
			f.set(PoseBlend.RIGHT_ARM, DRAG_ARM, 0.0F, 0.1F);
			f.shake(PoseBlend.RIGHT_ARM, Mth.sin(state.ageInTicks * JUDDER_BEAT) * JUDDER);
			f.set(PoseBlend.LEFT_ARM, BALANCE_ARM, 0.0F, -BALANCE_OUT);
			slide.apply(h, f);
		}
	}

	/** Right before the model is drawn: running up the wall, each knee folding as it swings up and setting the foot on the wall. */
	public static void bendLimbs(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		PoseBlend.Track run = RUN.peek(state.id);
		Float phase = state.getData(STRIDE);
		if (run == null || phase == null) {
			return;
		}
		com.jaymospowers.client.KneeBend.bend(h.rightLeg, knee(phase) * run.weight());
		com.jaymospowers.client.KneeBend.bend(h.leftLeg, knee(phase + Mth.PI) * run.weight());
	}

	/** A knee at stride phase {@code psi}: folding as the leg swings up, then bent to put the foot flat on the wall under the knee. */
	static float knee(float psi) {
		float swing = Math.max(0.0F, Mth.cos(psi));
		return KNEE_PUSH + KNEE_FOLD * swing * (float) Math.sqrt(swing) + KNEE_PLANT * Math.max(0.0F, Mth.sin(psi));
	}
}
