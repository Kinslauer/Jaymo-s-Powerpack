package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.BombGliderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.13.7) On a bomb glider a left click drops a stick instead of swinging, and holding it mines nothing. Ahead of the
 * default priority so a held Better Combat weapon doesn't take the click first.
 */
@Mixin(value = Minecraft.class, priority = 900)
abstract class BombGliderAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$gliderDrop(CallbackInfoReturnable<Boolean> cir) {
		if (BombGliderClient.drop((Minecraft)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$gliderNoMining(boolean down, CallbackInfo ci) {
		if (BombGliderClient.riding(((Minecraft)(Object)this).player) != null) {
			ci.cancel();
		}
	}
}
