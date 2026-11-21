package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A villager, guard or carter climbing onto or off a mount, or into a cart or boat (SeatSteps, MountClimbPose): read onto
 * the render state, and the body carried along its path. Players have MountClimbRendererMixin.
 */
@Mixin(LivingEntityRenderer.class)
abstract class MountClimbMobRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$mobClimbState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		MountClimbPose.extractMob(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$mobClimbPath(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState)) {
			MountClimbPose.rotate(state, poseStack, bodyRot);
		}
	}
}
