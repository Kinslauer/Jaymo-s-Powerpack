package com.jaymoskitchen.vintner;

import com.jaymoskitchen.OptionalItems;
import com.jaymoskitchen.brewing.Brew;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The juices of the treading tub and the apple press: what a cask makes each into, and the fruit each comes from. */
public enum Juice {
	GRAPE(Brew.GRAPE_WINE, true),
	APPLE(Brew.CIDER, false),
	BLACK(Brew.BLACK_WINE, true);

	/** What a cask ferments it into. */
	public final Brew brew;
	/** Trodden in the tub (grapes), else pressed (apples). */
	public final boolean tub;

	Juice(Brew brew, boolean tub) {
		this.brew = brew;
		this.tub = tub;
	}

	/** The bottle it is drawn into. */
	public Item bottle() {
		return switch (this) {
			case GRAPE -> Vintner.GRAPE_JUICE;
			case APPLE -> Vintner.APPLE_JUICE;
			case BLACK -> Vintner.BLACK_JUICE;
		};
	}

	/** Whether {@code stack} is fruit for this juice. */
	public boolean takes(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return switch (this) {
			case GRAPE -> stack.is(Vintner.GRAPES) || stack.is(pantryGrapes());
			case APPLE -> stack.is(Items.APPLE);
			case BLACK -> stack.is(Vintner.CRIMSON_GRAPES) || stack.is(Vintner.WARPED_GRAPES);
		};
	}

	private static Item pantryGrapes() {
		return OptionalItems.item(OptionalItems.PANTRY, "grapes");
	}

	/** The juice a tub or a press makes of this fruit, or null. */
	public static @Nullable Juice ofFruit(ItemStack stack, boolean tub) {
		for (Juice juice : values()) {
			if (juice.tub == tub && juice.takes(stack)) {
				return juice;
			}
		}
		return null;
	}

	/** The juice in this bottle, or null. */
	public static @Nullable Juice ofBottle(ItemStack stack) {
		for (Juice juice : values()) {
			if (stack.is(juice.bottle())) {
				return juice;
			}
		}
		return null;
	}

	public static @Nullable Juice byName(String name) {
		for (Juice juice : values()) {
			if (juice.name().equals(name)) {
				return juice;
			}
		}
		return null;
	}
}
