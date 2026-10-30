package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.FangGauntletsClient;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates, as it is finally drawn: the Fang Gauntlets' guard (see FangGauntletsClient). */
@Mixin(ModelFeatureRenderer.class)
abstract class FangGauntletsModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$gauntletGuard(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		FangGauntletsClient.applyModel(submit.model(), submit.state());
	}
}
