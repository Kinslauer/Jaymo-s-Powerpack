package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.hands.HandsClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak and scroll over a station with things on it chooses the dish they make, instead of changing the hotbar slot. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$pickDish(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (HandsClient.scroll(yoffset)) {
			ci.cancel();
		}
	}
}
