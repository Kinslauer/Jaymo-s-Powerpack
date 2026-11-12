package com.jaymospowers.gear.arrow;

import com.jaymospowers.tinkering.fletching.Fletching;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Fitting an arrowhead at the Fletching Station: plain arrows in the head slot, an arrowhead in the shaft slot, and
 * {@link #ARROWS_PER_HEAD} headed arrows come out for each head.
 */
public final class Fitting {
	public static final int ARROWS_PER_HEAD = 4;

	private Fitting() {
	}

	public static boolean isHead(ItemStack stack) {
		return stack.getItem() instanceof ArrowHeadItem;
	}

	/** What these inputs make, or null when they aren't a fitting. */
	public static Fletching.@Nullable Craft craft(ItemStack arrows, ItemStack head, ItemStack fletching, ItemStack potion) {
		if (!(head.getItem() instanceof ArrowHeadItem fitted) || !arrows.is(Items.ARROW) || !fletching.isEmpty() || !potion.isEmpty()) {
			return null;
		}
		int count = Math.min(arrows.getCount(), ARROWS_PER_HEAD);
		return new Fletching.Craft(HeadedArrowItem.of(fitted.head, count), count, 1, 0, 0);
	}
}
