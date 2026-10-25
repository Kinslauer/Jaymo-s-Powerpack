package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.LashSockets;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.16.11) The level's entities are being submitted: a held flail's or whip's handle drawn now is in the world (LashSockets). */
@Mixin(LevelRenderer.class)
abstract class LashSocketLevelRendererMixin {
	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void jaymospowers$lashSocketsOn(CallbackInfo ci) {
		LashSockets.level(true);
	}

	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void jaymospowers$lashSocketsOff(CallbackInfo ci) {
		LashSockets.level(false);
	}
}
