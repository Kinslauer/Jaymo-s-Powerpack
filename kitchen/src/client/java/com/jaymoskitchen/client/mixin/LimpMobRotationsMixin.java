package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.ragdoll.LimpMobs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A mob lying limp (see LimpMobs) is placed onto its ragdoll instead of being turned by its renderer: wrapped around
 * the call rather than inside setupRotations, so a renderer's own override (a squid's tilt, a bat hanging) is left
 * out too. Players are placed by RagdollAvatarRendererMixin.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LimpMobRotationsMixin {
	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations("
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"))
	private void jaymoskitchen$limpPlace(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, PoseStack poseStack,
		float bodyRot, float scale, Operation<Void> original) {
		if (!LimpMobs.place(state, poseStack, scale)) {
			original.call(renderer, state, poseStack, bodyRot, scale);
		}
	}
}
