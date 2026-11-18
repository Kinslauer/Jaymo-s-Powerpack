package com.jaymospowers.core;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the kind of armour a body wears ({@link ArmourKind}, by the four slots) does for how it moves, how long Slowness
 * holds it and how far mobs notice it: plate slows a climb, a vault and a swim; mail shortens Slowness; padding is
 * quieter than plate. Each is scaled by the slots' own shares of the load.
 */
public final class ArmourEase {
	// ===== the knobs ================================================================================================
	/** A full suit of plate cuts the climbing grip, the vault's speed and the swimming stroke by this much (scaled by the plate share). */
	public static final float PLATE_CLIMB = 0.3F, PLATE_VAULT = 0.2F, PLATE_SWIM = 0.2F;
	/** Each mail piece worn shortens a newly added Slowness by this share (a full set: four times it). */
	public static final float MAIL_SLOW_SHORTEN = 0.1F;
	/** How far mobs notice a body in each kind of armour, as a share of the distance (bare slots count 1). */
	public static final float QUIET_PADDING = 0.9F, QUIET_MAIL = 1.0F, QUIET_PLATE = 1.15F;
	// ================================================================================================================

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmourEase() {
	}

	/** The share of {@code body}'s armour that is plate, 0 (none) to 1 (plate head to foot), by each slot's share of the load. */
	public static float plateShare(LivingEntity body) {
		float share = 0.0F;
		for (EquipmentSlot slot : SLOTS) {
			if (ArmourKind.worn(body, slot) == ArmourKind.PLATE) {
				share += slotShare(slot);
			}
		}
		return share;
	}

	/** What {@code body}'s plate leaves of a move that a full suit cuts by {@code cut}: 1 in light armour, 1 - cut in full plate. */
	public static float plateEase(LivingEntity body, float cut) {
		return 1.0F - cut * plateShare(body);
	}

	/** What a Slowness added to {@code body} keeps of its duration: each mail piece worn shortens it. */
	public static float slownessKept(LivingEntity body) {
		int pieces = 0;
		for (EquipmentSlot slot : SLOTS) {
			if (ArmourKind.worn(body, slot) == ArmourKind.MAIL) {
				pieces++;
			}
		}
		return 1.0F - MAIL_SLOW_SHORTEN * pieces;
	}

	/** What mobs' notice of {@code body} is multiplied by for its armour: padding, mail and plate by their slots' shares. */
	public static double noticed(LivingEntity body) {
		float factor = 0.0F;
		for (EquipmentSlot slot : SLOTS) {
			factor += slotShare(slot) * switch (ArmourKind.worn(body, slot)) {
				case CLOTH -> QUIET_PADDING;
				case MAIL -> QUIET_MAIL;
				case PLATE -> QUIET_PLATE;
				case NONE -> 1.0F;
			};
		}
		return factor;
	}

	/** Whether every piece {@code body} wears, in its slots and in Jaymo's Kingdoms' harness layers, is padding, mail or nothing: no plate anywhere. */
	public static boolean light(LivingEntity body) {
		for (EquipmentSlot slot : SLOTS) {
			if (ArmourKind.worn(body, slot) == ArmourKind.PLATE) {
				return false;
			}
		}
		return !ArmourKind.layers(body, "all").contains(ArmourKind.PLATE);
	}

	/**
	 * Whether {@code body} wears the whole {@code set} (head, chest, legs, feet, in that order). Each of the four slots
	 * holds its piece of the set (not broken), or, for head, legs and feet only, is covered by a harness layer of kind
	 * {@code layer} ({@link ArmourKind#harness}: mail for the mailed set, padding for the studded) when the chest slot
	 * holds the set's chest piece. So a mailed jack over a gambeson and a mail hauberk, in a surcoat, still counts as a
	 * mailed set; a harness alone, without the set's chest piece, does not.
	 */
	public static boolean fullSet(LivingEntity body, Item[] set, ArmourKind layer) {
		if (!has(body, EquipmentSlot.CHEST, set[1])) {
			return false;
		}
		boolean layered = ArmourKind.layers(body, "all").contains(layer);
		return (layered || has(body, EquipmentSlot.HEAD, set[0])) && (layered || has(body, EquipmentSlot.LEGS, set[2]))
			&& (layered || has(body, EquipmentSlot.FEET, set[3]));
	}

	private static boolean has(LivingEntity body, EquipmentSlot slot, Item piece) {
		ItemStack stack = body.getItemBySlot(slot);
		return stack.is(piece) && !ArmourWear.broken(stack);
	}

	private static float slotShare(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ArmourKind.HEAD_SHARE;
			case CHEST -> ArmourKind.CHEST_SHARE;
			case LEGS -> ArmourKind.LEGS_SHARE;
			default -> ArmourKind.FEET_SHARE;
		};
	}
}
