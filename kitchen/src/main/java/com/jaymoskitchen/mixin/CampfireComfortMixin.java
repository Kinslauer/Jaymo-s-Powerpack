package com.jaymoskitchen.mixin;

import com.jaymoskitchen.cooking.CookfireComfort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lit campfire with food in its places eases those round it ({@link CookfireComfort}). */
@Mixin(CampfireBlockEntity.class)
abstract class CampfireComfortMixin {
	@Inject(method = "cookTick", at = @At("HEAD"))
	private static void jaymoskitchen$comfort(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity entity,
		RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache, CallbackInfo ci) {
		for (ItemStack item : entity.getItems()) {
			if (!item.isEmpty()) {
				CookfireComfort.cooking(level, pos);
				return;
			}
		}
	}
}
