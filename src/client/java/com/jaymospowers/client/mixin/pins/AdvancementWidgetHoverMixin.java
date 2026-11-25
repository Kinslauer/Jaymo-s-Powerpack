package com.jaymospowers.client.mixin.pins;

import com.jaymospowers.client.hud.AdvancementPins;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's advancements screen draws the hovered advancement's tooltip: that's the one a shift-click pins ({@link AdvancementPins}). */
@Mixin(AdvancementWidget.class)
abstract class AdvancementWidgetHoverMixin {
	@Inject(method = "extractHover", at = @At("HEAD"))
	private void jaymospowers$hovered(CallbackInfo ci) {
		AdvancementHolder holder = ((AdvancementWidget)(Object)this).getAdvancement();
		AdvancementPins.hovering(holder);
	}
}
