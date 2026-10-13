package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlPose;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after a model animates: a gesture crawler's elbow crawl or roll in place of the swimming strokes (see CrawlPose). */
@Mixin(ModelFeatureRenderer.class)
abstract class CrawlModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$crawlLimbs(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		CrawlPose.applyModel(submit.model(), submit.state());
	}
}
