package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Downed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.73) A downed guard ({@link Downed}) on the ground: a crawler lies face down and hauls itself along on its
 * forearms, legs dragging, head raised; the rest lie on their backs, one hand clutched to the chest, the other arm flung
 * out, a knee drawn up, head lolled to one side, breathing shallow. They drop into it over {@link #FALL_TICKS}.
 */
public final class DownedPose {
	/** How long the fall to the ground takes (ticks). */
	static final float FALL_TICKS = 6.0F;
	/** How far the body is raised off the ground lying face down and on its back (blocks). */
	static final float FACE_DOWN_LIFT = 0.3F, ON_BACK_LIFT = 0.2F;

	/** This frame's {amount 0..1, 1 for a crawler, 0 for one on its back}. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:downed");

	private DownedPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!Downed.isDowned(entity)) {
			return;
		}
		float down = entity.level().getGameTime() + partialTicks - Downed.since(entity);
		float amount = Mth.clamp(down / FALL_TICKS, 0.0F, 1.0F);
		state.setData(KEY, new float[] {amount * amount * (3.0F - 2.0F * amount), Downed.crawls(entity) ? 1.0F : 0.0F});
	}

	/** After the body's been turned to face its way: lay it on the ground. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		float[] pose = state.getData(KEY);
		if (pose == null) {
			return;
		}
		float amount = pose[0];
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		if (pose[1] > 0.0F) {
			// face down, the body along the ground ahead of its feet
			poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * amount, FACE_DOWN_LIFT * amount);
		} else {
			// on its back, fallen backwards
			poseStack.rotate(Axis.XP.rotationDegrees(90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * amount, -ON_BACK_LIFT * amount);
		}
	}

	/** Right after a model was posed for this state (the body, and each armour piece). */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		if (pose[1] > 0.0F) {
			crawl(m, living, pose[0]);
		} else {
			lie(m, living, pose[0]);
		}
	}

	/**
	 * Face down, so in the model's own terms "ahead along the ground" is up the body (past the head) and "up" is out of
	 * its back: the arms reach up past the head in turn and pull back under the chest.
	 */
	private static void crawl(HumanoidModel<?> m, LivingEntityRenderState state, float amount) {
		float effort = Math.min(1.0F, 0.3F + state.walkAnimationSpeed * 4.0F);
		float phase = state.ageInTicks * (0.1F + 0.16F * effort);
		float reach = Mth.sin(phase);
		pose(m.rightArm, Mth.PI - 0.35F + reach * 0.7F * effort, 0.0F, -0.25F - Math.max(0.0F, -reach) * 0.5F, amount);
		pose(m.leftArm, Mth.PI - 0.35F - reach * 0.7F * effort, 0.0F, 0.25F + Math.max(0.0F, reach) * 0.5F, amount);
		pose(m.rightLeg, 0.08F + Mth.sin(phase + 1.3F) * 0.1F * effort, 0.0F, 0.06F, amount);
		pose(m.leftLeg, 0.08F - Mth.sin(phase + 1.3F) * 0.1F * effort, 0.0F, -0.06F, amount);
		// head tipped back to look along the ground
		pose(m.head, -Mth.HALF_PI + 0.3F + reach * 0.05F * effort, 0.0F, 0.0F, amount);
	}

	/** On its back, so "forward" (negative x rotation) is up off the ground. */
	private static void lie(HumanoidModel<?> m, LivingEntityRenderState state, float amount) {
		float breath = Mth.sin(state.ageInTicks * 0.09F) * 0.04F;
		// right hand clutched to the chest, left arm flung out on the ground
		pose(m.rightArm, -1.25F + breath, 0.0F, -0.55F, amount);
		pose(m.leftArm, -0.15F, 0.0F, -1.2F - breath * 0.5F, amount);
		// right knee drawn up, left leg out straight and a little apart
		pose(m.rightLeg, -0.55F, 0.0F, 0.08F, amount);
		pose(m.leftLeg, 0.0F, 0.0F, -0.18F, amount);
		// head lolled to one side
		pose(m.head, 0.1F, 0.65F, 0.0F, amount);
	}

	private static void pose(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
