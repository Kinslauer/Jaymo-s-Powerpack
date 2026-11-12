package com.jaymoskingdoms.client.mixin.thirst;

import com.jaymoskingdoms.client.thirst.ThirstClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The thirsty bat's outline is red (a creature glowing for any other reason keeps its own colour). */
@Mixin(LivingEntityRenderer.class)
abstract class ThirstGlowColorMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$thirstColor(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.outlineColor != 0 && !entity.isCurrentlyGlowing() && ThirstClient.sensed(entity)) {
			state.outlineColor = ThirstClient.RED;
		}
	}
}
