package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * TaCZ isn't shown in JEI at all (Andy, 2026-11-18: "Tacz should not be mentioned anywhere in jei or creative"): none of
 * its items and none of its recipe pages (the gun smith table's). Its guns stay in the game for the future's gunners;
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
			}
		}
		List<ItemStack> tacz = runtime.getIngredientManager().getAllItemStacks().stream()
			.filter(stack -> TaczGuns.NAMESPACE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()))
			.toList();
		if (!tacz.isEmpty()) {
			runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, tacz);
		}
	}
}
