package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a humanoid model animates: Wisp's ghostly glide (XenoformFlightPose), then the slam of the band (XenoformSlap). */
@Mixin(ModelFeatureRenderer.class)
abstract class XenoformModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$xenoformSlap(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		XenoformFlightPose.applyModel(submit.model(), submit.state());
		XenoformSlap.applyModel(submit.model(), submit.state());
	}
}
