package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoReelPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creatures reel from Time Flow blows (see ChronoReelPose): the reel is read, and the body jolted. */
@Mixin(LivingEntityRenderer.class)
abstract class ChronoLivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$chronoReelTip(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		ChronoReelPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.chrono.ChronoFlowPose.rotate(state, poseStack);
		com.jaymospowers.client.power.chrono.ChronoRunPose.rotate(state, poseStack);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$chronoReel(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		ChronoReelPose.extract(entity, state, partialTicks);
	}
}
