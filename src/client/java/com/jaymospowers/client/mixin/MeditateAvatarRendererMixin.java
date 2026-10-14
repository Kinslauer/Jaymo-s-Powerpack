package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MeditatePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Meditating or shadowboxing (see MeditatePose): read onto the render state; the body sunk to the ground or into the stance. */
@Mixin(AvatarRenderer.class)
abstract class MeditateAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$meditateState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		MeditatePose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$meditateSink(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		MeditatePose.rotate(state, poseStack);
	}
}
