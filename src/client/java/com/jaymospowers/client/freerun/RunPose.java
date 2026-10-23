package com.jaymospowers.client.freerun;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.freerun.FastRun;
import com.jaymospowers.freerun.Slide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.12) The fast run and the slide as everyone sees them: a runner in a fast run ({@link FastRun#active}) leans
 * forward from the feet; a slider ({@link Slide#sliding}) drops into a feet-first slide. Both come from the synced flags,
 * so they show on every player in view.
 * <p>
 * (0.15.42) Both ease now ({@link PoseBlend}). The lean comes and goes on a spring. The slide (it was the swimming pose,
 * arms straight up and legs locked together) drops in on bent knees, then lies back on one hip: the lead leg out, the
 * other tucked, one hand trailing the ground and the other up for balance, the head up and looking ahead; when it ends
 * it springs back up into the run with a pop forward. Vanilla's swimming tilt is switched off for it (as for a carried
 * body) and the lie-back is laid on here instead.
 */
public final class RunPose {
	/** The fast run's forward lean (degrees), and how quick it eases (the weight's spring, times its usual). */
	static final float LEAN_DEGREES = 14.0F, LEAN_SPEED = 0.8F;
	/** The slide: how far the body lies back about the feet (degrees) and how far it drops (blocks). */
	static final float SLIDE_TILT = 70.0F, SLIDE_DROP = 0.2F;
	/** The drop in: how long (seconds), crouched forward at the hips (radians) on bent knees. */
	static final float DROP_IN_TIME = 0.14F, DROP_IN_CROUCH = 0.5F, DROP_IN_KNEES = -0.4F;
	/** Lying back: the torso sat up at the hips, the head brought round to look ahead (radians). */
	static final float SIT_UP = 0.35F, HEAD_AHEAD = 0.55F;
	/** The legs: the lead (right) out and a little raised, the other tucked out to the side (radians). */
	static final float LEAD_LEG = -0.25F, TUCK_LEG = 0.25F, TUCK_OUT = 0.2F;
	/** The arms: one trailing back on the ground, the other up for balance (pitch, and out to the side). */
	static final float TRAIL_ARM = 0.95F, TRAIL_OUT = 0.45F, BALANCE_ARM = -2.1F, BALANCE_OUT = 0.45F;
	/** The ground's rub through the trailing hand (radians) at this beat (per tick). */
	static final float RUB = 0.04F, RUB_BEAT = 1.9F;
	/** Back up: the pop forward (degrees, and how long, seconds), the arms swinging into the run. */
	static final float POP_DEGREES = 12.0F, POP_TIME = 0.3F, POP_ARMS = 0.7F, POP_LEGS = 0.55F;

	public static final RenderStateDataKey<Boolean> LEAN = RenderStateDataKey.create(() -> "jaymospowers:freerun_lean");
	public static final RenderStateDataKey<Boolean> SLIDE = RenderStateDataKey.create(() -> "jaymospowers:freerun_slide");

	private static final PoseBlend.Channel LEAN_BLEND = PoseBlend.channel("freerun_lean");
	private static final PoseBlend.Channel SLIDE_BLEND = PoseBlend.channel("freerun_slide");

	private RunPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			return;
		}
		boolean slide = Slide.sliding(player);
		state.setData(SLIDE, slide ? Boolean.TRUE : null);
		// (0.15.122) not your own body in first person (First-person Model): leaning from the feet, its back and
		// shoulders swung up in front of the camera
		boolean lean = !slide && FastRun.active(player) && !player.isFallFlying() && !player.isSwimming()
			&& !(player == Minecraft.getInstance().player && FirstPersonCompat.bodyShown());
		state.setData(LEAN, lean ? Boolean.TRUE : null);
		LEAN_BLEND.track(player.getId(), lean, 0.0F, LEAN_SPEED);
	}

	/**
	 * From the avatar renderer's extractRenderState (after the player's own fields are filled): the slide steps its easing,
	 * and while it's on (or easing out) vanilla's swimming tilt and walk are switched off, as for a carried body.
	 */
	public static void extractAvatar(Avatar entity, AvatarRenderState state, float partialTicks) {
		boolean slide = entity instanceof Player player && Slide.sliding(player);
		PoseBlend.Track track = SLIDE_BLEND.track(entity.getId(), slide);
		// while easing out too, unless it's become a real crawl (a low ceiling), which vanilla tilts as usual
		if (track == null || !slide && entity.isVisuallySwimming()) {
			return;
		}
		state.isVisuallySwimming = false;
		state.swimAmount = 0.0F;
		state.isCrouching = false;
		state.walkAnimationSpeed = 0.0F;
	}

	/** After the body faces its way: the run's lean, the slide's lie-back (and the pop back up after it). */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		if (state.deathTime > 0.0F || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		PoseBlend.Track lean = LEAN_BLEND.peek(avatar.id);
		if (lean != null) {
			poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES * lean.weight());
		}
		PoseBlend.Track slide = SLIDE_BLEND.peek(avatar.id);
		if (slide == null) {
			return;
		}
		float w = slide.amount();
		if (slide.on()) {
			// lying back about the feet, the hips let down to the ground
			poseStack.translate(0.0F, -SLIDE_DROP * w, 0.0F);
			poseStack.rotateDegrees(Axis.XP, SLIDE_TILT * w);
		} else {
			// sprung back up: the last of the lie-back, then a pop forward into the run
			float pop = PoseBlend.bump(slide.age(), 0.0F, POP_TIME * 0.35F, POP_TIME * 0.4F, POP_TIME);
			poseStack.rotateDegrees(Axis.XP, SLIDE_TILT * w - POP_DEGREES * pop);
		}
	}

	/** After the model animates: the slide's arms and legs, and the spring back up. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		PoseBlend.Track track = SLIDE_BLEND.peek(state.id);
		if (track == null || state.deathTime > 0.0F) {
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		float t = track.age();
		if (!track.on()) {
			// back up: arms swinging into the stride, the lead leg stepping through
			float pop = PoseBlend.bump(t, 0.0F, POP_TIME * 0.35F, POP_TIME * 0.4F, POP_TIME);
			f.set(PoseBlend.RIGHT_ARM, -POP_ARMS * pop, 0.0F, 0.05F);
			f.set(PoseBlend.LEFT_ARM, POP_ARMS * 0.85F * pop, 0.0F, -0.05F);
			f.set(PoseBlend.RIGHT_LEG, -POP_LEGS * pop, 0.0F, 0.0F);
			f.set(PoseBlend.LEFT_LEG, POP_LEGS * pop, 0.0F, 0.0F);
			f.set(PoseBlend.HIP_PITCH, 0.2F * pop);
			track.apply(h, f);
			return;
		}
		float in = PoseBlend.seg(t, DROP_IN_TIME * 0.5F, DROP_IN_TIME * 1.5F);
		// dropping in: crouched forward on bent knees; then lying back on one hip
		f.set(PoseBlend.HIP_PITCH, Mth.lerp(in, DROP_IN_CROUCH, SIT_UP));
		f.set(PoseBlend.HEAD, Mth.lerp(in, 0.0F, HEAD_AHEAD) - Mth.lerp(in, DROP_IN_CROUCH, SIT_UP) * 0.5F, f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
		f.set(PoseBlend.RIGHT_LEG, Mth.lerp(in, DROP_IN_KNEES, LEAD_LEG), 0.0F, 0.03F);
		f.set(PoseBlend.LEFT_LEG, Mth.lerp(in, DROP_IN_KNEES * 0.8F, TUCK_LEG), 0.0F, -TUCK_OUT * in);
		// the right hand trailing the ground, the left up for balance
		float rub = Mth.sin(state.ageInTicks * RUB_BEAT) * RUB * in;
		f.set(PoseBlend.RIGHT_ARM, Mth.lerp(in, -0.5F, TRAIL_ARM), 0.0F, Mth.lerp(in, 0.1F, TRAIL_OUT));
		f.shake(PoseBlend.RIGHT_ARM + 2, rub);
		f.set(PoseBlend.LEFT_ARM, Mth.lerp(in, -0.9F, BALANCE_ARM), 0.0F, -Mth.lerp(in, 0.1F, BALANCE_OUT));
		track.apply(h, f);
	}
}
