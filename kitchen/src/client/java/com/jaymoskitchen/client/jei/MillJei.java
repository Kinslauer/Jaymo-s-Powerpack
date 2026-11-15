package com.jaymoskitchen.client.jei;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mill.MachineFluid;
import com.jaymoskitchen.mill.MachineKind;
import com.jaymoskitchen.mill.MachineRecipes;
import com.jaymoskitchen.mill.Mill;
import com.jaymoskitchen.mill.power.Power;
import com.jaymoskitchen.mill.power.SailKind;
import com.jaymoskitchen.mill.steam.BoilerMetal;
import com.jaymoskitchen.mill.steam.Steam;
import net.minecraft.core.component.DataComponents;
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
	static final IRecipeType<SimpleCategory.Recipe> GRINDING = type("grinding"), CHOPPING = type("chopping"), PRESSING = type("pressing"),
		DRYING = type("drying"), FORAGING = type("foraging"), WIND = type("wind"), STEAM = type("steam"), POWER_BUILD = type("power_build");

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
			new SimpleCategory(gui, PRESSING, title("pressing"), Mill.MACHINES.get(MachineKind.PRESS), 2),
			new SimpleCategory(gui, DRYING, title("drying"), com.jaymoskitchen.tanning.Hides.DRYING_RACK, 1),
			new SimpleCategory(gui, FORAGING, title("foraging"), Items.WOODEN_HOE, 2),
			// (GT New Horizons picks, patch 8) sails and the windsock, the boiler's fuel and water, and how each is built
			new SimpleCategory(gui, WIND, title("wind"), Power.WINDMILL_ITEM, 2),
			new SimpleCategory(gui, STEAM, title("steam"), Steam.COPPER_BOILER, 2),
			new SimpleCategory(gui, POWER_BUILD, title("power_build"), Power.GEARBOX, 3));
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
		registration.addCraftingStation(DRYING, com.jaymoskitchen.tanning.Hides.DRYING_RACK);
		for (ItemStack hoe : hoes()) {
			registration.addCraftingStation(FORAGING, hoe);
		}
		registration.addCraftingStation(WIND, Power.WINDMILL_ITEM, Power.WINDSOCK_ITEM);
		registration.addCraftingStation(STEAM, Steam.FIREBOX_ITEM, Steam.COPPER_BOILER_ITEM, Steam.BRONZE_BOILER_ITEM, Steam.ENGINE_ITEM);
		registration.addCraftingStation(POWER_BUILD, Power.SHAFT_ITEM);
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
		// (0.5.27) the drying rack: salted hides to dried, wet tanned hides to leather
		List<SimpleCategory.Recipe> drying = new ArrayList<>();
		for (Item item : List.of(com.jaymoskitchen.tanning.Hides.SALTED_HIDE, com.jaymoskitchen.tanning.Hides.WET_TANNED_HIDE)) {
			ItemStack stack = new ItemStack(item);
			drying.add(new SimpleCategory.Recipe(List.of(List.of(stack)), List.of(com.jaymoskitchen.tanning.Hides.dried(stack)),
				List.of(note("drying.time", com.jaymoskitchen.tanning.Hides.DRY_TICKS / 1200))));
		}
		registration.addRecipes(DRYING, drying);
		// (GT New Horizons picks) seeds a hoe scratches from grass too far from water for farmland, land by land
		List<ItemStack> hoes = hoes();
		List<SimpleCategory.Recipe> foraging = new ArrayList<>();
		for (com.jaymoskitchen.farm.DryFields.Forage forage : com.jaymoskitchen.farm.DryFields.FORAGE) {
			foraging.add(new SimpleCategory.Recipe(List.of(hoes, List.of(new ItemStack(Items.GRASS_BLOCK))), List.of(new ItemStack(forage.seed().get())),
				List.of(note("foraging.where." + forage.where()), note("foraging.odds", com.jaymoskitchen.farm.DryFields.FORAGE_ODDS))));
		}
		registration.addRecipes(FORAGING, foraging);
		powerPages(registration);
		// (Hardrock picks) warmth you build: how the hearth, icebox, thermometer, linings and warm or cold water work
		registration.addIngredientInfo(com.jaymoskitchen.climate.Warmth.HEARTH_BLOCK, note("hearth"));
		registration.addIngredientInfo(com.jaymoskitchen.climate.Warmth.ICEBOX_BLOCK, note("icebox"));
		registration.addIngredientInfo(com.jaymoskitchen.climate.Warmth.THERMOMETER, note("thermometer"));
		registration.addIngredientInfo(Items.LOOM, note("lining"));
		// unpurified water and the ways to purify it
		registration.addIngredientInfo(com.jaymoskitchen.climate.Climate.CLEAN_WATER, note("purifying_water"));
		registration.addIngredientInfo(com.jaymoskitchen.climate.Climate.WATER_FILTER, note("purifying_water"));
		// (GT New Horizons picks) cactus water: its recipe shows as a plain crafting recipe
		registration.addIngredientInfo(com.jaymoskitchen.climate.Climate.CACTUS_WATER, note("cactus_water"));
		for (Item skin : List.of(com.jaymoskitchen.climate.Climate.WATERSKIN_LEATHER, com.jaymoskitchen.climate.Climate.WATERSKIN_IRON,
			com.jaymoskitchen.climate.Climate.WATERSKIN_DRAGON)) {
			registration.addIngredientInfo(skin, note("waterskin_temp"));
		}
		// (Hardrock picks) food that doesn't keep: rot, salting, smoking, cellars
		registration.addIngredientInfo(com.jaymoskitchen.fresh.Freshness.SPOILED_FOOD, note("spoiled_food"));
		registration.addIngredientInfo(com.jaymoskitchen.tanning.Hides.SALT, note("spoiled_food"));
		registration.addIngredientInfo(com.jaymoskitchen.fresh.Cheese.CHEESE_WHEEL, note("cheese_wheel"));
		registration.addIngredientInfo(com.jaymoskitchen.rats.Rats.SNAP_TRAP, note("traps_small_game"));
		registration.addIngredientInfo(com.jaymoskitchen.rats.Rats.CAGE_TRAP, note("traps_small_game"));
		// (Hardrock picks, the parts #1253 left out) furs and the tailor, damp, preserving, soot, tired fields, familiar animals
		for (Item fur : List.of(com.jaymoskitchen.climate.Furs.WOLF_FUR, com.jaymoskitchen.climate.Furs.BEAR_FUR, com.jaymoskitchen.climate.Furs.WILD_FUR)) {
			registration.addIngredientInfo(fur, note("fur_lining"));
		}
		registration.addIngredientInfo(Items.LOOM, note("tailor"));
		registration.addIngredientInfo(com.jaymoskitchen.climate.Warmth.THERMOMETER, note("damp"));
		registration.addIngredientInfo(com.jaymoskitchen.tanning.Hides.DRYING_RACK, note("drying_food"));
		registration.addIngredientInfo(Items.BRUSH, note("familiar_brush"));
		registration.addIngredientInfo(Items.FURNACE, note("soot"));
		registration.addIngredientInfo(Items.BLAST_FURNACE, note("soot"));
		registration.addIngredientInfo(Items.BONE_MEAL, note("tired_fields"));
		registration.addIngredientInfo(com.jaymoskitchen.delight.DelightFarm.ORGANIC_COMPOST, note("tired_fields"));
	}

	/**
	 * (GT New Horizons picks, patch 8) Each sail with what patches it and the windmill it goes on (how hard it pulls,
	 * how long it lasts, how it stands a storm); the windsock; each boiler with the fuel and water it takes and the
	 * engine it runs (what it holds, how hard it drives); and the two builds, wind and steam, down to a horse mill.
	 */
	private static void powerPages(IRecipeRegistration registration) {
		List<SimpleCategory.Recipe> wind = new ArrayList<>();
		List<ItemStack> sails = new ArrayList<>();
		for (SailKind kind : SailKind.values()) {
			ItemStack sail = new ItemStack(kind.item());
			sails.add(sail);
			String storms = kind.tear >= 1.0F / 400.0F ? "often" : kind.tear >= 1.0F / 1500.0F ? "sometimes" : kind.tear >= 1.0F / 2500.0F
				? "rarely" : "seldom";
			wind.add(new SimpleCategory.Recipe(List.of(List.of(sail), tagged(kind.patch), List.of(new ItemStack(Power.WINDMILL_ITEM))), List.of(),
				List.of(note("wind.sail", Math.round(kind.pull * 100.0F), Math.round(kind.life / 1200.0F * 10.0F) / 10.0F),
					note("wind.storms." + storms))));
		}
		wind.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Power.WINDSOCK_ITEM))), List.of(),
			List.of(note("wind.windsock"), Component.empty())));
		registration.addRecipes(WIND, wind);

		List<ItemStack> fuels = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.has(DataComponents.COOKING_FUEL)) {
				fuels.add(stack);
			}
		}
		List<ItemStack> water = tagged(Steam.BOILER_WATER);
		List<SimpleCategory.Recipe> steam = new ArrayList<>();
		for (BoilerMetal metal : BoilerMetal.values()) {
			Item boiler = metal == BoilerMetal.COPPER ? Steam.COPPER_BOILER_ITEM : Steam.BRONZE_BOILER_ITEM;
			steam.add(new SimpleCategory.Recipe(List.of(fuels, water, List.of(new ItemStack(Steam.FIREBOX_ITEM)), List.of(new ItemStack(boiler))),
				List.of(new ItemStack(Steam.ENGINE_ITEM)),
				List.of(note("steam.holds", metal.water / 1000), note("steam.drives", metal.conducts))));
		}
		registration.addRecipes(STEAM, steam);

		ItemStack mill = new ItemStack(Mill.MACHINES.get(MachineKind.GRINDSTONE));
		List<ItemStack> boilers = List.of(new ItemStack(Steam.COPPER_BOILER_ITEM), new ItemStack(Steam.BRONZE_BOILER_ITEM));
		registration.addRecipes(POWER_BUILD, List.of(
			new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Power.SHAFT_ITEM)), List.of(new ItemStack(Power.WINDMILL_ITEM)), sails,
				List.of(new ItemStack(Power.WINDSOCK_ITEM))), List.of(mill),
				List.of(note("power_build.windmill.1"), note("power_build.windmill.2"), note("power_build.windmill.3"))),
			new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Steam.FIREBOX_ITEM)), boilers, List.of(new ItemStack(Steam.ENGINE_ITEM)),
				List.of(new ItemStack(Power.SHAFT_ITEM))), List.of(mill),
				List.of(note("power_build.steam.1"), note("power_build.steam.2"), note("power_build.steam.3")))));
	}

	/** Everything in an item tag, for a slot to cycle through. */
	private static List<ItemStack> tagged(net.minecraft.tags.TagKey<Item> tag) {
		List<ItemStack> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (stack.is(tag)) {
				out.add(stack);
			}
		}
		return out;
	}

	/** Everything that works the ground as a hoe. */
	private static List<ItemStack> hoes() {
		List<ItemStack> hoes = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			if (com.jaymoskitchen.farm.DryFields.isHoe(stack)) {
				hoes.add(stack);
			}
		}
		return hoes;
	}

	private static Component title(String id) {
		return Component.translatable("jei.jaymoskitchen." + id);
	}

	private static Component note(String key, Object... args) {
		return Component.translatable("jei.jaymoskitchen." + key, args);
	}
}
