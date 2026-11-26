package com.jaymoskingdoms.client.wild;

import com.jaymoskingdoms.wild.DeathRoll;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A crocodile or alligator's death roll ({@link DeathRoll}), drawn: the whole body turns over once along its length in
 * {@link DeathRoll#ROLL_EVERY} ticks from when each turn began ({@link DeathRoll#ROLLING}), about its middle, so it rolls
 * where it lies. Both Cube Animals' and Naturalist's renderers are Minecraft's own living renderer, so this turns either.
 */
public final class CrocRoll {
	/** This frame's roll, in degrees. */
	static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:croc_roll");

	private CrocRoll() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Long began = entity.getAttached(DeathRoll.ROLLING);
		if (began == null) {
			state.setData(KEY, null);
			return;
		}
		float t = (entity.level().getGameTime() - began + partialTicks) / DeathRoll.ROLL_EVERY;
		state.setData(KEY, t >= 1.0F || t < 0.0F ? null : Mth.clamp(t, 0.0F, 1.0F) * 360.0F);
	}

	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Float roll = state.getData(KEY);
		if (roll == null || state.deathTime > 0.0F) {
			return;
		}
		float half = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, half, 0.0F);
		poseStack.rotate(Axis.ZP.rotationDegrees(roll));
		poseStack.translate(0.0F, -half, 0.0F);
	}
}
