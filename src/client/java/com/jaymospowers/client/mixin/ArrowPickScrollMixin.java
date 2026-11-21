package com.jaymospowers.client.mixin;

import com.jaymospowers.client.fighting.ArrowPickClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a bow or crossbow is being drawn the wheel picks the arrow (see ArrowPick), before the stances' wheel (Sneak + scroll)
 * and a rider's gaits, which keep it the rest of the time. A lower priority number runs first.
 */
@Mixin(value = MouseHandler.class, priority = 900)
abstract class ArrowPickScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$arrowScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window == net.minecraft.client.Minecraft.getInstance().getWindow().handle() && ArrowPickClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
