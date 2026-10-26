package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.TendPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.99) Kneeling to treat someone (see TendPose): read onto the render state; the body sunk onto the knee and leaning in. */
@Mixin(AvatarRenderer.class)
abstract class TendAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$tendState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		TendPose.extract(entity, state, partialTicks);
		// (0.17.7) and the crouch: low, arms out, onto one knee when still
		com.jaymospowers.client.CrouchPose.extract(entity, state, partialTicks);
		// (0.16.21) a hand to a wound now and then
		com.jaymospowers.client.wounds.WoundNursePose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$tendKneel(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		TendPose.rotate(state, poseStack);
		com.jaymospowers.client.CrouchPose.rotate(state, poseStack);
	}
}
