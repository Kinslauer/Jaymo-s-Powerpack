package com.jaymospowers.client.mixin;

import com.jaymospowers.client.TnmFollow;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.16.16) Just before a model is drawn, after every pose: armour, Armory pieces and costumes take Touch'n Motion's pose. */
@Mixin(value = ModelFeatureRenderer.class, priority = 900)
abstract class TnmFollowModelFeatureRendererMixin {
	@Inject(method = "prepareModel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
	private void jaymospowers$followTnm(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		TnmFollow.beforeDraw(submit.model(), submit.state());
	}
}
