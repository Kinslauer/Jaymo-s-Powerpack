package com.jaymoskitchen.horse;

import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Shared facts about the carts ported from NiftyCarts (by MennoMax, paul101 and jmb19905; MIT licensed, see
 * LICENSE_niftycarts): the woods they come in, and their wheel size.
 */
public final class NiftyCarts {
	/** Every wood the carts come in, in creative-tab order. */
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "acacia", "cherry", "jungle", "dark_oak",
		"pale_oak", "crimson", "warped", "mangrove", "bamboo");
	/** The wood's log block, where it isn't "&lt;wood&gt;_log". */
	private static final Map<String, String> LOG_NAMES = Map.of("crimson", "crimson_stem", "warped", "warped_stem",
		"bamboo", "bamboo_block");
	/** Their eight-sided wheels: a rim of 9 px sides makes a wheel this big (radius, model pixels). */
	public static final float WHEEL_RADIUS = 4.5F / (Mth.sin(Mth.PI / 8.0F) / Mth.cos(Mth.PI / 8.0F));
	/** The wagon is the supply cart's frame drawn this much bigger. */
	public static final float WAGON_SCALE = 1.2F;

	private NiftyCarts() {
	}

	public static String log(String wood) {
		return LOG_NAMES.getOrDefault(wood, wood + "_log");
	}

	public static boolean isWood(String wood) {
		return WOODS.contains(wood);
	}
}
