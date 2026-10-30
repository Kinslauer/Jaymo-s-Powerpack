package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.backhome.BackHome;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.jaymospowers.power.zfighter.pod.PodParts;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.smithing.Edges;
import com.jaymospowers.tinkering.smithing.Smithing;
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
 * (0.18.55) JEI's pages for what comes back from the far worlds: the Quench (a hot piece dipped in a water cauldron with a far
 * world's ice-glass or a Cinder Ember lying in it takes a Cold or a Hot Edge, {@link Edges}) and the Starlit Lens fitted to a
 * pod's beacon mast ({@link BackHome#fitLens}). Star metal's smeltery, casting and forging pages are TinkeringJei's, the lens's
 * own infusion AlchemyJei's. Only loaded by JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class BackHomeJei implements IModPlugin {
	static final int NOTES = 3;
	static final IRecipeType<SimpleCategory.Recipe> QUENCH = IRecipeType.create(JaymosPowers.MOD_ID, "quench_edge", SimpleCategory.Recipe.class);
	static final IRecipeType<SimpleCategory.Recipe> LENS = IRecipeType.create(JaymosPowers.MOD_ID, "star_lens", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("back_home");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), QUENCH, Component.translatable("jei.jaymospowers.quench_edge"), Items.CAULDRON, NOTES),
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), LENS, Component.translatable("jei.jaymospowers.star_lens"), BackHome.STARLIT_LENS, 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(QUENCH, Items.CAULDRON);
		registration.addCraftingStation(LENS, PodParts.item(PodPart.MAST));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		ItemStack hot = ToolPartItem.of(PartType.PICK_HEAD, TinkerMaterial.IRON, 1);
		hot.set(Smithing.HEATED_AT, 0L);
		List<SimpleCategory.Recipe> quench = new ArrayList<>();
		for (String edge : Edges.all()) {
			ItemStack edged = ToolPartItem.of(PartType.PICK_HEAD, TinkerMaterial.IRON, 1);
			edged.set(Edges.EDGE, edge);
			List<Component> notes = List.of(Component.translatable("jei.jaymospowers.quench_edge.how"),
				Component.translatable("jei.jaymospowers.quench_edge." + edge), Component.translatable("jei.jaymospowers.quench_edge.once"));
			quench.add(new SimpleCategory.Recipe(List.of(List.of(hot), List.of(new ItemStack(Edges.giver(edge))), List.of(new ItemStack(Items.WATER_BUCKET))),
				List.of(edged), notes));
		}
		registration.addRecipes(QUENCH, quench);
		// where the finds come from (a short line each, on the item's own page)
		registration.addIngredientInfo(BackHome.MAW_DUST, Component.translatable("jei.jaymospowers.found.maw_dust"));
		registration.addIngredientInfo(BackHome.VERDANT_FRUIT, Component.translatable("jei.jaymospowers.found.verdant_fruit"));
		registration.addIngredientInfo(BackHome.GLACIA_SALT, Component.translatable("jei.jaymospowers.found.glacia_salt"));
		registration.addIngredientInfo(Tinkering.STAR_METAL_ORE, Component.translatable("jei.jaymospowers.found.star_metal_ore"));
		registration.addRecipes(LENS, List.of(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(BackHome.STARLIT_LENS)), List.of(PodParts.itemOf(PodPart.MAST, false))),
			List.of(PodParts.itemOf(PodPart.MAST, false)), List.of(Component.translatable("jei.jaymospowers.star_lens.how"), Component.translatable("jei.jaymospowers.star_lens.shows")))));
	}
}
