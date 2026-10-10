package com.jaymospowers.client.power.arsenal.mixin;

import com.jaymospowers.client.power.arsenal.ArsenalArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The layers (held items among them) take the arm from the model as it's animated here, before the body is drawn:
 * the flintlock aim goes on now too, so the gun follows the raised arm (see ArsenalArmPose).
 */
@Mixin(LivingEntityRenderer.class)
abstract class ArsenalLivingEntityRendererMixin {
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
	private void jaymospowers$flintlockAim(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		ArsenalArmPose.applyAim(((LivingEntityRenderer<?, ?, ?>)(Object)this).getModel(), state);
	}
}
