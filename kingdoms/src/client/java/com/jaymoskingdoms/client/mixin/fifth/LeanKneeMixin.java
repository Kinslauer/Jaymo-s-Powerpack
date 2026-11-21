package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.client.LeanClient;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Just before a leaning player's model is drawn, the knee of the leg taking the lean is bent (LeanClient#bendKnee). */
@Mixin(ModelFeatureRenderer.class)
abstract class LeanKneeMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void jaymoskingdoms$leanKnee(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		LeanClient.bendKnee(submit.model(), submit.state());
	}
}
