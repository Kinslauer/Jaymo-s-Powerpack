package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carrying (see CarryPose): a held body is moved into the hold, a thrown one tumbles. Late, so the carry wins. */
@Mixin(value = LivingEntityRenderer.class, priority = 2000)
abstract class CarryLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$carryState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		CarryPose.extract(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$carryHold(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CarryPose.rotate(state, poseStack, entityScale);
	}
}
