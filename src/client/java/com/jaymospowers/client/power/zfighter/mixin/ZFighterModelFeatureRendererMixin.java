package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a humanoid model animates: a flying Z Fighter's arms and legs (ZFighterFlightPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class ZFighterModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$zfighterFlight(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		ZFighterPowerClient.applyModel(submit.model(), submit.state());
	}
}
