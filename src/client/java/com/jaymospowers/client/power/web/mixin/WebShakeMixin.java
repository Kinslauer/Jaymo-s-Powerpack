package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebShake;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The screen shakes with the hurt bob (see WebShake). */
@Mixin(GameRenderer.class)
abstract class WebShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void jaymospowers$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		WebShake.apply(poseStack);
	}
}
