package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a Z Fighter's hold combo (or, 0.14.0, an Emberborn's Kindle) has the attack key, holding it doesn't dig at
 * whatever block swings into view.
 */
@Mixin(Minecraft.class)
abstract class ZFighterAttackMixin {
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$zfighterCombo(boolean leftClick, CallbackInfo ci) {
		if (ZFighterPowerClient.comboHeld() || com.jaymospowers.client.power.ember.EmberKindleClient.kindling()) {
			ci.cancel();
		}
	}
}
