package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.client.gui.MCAButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.449) The Interact page's Ask the Way ({@code McaScreensMixin}) opens its own page of places, and that page's back
 * arrow returns to Interact. The places themselves go to the server ({@code McaCommandMixin}, {@code Directions}).
 */
@Mixin(InteractScreen.class)
abstract class McaWayPageMixin {
	/** (as McaScreensMixin names them: a mixin class can't be referenced from another) */
	private static final String WAY = "gui.button.way", WAY_PAGE = "way";

	@Inject(method = "buttonPressed", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$wayPage(MCAButton button, CallbackInfo ci) {
		InteractScreen self = (InteractScreen)(Object)this;
		String id = button.identifier();
		if (WAY.equals(id)) {
			self.setLayout(WAY_PAGE);
			ci.cancel();
		} else if ("gui.button.backarrow".equals(id) && WAY_PAGE.equals(self.getActiveScreen())) {
			self.setLayout("interact");
			ci.cancel();
		}
	}
}
