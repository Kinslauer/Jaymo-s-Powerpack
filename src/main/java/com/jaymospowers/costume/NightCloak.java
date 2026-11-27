package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The night-cloak (Night's Blood: "a deep-hooded cloak, sold only by tailors"): a hood joined to a cloak, as the Archers'
 * hoods are ({@link HoodedCloak#HOODS}), so it lowers and raises with the mask key and hoods its wearer. It has no recipe:
 * Kingdoms' tailors sell it. What it does against the sun, and to the folk who see its wearer, is Kingdoms' ({@code NightCloaks}).
 */
public final class NightCloak {
	/** The item. */
	public static Item NIGHT_CLOAK;

	private NightCloak() {
	}

	/** Registers the item. */
	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("night_cloak"));
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("night_cloak"));
		NIGHT_CLOAK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(asset)
				.build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(NIGHT_CLOAK));
	}
}
