package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.Lean;
import com.jaymoskingdoms.client.LeanClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every player who leans ({@link Lean}) is drawn with their hips slid out and the body above them tilted (LeanClient). */
@Mixin(AvatarRenderer.class)
abstract class AvatarLeanMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$leanState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		LeanClient.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$leanTilt(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		LeanClient.rotate(state, poseStack);
	}
}
