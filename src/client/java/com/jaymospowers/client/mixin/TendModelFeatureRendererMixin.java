package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.TendPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.99) Right after a model animates: a healer on one knee, hands at the wound (see TendPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class TendModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$tendLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		TendPose.applyModel(submit.model(), submit.state());
	}
}
