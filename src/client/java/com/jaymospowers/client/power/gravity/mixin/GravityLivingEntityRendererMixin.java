package com.jaymospowers.client.power.gravity.mixin;

import com.jaymospowers.client.power.gravity.GravityBoardPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An earthboard rider leans into turns and tips into the speed (see GravityBoardPose). */
@Mixin(LivingEntityRenderer.class)
abstract class GravityLivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$boardLean(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		GravityBoardPose.rotate(state, poseStack);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$boardRide(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		GravityBoardPose.extract(entity, state, partialTicks);
	}
}
