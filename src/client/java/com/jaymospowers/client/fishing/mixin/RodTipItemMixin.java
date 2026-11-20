package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.RodTip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fishing line starts at the rod tip: tells {@link RodTip} which item is being drawn, so it can watch for a rod. */
@Mixin(ItemStackRenderState.class)
abstract class RodTipItemMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void jaymospowers$rodTipBegin(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
		RodTip.submitting((ItemStackRenderState) (Object) this);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void jaymospowers$rodTipEnd(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
		RodTip.submitted();
	}
}
