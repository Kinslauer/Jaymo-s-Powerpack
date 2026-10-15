package com.jaymospowers.client.mixin;

import com.jaymospowers.client.costume.CostumeBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.11.78) Just before the render layers, when Entity Model Features has animated the body for them: its pose is kept
 * for the costume drawn over it (CostumeBody), which otherwise stayed in the vanilla pose under Fresh Animations.
 */
@Mixin(LivingEntityRenderer.class)
abstract class CostumePoseLivingEntityRendererMixin {
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
	private void jaymospowers$keepCostumeBody(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		CostumeBody.keep(state, this.model);
	}
}
