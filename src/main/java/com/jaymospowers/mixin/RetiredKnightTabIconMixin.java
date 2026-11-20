package com.jaymospowers.mixin;

import com.jaymospowers.armory.RetiredKnightArmour;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Knight Armor and Weapons' creative tab shows its long sword, not the retired Knight Helmet ({@link RetiredKnightArmour}). */
@Mixin(CreativeModeTab.class)
abstract class RetiredKnightTabIconMixin {
	@Inject(method = "getIconItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$knightTabIcon(CallbackInfoReturnable<ItemStack> cir) {
		ItemStack icon = RetiredKnightArmour.tabIcon((CreativeModeTab) (Object) this);
		if (icon != null) {
			cir.setReturnValue(icon);
		}
	}
}
