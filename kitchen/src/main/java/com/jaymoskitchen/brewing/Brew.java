package com.jaymoskitchen.brewing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The fermented drinks a cask makes (see {@link CaskEntity}); each is made from its own base ingredient. */
public enum Brew {
	ALE("ale", 2, 0.2F, 1),
	CIDER("cider", 2, 0.3F, 1),
	MEAD("mead", 3, 0.4F, 2),
	BERRY_WINE("berry_wine", 2, 0.3F, 2),
	/** From our grapes (Vintner), or Pantry for Blockheads' (see OptionalItems). */
	GRAPE_WINE("grape_wine", 2, 0.4F, 2),
	/** From the Nether's crimson and warped grapes (Vintner): the demons' dark wine. */
	BLACK_WINE("black_wine", 3, 0.4F, 3);

	public final String id;
	public final int nutrition;
	public final float saturation;
	/** Drunkenness points one drink gives (see {@link Drunk}). */
	public final int drunk;

	Brew(String id, int nutrition, float saturation, int drunk) {
		this.id = id;
		this.nutrition = nutrition;
		this.saturation = saturation;
		this.drunk = drunk;
	}

	/** What a cask is filled with to make this (three of it). */
	public Item baseItem() {
		return switch (this) {
			case ALE -> Items.WHEAT;
			case CIDER -> Items.APPLE;
			case MEAD -> Items.HONEY_BOTTLE;
			case BERRY_WINE -> Items.SWEET_BERRIES;
			case GRAPE_WINE -> com.jaymoskitchen.vintner.Vintner.GRAPES;
			case BLACK_WINE -> com.jaymoskitchen.vintner.Vintner.CRIMSON_GRAPES;
		};
	}

	public static @Nullable Brew byName(String name) {
		for (Brew brew : values()) {
			if (brew.name().equals(name)) {
				return brew;
			}
		}
		return null;
	}
}
