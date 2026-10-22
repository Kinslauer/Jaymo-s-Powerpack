package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Wounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.15.91) Food's own healing (vanilla's, where Kitchen's stomach doesn't stand in for it) runs at
 * {@link Wounds#UNPATCHED_REGEN} until you're patched up ({@link Wounds#patchedUp}). Kitchen's meals read
 * {@link Wounds#UNPATCHED_TAG} for the same.
 */
@Mixin(FoodData.class)
abstract class WoundsRegenMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private void jaymospowers$slowUntilPatched(ServerPlayer player, float amount, Operation<Void> original) {
		original.call(player, Wounds.patchedUp(player) ? amount : amount * Wounds.UNPATCHED_REGEN);
	}
}
