package com.jaymospowers.client.mixin;

import com.jaymospowers.client.Dismembered;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lost parts hidden right after a model animates (last, after every pose), shown again once it's drawn (see Dismembered). */
@Mixin(value = ModelFeatureRenderer.class, priority = 2000)
abstract class DismemberModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$hideLost(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		Dismembered.hide(submit.model(), submit.state());
		// Vapour Form's legs are smoke: hidden the same way
		com.jaymospowers.client.power.smoke.SmokeFlightPose.hide(submit.model(), submit.state());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"))
	private void jaymospowers$showLost(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		Dismembered.restore();
	}
}
