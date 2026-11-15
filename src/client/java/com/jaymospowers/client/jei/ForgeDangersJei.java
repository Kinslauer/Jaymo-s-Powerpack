package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.dangers.Cracks;
import com.jaymospowers.tinkering.dangers.ForgeDangers;
import com.jaymospowers.tinkering.dangers.SmithingGloves;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Andy's GT New Horizons picks, patch 5 ({@link ForgeDangers}): JEI's Creature Melting page (what each creature that's
 * made of something gives the smeltery at each burn, besides its blood), its Mending page (a cracked seared or kiln brick,
 * grout and a hammer), and the smithing gloves' and grout's info. The orb and rod casts and what they cast show on
 * Tinkering's casting page ({@link TinkeringJei}), the gloves' recipe on JEI's crafting page. Only loaded by JEI.
 */
public final class ForgeDangersJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> CREATURE_MELTING = type("creature_melting"), MENDING = type("brick_mending");

	private static IRecipeType<SimpleCategory.Recipe> type(String id) {
		return IRecipeType.create(JaymosPowers.MOD_ID, id, SimpleCategory.Recipe.class);
	}

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("forge_dangers");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, CREATURE_MELTING, Component.translatable("jei.jaymospowers.creature_melting"), Tinkering.SMELTERY, 2),
			new SimpleCategory(gui, MENDING, Component.translatable("jei.jaymospowers.brick_mending"), Tinkering.GROUT, 1));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(CREATURE_MELTING, Tinkering.SMELTERY);
		registration.addCraftingStation(MENDING, Tinkering.GROUT);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> melts = new ArrayList<>();
		for (Melting.Creature creature : Melting.CREATURES) {
			ItemStack egg = SpawnEggItem.byId(creature.type()).map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
			if (egg.isEmpty()) {
				continue;
			}
			melts.add(new SimpleCategory.Recipe(List.of(List.of(egg)), List.of(), List.of(
				burn(Molten.BLOOD, com.jaymospowers.tinkering.smeltery.SmelteryEntity.COOK_BLOOD), burn(creature.molten(), creature.perBurn()))));
		}
		registration.addRecipes(CREATURE_MELTING, melts);

		List<ItemStack> hammers = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.is(ForgeDangers.HAMMERS)) {
				hammers.add(stack);
			}
		}
		List<SimpleCategory.Recipe> mendings = new ArrayList<>();
		Cracks.mendings().forEach((cracked, whole) -> mendings.add(new SimpleCategory.Recipe(
			List.of(List.of(cracked), List.of(new ItemStack(Tinkering.GROUT)), hammers), List.of(whole),
			List.of(Component.translatable("jei.jaymospowers.brick_mending.how")))));
		registration.addRecipes(MENDING, mendings);

		registration.addIngredientInfo(SmithingGloves.ITEM, Component.translatable("jei.jaymospowers.smithing_gloves"));
		registration.addIngredientInfo(Tinkering.GROUT, Component.translatable("jei.jaymospowers.grout.mends"));
	}

	private static Component burn(Molten molten, int amount) {
		return Component.translatable("jei.jaymospowers.creature_melting.burn", Molten.amount(amount, molten.metal()), molten.displayName());
	}
}
