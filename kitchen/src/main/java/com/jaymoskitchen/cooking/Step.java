package com.jaymoskitchen.cooking;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.OptionalItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/**
 * One button in the cooking minigame (or one ingredient): either "add this ingredient" or a cooking action.
 *
 * <p>An ingredient may name a group, the item tag {@code jaymoskitchen:cooking/<group>}: anything in it does as well as
 * the ingredient's own item (venison for beef, duck for chicken, bass for cod), so a new food needs a line in a tag and
 * no dish of its own. The ingredient's own item is always used up first. Groups may share items (a peach is a
 * "fruit" and is the Peach ingredient): Kitchen#hasIngredients never counts one piece twice.
 */
public enum Step {
	BOWL(() -> Items.BOWL, null),
	BEEF(() -> Items.BEEF, null, "red_meat"),
	POTATO(() -> Items.POTATO, null),
	CARROT(() -> Items.CARROT, null),
	FLOUR(() -> com.jaymoskitchen.mill.Mill.FLOUR, null),
	BROWN_MUSHROOM(() -> Items.BROWN_MUSHROOM, null, "mushroom"),
	RED_MUSHROOM(() -> Items.RED_MUSHROOM, null),
	EGG(() -> Items.EGG, null, "egg"),
	SUGAR(() -> Items.SUGAR, null),
	SWEET_BERRIES(() -> Items.SWEET_BERRIES, null, "berries"),
	SALMON(() -> Items.SALMON, null, "oily_fish"),
	DRIED_KELP(() -> Items.DRIED_KELP, null),
	CHICKEN(() -> Items.CHICKEN, null, "poultry"),
	HONEY_BOTTLE(() -> Items.HONEY_BOTTLE, () -> Items.GLASS_BOTTLE),
	BEETROOT(() -> Items.BEETROOT, null),
	MUTTON(() -> Items.MUTTON, null, "mutton"),
	PORKCHOP(() -> Items.PORKCHOP, null, "pork"),
	COD(() -> Items.COD, null, "white_fish"),
	PUMPKIN(() -> Items.PUMPKIN, null),
	APPLE(() -> Items.APPLE, null, "fruit"),
	RABBIT(() -> Items.RABBIT, null, "small_game"),
	BLAZE_POWDER(() -> Items.BLAZE_POWDER, null),
	MAGMA_CREAM(() -> Items.MAGMA_CREAM, null),
	NETHER_WART(() -> Items.NETHER_WART, null),
	CRIMSON_FUNGUS(() -> Items.CRIMSON_FUNGUS, null),
	WARPED_FUNGUS(() -> Items.WARPED_FUNGUS, null),
	WATER_BOTTLE(() -> Items.POTION, () -> Items.GLASS_BOTTLE),
	COOKING_OIL(() -> ModItems.COOKING_OIL, () -> Items.GLASS_BOTTLE),
	DOUGH(() -> ModItems.DOUGH, null),
	PMEH(() -> ModItems.PMEH, null),
	PAPER(() -> Items.PAPER, null),
	// optional mods' food (air without the mod; see OptionalItems): Pantry for Blockheads' fruit and vegetables
	LEMON(() -> pantry("lemon"), null),
	PEACH(() -> pantry("peach"), null),
	GRAPES(() -> pantry("grapes"), null),
	// (0.5.33) the Kitchen's own tomato and onion (delight, looked up by id) come first, Pantry's work as well
	TOMATO(() -> own("tomato"), null, () -> pantry("tomato")),
	ONION(() -> own("onion"), null, () -> pantry("onion")),
	EGGPLANT(() -> pantry("eggplant"), null),
	CHILI_PEPPER(() -> pantry("chili_pepper"), null, "pepper"),
	STRAWBERRY(() -> pantry("strawberry"), null),
	TURNIP(() -> pantry("turnip"), null),
	PEANUT(() -> pantry("peanut"), null, "legume"),
	// Naturalist's game and fish
	VENISON(() -> naturalist("venison"), null, "venison"),
	DUCK(() -> naturalist("duck"), null),
	BUSHMEAT(() -> naturalist("bushmeat"), null),
	MAMMOTH_MEAT(() -> naturalist("mammoth_meat"), null),
	DRUMSTICK(() -> naturalist("drumstick"), null, "drumstick"),
	MORSEL(() -> naturalist("morsel"), null),
	BASS(() -> naturalist("bass"), null),
	CATFISH(() -> naturalist("catfish"), null),
	PIRANHA(() -> naturalist("piranha"), null),
	ANGLERFISH(() -> naturalist("anglerfish"), null),
	BLOBFISH(() -> naturalist("blobfish"), null),
	CRAB_MEAT(() -> naturalist("crab_meat"), null),
	CLAM_MEAT(() -> naturalist("clam_meat"), null, "shellfish"),
	LIZARD_TAIL(() -> naturalist("lizard_tail"), null, "reptile"),
	// Jaymo's Powers' Aether fruit, egg and meat
	ORANGE(() -> powers("orange"), null),
	WYNDBERRY(() -> powers("wyndberry"), null),
	BLUE_BERRY(() -> powers("blue_berry"), null),
	WHITE_APPLE(() -> powers("white_apple"), null),
	SATIVAL_BULB(() -> powers("satival_bulb"), null),
	PRISMALLARD_EGG(() -> powers("prismallard_egg"), null),
	TAEGORE_MEAT(() -> powers("raw_taegore_meat"), null),
	BURRUKAI_RIB(() -> powers("burrukai_rib_cut"), null),
	KIRRID_LOIN(() -> powers("kirrid_loin"), null),
	PRISMALLARD_LEG(() -> powers("prismallard_leg"), null),
	// (0.5.38) the far-world pantry
	VERDANT_FRUIT(() -> powers("verdant_fruit"), null),
	GLACIA_SALT(() -> powers("glacia_salt"), null),
	// vanilla's cooked fish and meat, and bread
	COOKED_COD(() -> Items.COOKED_COD, null, "cooked_white_fish"),
	COOKED_SALMON(() -> Items.COOKED_SALMON, null, "cooked_oily_fish"),
	COOKED_PORKCHOP(() -> Items.COOKED_PORKCHOP, null, "cooked_pork"),
	COOKED_MUTTON(() -> Items.COOKED_MUTTON, null, "cooked_mutton"),
	BREAD(() -> Items.BREAD, null, "bread"),
	// (0.5.28) Farmer's Delight's crops and cuts (delight/Delight)
	CABBAGE(() -> com.jaymoskitchen.delight.Delight.CABBAGE, null, "cabbage"),
	CABBAGE_LEAF(() -> com.jaymoskitchen.delight.Delight.CABBAGE_LEAF, null, "leaf"),
	RICE(() -> com.jaymoskitchen.delight.Delight.RICE, null, "rice"),
	MINCED_BEEF(() -> com.jaymoskitchen.delight.Delight.MINCED_BEEF, null),
	RAW_BACON(() -> com.jaymoskitchen.delight.Delight.RAW_BACON, null),
	// (0.5.33) the rest of Farmer's Delight's larder (delight/DelightDishes) and the vanilla food it needs
	GLASS_BOTTLE(() -> Items.GLASS_BOTTLE, null),
	MELON_SLICE(() -> Items.MELON_SLICE, null),
	MILK_BOTTLE(() -> com.jaymoskitchen.delight.DelightDishes.MILK_BOTTLE, () -> Items.GLASS_BOTTLE),
	COCOA_BEANS(() -> Items.COCOA_BEANS, null),
	GLOW_BERRIES(() -> Items.GLOW_BERRIES, null),
	BONE(() -> Items.BONE, null),
	BONE_MEAL(() -> Items.BONE_MEAL, null),
	ROTTEN_FLESH(() -> Items.ROTTEN_FLESH, null),
	INK_SAC(() -> Items.INK_SAC, null),
	GOLDEN_CARROT(() -> Items.GOLDEN_CARROT, null),
	SKEWER_STICK(() -> Items.STICK, null),
	BAKED_POTATO(() -> Items.BAKED_POTATO, null),
	COOKED_BEEF(() -> Items.COOKED_BEEF, null, "cooked_red_meat"),
	WHEAT(() -> Items.WHEAT, null),
	ICE(() -> Items.ICE, null),
	PASTA(() -> own("pasta"), null),
	RICE_COOKED(() -> com.jaymoskitchen.ModItems.dish(Dish.COOKED_RICE), null),
	CRUST(() -> com.jaymoskitchen.ModItems.dish(Dish.PIE_CRUST), null),
	SAUCE(() -> com.jaymoskitchen.ModItems.dish(Dish.TOMATO_SAUCE), null),
	COD_SLICE(() -> com.jaymoskitchen.delight.Delight.COD_SLICE, null),
	SALMON_SLICE(() -> com.jaymoskitchen.delight.Delight.SALMON_SLICE, null),
	MUTTON_CHOPS(() -> com.jaymoskitchen.delight.Delight.MUTTON_CHOPS, null),
	ROLL_SALMON(() -> com.jaymoskitchen.ModItems.dish(Dish.SALMON_ROLL), null),
	ROLL_COD(() -> com.jaymoskitchen.ModItems.dish(Dish.COD_ROLL), null),
	KELP_ROLL_SLICE(() -> com.jaymoskitchen.delight.DelightDishes.KELP_ROLL_SLICE, null),
	// actions: cost nothing, icon is just for the button
	STIR(() -> Items.STICK, null),
	FLIP(() -> Items.WOODEN_SHOVEL, null),
	SIMMER(() -> Items.CAMPFIRE, null),
	BAKE(() -> Items.BRICK, null);

	// suppliers, because mod items don't exist yet when this enum is first loaded
	private final Supplier<Item> item;
	private final @Nullable Supplier<Item> remainder;
	/** Another item that does as well (Pantry's tomato for the Kitchen's own), or null. */
	private final @Nullable Supplier<Item> alternative;

	/** The tag of everything else that does as well (jaymoskitchen:cooking/...), or null. */
	private final @Nullable TagKey<Item> group;

	Step(Supplier<Item> item, @Nullable Supplier<Item> remainder) {
		this(item, remainder, null, null);
	}

	Step(Supplier<Item> item, @Nullable Supplier<Item> remainder, String group) {
		this(item, remainder, null, group);
	}

	Step(Supplier<Item> item, @Nullable Supplier<Item> remainder, @Nullable Supplier<Item> alternative) {
		this(item, remainder, alternative, null);
	}

	Step(Supplier<Item> item, @Nullable Supplier<Item> remainder, @Nullable Supplier<Item> alternative, @Nullable String group) {
		this.item = item;
		this.remainder = remainder;
		this.alternative = alternative;
		this.group = group == null ? null
			: TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(OptionalItems.KITCHEN, "cooking/" + group));
	}

	/** One of this mod's own items by id, or air (without it): looked up, not linked, so the track that adds it can come later. */
	private static Item own(String path) {
		return OptionalItems.item(OptionalItems.KITCHEN, path);
	}

	private static Item pantry(String path) {
		return OptionalItems.item(OptionalItems.PANTRY, path);
	}

	private static Item naturalist(String path) {
		return OptionalItems.item(OptionalItems.NATURALIST, path);
	}

	private static Item powers(String path) {
		return OptionalItems.item(OptionalItems.POWERS, path);
	}

	/** The ingredient consumed (or, for actions, the icon shown on the button): the first of its items that exists. */
	public Item item() {
		Item first = item.get();
		if (first == Items.AIR && alternative != null) {
			return alternative.get();
		}
		return first;
	}

	/** Every item that counts as this ingredient and exists in this game, the preferred one first, then its group's. */
	public List<Item> items() {
		List<Item> items = new ArrayList<>(2);
		Item first = item.get();
		if (first != Items.AIR) {
			items.add(first);
		}
		if (alternative != null) {
			Item other = alternative.get();
			if (other != Items.AIR && other != first) {
				items.add(other);
			}
		}
		if (group != null) {
			// empty until the world's tags are loaded: only ask once a world is running
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(group)) {
				if (!items.contains(holder.value())) {
					items.add(holder.value());
				}
			}
		}
		return items;
	}

	/** What the ingredient leaves behind when used, e.g. a glass bottle from honey. */
	public @Nullable Item remainder() {
		return remainder == null ? null : remainder.get();
	}

	/** Whether an inventory stack counts as this ingredient (a "potion" only counts if it's plain water). */
	public boolean matches(ItemStack stack) {
		if (stack.isEmpty() || !items().contains(stack.getItem())) {
			return false;
		}
		if (this == WATER_BOTTLE) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		return true;
	}

	/** The icon at {@code turn}: an ingredient with a group shows each of its items in turn, so a list can cycle through them. */
	public ItemStack icon(int count, long turn) {
		if (group == null) {
			return icon(count);
		}
		List<Item> all = items();
		return all.isEmpty() ? icon(count) : new ItemStack(all.get((int) Math.floorMod(turn, (long) all.size())), count);
	}

	/** An icon for this step; the water bottle needs its potion set or it renders as an "uncraftable" potion. */
	public ItemStack icon(int count) {
		if (this == WATER_BOTTLE) {
			ItemStack stack = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			stack.setCount(count);
			return stack;
		}
		return new ItemStack(item(), count);
	}

	/** Whether the ingredient exists in this game (an optional mod's needs that mod). */
	public boolean available() {
		return item() != Items.AIR;
	}

	public boolean isAction() {
		return ordinal() >= STIR.ordinal();
	}

	public String translationKey() {
		return "step.jaymoskitchen." + name().toLowerCase();
	}
}
