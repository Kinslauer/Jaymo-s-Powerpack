package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindPowerClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While wind-flying, Jump means "rise": it must not also pop open an elytra. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
	@WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
	private boolean jaymospowers$windNoElytra(LocalPlayer player, Operation<Boolean> original) {
		if (WindPowerClient.suppressElytra(player)) {
			return false;
		}
		return original.call(player);
	}
}
