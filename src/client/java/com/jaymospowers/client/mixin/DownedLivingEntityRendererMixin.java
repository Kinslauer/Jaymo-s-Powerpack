package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.DownedPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.14.1) Someone downed is laid on the ground (see DownedPose): onto the render state, the pose stack and the model. */
@Mixin(LivingEntityRenderer.class)
abstract class DownedLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$downedState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		DownedPose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymospowers$downedModel(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		DownedPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$downedLying(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		DownedPose.rotate(state, poseStack, entityScale);
	}
}
