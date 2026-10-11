package com.jaymospowers.client.power.darktide.mixin;

import com.jaymospowers.client.power.darktide.DarktidePose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates, Darktide's pose goes on top (see DarktidePose). */
@Mixin(ModelFeatureRenderer.class)
abstract class DarktideModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$darktidePose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		DarktidePose.applyModel(submit.model(), submit.state());
	}
}
