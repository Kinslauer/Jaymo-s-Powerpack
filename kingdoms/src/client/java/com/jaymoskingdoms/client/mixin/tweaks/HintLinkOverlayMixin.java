package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.HintLinks;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An action-bar hint that names a place is remembered, so its key goes there ({@link HintLinks}). Before Jaymo's Powers'
 * tip cards take the line off the bar (theirs runs at priority 1100).
 */
@Mixin(value = Hud.class, priority = 900)
abstract class HintLinkOverlayMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void jaymoskingdoms$hintLink(Component message, boolean animateColor, CallbackInfo ci) {
		HintLinks.heard(message);
	}
}
