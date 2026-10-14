package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ScreenShake;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The screen shakes with the hurt bob (see ScreenShake). */
@Mixin(GameRenderer.class)
abstract class ScreenShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void jaymospowers$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		ScreenShake.apply(cameraState, poseStack);
		com.jaymospowers.client.FlightRolls.applyCamera(poseStack, net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}
}
