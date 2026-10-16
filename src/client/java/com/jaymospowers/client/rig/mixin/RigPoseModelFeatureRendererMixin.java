package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.11.107) Right after a model animates (the body and the suit over it alike): the Rig pilot's pose (see RigPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class RigPoseModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$rigPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		RigPose.applyModel(submit.model(), submit.state());
	}
}
