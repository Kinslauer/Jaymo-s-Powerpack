package com.jaymospowers.mixin;

import com.jaymospowers.armory.DualFlintlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Motion patch 4) A WeaponMod flintlock in each hand: the main hand's use steps aside ({@code PASS}) when it's the
 * off hand's turn, so the game tries the off hand next ({@link DualFlintlocks}). Every other WeaponMod gun is left as it was.
 */
@Mixin(targets = "ckathode.weaponmod.item.RangedComponent")
abstract class WeaponModDualFlintlockMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$offHandsTurn(ItemStack stack, Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (DualFlintlocks.offHandsTurn(player, hand)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
