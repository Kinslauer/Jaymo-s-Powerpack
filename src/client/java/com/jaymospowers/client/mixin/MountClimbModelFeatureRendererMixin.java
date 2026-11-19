package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after a model animates to be drawn (the body, and each armour piece): the climb's limbs (MountClimbPose). The
 * render-layer pass (ModelPoses) alone didn't carry them to the final draw, which animates the model again.
 */
@Mixin(ModelFeatureRenderer.class)
abstract class MountClimbModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$climbLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		MountClimbPose.applyModel(submit.model(), submit.state());
	}
}
