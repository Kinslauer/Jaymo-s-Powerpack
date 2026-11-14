package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.DownedHud;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a line is on the black (a death turned away, a wake), other action-bar lines wait until it lifts ({@link DownedHud#holdOverlay}). */
@Mixin(Hud.class)
abstract class SceneBlackOverlayMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$holdOverBlack(Component message, boolean animateColor, CallbackInfo ci) {
		if (DownedHud.holdOverlay(message)) {
			ci.cancel();
		}
	}
}
