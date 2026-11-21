package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.core.ArmourEase;
import com.jaymospowers.core.ArmourKind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * A full studded set ({@link ArmourEase#fullSet}, harness layers counted) weathers fire and frost: burning lasts
 * {@link #BASE} shorter and freezing builds {@link #BASE} slower, each Fire Protection (burning) or Frost Walker
 * (freezing) level worn adds {@link #PER_LEVEL}, never more than {@link #MOST} in all. No immunity. The cuts are applied by
 * {@code StuddedFireMixin} and {@code StuddedFrostMixin}; Kitchen's body heat counts the set as a warm piece by itself.
 */
public final class StuddedWeather {
	// ===== the knobs ================================================================================================
	public static final float BASE = 0.4F, PER_LEVEL = 0.1F, MOST = 0.6F;
	// ================================================================================================================

	private StuddedWeather() {
	}

	/** The share by which {@code body}'s burning is shorter: 0 unless it wears a full studded set. */
	public static float fireCut(LivingEntity body) {
		return cut(body, Enchantments.FIRE_PROTECTION);
	}

	/** The share by which freezing builds slower on {@code body}: 0 unless it wears a full studded set. */
	public static float frostCut(LivingEntity body) {
		return cut(body, Enchantments.FROST_WALKER);
	}

	private static float cut(LivingEntity body, ResourceKey<Enchantment> enchantment) {
		if (!ArmourEase.fullSet(body, LightArmour.STUDDED, ArmourKind.CLOTH)) {
			return 0.0F;
		}
		int levels = 0;
		var holder = body.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment);
		if (holder.isPresent()) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				levels += body.getItemBySlot(slot).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder.get());
			}
		}
		return Math.min(MOST, BASE + PER_LEVEL * levels);
	}
}
