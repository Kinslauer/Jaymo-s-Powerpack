package com.jaymospowers.client.mixin;

import com.jaymospowers.client.AimCrosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.43) The armed aim crosshair ({@link AimCrosshair#renderArmed}) is drawn right after the crosshair layer, not
 * inside it: Fabric's crosshair layer is what Auto HUD wraps and fades, so out here a weapon's crosshair can't be faded
 * away while you aim. Fabric wraps the call (WrapOperation), so the call itself stays for this to land after.
 */
@Mixin(Hud.class)
abstract class AimCrosshairHudMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
		at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/Hud;extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void jaymospowers$armedCrosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		AimCrosshair.renderArmed(graphics, delta);
	}
}
