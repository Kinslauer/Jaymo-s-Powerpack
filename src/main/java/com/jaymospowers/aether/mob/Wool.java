package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * Wool colours of the sheepuff and the kirrid. The first six are the sheepuff's first colours (saved worlds hold their
 * indexes, so they never move); the rest are the other dyes. A kirrid's colour {@link #UNDYED} is its own bare wool
 * (cloudwool); everything else drops the vanilla wool of that colour (the Aether has only the white cloudwool).
 */
public final class Wool {
	/** Colour names by index (the skins are {@code sheepuff_<name>} and {@code kirrid_<zone>_<name>}). */
	public static final String[] NAMES = {"white", "light_blue", "cyan", "lime", "pink", "purple", "light_gray", "gray", "yellow", "brown", "magenta",
		"orange", "red", "green", "blue", "black"};
	/** A kirrid's own wool, no dye. */
	public static final int UNDYED = NAMES.length;

	private Wool() {
	}

	public static int index(String name) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equals(name)) {
				return i;
			}
		}
		return 0;
	}

	public static int index(DyeColor dye) {
		return index(dye.getName());
	}

	/** The item dropped for a colour. */
	public static Item item(int color) {
		if (color == UNDYED || color == 0) {
			return AetherBlocks.CLOUDWOOL.asItem();
		}
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(NAMES[Math.floorMod(color, NAMES.length)] + "_wool"));
	}

	/** One chance in {@code odds} (a rare colour). */
	public static boolean rare(RandomSource random, int odds) {
		return random.nextInt(odds) == 0;
	}
}
