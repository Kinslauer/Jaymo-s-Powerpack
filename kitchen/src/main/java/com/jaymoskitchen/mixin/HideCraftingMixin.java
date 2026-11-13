package com.jaymoskitchen.mixin;

import com.jaymoskitchen.tanning.HideOrigin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** What's crafted from hides and leather remembers their animal ({@link HideOrigin#crafted}): tanning steps, finishes, armour, rugs. */
@Mixin({ShapedRecipe.class, ShapelessRecipe.class})
abstract class HideCraftingMixin {
	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"),
		cancellable = true)
	private void jaymoskitchen$hideOf(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack made = HideOrigin.crafted(cir.getReturnValue(), input.items());
		if (made != cir.getReturnValue()) {
			cir.setReturnValue(made);
		}
	}
}
