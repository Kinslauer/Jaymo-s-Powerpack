package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Mailed and studded leather (Jaymo's Powers' light armour), for the folk who fight in leather rather than plate:
 * rangers, ship crews. Powers is optional: with no such piece registered, the leather piece they would have worn is
 * handed back. A dyed leather piece's dye goes onto the piece that replaces it.
 */
final class LightLeather {
	/** Leather armour by rank: studded for the rank and file, mailed for those over them. */
	static final String MAILED = "mailed", STUDDED = "studded";
	private static final String[] MAILED_NAMES = {"mailed_cap", "mailed_jack", "mailed_chausses", "mailed_boots"},
		STUDDED_NAMES = {"studded_cap", "studded_brigandine", "studded_leggings", "studded_boots"};

	private LightLeather() {
	}

	/** The {@code set} ({@link #MAILED} or {@link #STUDDED}) piece for {@code slot}, in the dye of {@code leather}; {@code leather} itself if Powers' piece isn't there. */
	static ItemStack piece(String set, EquipmentSlot slot, ItemStack leather) {
		int i = switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			case FEET -> 3;
			default -> -1;
		};
		if (i < 0) {
			return leather;
		}
		Identifier id = Identifier.fromNamespaceAndPath("jaymospowers", (set.equals(MAILED) ? MAILED_NAMES : STUDDED_NAMES)[i]);
		return BuiltInRegistries.ITEM.getOptional(id).map(item -> {
			ItemStack stack = new ItemStack(item);
			stack.set(DataComponents.DYED_COLOR, leather.get(DataComponents.DYED_COLOR));
			return stack;
		}).orElse(leather);
	}
}
