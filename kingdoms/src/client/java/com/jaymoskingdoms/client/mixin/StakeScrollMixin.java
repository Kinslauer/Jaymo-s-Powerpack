package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.StakeStruggle;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.641) Sneak + scroll up while staked tugs at the stake (StakeStruggle), before Powers' stance wheel takes it. */
@Mixin(value = MouseHandler.class, priority = 400)
abstract class StakeScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$stakeTug(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (StakeStruggle.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
