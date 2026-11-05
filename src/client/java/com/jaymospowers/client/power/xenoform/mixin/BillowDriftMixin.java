package com.jaymospowers.client.power.xenoform.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.19.34) Billow's low box is the swimming pose's, but a cloud isn't crawling: it drifts at its own pace (0.19.66, and Croaker hops at its own). */
@Mixin(LocalPlayer.class)
abstract class BillowDriftMixin {
	@Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowDrift(CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer self = (LocalPlayer)(Object)this;
		if (com.jaymospowers.power.xenoform.XenoformState.lowBox(self)) {
			// (0.19.66) Croaker sneaks at a sneak's pace all the same (its pose stays the low box's); (0.19.82) so do the oozes
			String form = self.getAttached(com.jaymospowers.power.xenoform.XenoformState.FORM);
			cir.setReturnValue(form != null && !"billow".equals(form) && self.isShiftKeyDown()
				&& !self.isInWater() && !self.getAbilities().flying);
		}
	}
}
