package com.jaymospowers.compat.mixin;

import com.jaymospowers.core.TwoHanded;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two-handed weapons reach further ({@link TwoHanded#REACH}). Better Combat asks this for a swing's reach on the client
 * (where the swing finds its targets) and on the server (its range check), so both sides agree.
 */
@Mixin(value = PlayerAttackHelper.class, remap = false)
abstract class TwoHandedReachMixin {
	@Inject(method = "getRangeForItem", at = @At("RETURN"), cancellable = true)
	private static void jaymospowers$twoHandedReach(Player player, ItemStack stack, CallbackInfoReturnable<Double> cir) {
		if (TwoHanded.is(stack)) {
			cir.setReturnValue(cir.getReturnValue() * TwoHanded.REACH);
		}
	}
}
