package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.pod.PodGrades;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.jaymospowers.power.zfighter.pod.PodParts;
import com.jaymospowers.power.zfighter.pod.SalvageRack;
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

/**
 * (0.18.21) JEI's pages for mending a space pod: the Salvage Rack (a worn part, a hammer and the part's metal give a sound part,
 * {@link SalvageRack}) and Mended Better (a far world's own part grades a finished pod up, {@link PodGrades}). Only loaded by
 * JEI (the {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class SalvageJei implements IModPlugin {
	static final int NOTES = 3;
	static final IRecipeType<SimpleCategory.Recipe> SALVAGE = IRecipeType.create(JaymosPowers.MOD_ID, "salvage_rack", SimpleCategory.Recipe.class);
	static final IRecipeType<SimpleCategory.Recipe> MENDING = IRecipeType.create(JaymosPowers.MOD_ID, "pod_mending", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("salvage");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), SALVAGE, Component.translatable("jei.jaymospowers.salvage"), SalvageRack.BLOCK, NOTES),
			new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), MENDING, Component.translatable("jei.jaymospowers.mending"), PodGrades.partFor(1), NOTES));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(SALVAGE, SalvageRack.BLOCK);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> hammers = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.is(PodParts.HAMMERS)) {
				hammers.add(stack);
			}
		}
		List<SimpleCategory.Recipe> salvage = new ArrayList<>();
		for (PodPart part : PodPart.ORDER) {
			List<Component> notes = List.of(Component.translatable("jei.jaymospowers.salvage.how", SalvageRack.BLOWS),
				Component.translatable("jei.jaymospowers.salvage.metal"), Component.translatable("jei.jaymospowers.salvage.scrap"));
			salvage.add(new SimpleCategory.Recipe(List.of(List.of(PodParts.itemOf(part, true)), List.of(SalvageRack.metalOf(part)), hammers),
				List.of(PodParts.itemOf(part, false)), notes));
		}
		registration.addRecipes(SALVAGE, salvage);
		List<SimpleCategory.Recipe> mending = new ArrayList<>();
		for (int grade = 1; grade <= PodGrades.MAX_GRADE; grade++) {
			List<Component> notes = List.of(Component.translatable("jei.jaymospowers.mending.how"),
				Component.translatable("jei.jaymospowers.mending.grade." + grade),
				Component.translatable("jei.jaymospowers.mending.from", Component.translatable("space.jaymospowers.dest." + PodGrades.worldOf(grade))));
			mending.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(PodGrades.partFor(grade)))), List.of(PodParts.stackOf(PodPart.KEEL, false, grade)), notes));
		}
		registration.addRecipes(MENDING, mending);
	}
}
