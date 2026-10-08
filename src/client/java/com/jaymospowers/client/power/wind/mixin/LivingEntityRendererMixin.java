package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tips a cruising player's body into the flight direction (see WindPose). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$windFlightPose(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		WindPose.rotate(state, poseStack, entityScale);
	}
}
