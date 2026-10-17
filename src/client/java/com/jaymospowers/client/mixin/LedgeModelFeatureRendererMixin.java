package com.jaymospowers.client.mixin;

import com.jaymospowers.client.LedgePose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a ledge hanger's arms on the edge and dangling legs, or the climb (see LedgePose). */
@Mixin(ModelFeatureRenderer.class)
abstract class LedgeModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$ledgeLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		LedgePose.applyModel(submit.model(), submit.state());
	}
}
