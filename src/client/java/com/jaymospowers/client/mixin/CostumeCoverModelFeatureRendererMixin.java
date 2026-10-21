package com.jaymospowers.client.mixin;

import com.jaymospowers.client.costume.CostumeCover;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.15.32) Just before a model is drawn, after every pose has been laid on it: the skin a costume covers isn't drawn
 * (CostumeCover), and a costume over an MCA body drops its root's pose as MCA's layers do. Shown
 * again once drawn.
 */
@Mixin(ModelFeatureRenderer.class)
abstract class CostumeCoverModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void jaymospowers$coverSkin(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		CostumeCover.beforeDraw(submit.model(), submit.state(), submit.tintedColor());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"))
	private void jaymospowers$uncoverSkin(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		CostumeCover.afterDraw();
	}
}
