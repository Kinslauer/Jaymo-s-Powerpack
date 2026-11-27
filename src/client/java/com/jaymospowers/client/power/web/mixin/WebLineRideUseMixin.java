package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSwing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak and use on a webline starts a ride (see WebLineRideClient), and that use press is the ride's: the held item is
 * not used, and use stays the ride's while it lasts.
 */
@Mixin(Minecraft.class)
abstract class WebLineRideUseMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$lineRideUse(CallbackInfo ci) {
		if (this.player != null && WebSwing.lineRideTakesUse(this.player)) {
			ci.cancel();
		}
	}
}
