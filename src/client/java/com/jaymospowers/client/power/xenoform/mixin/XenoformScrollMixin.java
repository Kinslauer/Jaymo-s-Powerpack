package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.XenoformPowerClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A band wearer's scroll while holding Z turns the dial, not the hotbar. */
@Mixin(MouseHandler.class)
abstract class XenoformScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$xenoformScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (XenoformPowerClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
