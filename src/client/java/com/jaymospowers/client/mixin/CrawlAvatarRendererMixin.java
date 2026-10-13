package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlRollPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crawling player's sideways roll (see CrawlRollPose): read onto the render state, turned after the swimming tilt. */
@Mixin(AvatarRenderer.class)
abstract class CrawlAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$crawlRollState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		CrawlRollPose.extract(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$crawlRoll(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CrawlRollPose.rotate(state, poseStack);
	}
}
