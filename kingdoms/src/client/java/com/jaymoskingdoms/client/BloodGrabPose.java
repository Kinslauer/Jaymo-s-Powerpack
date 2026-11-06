package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BloodGrab;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.615) The grab-drink ({@link BloodGrab}): a vampire holding their prey up by the throat in one hand, the main arm
 * out in front and raised a little, leaning back against the weight, the hand shaking as they drink. Laid over the
 * player's model right after it's posed, like {@link BioAbsorbPose}.
 */
public final class BloodGrabPose {
	/** How long the arm takes to come up (ticks), how far above level it's raised (radians), the lean back, the shake. */
	static final float REACH_TICKS = 4.0F, RAISE = 0.3F, LEAN = 0.08F, SHAKE = 0.035F;
	/** The arm turns this far in toward the middle (radians), so the hand is before the chest. */
	static final float INWARD = 0.2F;

	/** This frame's {weight, age} for a vampire holding someone. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:blood_grab");

	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BloodGrabPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Avatar)) {
			return;
		}
		if (!entity.hasAttached(BloodGrab.GRABBING)) {
			STARTED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		float started = STARTED.computeIfAbsent(entity.getId(), k -> state.ageInTicks);
		float w = Mth.clamp((state.ageInTicks - started) / REACH_TICKS, 0.0F, 1.0F);
		state.setData(KEY, new float[] {w * w * (3.0F - 2.0F * w), state.ageInTicks});
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean left = state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT;
		ModelPart arm = left ? m.leftArm : m.rightArm;
		float w = pose[0];
		float shake = Mth.sin(pose[1] * 2.1F) * SHAKE;
		arm.xRot = Mth.lerp(w, arm.xRot, (float)(-Math.PI / 2.0) - RAISE + shake);
		arm.yRot = Mth.lerp(w, arm.yRot, left ? INWARD : -INWARD);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
		m.body.xRot = Mth.lerp(w, m.body.xRot, -LEAN);
	}
}
