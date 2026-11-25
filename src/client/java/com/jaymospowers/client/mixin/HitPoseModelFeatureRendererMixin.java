package com.jaymospowers.client.mixin;

import com.jaymospowers.client.HitReactionsClient;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: the flinch of the part just struck and the stance of a hurt body (see HitReactionsClient). */
@Mixin(ModelFeatureRenderer.class)
abstract class HitPoseModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$hitPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		HitReactionsClient.applyModel(submit.model(), submit.state());
	}
}
