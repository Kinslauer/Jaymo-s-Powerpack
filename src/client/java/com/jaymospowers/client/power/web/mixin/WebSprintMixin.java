package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSymbioteClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A takeover sprints you with no forward key held: vanilla would stop the sprint (and never tell the server it began). */
@Mixin(LocalPlayer.class)
abstract class WebSprintMixin {
	@Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$takeoverSprint(CallbackInfoReturnable<Boolean> cir) {
		if (WebSymbioteClient.holdsSprint()) {
			cir.setReturnValue(false);
		}
	}
}
