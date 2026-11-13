package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.client.FifthClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held fast, asleep or startled: no swinging, no using, no digging ({@link FifthClient#locked}). */
@Mixin(Minecraft.class)
abstract class LockedActionsMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (FifthClient.locked()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noUse(CallbackInfo ci) {
		if (FifthClient.locked()) {
			ci.cancel();
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noDig(boolean leftClick, CallbackInfo ci) {
		if (FifthClient.locked()) {
			ci.cancel();
		}
	}
}
