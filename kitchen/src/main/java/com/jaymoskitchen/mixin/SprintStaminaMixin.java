package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.Climate;
import com.jaymoskitchen.climate.Stamina;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sprinting on the ground costs wind ({@link Stamina}), not food: the hunger the game charges for each metre sprinted
 * isn't charged while the stamina rule is on. (The only one of its movement charges made while sprinting out of water.)
 */
@Mixin(ServerPlayer.class)
abstract class SprintStaminaMixin {
	@WrapOperation(method = "checkMovementStatistics",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
	private void jaymoskitchen$windNotFood(ServerPlayer player, float amount, Operation<Void> original) {
		if (player.isSprinting() && !player.isInWater() && !player.isSwimming() && player.level().getGameRules().get(Climate.STAMINA)) {
			return;
		}
		original.call(player, amount);
	}
}
