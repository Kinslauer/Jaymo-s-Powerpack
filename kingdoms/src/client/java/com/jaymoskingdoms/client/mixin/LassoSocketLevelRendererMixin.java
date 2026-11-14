package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoSockets;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The level's entities are being submitted: a held lasso's coil drawn now is in the world (LassoSockets). */
@Mixin(LevelRenderer.class)
abstract class LassoSocketLevelRendererMixin {
	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void jaymoskingdoms$lassoSocketsOn(CallbackInfo ci) {
		LassoSockets.level(true);
	}

	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void jaymoskingdoms$lassoSocketsOff(CallbackInfo ci) {
		LassoSockets.level(false);
	}
}
