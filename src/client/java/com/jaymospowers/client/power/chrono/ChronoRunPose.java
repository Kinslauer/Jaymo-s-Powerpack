package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoSpeed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.11.57) Overclock's run, after the comic-book speedsters: running overclocked, the whole body leans hard into the
 * run from the feet, the head held up to see ahead, the arms pumping and the legs cycling far faster than any sprint.
 * It eases in and out with the runner's speed ({@link ChronoStreak#run}), so it fades as they slow. Not on a wall run
 * (that's {@code GravityWalkPose}'s), in a Time Flow blow, swimming, gliding or riding.
 */
public final class ChronoRunPose {
	// ---- tuning ----
	/** How far the body leans forward at full speed (degrees, from the feet). */
	static final float LEAN_DEGREES = 32.0F;
	/** How fast the limbs cycle (radians a tick; vanilla sprinting is about 0.8). */
	static final float CADENCE = 2.1F;
	/** How far the legs swing either way, and how far the arms pump (radians). */
	static final float LEG_SWING = 1.25F;
	static final float ARM_SWING = 1.35F;
	/** Arms held a little forward and in, elbows-bent look (radians). */
	static final float ARM_FORWARD = 0.25F, ARM_IN = 0.12F;
	/** Below this much run the pose is left off entirely. */
	static final float MIN_AMOUNT = 0.03F;

	/** How much of the run pose this player shows (0-1). */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:chrono_run_pose");

	private ChronoRunPose() {
	}

	/** Render-state extraction (after {@link ChronoFlowPose#extract}, whose blow takes precedence). */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, null);
		if (!(entity instanceof Player player) || player.isPassenger() || player.isSwimming() || player.isFallFlying()
			|| player.getAttached(ChronoSpeed.WALL_RUN) != null || state.getData(ChronoFlowPose.KEY) != null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			// leaning, the body First-person Model draws would lie across the camera
			return;
		}
		float amount = ChronoStreak.run(player.getId(), partialTicks);
		if (amount >= MIN_AMOUNT) {
			state.setData(KEY, amount);
		}
	}

	/** After the body faces its way (setupRotations' tail, feet at the origin): the lean into the run. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Float amount = state.getData(KEY);
		if (amount == null || state.deathTime > 0.0F) {
			return;
		}
		// the body's front is -Z here, so a negative turn about X tips the head forward
		poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES * amount);
	}

	/** After the model animates (once per {@code setupAnim}): the fast pumping run, set from the age so every copy agrees. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Float boxed = avatar.getData(KEY);
		if (boxed == null) {
			return;
		}
		float a = boxed;
		float t = avatar.ageInTicks * CADENCE + avatar.id * 0.7F;
		float s = Mth.sin(t);
		set(h.rightLeg, s * LEG_SWING, 0.0F, 0.0F, a);
		set(h.leftLeg, -s * LEG_SWING, 0.0F, 0.0F, a);
		set(h.rightArm, -s * ARM_SWING - ARM_FORWARD, 0.0F, ARM_IN, a);
		set(h.leftArm, s * ARM_SWING - ARM_FORWARD, 0.0F, -ARM_IN, a);
		// the head held up against the lean, to look where it's going
		h.head.xRot -= Mth.DEG_TO_RAD * LEAN_DEGREES * a;
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
