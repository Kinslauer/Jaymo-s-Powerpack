package com.jaymospowers.client.mixin;

import com.jaymospowers.client.LedgePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player on a ledge (see LedgePose): read onto the render state, squared to the wall; the sway or lean after the body turns. */
@Mixin(AvatarRenderer.class)
abstract class LedgeAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$ledgeState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		LedgePose.extract(entity, state, partialTicks);
		// (0.13.36) hanging from a bar (Bar Swing)
		com.jaymospowers.client.freerun.BarHangPose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$ledgeSway(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		LedgePose.rotate(state, poseStack);
		com.jaymospowers.client.freerun.BarHangPose.rotate(state, poseStack);
	}
}
