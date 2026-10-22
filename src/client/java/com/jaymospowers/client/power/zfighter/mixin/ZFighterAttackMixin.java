package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.ember.EmberKindleClient;
import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a Z Fighter's hold combo (or, 0.14.0, an Emberborn's Kindle; 0.15.72, a Stormborn's or Dawnborn's charge) has the attack key, holding it doesn't dig at
 * whatever block swings into view. (0.15.65) Kindle also holds back the click's own punch and swing
 * ({@link EmberKindleClient#holdsClick}): a quick click still punches when it's let go.
 */
@Mixin(Minecraft.class)
abstract class ZFighterAttackMixin {
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$zfighterCombo(boolean leftClick, CallbackInfo ci) {
		if (ZFighterPowerClient.comboHeld() || EmberKindleClient.kindling() || EmberKindleClient.pending()
			|| com.jaymospowers.client.power.heritage.HeritageMovesClient.charging()) {
			ci.cancel();
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$kindleClick(CallbackInfoReturnable<Boolean> cir) {
		if (EmberKindleClient.holdsClick((Minecraft)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
