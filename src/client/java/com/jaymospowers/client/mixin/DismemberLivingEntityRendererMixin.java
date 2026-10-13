package com.jaymospowers.client.mixin;

import com.jaymospowers.client.Dismembered;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Which parts a body has lost, onto its render state (see Dismembered). */
@Mixin(LivingEntityRenderer.class)
abstract class DismemberLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$lostParts(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		Dismembered.extract(entity, state);
	}
}
