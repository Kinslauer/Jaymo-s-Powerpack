package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.bits.PullApart;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI's Pull Apart page ({@link PullApart}): each thing that comes apart in the inventory, and what it comes apart into.
 * Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class PullApartJei implements IModPlugin {
	static final int NOTES = 2;
	static final IRecipeType<SimpleCategory.Recipe> PULL_APART = IRecipeType.create(JaymosPowers.MOD_ID, "pull_apart", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("pull_apart");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), PULL_APART,
			Component.translatable("jei.jaymospowers.pull_apart"), Items.STRING, NOTES));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<Component> notes = List.of(Component.translatable("jei.jaymospowers.pull_apart.how"),
			Component.translatable("jei.jaymospowers.pull_apart.back"));
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Map.Entry<Item, PullApart.Parts> entry : PullApart.table().entrySet()) {
			PullApart.Parts parts = entry.getValue();
			recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(entry.getKey()))),
				List.of(parts.stack(parts.count())), notes));
		}
		SimpleCategory.addRecipes(registration, PULL_APART, recipes);
	}
}
