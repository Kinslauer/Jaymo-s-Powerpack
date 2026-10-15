package com.jaymospowers.client.power.smoke.mixin;

import com.jaymospowers.client.power.smoke.SmokeMovePose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a Smoke user's pose for the move they're making (see SmokeMovePose). */
@Mixin(ModelFeatureRenderer.class)
abstract class SmokeModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$smokeMove(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		SmokeMovePose.applyModel(submit.model(), submit.state());
	}
}
