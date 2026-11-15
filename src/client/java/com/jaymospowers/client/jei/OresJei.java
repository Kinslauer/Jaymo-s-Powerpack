package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.Ores;
import com.jaymospowers.tinkering.ore.OreWashing;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Andy's GT New Horizons picks, patch 7 ({@link Ores}): JEI's Ore Washing page (each crushed ore in a water cauldron or
 * running water: its washed ore, the nugget that rides along and its vein partner's tiny dust, with their chances) and
 * Small Ores page (each small ore, stone and deepslate, breaks into its crushed ore), and info pages for the dowsing rod,
 * the small ores and the tiny dusts. The rod's recipe, the tiny dusts into a dust and the hammer crushing are on JEI's
 * crafting page; the tiny dusts' melting on Tinkering's ({@link TinkeringJei}). Only loaded by JEI, on {@link SimpleCategory}.
 */
public final class OresJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> WASHING = type("ore_washing"), SMALL_ORES = type("small_ores");

	private static IRecipeType<SimpleCategory.Recipe> type(String id) {
		return IRecipeType.create(JaymosPowers.MOD_ID, id, SimpleCategory.Recipe.class);
	}

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("ores");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, WASHING, Component.translatable("jei.jaymospowers.ore_washing"), Items.CAULDRON, 2),
			new SimpleCategory(gui, SMALL_ORES, Component.translatable("jei.jaymospowers.small_ores"), Ores.Metal.IRON.small, 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(WASHING, Items.CAULDRON);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> water = List.of(new ItemStack(Items.CAULDRON), new ItemStack(Items.WATER_BUCKET));
		List<SimpleCategory.Recipe> washing = new ArrayList<>();
		for (OreWashing.Ore ore : OreWashing.Ore.values()) {
			List<ItemStack> byproducts = new ArrayList<>();
			for (Item item : new Item[] {ore.byproduct(), ore.partner()}) {
				if (item != null) {
					byproducts.add(new ItemStack(item));
				}
			}
			washing.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(ore.crushed)), water), List.of(new ItemStack(ore.purified)),
				List.of(Component.translatable("jei.jaymospowers.ore_washing.chances", percent(OreWashing.BYPRODUCT_CHANCE),
					percent(OreWashing.PARTNER_CHANCE)), Component.translatable("jei.jaymospowers.ore_washing.where")), byproducts));
		}
		SimpleCategory.addRecipes(registration, WASHING, washing);

		List<SimpleCategory.Recipe> small = new ArrayList<>();
		for (Ores.Metal metal : Ores.Metal.values()) {
			small.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(metal.small), new ItemStack(metal.smallDeep))),
				List.of(new ItemStack(metal.ore.crushed)), List.of(Component.translatable("jei.jaymospowers.small_ores.where"),
				Component.translatable("jei.jaymospowers.small_ores.tool"))));
			registration.addIngredientInfo(metal.small, Component.translatable("jei.jaymospowers.small_ore"));
			registration.addIngredientInfo(metal.smallDeep, Component.translatable("jei.jaymospowers.small_ore"));
		}
		SimpleCategory.addRecipes(registration, SMALL_ORES, small);

		registration.addIngredientInfo(Ores.DOWSING_ROD, Component.translatable("jei.jaymospowers.dowsing_rod.1"),
			Component.translatable("jei.jaymospowers.dowsing_rod.2", com.jaymospowers.ground.DowsingRodItem.REACH));
		for (Item tiny : new Item[] {Ores.TINY_IRON_DUST, Ores.TINY_COPPER_DUST, Ores.TINY_TIN_DUST}) {
			registration.addIngredientInfo(tiny, Component.translatable("jei.jaymospowers.tiny_dust"));
		}
	}

	private static int percent(float chance) {
		return Math.round(chance * 100.0F);
	}
}
