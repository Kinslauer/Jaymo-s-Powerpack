package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.FarmFamily;
import com.jaymoskingdoms.client.InteractActionsClient;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA's Work button (its chores: harvest, chop, hunt, fish) shows on a grown one of a farmhand's family
 * ({@link FarmFamily}) as it would on your own family: the server says so ahead of MCA's screen
 * ({@code InteractActions}, {@link FarmFamily#WORK_OFFER}). MCA's server takes a chore from anyone.
 */
@Mixin(targets = "net.conczin.mca.client.gui.AbstractDynamicScreen$ButtonEx")
abstract class McaFarmWorkButtonMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void jaymoskingdoms$farmWork(MCAButton apiButton, AbstractDynamicScreen screen, CallbackInfo ci) {
		if (!"gui.button.work".equals(apiButton.identifier()) || !(screen instanceof McaInteractScreenAccessor interact)) {
			return;
		}
		if (InteractActionsClient.offers(interact.jaymoskingdoms$villager().asEntity().getId(), FarmFamily.WORK_OFFER)) {
			AbstractWidget self = (AbstractWidget)(Object)this;
			self.visible = true;
			self.active = true;
		}
	}
}
