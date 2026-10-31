package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebArms;
import com.jaymospowers.client.power.web.WebRagdoll;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leans a gliding web slinger forward into the flying pose (see WebArms), and ragdolls creatures held by a web. */
@Mixin(LivingEntityRenderer.class)
abstract class WebLivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$webGlideLean(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		// (Motion patch 1) the small motions first, so every pose after them stands on top
		com.jaymospowers.client.BodyMotion.rotate(state, poseStack, entityScale);
		WebArms.rotate(state, poseStack, entityScale);
		WebRagdoll.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.FreefallPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.freerun.BalancePose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.water.SwimStrokePose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.freerun.RunPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.freerun.UpRunPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebClimbPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebExecutionPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebBodyPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebCounterPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebSuitTear.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebWallRunPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebParkourPose.rotate(state, poseStack, entityScale);
	}

	/** Your own move in first person: First-person Model shows the body's posed arms (see WebFirstPerson). */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void jaymospowers$ownArms(LivingEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
		net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar
			&& com.jaymospowers.client.power.web.WebFirstPerson.posing(avatar)) {
			com.jaymospowers.client.power.web.FirstPersonCompat.showArms(avatar);
		}
	}

	/** Creatures on the end of a web go limp (see WebRagdoll). */
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$webRagdoll(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		WebRagdoll.extract(entity, state, partialTicks);
		com.jaymospowers.client.BodyMotion.extract(entity, state, partialTicks);
		com.jaymospowers.client.FreefallPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.freerun.BalancePose.extract(entity, state, partialTicks);
		com.jaymospowers.client.water.SwimStrokePose.extract(entity, state, partialTicks);
		com.jaymospowers.client.freerun.RunPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.freerun.UpRunPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebHangPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebClimbPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebHeldGrabPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebExecutionPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebBodyPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebCounterPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebWallRunPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebParkourPose.extract(entity, state, partialTicks);
	}
}
