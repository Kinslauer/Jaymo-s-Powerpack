package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MeditatePose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a meditator sits, a Z Fighter shadowboxes (see MeditatePose). */
@Mixin(ModelFeatureRenderer.class)
abstract class MeditateModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$meditateLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		MeditatePose.applyModel(submit.model(), submit.state());
	}
}
