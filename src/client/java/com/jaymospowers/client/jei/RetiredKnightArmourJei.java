package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.RetiredKnightArmour;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Knight Armor and Weapons' retired armour isn't listed in JEI (the creative tabs are {@link RetiredKnightArmour}). */
public final class RetiredKnightArmourJei implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("retired_knight_armour");
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		List<ItemStack> retired = runtime.getIngredientManager().getAllItemStacks().stream().filter(RetiredKnightArmour::retired).toList();
		if (!retired.isEmpty()) {
			runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, retired);
		}
	}
}
