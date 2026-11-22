package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WeaponMod's reload gauge beside the hotbar isn't drawn. Andy, 2026-11-22: "the loading bar does not need to be there for
 * any of this old age weaponry". (Our own use bar leaves the guns out too, {@code client/bits/UseBar}.)
 */
@Mixin(targets = "ckathode.weaponmod.render.GuiOverlayReloaded")
abstract class WeaponModReloadOverlayMixin {
	@Inject(method = "renderGUIOverlay", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$noReloadBar(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		ci.cancel();
	}
}
