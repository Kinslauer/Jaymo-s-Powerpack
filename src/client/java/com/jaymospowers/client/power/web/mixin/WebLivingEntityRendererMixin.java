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
		WebArms.rotate(state, poseStack, entityScale);
		WebRagdoll.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.FreefallPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebBodyPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebWallRunPose.rotate(state, poseStack, entityScale);
		com.jaymospowers.client.power.web.WebParkourPose.rotate(state, poseStack, entityScale);
	}

	/** Creatures on the end of a web go limp (see WebRagdoll). */
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$webRagdoll(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		WebRagdoll.extract(entity, state, partialTicks);
		com.jaymospowers.client.FreefallPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebBodyPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebWallRunPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.web.WebParkourPose.extract(entity, state, partialTicks);
	}
}
