package com.jaymospowers.client.power.gravity.mixin;

import com.jaymospowers.client.power.gravity.GravityPowerClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wheel reels whatever Lift holds in or out (and only then), instead of turning the hotbar. */
@Mixin(MouseHandler.class)
abstract class GravityReelScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$reel(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (GravityPowerClient.reel(yOffset)) {
			ci.cancel();
		}
	}
}
