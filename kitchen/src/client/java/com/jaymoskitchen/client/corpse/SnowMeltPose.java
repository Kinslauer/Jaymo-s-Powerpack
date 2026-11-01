package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.SnowMelt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.5.41) A dying snow golem (see {@link SnowMelt}) is drawn slumping into itself: it sinks and spreads out at its
 * feet instead of tipping over red, until only a low mound of snow is left as it goes.
 */
public final class SnowMeltPose {
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:snow_melt");
	/** At the end of the melt: height kept, and how much wider it has spread. */
	static final float END_HEIGHT = 0.12F, END_SPREAD = 0.6F;

	private SnowMeltPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		if (state.deathTime > 0.0F && SnowMelt.melts(entity)) {
			state.setData(KEY, SnowMelt.progress(state.deathTime));
			// no vanilla tip-over, and it doesn't flash red as it melts
			state.deathTime = 0.0F;
			state.hasRedOverlay = entity.hurtTime > 0 && entity.deathTime <= 1;
		} else {
			state.setData(KEY, null);
		}
	}

	/** In {@code setupRotations}, at its feet: squash it down and out. */
	public static void squash(LivingEntityRenderState state, PoseStack poseStack) {
		Float melt = state.getData(KEY);
		if (melt == null) {
			return;
		}
		// eased, so it sags slowly at first and slumps fast at the end
		float p = melt * melt * (3.0F - 2.0F * melt);
		float height = Mth.lerp(p, 1.0F, END_HEIGHT);
		float spread = Mth.lerp(p, 1.0F, 1.0F + END_SPREAD);
		poseStack.scale(spread, height, spread);
	}
}
