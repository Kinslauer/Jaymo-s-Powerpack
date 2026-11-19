package com.jaymoskitchen.vintner;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.brewing.Brew;
import com.jaymoskitchen.season.Seasons;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bottle of wine and its year. Wine drawn from a cask carries the year the batch was done and how many in-game years
 * it sat in a cool cellar (up to {@link #MAX_AGE}); lords and merchants pay more for older vintages ({@link #worth}).
 * Both numbers are packed in one component: {@code year * 8 + age}.
 */
public final class Vintage {
	/** Most in-game years a wine improves. */
	public static final int MAX_AGE = 5;
	/** Each year of age adds this share of the base price. */
	public static final float PRICE_PER_YEAR = 0.5F;
	/** What a bottle of each brew sells for when young, in emeralds' worth. */
	private static final int ALE = 1, CIDER = 1, MEAD = 2, BERRY_WINE = 2, GRAPE_WINE = 3, BLACK_WINE = 12;

	public static DataComponentType<Integer> STAMP;

	private Vintage() {
	}

	static void register() {
		STAMP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("vintage"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	}

	/** The in-game year the world is in (every dimension goes by the overworld's clock). */
	public static int yearOf(Level level) {
		return (int)Math.floorDiv(level.getOverworldClockTime(), Seasons.YEAR * Seasons.DAY_TICKS);
	}

	/** Ticks of cellar a wine needs for each year of age. */
	public static long ticksPerYear() {
		return Seasons.YEAR * Seasons.DAY_TICKS;
	}

	public static ItemStack stamp(ItemStack bottle, int year, int age) {
		bottle.set(STAMP, year * 8 + Math.min(MAX_AGE, Math.max(0, age)));
		return bottle;
	}

	/** The year a bottle was made, or -1 if it carries none. */
	public static int year(ItemStack bottle) {
		Integer stamp = bottle.get(STAMP);
		return stamp == null ? -1 : Math.floorDiv(stamp, 8);
	}

	/** How many years it has improved, 0 if it carries no year. */
	public static int age(ItemStack bottle) {
		Integer stamp = bottle.get(STAMP);
		return stamp == null ? 0 : Math.floorMod(stamp, 8);
	}

	/** What a bottle sells for, in milli (see KingdomsMoney). */
	public static long worth(Brew brew, ItemStack bottle) {
		int emeralds = switch (brew) {
			case ALE -> ALE;
			case CIDER -> CIDER;
			case MEAD -> MEAD;
			case BERRY_WINE -> BERRY_WINE;
			case GRAPE_WINE -> GRAPE_WINE;
			case BLACK_WINE -> BLACK_WINE;
		};
		return Math.round(com.jaymoskitchen.KingdomsMoney.worth(emeralds) * (1.0F + PRICE_PER_YEAR * age(bottle)));
	}
}
