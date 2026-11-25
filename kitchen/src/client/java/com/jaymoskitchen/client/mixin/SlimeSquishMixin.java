package com.jaymoskitchen.client.mixin;

import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A slime that has been hit squishes and wobbles: its jelly stretch is pushed about for the length of its hurt flash. */
@Mixin(AbstractCubeMobRenderer.class)
public class SlimeSquishMixin {
	/** How far a fresh blow squashes it (the renderer's own squish units), and how many wobbles the hurt time holds. */
	@Unique
	private static final float WOBBLE = 0.6F, WOBBLES = 1.6F;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/cubemob/AbstractCubeMob;Lnet/minecraft/client/renderer/entity/state/SlimeRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$wobble(AbstractCubeMob mob, SlimeRenderState state, float partialTicks, CallbackInfo ci) {
		if (mob.hurtTime > 0 && mob.hurtDuration > 0) {
			float left = (mob.hurtTime - partialTicks) / mob.hurtDuration;
			state.squish += Mth.sin(left * WOBBLES * Mth.TWO_PI) * left * WOBBLE;
		}
	}
}
