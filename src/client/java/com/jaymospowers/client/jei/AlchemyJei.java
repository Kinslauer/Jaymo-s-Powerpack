package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.alchemy.infusion.Infusion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.18.49) JEI's Infusion Altar page: every infusion recipe the server sent ({@link Infusion#recipes()}): the centre
 * item first, then each kind of component with how many pedestals it fills, the result, and what it costs in essence,
 * research and instability. A recipe for any enchanted item shows an enchanted book. Only loaded by JEI (the
 * {@code jei_mod_plugin} entrypoint), on {@link SimpleCategory}.
 */
public final class AlchemyJei implements IModPlugin {
	static final int NOTES = 4;
	static final IRecipeType<SimpleCategory.Recipe> INFUSION = IRecipeType.create(JaymosPowers.MOD_ID, "infusion", SimpleCategory.Recipe.class);

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("alchemy");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SimpleCategory(registration.getJeiHelpers().getGuiHelper(), INFUSION,
			Component.translatable("jei.jaymospowers.infusion"), Infusion.MATRIX, NOTES));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(INFUSION, Infusion.MATRIX);
		registration.addCraftingStation(INFUSION, Infusion.PEDESTAL);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Infusion.Recipe recipe : Infusion.recipes()) {
			boolean anyEnchanted = Infusion.ANY_ENCHANTED.equals(recipe.centre());
			List<ItemStack> centre = anyEnchanted ? List.of(new ItemStack(Items.ENCHANTED_BOOK)) : matching(recipe.centre(), 1);
			if (centre.isEmpty()) {
				continue;
			}
			List<List<ItemStack>> inputs = new ArrayList<>();
			inputs.add(centre);
			// the same component on several pedestals is one slot with that count
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (String spec : recipe.components()) {
				counts.merge(spec, 1, Integer::sum);
			}
			for (Map.Entry<String, Integer> entry : counts.entrySet()) {
				List<ItemStack> choices = matching(entry.getKey(), entry.getValue());
				if (!choices.isEmpty()) {
					inputs.add(choices);
				}
			}
			List<ItemStack> output = new ArrayList<>();
			recipe.result().item().flatMap(BuiltInRegistries.ITEM::getOptional)
				.ifPresent(item -> output.add(new ItemStack(item, recipe.result().count())));
			if (output.isEmpty() && anyEnchanted) {
				output.add(new ItemStack(Items.ENCHANTED_BOOK));
			}
			List<Component> notes = new ArrayList<>();
			notes.add(Component.translatable(anyEnchanted ? "jei.jaymospowers.infusion.any_enchanted" : "jei.jaymospowers.infusion.centre"));
			notes.add(Component.translatable("jei.jaymospowers.infusion.essences", essences(recipe)));
			Research.Entry research = Research.get(recipe.research());
			notes.add(Component.translatable("jei.jaymospowers.infusion.research",
				research == null ? Component.literal(recipe.research()) : Component.translatable(research.key())));
			notes.add(Component.translatable("jei.jaymospowers.infusion.unstable", recipe.instability(), recipe.flux()));
			recipes.add(new SimpleCategory.Recipe(inputs, output, notes));
		}
		registration.addRecipes(INFUSION, recipes);
	}

	/** Every item {@code spec} (an id or {@code #tag}) names, {@code count} of each. */
	private static List<ItemStack> matching(String spec, int count) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item, count);
			if (item != Items.AIR && Infusion.itemMatches(spec, stack)) {
				stacks.add(stack);
			}
		}
		return stacks;
	}

	private static Component essences(Infusion.Recipe recipe) {
		Component line = CommonComponents.EMPTY;
		boolean first = true;
		for (Map.Entry<Essence, Integer> entry : recipe.essences().amounts().entrySet()) {
			Component one = Component.translatable("jei.jaymospowers.infusion.essence", entry.getValue(), Component.translatable(entry.getKey().key()));
			line = first ? one : line.copy().append(", ").append(one);
			first = false;
		}
		return line;
	}
}
