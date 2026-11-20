package com.peterwolf.forestfire.firefighting.equipment;

import com.peterwolf.forestfire.ForestFireMod;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class FirefighterArmorMaterials {
   public static final ResourceKey<EquipmentAsset> ASSET_KEY = ResourceKey.create(EquipmentAssets.ROOT_ID, ForestFireMod.id("firefighter"));
   public static final ArmorMaterial FIREFIGHTER;

   private FirefighterArmorMaterials() {
   }

   public static void register() {
      ForestFireMod.LOGGER.info("Registered firefighter armor material");
   }

   static {
      Map<ArmorType, Integer> defence = new EnumMap<>(ArmorType.class);
      defence.put(ArmorType.BOOTS, 2);
      defence.put(ArmorType.LEGGINGS, 5);
      defence.put(ArmorType.CHESTPLATE, 6);
      defence.put(ArmorType.HELMET, 2);
      defence.put(ArmorType.BODY, 4);
      FIREFIGHTER = new ArmorMaterial(18, defence, 12, SoundEvents.ARMOR_EQUIP_IRON, 0.5F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR, ASSET_KEY);
   }
}
