package com.jaymospowers.client.mixin;

import com.jaymospowers.client.combat.ArmedPose;
import com.jaymospowers.client.combat.ParryPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a weapon's guard on its line, else the armed stance (see ParryPose, ArmedPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class ArmedModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$armedPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		ParryPose.applyModel(submit.model(), submit.state());
		ArmedPose.applyModel(submit.model(), submit.state());
	}
}
