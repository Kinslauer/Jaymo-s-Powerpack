package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.Lean;
import com.jaymoskingdoms.client.LeanClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Everyone who leans ({@link Lean}), players and NPC shooters alike, is drawn with their hips slid out and the body above them
 * tilted (LeanClient).
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingLeanMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$leanState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		LeanClient.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$leanTilt(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		LeanClient.rotate(state, poseStack);
	}
}
