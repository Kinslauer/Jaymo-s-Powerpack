package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after a model is posed to be drawn, after Jaymo's Powers' lost-limb hide (2000) and Kitchen's ragdoll (3000): a
 * villager's fitted limbs put back where their own limbs are gone (FrostTechLayer).
 */
@Mixin(value = ModelFeatureRenderer.class, priority = 4000)
abstract class FrostTechModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$fittedLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		com.jaymoskingdoms.client.FrostTechLayer.reshow(submit.model(), submit.state());
	}
}
