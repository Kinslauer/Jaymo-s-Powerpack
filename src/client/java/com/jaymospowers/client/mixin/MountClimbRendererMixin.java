package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player climbing onto or off a mount (MountClimbPose): read onto the render state, and the body carried along its path. */
@Mixin(AvatarRenderer.class)
abstract class MountClimbRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$climbState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		MountClimbPose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$climbPath(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		MountClimbPose.rotate(state, poseStack, bodyRot);
	}
}
