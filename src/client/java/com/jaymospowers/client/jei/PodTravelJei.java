package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.fuel.FuelWorks;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import com.jaymospowers.power.zfighter.pod.PodStardust;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI's pages for the pod's travel kit that are not grid recipes: the Fuel Works (coal into fuel cans) and a falling star's
 * stardust (caught in a glass bottle). Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class PodTravelJei implements IModPlugin {
	static final int NOTES = 2;
	static final IRecipeType<SimpleCategory.Recipe> WORKS = IRecipeType.create(JaymosPowers.MOD_ID, "fuel_works", SimpleCategory.Recipe.class);
	static final IRecipeType<SimpleCategory.Recipe> STAR = IRecipeType.create(JaymosPowers.MOD_ID, "stardust", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("pod_travel");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), WORKS, Component.translatable("jei.jaymospowers.fuel_works"), FuelWorks.BLOCK, NOTES),
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), STAR, Component.translatable("jei.jaymospowers.stardust"), PodStardust.STARDUST, NOTES));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(WORKS, FuelWorks.BLOCK);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> coals = List.of(new ItemStack(Items.COAL, FuelWorks.COAL_PER_CAN), new ItemStack(Items.CHARCOAL, FuelWorks.COAL_PER_CAN));
		SimpleCategory.addRecipes(registration, WORKS, List.of(new SimpleCategory.Recipe(List.of(coals), List.of(new ItemStack(PodFuel.FUEL_CAN)),
			List.of(Component.translatable("jei.jaymospowers.fuel_works.how"), Component.translatable("jei.jaymospowers.fuel_works.hoppers")))));
		SimpleCategory.addRecipes(registration, STAR, List.of(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Items.GLASS_BOTTLE))),
			List.of(new ItemStack(PodStardust.STARDUST)), List.of(Component.translatable("jei.jaymospowers.stardust.how"),
				Component.translatable("jei.jaymospowers.stardust.trade")))));
	}
}
