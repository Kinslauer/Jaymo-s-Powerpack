package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.HungerPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The curse of the hungry on a player's body: sprinting at night they drop onto all fours (see {@link HungerPose}). */
@Mixin(AvatarRenderer.class)
abstract class HungerPoseRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$hungerState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		HungerPose.extract(entity, state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymoskingdoms$hungerRotate(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		HungerPose.rotate(state, poseStack);
	}
}
