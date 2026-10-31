package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.DownedPose;
import com.jaymospowers.client.wounds.TrippedPose;
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

/**
 * (0.14.1) Someone downed is laid on the ground (see DownedPose): onto the render state, the pose stack and the model.
 * (0.18.71) And one tripped by a bolas (TrippedPose). (0.18.84) One out cold has its eyes shut (Kitchen's ClosedEyes).
 */
@Mixin(LivingEntityRenderer.class)
abstract class DownedLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$downedState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		DownedPose.extract(entity, state, partialTicks);
		TrippedPose.extract(entity, state, partialTicks);
		// (Motion patch 6) pets' own poses, wet coats, and the mob's id so Fresh Animations can step aside for our poses
		com.jaymospowers.client.pets.PetPoses.extract(entity, state, partialTicks);
		com.jaymospowers.client.pets.WetCoats.extract(entity, state, partialTicks);
		com.jaymospowers.client.PoseTakeover.markMob(entity, state);
		// (0.18.84) out cold (a rock to the head, too much poppy, bled out): eyes shut, as Kitchen shuts a corpse's
		if (com.jaymospowers.core.wounds.Downed.passedOut(entity)) {
			com.jaymoskitchen.client.corpse.ClosedEyes.mark(state);
		}
	}

	/** (0.15.142) Whatever it held its arms up for is let go before the model is posed (the armour's posing comes later, off the same state). */
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
	private void jaymospowers$downedCalm(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		DownedPose.calm(state);
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymospowers$downedModel(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		DownedPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		TrippedPose.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
		com.jaymospowers.client.pets.PetPoses.apply(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$downedLying(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		DownedPose.rotate(state, poseStack, entityScale);
		TrippedPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.pets.PetPoses.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.pets.WetCoats.rotate(state, poseStack);
	}
}
