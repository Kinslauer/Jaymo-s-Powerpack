package com.jaymoskitchen.mill;

import com.jaymoskitchen.ModItems;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What the machines make (Horse Power's default recipes, brought up to date, plus a few for the kitchen: flour for
 * dough, and seeds pressed for cooking oil).
 */
public final class MachineRecipes {
	private MachineRecipes() {
	}

	// --- grinding ---

	/** (kept as items and counts: stacks can't be made before the game's components are bound) */
	private record Out(Item item, int count) {
	}

	private static final Map<Item, Out> GRIND = Map.ofEntries(
		Map.entry(Items.SUGAR_CANE, new Out(Items.SUGAR, 1)),
		Map.entry(Items.BONE, new Out(Items.BONE_MEAL, 3)),
		Map.entry(Items.BONE_BLOCK, new Out(Items.BONE_MEAL, 9)),
		Map.entry(Items.STONE, new Out(Items.GRAVEL, 1)),
		Map.entry(Items.COBBLESTONE, new Out(Items.GRAVEL, 1)),
		Map.entry(Items.GRAVEL, new Out(Items.SAND, 1)),
		// (0.4.48) sand ground down to flint
		Map.entry(Items.SAND, new Out(Items.FLINT, 1)),
		Map.entry(Items.BEETROOT, new Out(Items.DYE.pick(DyeColor.RED), 1)),
		Map.entry(Items.POPPY, new Out(Items.DYE.pick(DyeColor.RED), 1)),
		Map.entry(Items.RED_TULIP, new Out(Items.DYE.pick(DyeColor.RED), 1)),
		Map.entry(Items.ROSE_BUSH, new Out(Items.DYE.pick(DyeColor.RED), 2)),
		Map.entry(Items.DANDELION, new Out(Items.DYE.pick(DyeColor.YELLOW), 1)),
		Map.entry(Items.SUNFLOWER, new Out(Items.DYE.pick(DyeColor.YELLOW), 2)),
		Map.entry(Items.BLUE_ORCHID, new Out(Items.DYE.pick(DyeColor.LIGHT_BLUE), 1)),
		Map.entry(Items.ALLIUM, new Out(Items.DYE.pick(DyeColor.MAGENTA), 1)),
		Map.entry(Items.LILAC, new Out(Items.DYE.pick(DyeColor.MAGENTA), 2)),
		Map.entry(Items.AZURE_BLUET, new Out(Items.DYE.pick(DyeColor.LIGHT_GRAY), 1)),
		Map.entry(Items.OXEYE_DAISY, new Out(Items.DYE.pick(DyeColor.LIGHT_GRAY), 1)),
		Map.entry(Items.WHITE_TULIP, new Out(Items.DYE.pick(DyeColor.LIGHT_GRAY), 1)),
		Map.entry(Items.ORANGE_TULIP, new Out(Items.DYE.pick(DyeColor.ORANGE), 1)),
		Map.entry(Items.TORCHFLOWER, new Out(Items.DYE.pick(DyeColor.ORANGE), 1)),
		Map.entry(Items.PINK_TULIP, new Out(Items.DYE.pick(DyeColor.PINK), 1)),
		Map.entry(Items.PEONY, new Out(Items.DYE.pick(DyeColor.PINK), 2)),
		Map.entry(Items.CORNFLOWER, new Out(Items.DYE.pick(DyeColor.BLUE), 1)),
		Map.entry(Items.LILY_OF_THE_VALLEY, new Out(Items.DYE.pick(DyeColor.WHITE), 1)),
		Map.entry(Items.WITHER_ROSE, new Out(Items.DYE.pick(DyeColor.BLACK), 1)),
		Map.entry(Items.PITCHER_PLANT, new Out(Items.DYE.pick(DyeColor.CYAN), 2)),
		Map.entry(Items.COCOA_BEANS, new Out(Items.DYE.pick(DyeColor.BROWN), 1)));

	/** What grinding one of these makes, or null. */
	public static @Nullable ItemStack grind(ItemStack input) {
		if (input.is(Items.WHEAT)) {
			return new ItemStack(Mill.FLOUR);
		}
		// (0.5.27) bark ground into resin, for tanning hides
		if (input.is(com.jaymoskitchen.tanning.Tanning.BARK)) {
			return new ItemStack(com.jaymoskitchen.tanning.Hides.RESIN);
		}
		Out out = GRIND.get(input.getItem());
		return out == null ? null : new ItemStack(out.item(), out.count());
	}

	// --- chopping ---

	/** The planks a log chops into (hand: the crafting table's 4, plus 1; horse: plus 2), or null if it isn't a log. */
	public static @Nullable ItemStack chop(ItemStack input, boolean horse) {
		if (!input.is(ItemTags.LOGS)) {
			return null;
		}
		Optional<Item> planks = planksFor(input.getItem());
		int count = input.is(Items.BAMBOO_BLOCK) || input.is(Items.STRIPPED_BAMBOO_BLOCK) ? 2 : 4;
		return planks.map(item -> new ItemStack(item, count + (horse ? 2 : 1))).orElse(null);
	}

	/** oak_log, stripped_oak_wood, crimson_hyphae, bamboo_block... to the matching planks. */
	private static Optional<Item> planksFor(Item log) {
		Identifier id = BuiltInRegistries.ITEM.getKey(log);
		String name = id.getPath().replace("stripped_", "");
		for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae", "_block"}) {
			if (name.endsWith(suffix)) {
				name = name.substring(0, name.length() - suffix.length());
				break;
			}
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), name + "_planks"));
	}

	// --- pressing ---

	/** What the press makes from a full load: an item, or a fluid (millibuckets) to collect from it. */
	public record Pressing(int load, ItemStack item, MachineFluid fluid, int amount) {
	}

	public static @Nullable Pressing press(ItemStack input) {
		if (input.is(ItemTags.LEAVES)) {
			return new Pressing(8, ItemStack.EMPTY, MachineFluid.WATER, 1000);
		}
		if (input.is(Items.WHEAT_SEEDS) || input.is(Items.PUMPKIN_SEEDS) || input.is(Items.MELON_SEEDS) || input.is(Items.BEETROOT_SEEDS)
			|| input.is(Items.TORCHFLOWER_SEEDS) || input.is(Items.PITCHER_POD)) {
			return new Pressing(4, ItemStack.EMPTY, MachineFluid.OIL, 250);
		}
		if (input.is(Items.SUNFLOWER)) {
			return new Pressing(1, ItemStack.EMPTY, MachineFluid.OIL, 250);
		}
		return null;
	}

	/** A bottle of the press's oil. */
	public static ItemStack oilBottle() {
		return new ItemStack(ModItems.COOKING_OIL);
	}
}
