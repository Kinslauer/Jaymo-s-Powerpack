package com.jaymospowers.aether.gear;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The six armour sets of the Aether (notes: structures.md section 5): durability multiplier, defence (helmet, chestplate,
 * leggings, boots), enchantability, toughness. Zanite, arkenium, gravitite and neptune also give a bonus for the whole set
 * ({@link ArmorAbilities}); the pelt and plate sets are plain.
 */
public enum ArmorSet {
	// ---- tuning ----
	BEAST_PELT("beast_pelt", 5, 1, 3, 2, 1, 15, 0.0F, SoundEvents.ARMOR_EQUIP_LEATHER),
	BURRUKAI_PLATE("burrukai_plate", 5, 2, 5, 4, 1, 15, 0.0F, SoundEvents.ARMOR_EQUIP_IRON),
	ZANITE("zanite", 15, 2, 6, 5, 2, 9, 0.0F, SoundEvents.ARMOR_EQUIP_DIAMOND),
	ARKENIUM("arkenium", 15, 2, 6, 5, 2, 10, 1.0F, SoundEvents.ARMOR_EQUIP_NETHERITE),
	GRAVITITE("gravitite", 33, 3, 8, 6, 3, 10, 2.0F, SoundEvents.ARMOR_EQUIP_NETHERITE),
	NEPTUNE("neptune", 33, 2, 6, 5, 2, 10, 1.0F, SoundEvents.ARMOR_EQUIP_DIAMOND);

	/** The four slots, in the order {@link #pieces} lists them. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	public static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	public static final String[] NAMES = {"helmet", "chestplate", "leggings", "boots"};

	public final String id;
	public final ArmorMaterial material;
	public final TagKey<Item> repairs;
	/** Helmet, chestplate, leggings, boots (set by {@link AetherGear#init}). */
	public final Item[] pieces = new Item[4];

	ArmorSet(String id, int durability, int helmet, int chest, int legs, int boots, int enchantability, float toughness, Holder<SoundEvent> sound) {
		this.id = id;
		this.repairs = TagKey.create(Registries.ITEM, JaymosPowers.id("repairs_" + id + "_armor"));
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.HELMET, helmet);
		defense.put(ArmorType.CHESTPLATE, chest);
		defense.put(ArmorType.LEGGINGS, legs);
		defense.put(ArmorType.BOOTS, boots);
		defense.put(ArmorType.BODY, chest);
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id(id));
		this.material = new ArmorMaterial(durability, defense, enchantability, sound, toughness, 0.0F, this.repairs, asset);
	}

	/** Whether {@code entity} wears all four pieces. */
	public boolean wornBy(LivingEntity entity) {
		for (int i = 0; i < 4; i++) {
			if (this.pieces[i] == null || !entity.getItemBySlot(SLOTS[i]).is(this.pieces[i])) {
				return false;
			}
		}
		return true;
	}
}
