package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.Chopping;
import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.ground.StoneAge;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * JEI's Chopping Block page ({@link Chopping}): every log that has planks, split on the block by any axe, with the
 * blows and planks by axe grade and the sawdust beside. Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class ChoppingJei implements IModPlugin {
	static final int NOTES = 2;
	static final IRecipeType<SimpleCategory.Recipe> CHOPPING = IRecipeType.create(JaymosPowers.MOD_ID, "chopping", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("chopping");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), CHOPPING,
			Component.translatable("jei.jaymospowers.chopping"), Chopping.OAK, NOTES));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (Block block : Chopping.blocks()) {
			registration.addCraftingStation(CHOPPING, block);
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> axes = new ArrayList<>(), blocks = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.is(ItemTags.AXES)) {
				axes.add(stack);
			}
		}
		for (Block block : Chopping.blocks()) {
			blocks.add(new ItemStack(block));
		}
		List<Component> notes = List.of(
			Component.translatable("jei.jaymospowers.chopping.how", Chopping.CHOPS_CRUDE, Chopping.CHOPS_FAIR, Chopping.CHOPS_FINE),
			Component.translatable("jei.jaymospowers.chopping.yield", FirstTools.PLANKS_CRUDE + Chopping.BONUS,
				FirstTools.PLANKS_FAIR + Chopping.BONUS, FirstTools.PLANKS_FINE + Chopping.BONUS));
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack log = new ItemStack(item);
			Item planks = Chopping.planksOf(log);
			if (planks != null) {
				recipes.add(new SimpleCategory.Recipe(List.of(blocks, axes, List.of(log)),
					List.of(new ItemStack(planks, FirstTools.PLANKS_FINE + Chopping.BONUS)), notes, List.of(new ItemStack(StoneAge.SAWDUST))));
			}
		}
		if (!axes.isEmpty()) {
			SimpleCategory.addRecipes(registration, CHOPPING, recipes);
		}
		for (Block block : Chopping.blocks()) {
			registration.addIngredientInfo(block, Component.translatable("jei.jaymospowers.chopping_block"));
		}
	}
}
