package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.CalamitiesClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.31) An earthquake rattles the view ({@link CalamitiesClient#shake}), applied with the hurt bob. */
@Mixin(GameRenderer.class)
abstract class CalamityShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void jaymoskingdoms$quake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		CalamitiesClient.shake(poseStack, cameraState.cameraEntityPartialTicks);
	}
}
