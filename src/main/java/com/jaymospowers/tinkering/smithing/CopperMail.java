package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Copper mail (from Just Better Chainmail, CC0, by multyfora: its set, its recipes from copper chains and its art): a
 * helmet, shirt, leggings and boots of copper chains, cheaper and softer than iron mail. It is mail all the same
 * ({@code jaymospowers:mail_armor}), so a cut glances off it, a thrust finds the rings, a plain arrow is turned, and in a
 * full suit most of one ({@code combat/Blows}); like every piece it keeps {@code ArmourWear}'s share of its durability.
 */
public final class CopperMail {
	// ===== the knobs ================================================================================================
	/** Copper's durability (vanilla copper armour's 11, iron mail's 15) and chainmail's points (head, chest, legs, feet). */
	static final int DURABILITY = 11, ENCHANT = 12;
	static final int[] DEFENSE = {2, 5, 4, 1};
	// ================================================================================================================

	private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final String[] NAMES = {"copper_chainmail_helmet", "copper_chainmail_chestplate", "copper_chainmail_leggings",
		"copper_chainmail_boots"};

	/** Head, chest, legs, feet. */
	public static final Item[] PIECES = new Item[4];

	private CopperMail() {
	}

	static void register() {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		for (int i = 0; i < 4; i++) {
			defense.put(TYPES[i], DEFENSE[i]);
		}
		defense.put(ArmorType.BODY, DEFENSE[1]);
		TagKey<Item> repairs = TagKey.create(Registries.ITEM, JaymosPowers.id("copper_chains"));
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("copper_chain"));
		ArmorMaterial material = new ArmorMaterial(DURABILITY, defense, ENCHANT, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, repairs, asset);
		for (int i = 0; i < 4; i++) {
			ArmorType type = TYPES[i];
			PIECES[i] = Smithing.item(NAMES[i], Item::new, p -> p.humanoidArmor(material, type));
		}
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			for (Item piece : PIECES) {
				output.accept(piece);
			}
		});
	}
}
