package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoSockets;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A held lasso's model transform is on the stack: its coil's top goes to LassoSockets. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
abstract class LassoSocketItemLayerMixin {
	@Inject(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$lassoSocket(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
		LassoSockets.capture(poseStack.last().pose());
	}
}
