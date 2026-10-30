package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.18.14) JEI's Brick Kiln page: everything the kiln fires ({@link Kiln#recipes()}), the heat it needs and the chance
 * of one more. Each recipe's inputs are whatever items its test accepts, so the page follows the kiln's own rules.
 * Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class KilnJei implements IModPlugin {
	static final int NOTES = 3;
	static final IRecipeType<SimpleCategory.Recipe> KILN = IRecipeType.create(JaymosPowers.MOD_ID, "kiln", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("kiln");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), KILN,
			Component.translatable("jei.jaymospowers.kiln"), Items.BRICKS, NOTES));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(KILN, Items.BRICKS);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Kiln.Recipe recipe : Kiln.recipes()) {
			List<ItemStack> inputs = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack stack = new ItemStack(item);
				if (!stack.is(Kiln.KILN_LOAD.asItem()) && recipe.input().test(stack)) {
					inputs.add(stack);
				}
			}
			if (inputs.isEmpty()) {
				continue;
			}
			List<Component> notes = new ArrayList<>();
			notes.add(Component.translatable("jei.jaymospowers.kiln.heat", heat(recipe.heat())));
			if (recipe.bonus() > 0) {
				notes.add(Component.translatable("jei.jaymospowers.kiln.bonus", Math.round(recipe.bonus() * 100)));
			}
			notes.add(Component.translatable("jei.jaymospowers.kiln.how"));
			recipes.add(new SimpleCategory.Recipe(List.of(inputs), List.of(new ItemStack(recipe.output())), notes));
		}
		registration.addRecipes(KILN, recipes);
	}

	private static Component heat(int heat) {
		return heat >= 1 && heat <= 3 ? Component.translatable("jei.jaymospowers.kiln.heat." + heat)
			: Component.translatable("jei.jaymospowers.kiln.heat.more", heat);
	}
}
