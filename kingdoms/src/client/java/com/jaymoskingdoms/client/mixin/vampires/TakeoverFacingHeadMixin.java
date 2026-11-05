package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.TakeoverFacing;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (2026-11-05) A driven player's body faces the way they go (TakeoverFacingMixin): the head turns after the camera only so far. */
@Mixin(LivingEntityRenderer.class)
abstract class TakeoverFacingHeadMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$takeoverHead(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof Player player && com.jaymoskingdoms.client.SeizureClient.drivenBody(player)) {
			state.yRot = TakeoverFacing.head(state.yRot);
		}
	}
}
