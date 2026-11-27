package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FallingTree;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "your character should animate both hands on it and pushing, only moving it when you have sufficient strength,
 * much like the leaning on walls". Someone pushing a fallen tree ({@link FallingTree#PUSHING}) leans in with both hands
 * flat on the log, one foot set back, arms trembling with the strain; the strong move it, the rest only strain. Laid over
 * the model right after it's posed, like {@link BioAbsorbPose}; in first person both bare hands go out onto it
 * ({@link #firstPerson}).
 */
public final class TreePushPose {
	/** How long the hands take to go out (ticks), the lean (radians), how far apart the hands sit (radians each way), the tremble. */
	static final float REACH_TICKS = 5.0F, LEAN = 0.3F, HANDS_IN = 0.14F, TREMBLE = 0.035F;
	/** The back foot and the front one (radians), and the most the arms turn to either side of the body. */
	static final float BACK_FOOT = 0.4F, FRONT_FOOT = -0.25F, TURN_MAX = 0.8F;
	/** First person: how far the hands come in, up and out (blocks), and their tremble. */
	static final float FIRST_IN = 0.16F, FIRST_UP = 0.14F, FIRST_OUT = 0.22F, FIRST_TREMBLE = 0.008F;

	/** This frame's {weight, down, turn, age} for someone pushing, on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:tree_push");

	/** Entity id -> the age (ticks) their push began. */
	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private TreePushPose() {
	}

	/** Render-state extraction: a pusher's hands for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		float[] pose = pose(entity, state.ageInTicks, state.bodyRot, partialTicks);
		state.setData(KEY, pose);
	}

	private static float[] pose(LivingEntity entity, float age, float bodyRot, float partialTicks) {
		Vec3 log = entity.getAttached(FallingTree.PUSHING);
		if (log == null) {
			STARTED.remove(entity.getId());
			return null;
		}
		float started = STARTED.computeIfAbsent(entity.getId(), k -> age);
		float weight = Mth.clamp((age - started) / REACH_TICKS, 0.0F, 1.0F);
		double dx = log.x - Mth.lerp(partialTicks, entity.xo, entity.getX());
		double dz = log.z - Mth.lerp(partialTicks, entity.zo, entity.getZ());
		double dy = log.y - (Mth.lerp(partialTicks, entity.yo, entity.getY()) + entity.getBbHeight() * 0.75);
		float down = (float)Mth.atan2(-dy, Math.sqrt(dx * dx + dz * dz));
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float turn = Mth.clamp(Mth.wrapDegrees(yaw - bodyRot) * Mth.DEG_TO_RAD, -TURN_MAX, TURN_MAX);
		return new float[] {weight * weight * (3.0F - 2.0F * weight), down, turn, age};
	}

	/** Right after a model was posed for this state: both hands on the log, leaning in, a foot set back. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float w = pose[0], tremble = Mth.sin(pose[3] * 2.7F) * TREMBLE;
		// straight out along the line to the log (forward is -pi/2), a little in toward each other
		float x = (float)(-Math.PI / 2.0) + Mth.clamp(pose[1], -0.6F, 0.9F);
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, x + tremble);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, x - tremble);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, pose[2] - HANDS_IN);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, pose[2] + HANDS_IN);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.0F);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, 0.0F);
		m.body.xRot += LEAN * w;
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, BACK_FOOT);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, FRONT_FOOT);
	}

	/**
	 * First person, while you push: how far your hands are out on the log (0 not pushing), with {@code poseStack} moved
	 * for the {@code arm} about to be drawn: in toward the middle, up and out ahead, trembling.
	 */
	public static float firstPerson(PoseStack poseStack, HumanoidArm arm, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return 0.0F;
		}
		float age = minecraft.player.tickCount + partialTicks;
		float[] pose = pose(minecraft.player, age, minecraft.player.yBodyRot, partialTicks);
		if (pose == null) {
			return 0.0F;
		}
		float w = pose[0], side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float tremble = Mth.sin(age * 2.7F + side) * FIRST_TREMBLE;
		poseStack.translate(-side * FIRST_IN * w, (FIRST_UP + tremble) * w, -FIRST_OUT * w);
		poseStack.rotate(Axis.XP.rotationDegrees(12.0F * w));
		return w;
	}
}
