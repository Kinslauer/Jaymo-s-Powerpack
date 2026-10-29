package com.jaymospowers.client.mixin;

import com.jaymospowers.client.HeldInput;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.129) Every action-bar line goes by {@link HeldInput}, which tells a hold's charge bar from the rest. */
@Mixin(Hud.class)
abstract class HeldInputOverlayMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void jaymospowers$chargeBar(Component message, boolean animateColor, CallbackInfo ci) {
		HeldInput.overlay(message);
	}
}
