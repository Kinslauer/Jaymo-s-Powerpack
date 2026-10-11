package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoReelPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates, a creature reeling from a Time Flow blow flinches (see ChronoReelPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class ChronoModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$chronoReel(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		ChronoReelPose.applyModel(submit.model(), submit.state());
	}
}
