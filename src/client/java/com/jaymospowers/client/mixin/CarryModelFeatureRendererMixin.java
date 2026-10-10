package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a carried body's loose limbs, the carrier's arms, a thrown body's flop (see CarryPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class CarryModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$carryLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		CarryPose.applyModel(submit.model(), submit.state());
	}
}
