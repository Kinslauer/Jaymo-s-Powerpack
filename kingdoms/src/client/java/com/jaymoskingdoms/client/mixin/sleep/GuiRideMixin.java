package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.ThrallRideClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Riding a thrall by day ({@link ThrallRideClient}): the sleeper lies in the coffin, but the bed screen is not opened, and one open is let
 * go, so the keys and the mouse reach the thrall.
 */
@Mixin(Gui.class)
abstract class GuiRideMixin {
	@Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSleeping()Z", ordinal = 0))
	private boolean jaymoskingdoms$noBedScreen(LocalPlayer player) {
		return !ThrallRideClient.riding() && player.isSleeping();
	}

	@Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSleeping()Z", ordinal = 1))
	private boolean jaymoskingdoms$closeBedScreen(LocalPlayer player) {
		return !ThrallRideClient.riding() && player.isSleeping();
	}
}
