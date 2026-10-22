package com.jaymospowers.client.mixin;

import com.jaymospowers.client.finisher.FinisherPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.98) Right after a model animates: a finisher's arms, legs and head (see FinisherPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class FinisherModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$finisherLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		FinisherPose.applyModel(submit.model(), submit.state());
	}
}
