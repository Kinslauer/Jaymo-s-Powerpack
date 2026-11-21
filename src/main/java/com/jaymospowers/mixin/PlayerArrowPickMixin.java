package com.jaymospowers.mixin;

import com.jaymospowers.fighting.ArrowPick;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bow or crossbow with a chosen arrow (see ArrowPick) asks for that kind first, before the hands, the quiver and the pack. */
@Mixin(Player.class)
abstract class PlayerArrowPickMixin {
	@Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chosenArrow(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack ammo = ArrowPick.ammo((Player) (Object) this, weapon);
		if (ammo != null) {
			cir.setReturnValue(ammo);
		}
	}
}
