package com.jaymospowers.aether.gear;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFlora;
import com.jaymospowers.aether.AetherItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the altar does (notes: building.md part 2 A): one item in, one out, for a number of ambrosium shards (the first N fuel
 * slots must each hold one; each craft uses one from each). Damaged gear is repaired, by the tier of the gear; quicksoil is
 * fired to quicksoil glass; ore and inert arkenium are refined to arkenium plates; fruit is enchanted.
 */
public final class AltarRecipes {
	// ---- tuning: shards a repair takes, by tier ----
	public static final int SKYROOT = 1, BEAST_PELT = 1, HOLYSTONE = 2, BURRUKAI = 3, ZANITE = 4, ARKENIUM = 5, NEPTUNE = 6, GRAVITITE = 8;
	/** Glider repairs: cold, golden, blue and purple. */
	public static final int COLD_GLIDER = 1, GOLDEN_GLIDER = 3, BLUE_GLIDER = 2, PURPLE_GLIDER = 2;
	/** Shards the other recipes take. */
	public static final int QUICKSOIL_GLASS = 1, PLATE_FROM_INERT = 3, PLATE_FROM_ORE = 4, ENCHANT_FRUIT = 1, ENCHANT_WYNDBERRY = 2;

	/** What a recipe makes and how many shards it takes. */
	public record Recipe(ItemStack result, int fuel) {
	}

	/** Shards needed to repair each repairable item. */
	private static final Map<Item, Integer> REPAIRS = new HashMap<>();
	/** What an item turns into; stacks are made only when asked for, since an ItemStack can't exist before the registries bind. */
	private record Conversion(Item result, int fuel) {
	}

	/** Items that turn into something else. */
	private static final Map<Item, Conversion> CONVERSIONS = new HashMap<>();

	private AltarRecipes() {
	}

	static void init() {
		for (GearTier tier : GearTier.values()) {
			int fuel = switch (tier) {
				case SKYROOT -> SKYROOT;
				case HOLYSTONE -> HOLYSTONE;
				case ZANITE -> ZANITE;
				case ARKENIUM -> ARKENIUM;
				case GRAVITITE -> GRAVITITE;
			};
			for (Map<GearTier, Item> kind : java.util.List.of(AetherGear.SHORTSWORD, AetherGear.HAMMER, AetherGear.PICKAXE, AetherGear.AXE,
				AetherGear.SHOVEL, AetherGear.HOE)) {
				REPAIRS.put(kind.get(tier), fuel);
			}
		}
		REPAIRS.put(AetherGear.ZANITE_SHEARS, ZANITE);
		for (ArmorSet set : ArmorSet.values()) {
			int fuel = switch (set) {
				case BEAST_PELT -> BEAST_PELT;
				case BURRUKAI_PLATE -> BURRUKAI;
				case ZANITE -> ZANITE;
				case ARKENIUM -> ARKENIUM;
				case GRAVITITE -> GRAVITITE;
				case NEPTUNE -> NEPTUNE;
			};
			for (Item piece : set.pieces) {
				REPAIRS.put(piece, fuel);
			}
		}
		REPAIRS.put(AetherGear.GLIDERS.get(AercloudGliderItem.Kind.COLD), COLD_GLIDER);
		REPAIRS.put(AetherGear.GLIDERS.get(AercloudGliderItem.Kind.GOLDEN), GOLDEN_GLIDER);
		REPAIRS.put(AetherGear.GLIDERS.get(AercloudGliderItem.Kind.BLUE), BLUE_GLIDER);
		REPAIRS.put(AetherGear.GLIDERS.get(AercloudGliderItem.Kind.PURPLE), PURPLE_GLIDER);

		convert(AetherBlocks.QUICKSOIL.asItem(), AetherBlocks.QUICKSOIL_GLASS.asItem(), QUICKSOIL_GLASS);
		convert(AetherItems.INERT_ARKENIUM, AetherGear.ARKENIUM_PLATE, PLATE_FROM_INERT);
		convert(AetherBlocks.ARKENIUM_ORE.asItem(), AetherGear.ARKENIUM_PLATE, PLATE_FROM_ORE);
		convert(AetherBlocks.UNDERSHALE_ARKENIUM_ORE.asItem(), AetherGear.ARKENIUM_PLATE, PLATE_FROM_ORE);
		convert(AetherItems.BLUE_BERRY, AetherGear.ENCHANTED_BLUE_BERRY, ENCHANT_FRUIT);
		convert(AetherFlora.ORANGE, AetherGear.ENCHANTED_ORANGE, ENCHANT_FRUIT);
		convert(AetherFlora.WYNDBERRY, AetherGear.ENCHANTED_WYNDBERRY, ENCHANT_WYNDBERRY);
	}

	private static void convert(Item from, Item to, int fuel) {
		CONVERSIONS.put(from, new Conversion(to, fuel));
	}

	/** The recipe for what is in the input slot, or null. */
	public static @Nullable Recipe find(ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}
		Integer repair = REPAIRS.get(input.getItem());
		if (repair != null) {
			if (!input.isDamaged()) {
				return null;
			}
			ItemStack fixed = input.copyWithCount(1);
			fixed.setDamageValue(0);
			return new Recipe(fixed, repair);
		}
		Conversion conversion = CONVERSIONS.get(input.getItem());
		return conversion == null ? null : new Recipe(new ItemStack(conversion.result()), conversion.fuel());
	}
}
