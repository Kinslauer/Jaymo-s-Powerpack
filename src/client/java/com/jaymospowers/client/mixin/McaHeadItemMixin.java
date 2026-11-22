package com.jaymospowers.client.mixin;

import com.jaymospowers.client.McaPlayerHeads;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's head as an item (in a hand, a frame, on a trophy stand): drawn as the villager MCA draws that player as (McaPlayerHeads). */
@Mixin(PlayerHeadSpecialRenderer.class)
abstract class McaHeadItemMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$mcaHead(PlayerSkinRenderCache.@Nullable RenderInfo argument, PoseStack poseStack, SubmitNodeCollector collector,
		int light, int overlay, boolean foil, int outline, CallbackInfo ci) {
		if (argument != null && McaPlayerHeads.submit(argument.gameProfile().id(), poseStack, collector, light, outline)) {
			ci.cancel();
		}
	}
}
