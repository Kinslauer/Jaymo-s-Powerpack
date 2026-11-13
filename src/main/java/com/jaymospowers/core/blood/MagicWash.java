package com.jaymospowers.core.blood;

import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * The clean-a-coat cantrip's wash (fifth edition's everyday magic): Jaymo's Kingdoms asks for it here by reflection. A spell
 * scrubs the blood off a body's own coat and off what it wears and holds, as a scrub in the cauldron would
 * ({@link Bloodied#cleanCoat}, {@link Bloodied#clean}).
 */
public final class MagicWash {
	/** How much a cantrip scrubs from each piece (dried and rotten blood gives up less, as with any wash). */
	public static final int AMOUNT = 70;

	private static final List<EquipmentSlot> WORN = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

	private MagicWash() {
	}

	/** Scrubs {@code body}'s coat and what it wears and holds; true if there was any blood to scrub. */
	public static boolean wash(LivingEntity body) {
		boolean any = Bloodied.cleanCoat(body, AMOUNT);
		for (EquipmentSlot slot : WORN) {
			any |= Bloodied.clean(body.getItemBySlot(slot), AMOUNT);
		}
		return any;
	}
}
