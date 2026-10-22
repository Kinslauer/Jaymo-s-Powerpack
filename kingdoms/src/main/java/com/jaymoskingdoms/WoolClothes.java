package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Knitted wool clothes: a cap, a shirt, pants and socks. Light armour that barely turns a blow, but warm: each piece
 * worn takes {@link #WOOL_COLD_SHARE} off the cold's toll on an Emberborn ({@link Heritage}: the weakness and slowness
 * of cold lands and of freezing, and the hunger of the cold). They take dye like leather; shepherds (the tailors) sell
 * them already dyed, and they're knitted from wool at a crafting table.
 */
public final class WoolClothes {
	/** The share of the cold's penalties each worn piece takes away (all four: most of it). */
	static final double WOOL_COLD_SHARE = 0.2;
	/** Durability multiplier, defence (socks, pants, shirt, cap), enchantability. */
	private static final int DURABILITY = 4, SOCKS = 0, PANTS = 1, SHIRT = 1, CAP = 0, ENCHANT = 15;

	static final ArmorMaterial WOOL = new ArmorMaterial(DURABILITY, ArmorMaterials.makeDefense(SOCKS, PANTS, SHIRT, CAP, SHIRT), ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.WOOL, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("wool")));

	public static Item CAP_ITEM;
	public static Item SHIRT_ITEM;
	public static Item PANTS_ITEM;
	public static Item SOCKS_ITEM;

	private WoolClothes() {
	}

	static void register() {
		CAP_ITEM = piece("wool_cap", ArmorType.HELMET);
		SHIRT_ITEM = piece("wool_shirt", ArmorType.CHESTPLATE);
		PANTS_ITEM = piece("wool_pants", ArmorType.LEGGINGS);
		SOCKS_ITEM = piece("wool_socks", ArmorType.BOOTS);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.LEATHER_BOOTS;
			for (Item item : List.of(CAP_ITEM, SHIRT_ITEM, PANTS_ITEM, SOCKS_ITEM)) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static Item piece(String id, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(WOOL, type)));
	}

	/** How many wool pieces {@code entity} has on. */
	static int worn(LivingEntity entity) {
		if (CAP_ITEM == null) {
			return 0;
		}
		int pieces = 0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			Item item = entity.getItemBySlot(slot).getItem();
			if (item == CAP_ITEM || item == SHIRT_ITEM || item == PANTS_ITEM || item == SOCKS_ITEM) {
				pieces++;
			}
		}
		return pieces;
	}

	/** The share of the cold's penalties {@code entity}'s wool keeps off: 0 (none worn) to 1. */
	static double warmth(LivingEntity entity) {
		return Math.min(1.0, worn(entity) * WOOL_COLD_SHARE);
	}
}
