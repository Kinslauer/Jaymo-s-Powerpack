package com.jaymoskitchen.mixin;

import com.jaymoskitchen.tanning.HideOrigin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leather hardened in a furnace (or anything one carrier becomes on its own) remembers its animal ({@link HideOrigin}). */
@Mixin(SingleItemRecipe.class)
abstract class HideSmeltingMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"),
		cancellable = true)
	private void jaymoskitchen$hideOf(SingleRecipeInput input, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack made = HideOrigin.crafted(cir.getReturnValue(), java.util.List.of(input.item()));
		if (made != cir.getReturnValue()) {
			cir.setReturnValue(made);
		}
	}
}
