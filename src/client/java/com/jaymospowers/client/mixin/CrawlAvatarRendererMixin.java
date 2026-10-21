package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A gesture crawl (see CrawlPose): read onto the render state; a roll turned after the swimming tilt. (0.15.32) And the
 * freerun slide's state, finished here after the player's own fields (see RunPose).
 */
@Mixin(AvatarRenderer.class)
abstract class CrawlAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$crawlState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		CrawlPose.extract(entity, state, partialTicks);
		// (0.15.32) the freerun slide's own lie-back in place of the swimming tilt (see RunPose)
		com.jaymospowers.client.freerun.RunPose.extractAvatar(entity, state, partialTicks);
	}

	@Inject(
		method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void jaymospowers$crawlRoll(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CrawlPose.rotate(state, poseStack);
	}
}
