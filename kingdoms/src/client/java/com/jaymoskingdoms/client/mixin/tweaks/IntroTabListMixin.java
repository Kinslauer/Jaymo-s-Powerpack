package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.TabListNames;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.180) The tab list calls a player you haven't been introduced to a stranger ({@code Introductions}). */
@Mixin(PlayerTabOverlay.class)
abstract class IntroTabListMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$stranger(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (TabListNames.stranger(info.getProfile().id())) {
			cir.setReturnValue(TabListNames.strangerName());
		}
	}
}
