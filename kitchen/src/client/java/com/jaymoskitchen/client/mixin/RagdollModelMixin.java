package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.ragdoll.RagdollPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.1) A ragdoll's limbs, set right after the model animates and after every other pose (this mixin's priority is
 * above Jaymo's Powers' 2000): a limp or dead body goes where its physics put it, whatever else it was doing.
 */
@Mixin(value = ModelFeatureRenderer.class, priority = 3000)
abstract class RagdollModelMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymoskitchen$ragdoll(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		RagdollPose.apply(submit.model(), submit.state());
	}
}
