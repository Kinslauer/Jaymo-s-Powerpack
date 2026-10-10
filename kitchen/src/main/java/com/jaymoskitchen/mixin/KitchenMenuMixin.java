package com.jaymoskitchen.mixin;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.kitchen.CookingTableBridge;
import net.blay09.mods.cookingforblockheads.crafting.CraftingOperation;
import net.blay09.mods.cookingforblockheads.menu.KitchenMenu;
import net.blay09.mods.cookingforblockheads.menu.ServerKitchenMenuState;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Picking a minigame dish in Cooking for Blockheads' cooking table cooks it as a minigame (see CookingTableBridge). */
@Mixin(KitchenMenu.class)
abstract class KitchenMenuMixin {
	@Shadow
	@Final
	public Player player;

	@Inject(method = "craft", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$minigame(RecipeDisplayId id, NonNullList<ItemStack> lockedInputs, boolean craftFullStack,
		boolean addToInventory, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
			return;
		}
		RecipeManager.ServerDisplayInfo info = level.recipeAccess().getRecipeFromDisplay(id);
		Dish dish = info == null ? null : CookingTableBridge.dishOf(info.parent().value());
		if (dish != null) {
			ci.cancel();
			CookingTableBridge.cook(serverPlayer, dish);
		}
	}

	/**
	 * Without a No Filter book in the cooking table, Cooking for Blockheads only lists recipes whose every ingredient
	 * is already in the kitchen or the cook's inventory. Our dishes (the minigame ones, and the cookware foods) are
	 * always listed, showing what's missing, so the cook can see what each one needs. This covers both the list and
	 * the recipe shown when one is picked: both ask shouldShowRecipe.
	 */
	@Inject(method = "shouldShowRecipe", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$alwaysShow(CraftingOperation operation, ServerKitchenMenuState state,
		CallbackInfoReturnable<Boolean> cir) {
		RecipeHolder<?> recipe = ((CraftingOperationAccessor) operation).jaymoskitchen$getRecipe();
		if (recipe != null && JaymosKitchen.MOD_ID.equals(recipe.id().identifier().getNamespace())) {
			cir.setReturnValue(true);
		}
	}
}
