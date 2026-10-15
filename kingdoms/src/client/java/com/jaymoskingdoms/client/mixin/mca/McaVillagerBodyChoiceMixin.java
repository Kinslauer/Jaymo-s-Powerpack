package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.47) Andy: everyone is a villager ({@code McaVillagerBodyMixin}), so MCA's character screen no longer offers the
 * Villager / Player / Vanilla body buttons.
 */
@Mixin(VillagerEditorScreen.class)
abstract class McaVillagerBodyChoiceMixin {
	@Inject(method = "addModelSelectionWidgets", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noBodyChoice(int x, int y, CallbackInfo ci) {
		ci.cancel();
	}
}
