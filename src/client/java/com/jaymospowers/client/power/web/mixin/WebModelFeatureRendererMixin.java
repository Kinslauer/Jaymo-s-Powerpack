package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebArms;
import com.jaymospowers.client.power.web.WebRagdoll;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates (body and armour alike), a web slinger's arms reach along their webs (see WebArms). */
@Mixin(ModelFeatureRenderer.class)
abstract class WebModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$webArms(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		WebArms.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebFeral.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.web.WebSuitGrowth.applyModel(submit.model(), submit.state());
		WebRagdoll.applyModel(submit.model(), submit.state());
		com.jaymospowers.client.power.chrono.ChronoFlowPose.applyModel(submit.model(), submit.state());
	}
}
