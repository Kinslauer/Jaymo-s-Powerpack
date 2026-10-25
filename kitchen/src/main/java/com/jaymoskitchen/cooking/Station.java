package com.jaymoskitchen.cooking;

import com.jaymoskitchen.kitchen.KitchenBlocks;
import com.jaymoskitchen.kitchen.OvenHob;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.blay09.mods.cookingforblockheads.block.CuttingBoardBlock;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where you cook. A campfire is a simple grill; the furnace and smoker handle the involved recipes, and the
 * furnace doubles as the pizza oven. Each {@link Dish} lists the stations it can be made at.
 */
public enum Station {
	CAMPFIRE(4, 4),
	FURNACE(4, 4),
	// the smoker is the gentler, more controlled heat: 25% more time
	SMOKER(5, 4),
	BLAST_FURNACE(4, 4),
	/**
	 * A lit Cooking for Blockheads oven. It always bakes; a Cooking Pot anywhere on its hob adds the stews and soups,
	 * a Frying Pan the grilled and seared dishes. (Sent to the client by ordinal: only ever append.)
	 */
	STOVE(4, 4),
	STOVE_POT(4, 4),
	STOVE_PAN(4, 4),
	STOVE_FULL(4, 4),
	/** A Cooking for Blockheads cutting board: the chopped dishes. Opened from the prep screen there (0.4.38). */
	BOARD(4, 4);

	private final int timeNumerator;
	private final int timeDenominator;

	Station(int timeNumerator, int timeDenominator) {
		this.timeNumerator = timeNumerator;
		this.timeDenominator = timeDenominator;
	}

	public boolean allows(Dish dish) {
		if (!dish.available()) {
			return false;
		}
		return switch (this) {
			// the oven bakes; a pot on the hob adds the stews and soups, a pan the grilled and seared (chopping wants a
			// cutting board, spit roasts a campfire)
			case STOVE, STOVE_POT, STOVE_PAN, STOVE_FULL -> dish.method() == Dish.Method.OVEN
				|| (hasPot() && (dish.method() == Dish.Method.SEQUENCE || dish.method() == Dish.Method.STIR))
				|| (hasPan() && (dish.method() == Dish.Method.GRILL || dish.method() == Dish.Method.SEAR));
			default -> dish.stations().contains(this);
		};
	}

	/** Whether it cooks with heat (everything but the cutting board): what can reheat a dish. */
	public boolean heats() {
		return this != BOARD;
	}

	/** A lit Cooking for Blockheads oven, whatever is on its hob. */
	public boolean isStove() {
		return this == STOVE || this == STOVE_POT || this == STOVE_PAN || this == STOVE_FULL;
	}

	private boolean hasPot() {
		return this == STOVE_POT || this == STOVE_FULL;
	}

	private boolean hasPan() {
		return this == STOVE_PAN || this == STOVE_FULL;
	}

	private static Station stove(boolean pot, boolean pan) {
		return pot ? (pan ? STOVE_FULL : STOVE_POT) : (pan ? STOVE_PAN : STOVE);
	}

	public List<Dish> dishes() {
		return Arrays.stream(Dish.values()).filter(this::allows).toList();
	}

	public int scaleTicks(int ticks) {
		return ticks * timeNumerator / timeDenominator;
	}

	public String translationKey() {
		return "screen.jaymoskitchen." + name().toLowerCase();
	}

	/** The station this block is, or null if it isn't one (or is an unlit campfire). */
	public static @Nullable Station of(BlockState state) {
		if (state.getBlock() instanceof CampfireBlock) {
			return state.getValue(CampfireBlock.LIT) ? CAMPFIRE : null;
		}
		if (state.getBlock() instanceof CuttingBoardBlock) {
			return BOARD;
		}
		if (state.is(Blocks.FURNACE)) {
			return FURNACE;
		}
		if (state.is(Blocks.SMOKER)) {
			return SMOKER;
		}
		if (state.is(Blocks.BLAST_FURNACE)) {
			return BLAST_FURNACE;
		}
		return null;
	}

	/**
	 * The station at a spot. A Cooking for Blockheads oven is a stove: only when it's been turned on, and which kind
	 * depends on what's on its hob (any Cooking Pot / Frying Pan among the four tool slots; see OvenHob).
	 */
	public static @Nullable Station of(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof OvenBlock) {
			if (!(level.getBlockEntity(pos) instanceof OvenBlockEntity oven) || !OvenHob.isLit(oven)) {
				return null;
			}
			boolean pot = false;
			boolean pan = false;
			for (int i = 0; i < 4; i++) {
				ItemStack tool = oven.getToolItem(i);
				pot |= tool.is(KitchenBlocks.COOKING_POT);
				pan |= tool.is(KitchenBlocks.FRYING_PAN);
			}
			return stove(pot, pan);
		}
		return of(state);
	}

	/** Whether this station is (still) exactly what's at that spot. */
	public boolean at(Level level, BlockPos pos) {
		return of(level, pos) == this;
	}

	/**
	 * Whether the spot can (still) cook this dish as this station: the same station, or for an oven, still a lit oven
	 * whose hob still allows the dish (adding or removing other cookware doesn't interrupt it).
	 */
	public boolean cooks(Level level, BlockPos pos, Dish dish) {
		Station here = of(level, pos);
		return here == this || (here != null && isStove() && here.isStove() && here.allows(dish));
	}

	public static @Nullable Station byIndex(int index) {
		Station[] all = values();
		return index >= 0 && index < all.length ? all[index] : null;
	}
}
