package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.RodTip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The fishing line starts at the rod tip: before your hands are drawn, marks the rod hand and keeps the hands' base pose.
 * Runs ahead of the mixins that draw the hands their own way (priority), so their rod is watched too.
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 500)
abstract class RodTipHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void jaymospowers$rodTip(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		RodTip.hands(state, poseStack.last().pose());
	}
}
