package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
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
 * Qiviut: the muskox's underwool ({@code wild.Muskox} sheds it where it used to shed string) and the clothes knitted from
 * it, a cap, a jumper, trousers and socks. They are made as the knitted wool clothes are ({@link WoolClothes}): light
 * armour that barely turns a blow but keeps the cold off, each piece worn taking {@link #QIVIUT_COLD_SHARE} off the cold's
 * toll on an Emberborn (wool's share is smaller), and counting as warm clothing for Kitchen's body heat (the tag
 * {@code jaymoskitchen:warm_clothing}). Qiviut also lines a piece of armour at a loom, warmer than wool (Kitchen's
 * {@code Lining#QIVIUT}). Its own colour: they take no dye.
 */
public final class QiviutClothes {
	/** The share of the cold's penalties each worn piece takes away (all four: all of it). */
	static final double QIVIUT_COLD_SHARE = 0.25;
	/** Durability multiplier, defence (socks, pants, shirt, cap), enchantability. */
	private static final int DURABILITY = 6, SOCKS = 0, PANTS = 1, SHIRT = 1, CAP = 0, ENCHANT = 15;
	/** What mends a piece at an anvil. */
	public static final TagKey<Item> QIVIUT_TAG = TagKey.create(Registries.ITEM, JaymosKingdoms.id("qiviut"));

	static final ArmorMaterial QIVIUT = new ArmorMaterial(DURABILITY, ArmorMaterials.makeDefense(SOCKS, PANTS, SHIRT, CAP, SHIRT), ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, QIVIUT_TAG, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("qiviut")));

	public static Item RAW;
	public static Item CAP_ITEM;
	public static Item SHIRT_ITEM;
	public static Item PANTS_ITEM;
	public static Item SOCKS_ITEM;

	private QiviutClothes() {
	}

	static void register() {
		ResourceKey<Item> raw = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("qiviut"));
		RAW = Registry.register(BuiltInRegistries.ITEM, raw, new Item(new Item.Properties().setId(raw)));
		CAP_ITEM = piece("qiviut_cap", ArmorType.HELMET);
		SHIRT_ITEM = piece("qiviut_shirt", ArmorType.CHESTPLATE);
		PANTS_ITEM = piece("qiviut_pants", ArmorType.LEGGINGS);
		SOCKS_ITEM = piece("qiviut_socks", ArmorType.BOOTS);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.STRING, RAW));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = WoolClothes.SOCKS_ITEM;
			for (Item item : List.of(CAP_ITEM, SHIRT_ITEM, PANTS_ITEM, SOCKS_ITEM)) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static Item piece(String id, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(QIVIUT, type)));
	}

	/** How many qiviut pieces {@code entity} has on. */
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

	/** The share of the cold's penalties {@code entity}'s qiviut keeps off: 0 (none worn) to 1. */
	static double warmth(LivingEntity entity) {
		return Math.min(1.0, worn(entity) * QIVIUT_COLD_SHARE);
	}
}
