package com.jaymoskitchen.delight;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a knife makes of things on the cutting board (Farmer's Delight's cuts): meat into two smaller cuts that cook
 * quicker into finger food, a cabbage into leaves, a rice panicle into rice and straw; (0.5.33) a cake, a pie, a pumpkin,
 * a melon or a kelp roll into slices, and a ham into chops. (0.5.88) A cod or a salmon (a catch from Powers' real fishing, a tuna or a shark among them) is gutted and
 * cleaned into {@link #FISH_FILLETS} fillets and bone meal from the guts. (0.5.92) A fish from Powers' real fishing carries its
 * weight: the fillets go by it ({@link #KG_PER_FILLET}), and a whole tuna is cut into steaks by its weight too
 * ({@link #KG_PER_TUNA_STEAK}).
 */
public final class Chopping {
	/** Cuts from one piece of meat or fish. */
	static final int MEAT_CUTS = 2;
	/** (0.5.88) Gutting and cleaning a cod or a salmon on the board: fillets from one fish, worth more than the whole fish eaten raw. */
	static final int FISH_FILLETS = 3;
	/** (0.5.92) A weighed cod or salmon: a fillet for each this many kg, at least and at most. */
	static final float KG_PER_FILLET = 1.5F;
	static final int FILLETS_LEAST = 2, FILLETS_MOST = 8;
	/** (0.5.92) Powers' whole tuna: a steak for each this many kg, at least and at most (two for one with no weight). */
	static final float KG_PER_TUNA_STEAK = 1.5F;
	static final int TUNA_STEAKS_LEAST = 2, TUNA_STEAKS_MOST = 8;
	static final Identifier POWERS_TUNA = Identifier.fromNamespaceAndPath("jaymospowers", "tuna");
	static final Identifier POWERS_TUNA_STEAK = Identifier.fromNamespaceAndPath("jaymospowers", "tuna_steak");
	static final Identifier POWERS_FISH_WEIGHT = Identifier.fromNamespaceAndPath("jaymospowers", "fish_weight");
	/** The cooking groups (item tags, see cooking/Step) whose meats cut like beef, pork, chicken and mutton. */
	static final TagKey<Item> RED_MEAT = group("red_meat"), PORK = group("pork"), POULTRY = group("poultry"), MUTTON = group("mutton");
	static final int CABBAGE_LEAVES = 2;
	static final int PANICLE_RICE = 2;
	/** Slices from a cake, a pumpkin pie (a pie dish gives PieBlock.SLICES), a pumpkin, a melon and a kelp roll. */
	static final int CAKE_SLICES = 7;
	static final int PIE_SLICES = 4;
	static final int PUMPKIN_SLICES = 4;
	static final int MELON_SLICES = 9;
	static final int KELP_ROLL_SLICES = 3;

	private Chopping() {
	}

	private static TagKey<Item> group(String name) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("jaymoskitchen", "cooking/" + name));
	}

	/** The pieces this cuts into (fresh stacks), or empty if a knife can't do anything with it. */
	public static List<ItemStack> cut(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}
		Item item = stack.getItem();
		if (item == Items.BEEF) {
			return List.of(new ItemStack(Delight.MINCED_BEEF, MEAT_CUTS));
		}
		if (item == Items.PORKCHOP) {
			return List.of(new ItemStack(Delight.RAW_BACON, MEAT_CUTS));
		}
		if (item == Items.CHICKEN) {
			return List.of(new ItemStack(Delight.CHICKEN_CUTS, MEAT_CUTS), new ItemStack(Items.BONE_MEAL));
		}
		if (item == Items.MUTTON) {
			return List.of(new ItemStack(Delight.MUTTON_CHOPS, MEAT_CUTS));
		}
		if (item == Items.COD) {
			return List.of(new ItemStack(Delight.COD_SLICE, fillets(stack)), new ItemStack(Items.BONE_MEAL));
		}
		if (item == Items.SALMON) {
			return List.of(new ItemStack(Delight.SALMON_SLICE, fillets(stack)), new ItemStack(Items.BONE_MEAL));
		}
		// any other meat of the same kind cuts the same way (the cooking groups, see cooking/Step): venison minces like beef
		if (stack.is(RED_MEAT)) {
			return List.of(new ItemStack(Delight.MINCED_BEEF, MEAT_CUTS));
		}
		if (stack.is(PORK)) {
			return List.of(new ItemStack(Delight.RAW_BACON, MEAT_CUTS));
		}
		if (stack.is(POULTRY)) {
			return List.of(new ItemStack(Delight.CHICKEN_CUTS, MEAT_CUTS), new ItemStack(Items.BONE_MEAL));
		}
		if (stack.is(MUTTON)) {
			return List.of(new ItemStack(Delight.MUTTON_CHOPS, MEAT_CUTS));
		}
		if (BuiltInRegistries.ITEM.getKey(item).equals(POWERS_TUNA)) {
			Item steak = BuiltInRegistries.ITEM.getOptional(POWERS_TUNA_STEAK).orElse(null);
			if (steak != null) {
				Float kg = weight(stack);
				int steaks = kg == null ? TUNA_STEAKS_LEAST : Mth.clamp(Math.round(kg / KG_PER_TUNA_STEAK), TUNA_STEAKS_LEAST, TUNA_STEAKS_MOST);
				return List.of(new ItemStack(steak, steaks), new ItemStack(Items.BONE_MEAL, 2));
			}
		}
		if (item == Delight.CABBAGE) {
			return List.of(new ItemStack(Delight.CABBAGE_LEAF, CABBAGE_LEAVES));
		}
		if (item == Delight.RICE_PANICLE) {
			return List.of(new ItemStack(Delight.RICE, PANICLE_RICE), new ItemStack(Delight.STRAW));
		}
		if (item == Items.WHEAT) {
			return List.of(new ItemStack(Delight.STRAW), new ItemStack(Items.WHEAT_SEEDS));
		}
		// (0.5.33) the rest of Farmer's Delight's cuts (see DelightDishes)
		if (item == Items.CAKE) {
			return List.of(new ItemStack(DelightDishes.CAKE_SLICE, CAKE_SLICES));
		}
		if (item == Items.PUMPKIN_PIE) {
			return List.of(new ItemStack(DelightDishes.PUMPKIN_PIE_SLICE, PIE_SLICES));
		}
		if (item == Items.PUMPKIN) {
			return List.of(new ItemStack(DelightDishes.PUMPKIN_SLICE, PUMPKIN_SLICES));
		}
		if (item == Items.MELON) {
			return List.of(new ItemStack(Items.MELON_SLICE, MELON_SLICES));
		}
		if (item == DelightDishes.HAM) {
			return List.of(new ItemStack(Items.PORKCHOP, MEAT_CUTS), new ItemStack(Items.BONE));
		}
		Dish dish = ModItems.dishOf(item);
		if (dish == Dish.SMOKED_HAM) {
			return List.of(new ItemStack(Items.COOKED_PORKCHOP, MEAT_CUTS), new ItemStack(Items.BONE));
		}
		if (dish == Dish.KELP_ROLL) {
			return List.of(new ItemStack(DelightDishes.KELP_ROLL_SLICE, KELP_ROLL_SLICES));
		}
		ItemStack slices = DelightDishes.pieSlices(stack);
		if (slices != null) {
			return List.of(slices);
		}
		return List.of();
	}

	/** (0.5.92) Fillets from a cod or salmon: by its weight, if Powers' fishing weighed it. */
	private static int fillets(ItemStack stack) {
		Float kg = weight(stack);
		return kg == null ? FISH_FILLETS : Mth.clamp(Math.round(kg / KG_PER_FILLET), FILLETS_LEAST, FILLETS_MOST);
	}

	/** The weight (kg) Powers' fishing put on {@code stack}, or null (none, or no Powers). */
	private static Float weight(ItemStack stack) {
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(POWERS_FISH_WEIGHT).orElse(null);
		return type != null && stack.get(type) instanceof Float kg ? kg : null;
	}

	// --- sturdy blocks: Tinker's Delight's brick split ---

	/**
	 * Blocks a pickaxe takes back apart into the items they were built from, so a smeltery builder can undo a wall.
	 * Named by id, not by class: Kitchen never compiles against Powers.
	 */
	private static final Map<Identifier, Split> SPLITS = Map.of(
		Identifier.fromNamespaceAndPath("jaymospowers", "seared_bricks"),
		new Split(Identifier.fromNamespaceAndPath("jaymospowers", "seared_brick"), 4));

	/** A block that comes apart: the item it gives back and how many. */
	record Split(Identifier piece, int count) {
	}

	/** The pieces {@code stack} splits into with a pickaxe, or empty if it is not one of ours (or its mod isn't here). */
	public static List<ItemStack> split(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}
		Split split = SPLITS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		if (split == null) {
			return List.of();
		}
		Item piece = BuiltInRegistries.ITEM.getOptional(split.piece()).orElse(null);
		return piece == null ? List.of() : List.of(new ItemStack(piece, split.count()));
	}
}
