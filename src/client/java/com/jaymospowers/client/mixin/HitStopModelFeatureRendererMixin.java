package com.jaymospowers.client.mixin;

import com.jaymospowers.client.HitStopClient;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates, and after our own poses have gone on it: a body in a hit-stop keeps its pose (see HitStopClient). */
@Mixin(value = ModelFeatureRenderer.class, priority = 2000)
abstract class HitStopModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$hitStop(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		HitStopClient.applyModel(submit.model(), submit.state());
	}
}
