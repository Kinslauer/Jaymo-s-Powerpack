package com.jaymoskingdoms.client.mixin.forms;

import com.jaymoskingdoms.client.forms.FormsClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.366) Alt and scroll in the Battle Stance move the belt's slot. Runs before Powers' lock-on scroll (lower priority
 * number), so a plain scroll is still the lock-on's.
 */
@Mixin(value = MouseHandler.class, priority = 500)
abstract class FormsScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$formsScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (FormsClient.scroll(xOffset, yOffset)) {
			ci.cancel();
		}
	}
}
