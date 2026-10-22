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
 */
public final class UpRunPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Running: the lean into the wall (degrees) and the bend at the hips (radians). */
	static final float LEAN_DEGREES = 22.0F, HIP_BEND = 0.3F;
	/** Running: the arms swept back (radians from hanging down) and a little out; the head tipped back to look up. */
	static final float ARMS_BACK = 1.25F, ARMS_OUT = 0.2F, HEAD_UP = -0.7F;
	/** Running: the stride (radians either way) and its beat (radians a tick: a sprint is about 0.66). */
	static final float STRIDE = 1.1F, STRIDE_BEAT = 1.5F;
	/** Sliding: leaning back off the wall (degrees), the knees up to it, the dragging hand and the balancing one (radians). */
	static final float SLIDE_LEAN = -8.0F, SLIDE_KNEE = -1.1F, SLIDE_KNEE_OTHER = -0.6F, DRAG_ARM = -1.9F, BALANCE_ARM = -0.6F, BALANCE_OUT = 0.6F;
	/** Sliding: the dragging hand's judder against the wall (radians) at this beat (per tick). */
	static final float JUDDER = 0.05F, JUDDER_BEAT = 2.3F;
	// ================================================================================================================

	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:freerun_up_run");

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
			poseStack.rotateDegrees(Axis.XP, -degrees);
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
			float stride = Mth.sin(state.ageInTicks * STRIDE_BEAT) * STRIDE;
			f.set(PoseBlend.HIP_PITCH, HIP_BEND);
			f.set(PoseBlend.HEAD, HEAD_UP, f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
			f.set(PoseBlend.RIGHT_ARM, ARMS_BACK, 0.0F, ARMS_OUT);
			f.set(PoseBlend.LEFT_ARM, ARMS_BACK, 0.0F, -ARMS_OUT);
			// the knee comes up high on the way forward, the push-off leg stays straighter
			f.set(PoseBlend.RIGHT_LEG, Math.min(stride, 0.5F) - Math.max(0.0F, -stride) * 0.3F, 0.0F, 0.0F);
			f.set(PoseBlend.LEFT_LEG, Math.min(-stride, 0.5F) - Math.max(0.0F, stride) * 0.3F, 0.0F, 0.0F);
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
}
