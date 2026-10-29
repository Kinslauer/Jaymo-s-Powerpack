package com.jaymoskingdoms.client.mixin.bb;

import com.jaymoskingdoms.client.bb.BbBodies;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.326) A villager with a body of its own ({@link BbBodies}): that body is handed in in place of the renderer's
 * model, and none of the renderer's layers are drawn over it (MCA's clothes and hair, armour, held things, our looks).
 */
@Mixin(LivingEntityRenderer.class)
abstract class BbBodyLivingEntityRendererMixin {
	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
	private void jaymoskingdoms$body(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack poseStack, RenderType renderType, int light,
		int overlay, int color, UvMapping uv, int outline, Operation<Void> original) {
		BbBodies.BodyModel body = BbBodies.model(state);
		if (body == null) {
			original.call(collector, model, state, poseStack, renderType, light, overlay, color, uv, outline);
			return;
		}
		BbBodies.submit(body, collector, state, poseStack, renderType, light, overlay, color, uv, outline);
	}

	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noLayersOnBody(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (state.getData(BbBodies.KEY) != null) {
			cir.setReturnValue(false);
		}
	}
}
