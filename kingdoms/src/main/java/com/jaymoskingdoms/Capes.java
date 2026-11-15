package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * (gb-capes) A lord's, a king's and a knight's chest armour carries a cape in the realm's colours: its field, edged in
 * its charge. The colours ride on the stack ({@link #TAG}, as RGB), so the cape goes wherever the armour does; the
 * client hangs it from the shoulders as cloth that sways as its wearer moves ({@code CapeLayer}).
 */
public final class Capes {
	/** The stack's custom data key: {field RGB, edge RGB}. */
	public static final String TAG = "jaymoskingdoms_cape";

	private Capes() {
	}

	/** {@code chest}, with a cape in {@code arms}' colours (the same stack, for chaining). */
	static ItemStack cape(ItemStack chest, Outfits.Heraldry arms) {
		if (!chest.isEmpty()) {
			int[] colours = {Outfits.rgb(arms.field()), Outfits.rgb(arms.charge())};
			CustomData.update(DataComponents.CUSTOM_DATA, chest, tag -> tag.putIntArray(TAG, colours));
		}
		return chest;
	}

	/** The cape's colours on {@code chest} ({field, edge} RGB), or null for none. */
	public static int @org.jspecify.annotations.Nullable [] of(ItemStack chest) {
		CustomData data = chest.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		int[] colours = data.copyTag().getIntArray(TAG).orElse(null);
		return colours != null && colours.length >= 2 ? colours : null;
	}
}
