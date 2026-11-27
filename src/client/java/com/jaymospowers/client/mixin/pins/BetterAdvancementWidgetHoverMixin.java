package com.jaymospowers.client.mixin.pins;

import com.jaymospowers.client.hud.AdvancementPins;
import net.minecraft.advancements.AdvancementNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Better Advancements ("Don't Be a Jerk" licence, way2muchnoise; shipped unchanged, nothing of it copied) has its own
 * screen: its widget's hover tooltip names the advancement a shift-click pins ({@link AdvancementPins}).
 */
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementWidget", remap = false)
abstract class BetterAdvancementWidgetHoverMixin {
	@Shadow
	private AdvancementNode advancementNode;

	@Inject(method = "drawHover", at = @At("HEAD"), require = 0)
	private void jaymospowers$hovered(CallbackInfo ci) {
		AdvancementPins.hovering(advancementNode.holder());
	}
}
