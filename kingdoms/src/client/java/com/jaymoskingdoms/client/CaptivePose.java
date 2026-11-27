package com.jaymoskingdoms.client;

import com.jaymoskingdoms.CaptiveIdle;
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
 * What a captive in chains is doing ({@link CaptiveIdle#DOING}), drawn like {@link FiresidePose}: the whole body brought
 * down to sit or kneel ({@link #rotate}, after {@code setupRotations}), then the limbs laid over the pose:
 * <ul>
 * <li>{@link CaptiveIdle#HUG}: sat with the knees drawn up, arms round them, head down on them, rocking.</li>
 * <li>{@link CaptiveIdle#SLUMP}: sat with the legs out, the bound hands behind, hunched, head hung.</li>
 * <li>{@link CaptiveIdle#KNEEL}: up on the knees, the bound hands held up together, face lifted (pleading).</li>
 * <li>{@link CaptiveIdle#STRAIN}: kneeling, the hands behind, every so often wrenching at the bonds: the shoulders twist,
 * the arms pull apart, the head goes back.</li>
 * <li>{@link CaptiveIdle#PICK}: sat with the legs out, the hands together in the lap, fingers working at the knot.</li>
 * <li>{@link CaptiveIdle#DIG}: kneeling, bent forward, one hand braced and the other scraping at the ground.</li>
 * </ul>
 * A kneel lowers the body by half a leg, so the shins are in the ground and only the thighs stand. Arm turns are the
 * model's (radians): x forward and up from hanging (negative), y in across the chest (right arm: negative), z out to
 * the side (right arm: positive).
 */
public final class CaptivePose {
	/** This frame's {doing, age in ticks} for a captive, on its render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:captive_pose");
	/** How far the body comes down to sit on the ground, and to kneel (blocks, at scale 1). */
	static final float SIT_DROP = 0.62F, KNEEL_DROP = 0.38F;
	/** Legs out in front, sitting; drawn up, hugged. */
	static final float LEGS_OUT = -1.45F, LEGS_UP = -2.0F;

	private CaptivePose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer doing = entity.getAttached(CaptiveIdle.DOING);
		if (doing != null && doing != CaptiveIdle.STAND && !entity.isPassenger()) {
			state.setData(KEY, new float[] {doing, entity.tickCount + partialTicks + (entity.getId() % 37)});
		}
	}

	/** (after {@code setupRotations}) Down onto the ground, leaning as they sit. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		float[] data = state.getData(KEY);
		if (data == null) {
			return;
		}
		float age = data[1];
		int doing = (int)data[0];
		boolean sits = doing == CaptiveIdle.HUG || doing == CaptiveIdle.SLUMP || doing == CaptiveIdle.PICK;
		poseStack.translate(0.0F, -(sits ? SIT_DROP : KNEEL_DROP), 0.0F);
		// (negative about x: the top of the body forward)
		float lean = switch (doing) {
			case CaptiveIdle.HUG -> -10.0F + Mth.sin(age * 0.09F) * 5.0F;
			case CaptiveIdle.SLUMP -> -14.0F + Mth.sin(age * 0.05F) * 1.5F;
			case CaptiveIdle.PICK -> -8.0F;
			case CaptiveIdle.DIG -> -26.0F + Mth.sin(age * 0.45F) * 3.0F;
			default -> 0.0F;
		};
		if (lean != 0.0F) {
			float hips = (sits ? 0.75F - SIT_DROP : 0.75F - KNEEL_DROP) + 0.125F;
			poseStack.translate(0.0F, hips, 0.0F);
			poseStack.rotate(Axis.XP.rotationDegrees(lean));
			poseStack.translate(0.0F, -hips, 0.0F);
		}
		if (doing == CaptiveIdle.STRAIN) {
			// a wrench of the shoulders, one way and then the other
			poseStack.rotate(Axis.YP.rotationDegrees(jerk(age) * 9.0F * (Mth.sin(age * 0.07F) >= 0.0F ? 1.0F : -1.0F)));
		}
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] data = living.getData(KEY);
		if (data == null) {
			return;
		}
		float age = data[1];
		switch ((int)data[0]) {
			case CaptiveIdle.HUG -> {
				set(m.rightLeg, LEGS_UP, 0.0F, 0.05F);
				set(m.leftLeg, LEGS_UP, 0.0F, -0.05F);
				set(m.rightArm, -1.35F, -0.55F, 0.0F);
				set(m.leftArm, -1.35F, 0.55F, 0.0F);
				m.head.xRot = 0.55F;
				m.head.yRot *= 0.2F;
			}
			case CaptiveIdle.SLUMP -> {
				set(m.rightLeg, LEGS_OUT, 0.18F, 0.06F);
				set(m.leftLeg, LEGS_OUT, -0.18F, -0.06F);
				behind(m, 0.0F);
				m.head.xRot = 0.8F + Mth.sin(age * 0.05F) * 0.05F;
				m.head.yRot *= 0.2F;
			}
			case CaptiveIdle.KNEEL -> {
				set(m.rightLeg, 0.08F, 0.0F, 0.04F);
				set(m.leftLeg, 0.08F, 0.0F, -0.04F);
				// the bound hands held up together, shaken as they beg
				float beg = Mth.sin(age * 0.28F) * 0.1F;
				set(m.rightArm, -1.05F + beg, -0.45F, 0.0F);
				set(m.leftArm, -1.05F + beg, 0.45F, 0.0F);
				m.head.xRot = Math.min(m.head.xRot, -0.15F);
			}
			case CaptiveIdle.STRAIN -> {
				set(m.rightLeg, 0.08F, 0.0F, 0.04F);
				set(m.leftLeg, 0.08F, 0.0F, -0.04F);
				float pull = jerk(age);
				behind(m, pull);
				m.head.xRot = 0.25F - pull * 0.55F;
			}
			case CaptiveIdle.PICK -> {
				set(m.rightLeg, LEGS_OUT, 0.08F, 0.0F);
				set(m.leftLeg, LEGS_OUT, -0.08F, 0.0F);
				// wrist to wrist in the lap, the fingers of one and then the other at the knot
				float work = Mth.sin(age * 0.9F) * 0.07F;
				set(m.rightArm, -0.75F + work, -0.5F, 0.0F);
				set(m.leftArm, -0.75F - work, 0.5F, 0.0F);
				m.head.xRot = 0.65F;
				m.head.yRot *= 0.2F;
			}
			case CaptiveIdle.DIG -> {
				set(m.rightLeg, 0.08F, 0.0F, 0.04F);
				set(m.leftLeg, 0.08F, 0.0F, -0.04F);
				// the left hand braced on the ground, the right raking the earth toward them
				float stroke = Mth.sin(age * 0.45F);
				set(m.leftArm, -0.55F, 0.1F, -0.15F);
				set(m.rightArm, -0.75F + stroke * 0.35F, -0.15F + stroke * 0.2F, 0.3F);
				m.head.xRot = 0.5F;
				m.head.yRot *= 0.3F;
			}
			default -> {
			}
		}
	}

	/** The bound hands behind the back; {@code pull} (0 to 1) wrenching them apart and back. */
	private static void behind(HumanoidModel<?> m, float pull) {
		set(m.rightArm, 0.55F + pull * 0.3F, 0.0F, -0.15F + pull * 0.35F);
		set(m.leftArm, 0.55F + pull * 0.3F, 0.0F, 0.15F - pull * 0.35F);
	}

	/** A sharp wrench at the bonds about every two seconds (0 at rest, 1 at the pull). */
	private static float jerk(float age) {
		float s = Math.max(0.0F, Mth.sin(age * 0.16F));
		return s * s * s * s;
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
