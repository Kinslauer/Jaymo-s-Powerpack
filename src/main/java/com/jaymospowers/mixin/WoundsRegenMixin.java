package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Wounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.15.94) Food's own healing (vanilla's, where Kitchen's stomach doesn't stand in for it) runs at
 * {@link Wounds#UNPATCHED_REGEN} until you're patched up ({@link Wounds#patchedUp}). Kitchen's meals read
 * {@link Wounds#UNPATCHED_TAG} for the same.
 */
@Mixin(FoodData.class)
abstract class WoundsRegenMixin {
	/**
	 * Hurt, but with nothing left that may heal (a broken part held at its cap, a severed one): not hurt, as far as food's
	 * healing goes, so the healing it would refuse doesn't burn their hunger for nothing.
	 */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
	private boolean jaymospowers$onlyWhatCanHeal(ServerPlayer player, Operation<Boolean> original) {
		// (and a Smog user in clean open air heals nothing there)
		return original.call(player) && Wounds.canHealMore(player) && !player.entityTags().contains(com.jaymospowers.power.smoke.SmokeSmog.CLEAN_AIR_TAG);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private void jaymospowers$slowUntilPatched(ServerPlayer player, float amount, Operation<Void> original) {
		original.call(player, Wounds.patchedUp(player) ? amount : amount * Wounds.UNPATCHED_REGEN);
	}
}
