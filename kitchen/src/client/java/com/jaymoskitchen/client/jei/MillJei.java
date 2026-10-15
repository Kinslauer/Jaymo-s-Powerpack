package com.jaymoskitchen.client.jei;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mill.MachineFluid;
import com.jaymoskitchen.mill.MachineKind;
import com.jaymoskitchen.mill.MachineRecipes;
import com.jaymoskitchen.mill.Mill;
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
import net.minecraft.world.item.Items;

/**
 * (0.4.41) JEI shows what the mill's machines make: grinding (wheat to flour, flowers to dye...), chopping logs into
 * planks, and pressing seeds to oil and leaves to water. Read off {@link MachineRecipes}, so it's what they really make.
 * Only loaded by JEI (the {@code jei_mod_plugin} entrypoint).
 */
public final class MillJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> GRINDING = type("grinding"), CHOPPING = type("chopping"), PRESSING = type("pressing");

	private static IRecipeType<SimpleCategory.Recipe> type(String id) {
		return IRecipeType.create(JaymosKitchen.MOD_ID, id, SimpleCategory.Recipe.class);
	}

	@Override
	public Identifier getPluginUid() {
		return JaymosKitchen.id("mill");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, GRINDING, title("grinding"), Mill.MACHINES.get(MachineKind.HAND_GRINDSTONE), 1),
			new SimpleCategory(gui, CHOPPING, title("chopping"), Mill.MACHINES.get(MachineKind.CHOPPING_BLOCK), 2),
			new SimpleCategory(gui, PRESSING, title("pressing"), Mill.MACHINES.get(MachineKind.PRESS), 2));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		for (MachineKind kind : MachineKind.values()) {
			IRecipeType<SimpleCategory.Recipe> type = switch (kind.work) {
				case GRIND -> GRINDING;
				case CHOP -> CHOPPING;
				case PRESS -> PRESSING;
			};
			registration.addCraftingStation(type, Mill.MACHINES.get(kind));
		}
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> grinding = new ArrayList<>(), chopping = new ArrayList<>(), pressing = new ArrayList<>();
		Component turns = note("grinding.effort", MachineKind.HAND_GRINDSTONE.effort);
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			ItemStack ground = MachineRecipes.grind(stack);
			if (ground != null) {
				grinding.add(new SimpleCategory.Recipe(List.of(List.of(stack)), List.of(ground), List.of(turns)));
			}
			ItemStack planks = MachineRecipes.chop(stack, false);
			ItemStack horse = MachineRecipes.chop(stack, true);
			if (planks != null && horse != null) {
				chopping.add(new SimpleCategory.Recipe(List.of(List.of(stack)), List.of(planks),
					List.of(note("chopping.effort", MachineKind.CHOPPING_BLOCK.effort), note("chopping.horse", horse.getCount()))));
			}
			MachineRecipes.Pressing pressed = MachineRecipes.press(stack);
			if (pressed != null) {
				boolean oil = pressed.fluid() == MachineFluid.OIL;
				List<ItemStack> out = !pressed.item().isEmpty() ? List.of(pressed.item())
					: pressed.fluid() == MachineFluid.NONE ? List.of() : List.of(oil ? MachineRecipes.oilBottle() : new ItemStack(Items.WATER_BUCKET));
				pressing.add(new SimpleCategory.Recipe(List.of(List.of(stack.copyWithCount(pressed.load()))), out,
					List.of(note("pressing.load", pressed.load(), MachineKind.PRESS.effort),
						pressed.fluid() == MachineFluid.NONE ? Component.empty()
							: note(oil ? "pressing.oil" : "pressing.water", pressed.amount()))));
			}
		}
		registration.addRecipes(GRINDING, grinding);
		registration.addRecipes(CHOPPING, chopping);
		registration.addRecipes(PRESSING, pressing);
	}

	private static Component title(String id) {
		return Component.translatable("jei.jaymoskitchen." + id);
	}

	private static Component note(String key, Object... args) {
		return Component.translatable("jei.jaymoskitchen." + key, args);
	}
}
