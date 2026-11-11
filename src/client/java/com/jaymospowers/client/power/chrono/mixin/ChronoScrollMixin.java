package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoGearsClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wheel shifts a speedster's gears while it sprints at speed (and only then). */
@Mixin(MouseHandler.class)
abstract class ChronoScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoGears(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ChronoGearsClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
