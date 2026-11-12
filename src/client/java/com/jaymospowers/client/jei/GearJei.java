package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.gear.GearPicks;
import com.jaymospowers.gear.arrow.ArrowHead;
import com.jaymospowers.gear.arrow.Fitting;
import com.jaymospowers.gear.arrow.HeadedArrowItem;
import java.util.ArrayList;
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
 * JEI's page for fitting an arrowhead at the Fletching Station ({@link Fitting}): the arrows and the head in, headed arrows out.
 * The rest of the gear (heads' Tool Station routes, the belt, the throwables) is on the Tinkers pages and the crafting
 * table's. Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class GearJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> FITTING = IRecipeType.create(JaymosPowers.MOD_ID, "arrowhead_fitting", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("gear_picks");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		var gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new SimpleCategory(gui, FITTING, Component.translatable("jei.jaymospowers.arrowhead_fitting"), Items.FLETCHING_TABLE, 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(FITTING, Items.FLETCHING_TABLE);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (ArrowHead head : ArrowHead.values()) {
			recipes.add(new SimpleCategory.Recipe(
				List.of(List.of(new ItemStack(Items.ARROW, Fitting.ARROWS_PER_HEAD)), List.of(new ItemStack(GearPicks.HEADS.get(head)))),
				List.of(HeadedArrowItem.of(head, Fitting.ARROWS_PER_HEAD)),
				List.of(Component.translatable("jei.jaymospowers.arrowhead_fitting.how"), Component.translatable("tooltip.jaymospowers.head." + head.id))));
		}
		registration.addRecipes(FITTING, recipes);
		registration.addIngredientInfo(GearPicks.LEATHER_BELT, Component.translatable("jei.jaymospowers.thrower_belt"));
		registration.addIngredientInfo(GearPicks.IRON_BELT, Component.translatable("jei.jaymospowers.thrower_belt"));
		registration.addIngredientInfo(GearPicks.STEEL_BELT, Component.translatable("jei.jaymospowers.thrower_belt"));
	}
}
