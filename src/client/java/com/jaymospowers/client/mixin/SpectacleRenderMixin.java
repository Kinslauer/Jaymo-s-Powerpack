package com.jaymospowers.client.mixin;

import com.jaymospowers.client.fx.effekseer.SpectacleClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class SpectacleRenderMixin {
	// World compositing has finished here. The hand pass will clear or replace the world's depth.
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
	private void jaymospowers$renderSpectacles(CallbackInfo ci, @Local CameraRenderState camera, @Local Matrix4f projection) {
		SpectacleClient.render(camera, projection, ((GameRenderer)(Object)this).mainRenderTarget());
	}
}

