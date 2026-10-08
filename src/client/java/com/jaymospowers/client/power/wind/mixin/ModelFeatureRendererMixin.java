package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates (body and armour alike), a cruising player's limbs take the flight pose. */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$windFlightLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		WindPose.applyModel(submit.model(), submit.state());
	}
}
