package com.jaymospowers.client.mixin;

import com.jaymospowers.client.stamina.DodgeRollPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a dodging player's tuck (see DodgeRollPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class DodgeModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$dodgeTuck(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		DodgeRollPose.applyModel(submit.model(), submit.state());
	}
}
