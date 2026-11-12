package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.HunterPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a tail sweep turns the body round (HunterPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class XenoformHunterPoseMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$tailSweep(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		HunterPose.applyModel(submit.model(), submit.state());
	}
}
