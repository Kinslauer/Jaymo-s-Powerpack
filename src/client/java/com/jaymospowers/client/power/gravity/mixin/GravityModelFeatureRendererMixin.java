package com.jaymospowers.client.power.gravity.mixin;

import com.jaymospowers.client.power.gravity.GravityArmPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a gravity user's arm reaches out toward what they hold (see GravityArmPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class GravityModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$gravityReach(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		GravityArmPose.applyModel(submit.model(), submit.state());
	}
}
