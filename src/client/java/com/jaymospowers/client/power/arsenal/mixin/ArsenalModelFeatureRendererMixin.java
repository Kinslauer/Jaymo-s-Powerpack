package com.jaymospowers.client.power.arsenal.mixin;

import com.jaymospowers.client.power.arsenal.ArsenalArmPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates (body and armour alike), the weapon arm reaches into the portal (see ArsenalArmPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class ArsenalModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$arsenalReachArm(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		ArsenalArmPose.applyModel(submit.model(), submit.state());
	}
}
