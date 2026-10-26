package com.jaymospowers.alchemy.essence;

import com.jaymospowers.alchemy.EssenceList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** (stub: filled in by track A; see docs/Alchemy-Plan.md) What every item and creature is made of. */
public final class Essences {
	private Essences() {
	}

	public static void init() {
	}

	/** The essences in one of this item (not times the stack's count); empty for things with none (air, unknowns). */
	public static EssenceList of(ItemStack stack) {
		return EssenceList.EMPTY;
	}

	/** The essences a creature is made of (by its type). */
	public static EssenceList of(Entity entity) {
		return EssenceList.EMPTY;
	}
}
