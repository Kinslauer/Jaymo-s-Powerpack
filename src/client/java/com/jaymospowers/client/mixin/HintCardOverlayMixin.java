package com.jaymospowers.client.mixin;

import com.jaymospowers.client.hud.HintCards;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A hint meant for the action bar comes up as a tip card in the top right instead ({@link HintCards}). After the other lines' hooks. */
@Mixin(value = Hud.class, priority = 1100)
abstract class HintCardOverlayMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$hintCard(Component message, boolean animateColor, CallbackInfo ci) {
		if (HintCards.take(message)) {
			ci.cancel();
		}
	}
}
