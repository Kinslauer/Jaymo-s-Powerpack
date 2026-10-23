package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.McaSkinCover;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.177) Just before MCA's bare body is drawn, the parts its clothes cover go (McaSkinCover); shown again once drawn. */
@Mixin(ModelFeatureRenderer.class)
abstract class McaSkinCoverDrawMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void jaymoskingdoms$coverSkin(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		McaSkinCover.beforeDraw(submit.model(), submit.state());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"))
	private void jaymoskingdoms$uncoverSkin(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		McaSkinCover.afterDraw();
	}
}
