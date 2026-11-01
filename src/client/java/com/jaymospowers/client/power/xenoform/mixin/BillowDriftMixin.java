package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.forms.Billow;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.19.29) Billow's low box is the swimming pose's, but a cloud isn't crawling: it drifts at its own pace. */
@Mixin(LocalPlayer.class)
abstract class BillowDriftMixin {
	@Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowDrift(CallbackInfoReturnable<Boolean> cir) {
		if (Billow.cloud((LocalPlayer)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
