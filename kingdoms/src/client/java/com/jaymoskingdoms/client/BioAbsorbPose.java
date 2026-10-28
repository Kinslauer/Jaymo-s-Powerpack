package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBuilt;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.288, Andy: "it should animate with some part of the body touching the enemy") A Bio-built absorbing a foe
 * ({@link BioBuilt#ABSORBING}) reaches their main hand out and down onto it, leaning in, the hand trembling with each
 * pulse. Laid over the player's model right after it's posed, like {@link PickpocketPose}.
 */
public final class BioAbsorbPose {
	/** How long the hand takes to go out (ticks), how far the body leans in (radians), and the tremble (radians). */
	static final float REACH_TICKS = 6.0F, LEAN = 0.15F, TREMBLE = 0.04F;
	/** The arm turns at most this far to either side (radians). */
	static final float TURN_MAX = 1.1F;

	/** This frame's {weight, down, turn, age} for a player absorbing, on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:bio_absorb");

	/** Entity id → the age (ticks) their reach began. */
	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BioAbsorbPose() {
	}

	/** Render-state extraction: an absorbing player's reach for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Avatar)) {
			return;
		}
		Integer id = entity.getAttached(BioBuilt.ABSORBING);
		Entity foe = id == null ? null : entity.level().getEntity(id);
		if (foe == null) {
			STARTED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		float started = STARTED.computeIfAbsent(entity.getId(), k -> state.ageInTicks);
		float weight = Mth.clamp((state.ageInTicks - started) / REACH_TICKS, 0.0F, 1.0F);
		double dx = Mth.lerp(partialTicks, foe.xo, foe.getX()) - Mth.lerp(partialTicks, entity.xo, entity.getX());
		double dz = Mth.lerp(partialTicks, foe.zo, foe.getZ()) - Mth.lerp(partialTicks, entity.zo, entity.getZ());
		double dy = Mth.lerp(partialTicks, foe.yo, foe.getY()) + foe.getBbHeight() * 0.5
			- (Mth.lerp(partialTicks, entity.yo, entity.getY()) + entity.getBbHeight() * 0.75);
		float down = (float)Mth.atan2(-dy, Math.sqrt(dx * dx + dz * dz));
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float turn = Mth.clamp(Mth.wrapDegrees(yaw - state.bodyRot) * Mth.DEG_TO_RAD, -TURN_MAX, TURN_MAX);
		state.setData(KEY, new float[] {weight * weight * (3.0F - 2.0F * weight), down, turn, state.ageInTicks});
	}

	/** Right after a model was posed for this state: the hand out on the foe. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean left = state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT;
		ModelPart reach = left ? m.leftArm : m.rightArm;
		float w = pose[0];
		// straight out along the line to the foe (forward is -pi/2, straight down 0), trembling as the pulses run
		float tremble = Mth.sin(pose[3] * 1.3F) * TREMBLE;
		reach.xRot = Mth.lerp(w, reach.xRot, (float)(-Math.PI / 2.0) + pose[1] + tremble);
		reach.yRot = Mth.lerp(w, reach.yRot, pose[2]);
		reach.zRot = Mth.lerp(w, reach.zRot, 0.0F);
		m.body.xRot += LEAN * w * Mth.clamp(pose[1], 0.0F, 1.0F);
	}
}
