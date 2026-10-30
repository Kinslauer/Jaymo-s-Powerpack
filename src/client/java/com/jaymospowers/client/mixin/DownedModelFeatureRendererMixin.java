package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.DownedPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.14.1) Right after a model is posed to be drawn (the body, and each armour piece): a downed person's limbs (DownedPose), (0.18.69) a tripped one's (TrippedPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class DownedModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$downed(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		DownedPose.apply(submit.model(), submit.state());
		com.jaymospowers.client.wounds.TrippedPose.apply(submit.model(), submit.state());
	}
}
