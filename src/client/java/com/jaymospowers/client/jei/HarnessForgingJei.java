package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smithing.Forging;
import com.jaymospowers.tinkering.smithing.HeraldryForging;
import com.jaymospowers.tinkering.smithing.HotMetal;
import com.jaymospowers.tinkering.smithing.SmithTiers;
import com.jaymospowers.tinkering.smithing.Smithing;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI's Smithing Anvil page for Jaymo's Kingdoms' harness ({@link HeraldryForging}): every plate and mail piece the anvil
 * forges, with the ingots it takes (heated at the hearth), anything else the smith gives up for it (rabbit hides for a
 * fur lining, gold for gilding), and the blows. Without it the metal harness pieces showed no way to make them at all.
 * Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class HarnessForgingJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> HARNESS = IRecipeType.create(JaymosPowers.MOD_ID, "harness_forging", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("harness_forging");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new SimpleCategory(gui, HARNESS, Component.translatable("jei.jaymospowers.harness_forging"), Smithing.SMITHING_ANVIL, 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(HARNESS, Smithing.SMITHING_ANVIL);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Forging.Option option : HeraldryForging.options()) {
			ItemStack ingots = HotMetal.ingot(option.metal(), option.ingots());
			if (ingots.isEmpty()) {
				continue;
			}
			List<List<ItemStack>> inputs = new ArrayList<>();
			inputs.add(List.of(ingots));
			for (ItemStack extra : HeraldryForging.extras(option)) {
				inputs.add(List.of(extra));
			}
			List<Component> notes = new ArrayList<>();
			notes.add(Component.translatable("jei.jaymospowers.harness_forging.how", option.ingots(), option.metal().displayName(), option.hits()));
			if (HeraldryForging.gildedOption(option)) {
				notes.add(Component.translatable("jei.jaymospowers.harness_forging.gilded"));
			} else if (Forging.difficulty(option.metal()) >= SmithTiers.HARD_METAL) {
				notes.add(Component.translatable("jei.jaymospowers.harness_forging.hard"));
			} else {
				notes.add(Component.empty());
			}
			recipes.add(new SimpleCategory.Recipe(inputs, List.of(option.result()), notes));
		}
		SimpleCategory.addRecipes(registration, HARNESS, recipes);
	}
}
