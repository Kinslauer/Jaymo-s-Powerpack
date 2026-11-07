package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.Hiding;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hiding in a hay bale ({@link Hiding}): the body is inside solid blocks on purpose, so it isn't nudged out of them. */
@Mixin(LocalPlayer.class)
abstract class HidingPushMixin {
	@Inject(method = "suffocatesAt", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$stayHidden(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (Hiding.hidden((LocalPlayer)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
