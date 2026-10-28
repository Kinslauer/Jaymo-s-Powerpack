package com.jaymospowers.client.mixin;

import com.jaymospowers.client.finisher.FinisherClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.17.97) While an execution is being charged or played, attack is the hold's ({@link FinisherClient#holdsAttack}): the click
 * doesn't swing and holding it neither repeats Better Combat's swings (its hold-to-attack runs in {@code continueAttack}) nor
 * digs. Ahead of the default priority so Better Combat doesn't take the click first, as with the bomb glider.
 */
@Mixin(value = Minecraft.class, priority = 900)
abstract class FinisherAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$executionClick(CallbackInfoReturnable<Boolean> cir) {
		if (FinisherClient.holdsAttack((Minecraft)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$executionHold(boolean down, CallbackInfo ci) {
		if (FinisherClient.holdsAttack((Minecraft)(Object)this)) {
			ci.cancel();
		}
	}
}
