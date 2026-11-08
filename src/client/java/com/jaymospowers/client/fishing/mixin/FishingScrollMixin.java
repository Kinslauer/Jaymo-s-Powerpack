package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.FishingRealClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Track A) With a rod in hand, sneak + scroll turns its drag, and scrolling while a fish is on reels it in. */
@Mixin(MouseHandler.class)
abstract class FishingScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$fishingScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (window == net.minecraft.client.Minecraft.getInstance().getWindow().handle()
				&& net.minecraft.client.Minecraft.getInstance().gui.screen() == null
				&& FishingRealClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
