package com.jaymospowers.client.jei;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartBuilderMenu;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import com.jaymospowers.tinkering.smeltery.CastItem;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

/**
 * (0.11.31) JEI shows how Tinkering's things are made: parts at the Part Builder, tools at the Tool Station, metal parts,
 * ingots and casts poured at the casting table, blocks at the basin, what melts into what in the smeltery and its
 * alloys. Every recipe is read off the machines' own rules ({@link PartBuilderMenu}, {@link ToolStats}, {@link Melting}),
 * so what JEI shows is what they make. Only loaded by JEI (the {@code jei_mod_plugin} entrypoint).
 */
public final class TinkeringJei implements IModPlugin {
	static final IRecipeType<SimpleCategory.Recipe> PART_BUILDER = type("part_builder"), TOOL_STATION = type("tool_station"),
		CASTING = type("casting"), CASTING_BASIN = type("casting_basin"), MELTING = type("melting"), ALLOYING = type("alloying");

	private static IRecipeType<SimpleCategory.Recipe> type(String id) {
		return IRecipeType.create(JaymosPowers.MOD_ID, id, SimpleCategory.Recipe.class);
	}

	@Override
	public Identifier getPluginUid() {
		return JaymosPowers.id("tinkering");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
			new SimpleCategory(gui, PART_BUILDER, title("part_builder"), Tinkering.PART_BUILDER, 1),
			new SimpleCategory(gui, TOOL_STATION, title("tool_station"), Tinkering.TOOL_STATION, 0),
			new SimpleCategory(gui, CASTING, title("casting"), Tinkering.CASTING_TABLE, 2),
			new SimpleCategory(gui, CASTING_BASIN, title("casting_basin"), Tinkering.CASTING_BASIN, 1),
			new SimpleCategory(gui, MELTING, title("melting"), Tinkering.SMELTERY, 1),
			new SimpleCategory(gui, ALLOYING, title("alloying"), Tinkering.SMELTERY, 3));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addCraftingStation(PART_BUILDER, Tinkering.PART_BUILDER);
		registration.addCraftingStation(TOOL_STATION, Tinkering.TOOL_STATION);
		registration.addCraftingStation(CASTING, Tinkering.CASTING_TABLE);
		registration.addCraftingStation(CASTING_BASIN, Tinkering.CASTING_BASIN);
		registration.addCraftingStation(MELTING, Tinkering.SMELTERY);
		registration.addCraftingStation(ALLOYING, Tinkering.SMELTERY);
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		// every item once: which material it is, what it melts into, which cast shape it has
		Map<TinkerMaterial, List<ItemStack>> materials = new EnumMap<>(TinkerMaterial.class);
		Map<Molten, List<ItemStack>> melts = new EnumMap<>(Molten.class);
		List<SimpleCategory.Recipe> melting = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			TinkerMaterial material = TinkerMaterial.of(stack);
			if (material != null) {
				materials.computeIfAbsent(material, m -> new ArrayList<>()).add(stack);
			}
			if (!(item instanceof ToolPartItem) && !(item instanceof WeaponPartItem)) {
				melted(stack, melting, melts);
			}
		}
		for (PartType part : PartType.values()) {
			for (TinkerMaterial material : TinkerMaterial.values()) {
				if (Molten.of(material) != null && material.makes(part.kind)) {
					melted(ToolPartItem.of(part, material, 1), melting, melts);
				}
			}
		}
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			WeaponParts.castResults(kind).forEach(head -> melted(head, melting, melts));
		}

		List<SimpleCategory.Recipe> parts = partBuilder(materials);
		weaponHeads(parts, materials);
		registration.addRecipes(PART_BUILDER, parts);
		List<SimpleCategory.Recipe> tools = toolStation();
		weaponAssembly(tools);
		registration.addRecipes(TOOL_STATION, tools);
		registration.addRecipes(CASTING, casting());
		registration.addRecipes(CASTING_BASIN, basin());
		registration.addRecipes(MELTING, melting);
		registration.addRecipes(ALLOYING, alloying(melts));
	}

	/** A pattern and enough of a material: each part of each material that's shaped here (metal parts are cast). */
	private static List<SimpleCategory.Recipe> partBuilder(Map<TinkerMaterial, List<ItemStack>> materials) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (PartType part : PartType.values()) {
			for (TinkerMaterial material : TinkerMaterial.values()) {
				List<ItemStack> items = materials.get(material);
				if (material.cast || !material.makes(part.kind) || items == null) {
					continue;
				}
				List<ItemStack> needed = items.stream().map(s -> s.copyWithCount(PartBuilderMenu.itemsNeeded(part, s))).toList();
				recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Tinkering.PATTERN)), needed),
					List.of(ToolPartItem.of(part, material, 1)), List.of(note("part_builder.cost", part.cost))));
			}
		}
		return recipes;
	}

	/** Each tool, shown in each head material with every part of that material (any mix of materials works). */
	private static List<SimpleCategory.Recipe> toolStation() {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (ToolType type : ToolType.values()) {
			for (TinkerMaterial material : TinkerMaterial.heads()) {
				List<List<ItemStack>> inputs = new ArrayList<>();
				List<TinkerMaterial> used = new ArrayList<>();
				for (ToolType.Slot slot : type.parts) {
					inputs.add(List.of(ToolPartItem.of(slot.part(), material, 1)));
					used.add(material);
				}
				recipes.add(new SimpleCategory.Recipe(inputs, List.of(ToolStats.build(type, used)), List.of()));
			}
		}
		return recipes;
	}

	/** Liquid poured over a cast (a part, an ingot, a nugget), and gold over an item to make the cast of its shape. */
	private static List<SimpleCategory.Recipe> casting() {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Map.Entry<CastItem.Shape, Item> cast : Tinkering.CASTS.entrySet()) {
			if (cast.getKey() == CastItem.Shape.WEAPON) {
				weaponCasting(recipes);
				continue;
			}
			ItemStack castStack = new ItemStack(cast.getValue());
			for (Molten molten : Molten.values()) {
				ItemStack result = Melting.tableResult(castStack, molten);
				if (result != null && !result.isEmpty()) {
					recipes.add(new SimpleCategory.Recipe(List.of(List.of(castStack)), List.of(result),
						List.of(pour(Melting.tableAmount(castStack, molten), molten), note("casting.kept"))));
				}
			}
			List<ItemStack> shaped = new ArrayList<>();
			CastItem.Shape shape = cast.getKey();
			if (shape.part != null) {
				for (TinkerMaterial material : TinkerMaterial.values()) {
					if (material.makes(shape.part.kind)) {
						shaped.add(ToolPartItem.of(shape.part, material, 1));
					}
				}
			} else {
				for (Item item : BuiltInRegistries.ITEM) {
					ItemStack stack = new ItemStack(item);
					if (!(item instanceof CastItem) && CastItem.Shape.of(stack) == shape) {
						shaped.add(stack);
					}
				}
			}
			if (!shaped.isEmpty()) {
				recipes.add(new SimpleCategory.Recipe(List.of(shaped), List.of(castStack),
					List.of(pour(Melting.tableAmount(shaped.getFirst(), Molten.GOLD), Molten.GOLD), note("casting.used_up"))));
			}
		}
		return recipes;
	}

	/** (0.11.74) Each weapon head: gold over any of them makes its cast, and metal over the cast makes the head. */
	private static void weaponCasting(List<SimpleCategory.Recipe> recipes) {
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			ItemStack castStack = WeaponParts.cast(kind);
			for (Molten molten : Molten.values()) {
				ItemStack result = WeaponParts.castResult(kind, molten);
				if (result != null) {
					recipes.add(new SimpleCategory.Recipe(List.of(List.of(castStack)), List.of(result),
						List.of(pour(kind.mb(), molten), note("casting.kept"))));
				}
			}
			recipes.add(new SimpleCategory.Recipe(List.of(WeaponParts.heads(kind)), List.of(castStack),
				List.of(pour(Molten.INGOT, Molten.GOLD), note("casting.used_up"))));
		}
	}

	/** (0.11.74) Stone weapon heads at the Part Builder. */
	private static void weaponHeads(List<SimpleCategory.Recipe> recipes, Map<TinkerMaterial, List<ItemStack>> materials) {
		List<ItemStack> stone = new ArrayList<>();
		materials.forEach((material, items) -> {
			if (PartBuilderMenu.shapesWeapons(material)) {
				stone.addAll(items);
			}
		});
		if (stone.isEmpty()) {
			return;
		}
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			List<ItemStack> needed = stone.stream().map(s -> s.copyWithCount(PartBuilderMenu.itemsNeeded(kind.stoneCost(), s))).toList();
			recipes.add(new SimpleCategory.Recipe(List.of(List.of(new ItemStack(Tinkering.PATTERN)), needed),
				List.of(WeaponPartItem.of(kind, WeaponParts.Metal.STONE, 1)), List.of(note("part_builder.cost", kind.stoneCost()))));
		}
	}

	/** (0.11.74) Each weapon from its head (every metal it comes in), a Tool Handle and a Tool Binding of any material. */
	private static void weaponAssembly(List<SimpleCategory.Recipe> recipes) {
		List<ItemStack> rods = new ArrayList<>(), bindings = new ArrayList<>();
		for (TinkerMaterial material : TinkerMaterial.values()) {
			if (material.makes(PartType.Kind.HANDLE)) {
				rods.add(ToolPartItem.of(PartType.TOOL_HANDLE, material, 1));
			}
			if (material.makes(PartType.Kind.BINDING)) {
				bindings.add(ToolPartItem.of(PartType.TOOL_BINDING, material, 1));
			}
		}
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			for (ItemStack head : WeaponParts.heads(kind)) {
				ItemStack weapon = WeaponPartItem.weapon(head);
				if (weapon == null) {
					continue;
				}
				List<List<ItemStack>> inputs = new ArrayList<>();
				inputs.add(List.of(head));
				if (kind.assembly() == WeaponParts.Assembly.ROD_BINDING) {
					inputs.add(rods);
				}
				if (kind.assembly() != WeaponParts.Assembly.ALONE) {
					inputs.add(bindings);
				}
				recipes.add(new SimpleCategory.Recipe(inputs, List.of(weapon), List.of()));
			}
		}
	}

	private static List<SimpleCategory.Recipe> basin() {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Molten molten : Molten.values()) {
			Melting.BasinResult result = Melting.basinResult(molten);
			if (result != null) {
				recipes.add(new SimpleCategory.Recipe(List.of(), List.of(result.result()), List.of(pour(result.amount(), molten))));
			}
		}
		return recipes;
	}

	/** One item into the smeltery: what it melts into (and it's a source of that liquid for the alloys). */
	private static void melted(ItemStack stack, List<SimpleCategory.Recipe> recipes, Map<Molten, List<ItemStack>> melts) {
		Melting.Result result = Melting.of(stack);
		if (result == null) {
			return;
		}
		melts.computeIfAbsent(result.molten(), m -> new ArrayList<>()).add(stack);
		recipes.add(new SimpleCategory.Recipe(List.of(List.of(stack)), List.of(),
			List.of(note("melting.into", Molten.amount(result.amount(), result.molten().metal()), result.molten().displayName()))));
	}

	/** Liquids mixed in the smeltery: each input slot shows what melts into that liquid; the alloy's ingot comes out. */
	private static List<SimpleCategory.Recipe> alloying(Map<Molten, List<ItemStack>> melts) {
		List<SimpleCategory.Recipe> recipes = new ArrayList<>();
		for (Melting.Alloy alloy : Melting.ALLOYS) {
			List<List<ItemStack>> inputs = new ArrayList<>();
			List<Component> notes = new ArrayList<>();
			for (Melting.Input input : alloy.inputs()) {
				inputs.add(melts.getOrDefault(input.molten(), List.of()));
				notes.add(note("alloying.in", Molten.amount(input.amount(), false), input.molten().displayName()));
			}
			notes.add(note("alloying.out", Molten.amount(alloy.amount(), false), alloy.output().displayName()));
			Item ingot = Tinkering.ALLOY_INGOTS.get(alloy.output());
			recipes.add(new SimpleCategory.Recipe(inputs, ingot == null ? List.of() : List.of(new ItemStack(ingot)), notes));
		}
		return recipes;
	}

	private static Component pour(int amount, Molten molten) {
		return note("casting.pour", Molten.amount(amount, molten.metal()), molten.displayName());
	}

	private static Component title(String id) {
		return Component.translatable("jei.jaymospowers." + id);
	}

	private static Component note(String key, Object... args) {
		return Component.translatable("jei.jaymospowers." + key, args);
	}
}
