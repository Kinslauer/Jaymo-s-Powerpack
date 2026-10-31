package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.XenoFinds;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
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
 * (0.18.122, Xeno-Band patch 6) JEI's pages for the aliens' own crafting ({@link XenoFinds}): Gremlin's tablets at a crafting
 * table, Bulwark's dusts at a grindstone, Pyre's smelting in its hands; and where tablets, circuits and wreck cores come from.
 * Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class XenoJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> GREMLIN = IRecipeType.create(JaymosPowers.MOD_ID, "xeno_gremlin", SimpleCategory.Recipe.class);
	static final IRecipeType<SimpleCategory.Recipe> BULWARK = IRecipeType.create(JaymosPowers.MOD_ID, "xeno_bulwark", SimpleCategory.Recipe.class);
	static final IRecipeType<SimpleCategory.Recipe> PYRE = IRecipeType.create(JaymosPowers.MOD_ID, "xeno_pyre", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("xeno_finds");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		var gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, GREMLIN, Component.translatable("jei.jaymospowers.xeno_gremlin"), XenoFinds.TABLET, 2),
			new SimpleCategory(gui, BULWARK, Component.translatable("jei.jaymospowers.xeno_bulwark"), Items.GRINDSTONE, 2),
			new SimpleCategory(gui, PYRE, Component.translatable("jei.jaymospowers.xeno_pyre"), Items.BLAZE_POWDER, 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(GREMLIN, Items.CRAFTING_TABLE);
		registration.addCraftingStation(BULWARK, Items.GRINDSTONE);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(GREMLIN, List.of(new SimpleCategory.Recipe(
			List.of(List.of(new ItemStack(XenoFinds.CIRCUIT, XenoFinds.TABLET_CIRCUITS)), List.of(new ItemStack(Items.AMETHYST_SHARD))),
			List.of(new ItemStack(XenoFinds.TABLET)),
			List.of(Component.translatable("jei.jaymospowers.xeno_gremlin.how"), Component.translatable("jei.jaymospowers.xeno_tablet.read")))));
		List<SimpleCategory.Recipe> grind = new ArrayList<>();
		for (ItemStack ore : XenoFinds.grindable()) {
			Item dust = XenoFinds.dustOf(ore);
			grind.add(new SimpleCategory.Recipe(List.of(List.of(ore)), List.of(new ItemStack(dust, XenoFinds.DUSTS_PER_ORE)),
				List.of(Component.translatable("jei.jaymospowers.xeno_bulwark.how"), Component.translatable("jei.jaymospowers.xeno_bulwark.smelt"))));
		}
		registration.addRecipes(BULWARK, grind);
		List<SimpleCategory.Recipe> pyre = new ArrayList<>();
		for (Item[] pair : new Item[][] {{Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_COPPER, Items.COPPER_INGOT}, {Items.RAW_GOLD, Items.GOLD_INGOT},
			{Items.SAND, Items.GLASS}, {Items.COBBLESTONE, Items.STONE}, {Items.BEEF, Items.COOKED_BEEF}, {Items.CLAY_BALL, Items.BRICK}}) {
			pyre.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(pair[0], XenoFinds.PYRE_BATCH))),
				List.of(new ItemStack(pair[1], XenoFinds.PYRE_BATCH)),
				List.of(Component.translatable("jei.jaymospowers.xeno_pyre.how"), Component.translatable("jei.jaymospowers.xeno_pyre.any"))));
		}
		registration.addRecipes(PYRE, pyre);
		registration.addIngredientInfo(XenoFinds.TABLET, Component.translatable("jei.jaymospowers.found.star_tablet"));
		registration.addIngredientInfo(XenoFinds.CIRCUIT, Component.translatable("jei.jaymospowers.found.probe_circuit"));
		registration.addIngredientInfo(XenoFinds.WRECK_CORE, Component.translatable("jei.jaymospowers.found.wreck_core"));
	}
}
