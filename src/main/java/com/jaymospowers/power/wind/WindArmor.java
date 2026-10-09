package com.jaymospowers.power.wind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;

/**
 * Armour weighs a flyer down. Leather and chainmail are light enough to fly in freely; everything heavier slows the
 * flight more and more (per piece: copper and turtle 0.5, gold 0.75, iron 1, diamond 1.5, netherite 2; any other
 * mod's armour counts as 0.75). The total takes {@link #SPEED_PER_WEIGHT} off the top flight speed per point.
 */
public final class WindArmor {
	/** Top speed lost per point of weight (full netherite, 8 points: −48%), and the least that's left. */
	public static final double SPEED_PER_WEIGHT = 0.06;
	public static final double MIN_SPEED_FACTOR = 0.4;

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private WindArmor() {
	}

	/** The armour's total weight (0 = nothing heavier than chainmail). Works on both sides. */
	public static double weight(Player player) {
		double total = 0.0;
		for (EquipmentSlot slot : SLOTS) {
			total += pieceWeight(player.getItemBySlot(slot), slot);
		}
		return total;
	}

	/** What the armour leaves of the flight's top speed (1 = unburdened). */
	public static double speedFactor(Player player) {
		return Math.max(MIN_SPEED_FACTOR, 1.0 - SPEED_PER_WEIGHT * weight(player));
	}

	private static double pieceWeight(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) {
			return 0.0;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != slot) {
			// not armour worn in its place (a pumpkin, a banner, an elytra's no burden either)
			return 0.0;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.startsWith("leather_") || path.startsWith("chainmail_") || path.equals("elytra") || path.endsWith("_head")
			|| path.endsWith("_skull") || path.contains("pumpkin")) {
			return 0.0;
		}
		if (path.startsWith("netherite_")) {
			return 2.0;
		}
		if (path.startsWith("diamond_")) {
			return 1.5;
		}
		if (path.startsWith("iron_")) {
			return 1.0;
		}
		if (path.startsWith("golden_")) {
			return 0.75;
		}
		if (path.startsWith("copper_") || path.startsWith("turtle_")) {
			return 0.5;
		}
		return 0.75;
	}
}
