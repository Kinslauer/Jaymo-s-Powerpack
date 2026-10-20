package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebWallRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The wall run as everyone sees it ({@link WebWallRun#WALL}), after the Prince of Persia's: feet on the wall and the
 * body slanted out from it, pitched into the run, legs pumping in long strides along the wall, the arm by the wall
 * reaching back to skim it and the outer arm swinging hard for balance, head held up to look where you're going.
 * Runs after {@link WebBodyPose}, so it has the body for as long as the run lasts; a kick-off's cartwheel takes
 * over the moment it ends. Your own run doesn't show in first person.
 */
public final class WebWallRunPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** How far the body slants out from the wall, and pitches forward into the run (degrees). */
	static final float LEAN = 32.0F, PITCH = 14.0F;
	/** How far the feet sit over towards the wall (blocks), so they land on it rather than in the air beside it. */
	static final float FOOT_SHIFT = 0.18F;
	/** Stride: radians of leg swing each way, and how fast (radians per tick: a full stride about every 8 ticks). */
	static final float STRIDE = 1.05F, STRIDE_SPEED = 0.8F;
	/** Ticks the pose takes to settle in after stepping onto the wall. */
	static final float EASE_IN = 3.0F;
	// ================================================================================================================

	/** {@code side}: +1 with the wall on your right, -1 on your left; {@code in}: how settled into the pose (0-1). */
	public record Run(int side, float time, float in) {
	}

	public static final RenderStateDataKey<Run> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_wall_run");

	/** Per entity id: the game time its current run began (for the ease in). */
	private static final java.util.Map<Integer, Float> STARTED = new java.util.HashMap<>();

	private WebWallRunPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer face = entity.getAttached(WebWallRun.WALL);
		if (face == null) {
			// (0.13.36) a freerunner's wall run (Wall Runner) looks the same
			face = entity.getAttached(com.jaymospowers.freerun.Freerun.WALL);
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		if (face == null || face < 0 || face >= Direction.values().length || ownView || entity.isPassenger()) {
			if (face == null) {
				STARTED.remove(entity.getId());
			}
			state.setData(KEY, null);
			return;
		}
		Direction wall = Direction.from3DDataValue(face);
		float time = entity.level().getGameTime() + partialTicks;
		float started = STARTED.computeIfAbsent(entity.getId(), id -> time);
		// the body's right, from the way it faces: the wall is on that side or the other
		float yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
		double right = -Mth.cos(yaw) * wall.getStepX() - Mth.sin(yaw) * wall.getStepZ();
		int side = right >= 0.0 ? 1 : -1;
		float in = Mth.clamp((time - started) / EASE_IN, 0.0F, 1.0F);
		state.setData(KEY, new Run(side, time, in * in * (3.0F - 2.0F * in)));
	}

	/** After the body faces its way: slant out from the wall about the feet, and pitch into the run. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Run run = state.getData(KEY);
		if (run == null || state.deathTime > 0.0F) {
			return;
		}
		// (here +X is the body's right, +Y up, +Z behind it; a turn about +Z tips the top over to the left)
		poseStack.translate(run.side() * FOOT_SHIFT * run.in(), 0.0F, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, run.side() * LEAN * run.in());
		poseStack.rotateDegrees(Axis.XP, -PITCH * run.in());
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
		float e = run.in();
		float stride = Mth.sin(run.time() * STRIDE_SPEED);
		// long strides along the wall, the leading knee driven high
		float rightLeg = -0.25F + STRIDE * stride;
		float leftLeg = -0.25F - STRIDE * stride;
		h.rightLeg.xRot = Mth.lerp(e, h.rightLeg.xRot, Math.min(rightLeg, 0.6F));
		h.leftLeg.xRot = Mth.lerp(e, h.leftLeg.xRot, Math.min(leftLeg, 0.6F));
		h.rightLeg.zRot = Mth.lerp(e, h.rightLeg.zRot, 0.05F);
		h.leftLeg.zRot = Mth.lerp(e, h.leftLeg.zRot, -0.05F);
		// the arm by the wall reaches out and back to skim it; the outer arm swings against the legs
		boolean wallRight = run.side() > 0;
		var inner = wallRight ? h.rightArm : h.leftArm;
		var outer = wallRight ? h.leftArm : h.rightArm;
		float outerSwing = wallRight ? STRIDE * stride : -STRIDE * stride;
		inner.xRot = Mth.lerp(e, inner.xRot, 0.35F + 0.1F * stride);
		inner.yRot = Mth.lerp(e, inner.yRot, 0.0F);
		inner.zRot = Mth.lerp(e, inner.zRot, run.side() * 1.15F);
		outer.xRot = Mth.lerp(e, outer.xRot, -0.2F + 1.05F * outerSwing);
		outer.zRot = Mth.lerp(e, outer.zRot, -run.side() * 0.35F);
		// head held level against the slant, looking ahead
		h.head.zRot = Mth.lerp(e, h.head.zRot, -run.side() * LEAN * 0.6F * Mth.DEG_TO_RAD);
		h.head.xRot = Mth.lerp(e, h.head.xRot, h.head.xRot - 0.15F);
	}
}
