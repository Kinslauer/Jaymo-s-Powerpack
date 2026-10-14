package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.BloodVision;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blood vision's outline beats red (a creature glowing for any other reason keeps its own colour). */
@Mixin(LivingEntityRenderer.class)
abstract class BloodGlowColorMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$bloodColor(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.outlineColor != 0 && !entity.isCurrentlyGlowing() && BloodVision.sensed(entity)) {
			state.outlineColor = BloodVision.color(partialTicks);
		}
	}
}
