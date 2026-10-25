package com.jaymoskitchen.cooking;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.OptionalItems;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/** One button in the cooking minigame (or one ingredient): either "add this ingredient" or a cooking action. */
public enum Step {
	BOWL(() -> Items.BOWL, null),
	BEEF(() -> Items.BEEF, null),
	POTATO(() -> Items.POTATO, null),
	CARROT(() -> Items.CARROT, null),
	FLOUR(() -> com.jaymoskitchen.mill.Mill.FLOUR, null),
	BROWN_MUSHROOM(() -> Items.BROWN_MUSHROOM, null),
	RED_MUSHROOM(() -> Items.RED_MUSHROOM, null),
	EGG(() -> Items.EGG, null),
	SUGAR(() -> Items.SUGAR, null),
	SWEET_BERRIES(() -> Items.SWEET_BERRIES, null),
	SALMON(() -> Items.SALMON, null),
	DRIED_KELP(() -> Items.DRIED_KELP, null),
	CHICKEN(() -> Items.CHICKEN, null),
	HONEY_BOTTLE(() -> Items.HONEY_BOTTLE, () -> Items.GLASS_BOTTLE),
	BEETROOT(() -> Items.BEETROOT, null),
	MUTTON(() -> Items.MUTTON, null),
	PORKCHOP(() -> Items.PORKCHOP, null),
	COD(() -> Items.COD, null),
	PUMPKIN(() -> Items.PUMPKIN, null),
	APPLE(() -> Items.APPLE, null),
	RABBIT(() -> Items.RABBIT, null),
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
	TOMATO(() -> pantry("tomato"), null),
	ONION(() -> pantry("onion"), null),
	EGGPLANT(() -> pantry("eggplant"), null),
	CHILI_PEPPER(() -> pantry("chili_pepper"), null),
	STRAWBERRY(() -> pantry("strawberry"), null),
	TURNIP(() -> pantry("turnip"), null),
	PEANUT(() -> pantry("peanut"), null),
	// Naturalist's game and fish
	VENISON(() -> naturalist("venison"), null),
	DUCK(() -> naturalist("duck"), null),
	BUSHMEAT(() -> naturalist("bushmeat"), null),
	MAMMOTH_MEAT(() -> naturalist("mammoth_meat"), null),
	DRUMSTICK(() -> naturalist("drumstick"), null),
	MORSEL(() -> naturalist("morsel"), null),
	BASS(() -> naturalist("bass"), null),
	CATFISH(() -> naturalist("catfish"), null),
	PIRANHA(() -> naturalist("piranha"), null),
	ANGLERFISH(() -> naturalist("anglerfish"), null),
	BLOBFISH(() -> naturalist("blobfish"), null),
	CRAB_MEAT(() -> naturalist("crab_meat"), null),
	CLAM_MEAT(() -> naturalist("clam_meat"), null),
	LIZARD_TAIL(() -> naturalist("lizard_tail"), null),
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
	// vanilla's cooked fish and meat, and bread
	COOKED_COD(() -> Items.COOKED_COD, null),
	COOKED_SALMON(() -> Items.COOKED_SALMON, null),
	COOKED_PORKCHOP(() -> Items.COOKED_PORKCHOP, null),
	COOKED_MUTTON(() -> Items.COOKED_MUTTON, null),
	BREAD(() -> Items.BREAD, null),
	// actions: cost nothing, icon is just for the button
	STIR(() -> Items.STICK, null),
	FLIP(() -> Items.WOODEN_SHOVEL, null),
	SIMMER(() -> Items.CAMPFIRE, null),
	BAKE(() -> Items.BRICK, null);

	// suppliers, because mod items don't exist yet when this enum is first loaded
	private final Supplier<Item> item;
	private final @Nullable Supplier<Item> remainder;

	Step(Supplier<Item> item, @Nullable Supplier<Item> remainder) {
		this.item = item;
		this.remainder = remainder;
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

	/** The ingredient consumed (or, for actions, the icon shown on the button). */
	public Item item() {
		return item.get();
	}

	/** What the ingredient leaves behind when used, e.g. a glass bottle from honey. */
	public @Nullable Item remainder() {
		return remainder == null ? null : remainder.get();
	}

	/** Whether an inventory stack counts as this ingredient (a "potion" only counts if it's plain water). */
	public boolean matches(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() != item()) {
			return false;
		}
		if (this == WATER_BOTTLE) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		return true;
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
