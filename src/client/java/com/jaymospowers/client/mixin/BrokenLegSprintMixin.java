package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Breaks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A broken leg stops a sprint on your own client too ({@link Breaks#NO_SPRINT}, from the server's reading of it), so the
 * sprint never starts and snaps back, as it did when only the server stopped it.
 */
@Mixin(LocalPlayer.class)
abstract class BrokenLegSprintMixin {
	@Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$brokenLeg(boolean allowedInWater, CallbackInfoReturnable<Boolean> cir) {
		if (((LocalPlayer) (Object) this).hasAttached(Breaks.NO_SPRINT)) {
			cir.setReturnValue(false);
		}
	}
}
