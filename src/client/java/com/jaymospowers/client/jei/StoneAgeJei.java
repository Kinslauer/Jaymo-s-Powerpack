package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.brewing.Brewing;
import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.ground.Panning;
import com.jaymospowers.ground.StoneAge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Andy's GT New Horizons picks, patch 1 ({@link StoneAge}): JEI's Sieve page (gravel through a plant mesh, and what a pan
 * turns up, with the chances) and Mortar page (what the mortar grinds things into), and how a clay bucket is used. The
 * kiln's new loads (unfired bricks, the clay bucket) are on its own page ({@link KilnJei}), the dusts and molten glass on
 * Tinkering's ({@link TinkeringJei}), and the grid recipes on JEI's crafting page. Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class StoneAgeJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> SIEVE = IRecipeType.create(JaymosPowers.MOD_ID, "sieve", SimpleCategory.Recipe.class),
		MORTAR = IRecipeType.create(JaymosPowers.MOD_ID, "mortar_grinding", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("stone_age");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new SimpleCategory(gui, SIEVE, Component.translatable("jei.jaymospowers.sieve"), FirstTools.PLANT_MESH, 1),
			new SimpleCategory(gui, MORTAR, Component.translatable("jei.jaymospowers.mortar_grinding"), Brewing.MORTAR, 1));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(SIEVE, FirstTools.PLANT_MESH);
		registration.addCraftingStation(SIEVE, Panning.PAN);
		registration.addCraftingStation(MORTAR, Brewing.MORTAR);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> gravel = List.of(new ItemStack(Items.GRAVEL));
		List<ItemStack> washed = List.of(new ItemStack(Items.GRAVEL), new ItemStack(Items.SAND), new ItemStack(Items.RED_SAND));
		List<ItemStack> pan = List.of(new ItemStack(Panning.PAN));
		List<SimpleCategory.Recipe> sieve = new ArrayList<>();
		sieve.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(FirstTools.PLANT_MESH)), gravel), List.of(new ItemStack(Items.FLINT)),
			List.of(chance(StoneAge.SIEVE_FLINT))));
		sieve.add(panned(pan, washed, Items.GOLD_NUGGET, Panning.GOLD));
		sieve.add(panned(pan, washed, Items.COPPER_NUGGET, Panning.COPPER));
		sieve.add(panned(pan, washed, Items.IRON_NUGGET, Panning.IRON));
		sieve.add(panned(pan, washed, Items.FLINT, Panning.FLINT));
		// (patch 4) the rivers' tin sand, in a pan or a sluice
		sieve.add(panned(List.of(new ItemStack(Panning.PAN), new ItemStack(Panning.SLUICE)), List.of(new ItemStack(com.jaymospowers.tinkering.Tin.TIN_SAND)),
			com.jaymospowers.tinkering.Tin.TIN_NUGGET, Panning.TIN));
		SimpleCategory.addRecipes(registration, SIEVE, sieve);

		List<SimpleCategory.Recipe> mortar = new ArrayList<>();
		for (Map.Entry<Item, Item> grind : StoneAge.grinds().entrySet()) {
			mortar.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(grind.getKey()))), List.of(new ItemStack(grind.getValue())),
				List.of(Component.translatable("jei.jaymospowers.mortar_grinding.how"))));
		}
		SimpleCategory.addRecipes(registration, MORTAR, mortar);

		registration.addIngredientInfo(StoneAge.CLAY_BUCKET, Component.translatable("jei.jaymospowers.clay_bucket"));
		registration.addIngredientInfo(StoneAge.BRICK_FORM, Component.translatable("jei.jaymospowers.brick_form"));
	}

	private static SimpleCategory.Recipe panned(List<ItemStack> pan, List<ItemStack> washed, Item find, float chance) {
		return new SimpleCategory.Recipe(List.of(pan, washed), List.of(new ItemStack(find)), List.of(chance(chance)));
	}

	private static Component chance(float chance) {
		return Component.translatable("jei.jaymospowers.sieve.chance", Math.round(chance * 100));
	}
}
