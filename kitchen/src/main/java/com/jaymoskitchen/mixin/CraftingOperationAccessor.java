package com.jaymoskitchen.mixin;

import net.blay09.mods.cookingforblockheads.crafting.CraftingOperation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which recipe a Cooking for Blockheads crafting operation is for (see KitchenMenuMixin#jaymoskitchen$alwaysShow). */
@Mixin(CraftingOperation.class)
public interface CraftingOperationAccessor {
	@Accessor("recipe")
	RecipeHolder<?> jaymoskitchen$getRecipe();
}
