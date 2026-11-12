package com.jaymospowers.gear.arrow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * The Pocket Arsenal's rift, now and then, sends a bundle of special arrows along with a bow it pulls out (one small hook in
 * {@code ArsenalPower}). They are summoned like the weapon and go back with it, so nothing here needs to track them.
 */
public final class RiftArrows {
	/** The chance a pulled bow or crossbow comes with special arrows, and how many. */
	public static final float CHANCE = 0.15F;
	public static final int COUNT = 6;

	private RiftArrows() {
	}

	/** {@code pulled} with a bundle of one random head's arrows added when it holds a bow, now and then. */
	public static List<ItemStack> sometimes(List<ItemStack> pulled, RandomSource random) {
		boolean bow = false;
		for (ItemStack stack : pulled) {
			bow |= stack.getItem() instanceof ProjectileWeaponItem;
		}
		if (!bow || random.nextFloat() >= CHANCE) {
			return pulled;
		}
		List<ItemStack> more = new ArrayList<>(pulled);
		ArrowHead[] heads = ArrowHead.values();
		more.add(HeadedArrowItem.of(heads[random.nextInt(heads.length)], COUNT));
		return more;
	}
}
