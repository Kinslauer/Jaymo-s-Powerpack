package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.FirstTools;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.17.178) JEI's Gathering page: what drops from the world rather than being crafted. For now plant fibre, cut from
 * the {@link FirstTools#FIBRE_PLANTS} with one of the {@link FirstTools#FIBRE_KNIVES}; both lists are read off the tags,
 * so the page follows them. Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class GatheringJei implements IModPlugin {
	static final int NOTES = 2;
	static final IRecipeType<SimpleCategory.Recipe> GATHERING = IRecipeType.create(JaymosPowers.MOD_ID, "gathering", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("gathering");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), GATHERING,
			Component.translatable("jei.jaymospowers.gathering"), FirstTools.PLANT_FIBRE, NOTES));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> knives = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.is(FirstTools.FIBRE_KNIVES)) {
				knives.add(stack);
			}
		}
		// the same split as FirstTools' break hook: the two-block-tall plants give twice the fibre
		List<ItemStack> plants = new ArrayList<>(), tallPlants = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!block.defaultBlockState().is(FirstTools.FIBRE_PLANTS) || block.asItem() == Items.AIR) {
				continue;
			}
			boolean tall = block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN || block == Blocks.TALL_DRY_GRASS;
			(tall ? tallPlants : plants).add(new ItemStack(block));
		}
		if (knives.isEmpty()) {
			return;
		}
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		if (!plants.isEmpty()) {
			recipes.add(fibre(knives, plants, FirstTools.FIBRE_PER_GRASS));
		}
		if (!tallPlants.isEmpty()) {
			recipes.add(fibre(knives, tallPlants, FirstTools.FIBRE_PER_GRASS * 2));
		}
		registration.addRecipes(GATHERING, recipes);
	}

	private static SimpleCategory.Recipe fibre(List<ItemStack> knives, List<ItemStack> plants, int count) {
		return new SimpleCategory.Recipe(List.of(knives, plants), List.of(new ItemStack(FirstTools.PLANT_FIBRE, count)),
			List.of(Component.translatable("jei.jaymospowers.gathering.fiber.1"),
				Component.translatable("jei.jaymospowers.gathering.fiber.2")));
	}
}
