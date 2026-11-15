package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.Mending;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Two worn wood or flint tools don't combine into one in the grid ({@link Mending#unmendable}). */
@Mixin(RepairItemRecipe.class)
abstract class UnmendableGridRepairMixin {
	@Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$noMending(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
		if (Mending.unmendable(first)) {
			cir.setReturnValue(false);
		}
	}
}
