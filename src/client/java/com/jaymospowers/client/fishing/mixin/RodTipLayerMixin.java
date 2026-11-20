package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.RodTip;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fishing line starts at the rod tip: hands {@link RodTip} the rod layer's model matrix once its display transform is on. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
abstract class RodTipLayerMixin {
	@Shadow
	private Supplier<Vector3fc[]> extents;

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$rodTip(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
		if (RodTip.watching()) {
			RodTip.layer(poseStack.last().pose(), this.extents.get());
		}
	}
}
