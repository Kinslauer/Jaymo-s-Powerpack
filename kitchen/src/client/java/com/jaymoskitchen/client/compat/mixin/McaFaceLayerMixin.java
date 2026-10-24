package com.jaymoskitchen.client.compat.mixin;

import com.jaymoskitchen.client.corpse.ClosedEyes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.22) MCA draws a face (eyes and all) over its villagers and players, blinking on its own clock, so a corpse's
 * painted-shut eyes (ClosedEyes) never showed and dead players kept blinking. While MCA draws a corpse's face it is held
 * mid-blink: eyes shut ({@code McaVisualsMixin}). Andy (2026-10-24): "dead players still blink as if they were alive".
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.client.render.layer.FaceLayer", remap = false)
abstract class McaFaceLayerMixin {
	@Inject(method = "renderFinal(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FZZ)V",
		at = @At("HEAD"))
	private void jaymoskitchen$corpseFace(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float partialTicks,
		boolean a, boolean b, CallbackInfo ci) {
		ClosedEyes.drawingShut = ClosedEyes.closed(state);
	}

	@Inject(method = "renderFinal(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FZZ)V",
		at = @At("RETURN"))
	private void jaymoskitchen$corpseFaceDone(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float partialTicks,
		boolean a, boolean b, CallbackInfo ci) {
		ClosedEyes.drawingShut = false;
	}
}
