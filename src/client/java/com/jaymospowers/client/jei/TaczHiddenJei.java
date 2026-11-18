package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * TaCZ isn't shown in JEI at all (Andy, 2026-11-18: "Tacz should not be mentioned anywhere in jei or creative"): none of
 * its items (nor a painting of its variant), none of its recipe pages (the gun smith table's), and none of its recipes on
 * other pages (its gunpowder, which still crafts, named TaCZ on the crafting page). Its guns stay in the game for the future's gunners;
 * the creative tabs are {@link TaczGuns#hideModern}.
 */
public final class TaczHiddenJei implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("tacz_hidden");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		if (!TaczGuns.LOADED) {
			return;
		}
		IRecipeManager recipes = runtime.getRecipeManager();
		for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup().get().toList()) {
			if (TaczGuns.NAMESPACE.equals(category.getRecipeType().getUid().getNamespace())) {
				recipes.hideRecipeCategory(category.getRecipeType());
			} else {
				hideTaczRecipes(recipes, category.getRecipeType());
			}
		}
		List<ItemStack> tacz = runtime.getIngredientManager().getAllItemStacks().stream().filter(TaczGuns::hidden).toList();
		if (!tacz.isEmpty()) {
			runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, tacz);
		}
	}

	/** Hides the recipes of {@code type} that are TaCZ's own (a vanilla recipe whose id is in its namespace). */
	private static <T> void hideTaczRecipes(IRecipeManager recipes, IRecipeType<T> type) {
		List<T> tacz = recipes.createRecipeLookup(type).get()
			.filter(recipe -> recipe instanceof RecipeHolder<?> holder && TaczGuns.NAMESPACE.equals(holder.id().identifier().getNamespace()))
			.toList();
		if (!tacz.isEmpty()) {
			recipes.hideRecipes(type, tacz);
		}
	}
}
