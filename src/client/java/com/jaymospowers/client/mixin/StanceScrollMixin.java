package com.jaymospowers.client.mixin;

import com.jaymospowers.client.StanceScroll;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.57) Sneak + the wheel steps through the stances, not the hotbar (see StanceScroll). */
@Mixin(MouseHandler.class)
abstract class StanceScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$stanceScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (StanceScroll.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
