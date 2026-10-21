package com.jaymospowers.client.power.cryo.mixin;

import com.jaymospowers.client.power.cryo.CryoPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.42) Right after a model animates: a Cryokinesis user's pose for the move they're making (see CryoPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class CryoModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$cryoPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		CryoPose.applyModel(submit.model(), submit.state());
	}
}
