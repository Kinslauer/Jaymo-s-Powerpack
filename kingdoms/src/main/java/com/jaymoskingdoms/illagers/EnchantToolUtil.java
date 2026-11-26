package com.jaymoskingdoms.illagers;

import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;

/** (Illager Invasion, MIT) The archivist's blessing: an ally's weapon enchanted. */
public class EnchantToolUtil {
   /** A sword, an axe, a bow, a crossbow or a trident. */
   static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
   }

   public static boolean eligibleForEnchant(LivingEntity entity) {
      return isWeapon(entity.getMainHandItem()) || isWeapon(entity.getOffhandItem());
   }

   public static void enchantHeldGear(LivingEntity entity) {
      tryEnchantHeldGear(entity, EquipmentSlot.MAINHAND, entity.getMainHandItem());
      tryEnchantHeldGear(entity, EquipmentSlot.OFFHAND, entity.getOffhandItem());
   }

   private static void tryEnchantHeldGear(LivingEntity entity, EquipmentSlot equipmentSlot, ItemStack mainHandItem) {
      if (isWeapon(mainHandItem)) {
         RegistryLookup<Enchantment> registryLookup = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
         enchantWeaponItem(mainHandItem, entity.getRandom(), registryLookup);
         entity.setItemSlot(equipmentSlot, mainHandItem);
      }
   }

   private static void enchantWeaponItem(ItemStack itemStack, RandomSource randomSource, RegistryLookup<Enchantment> registryLookup) {
      Mutable enchantments = new Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemStack));
      if (itemStack.is(Items.BOW)) {
         enchantments.upgrade(registryLookup.getOrThrow(Enchantments.POWER), 3);
      } else if (itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTags.AXES)) {
         enchantments.upgrade(registryLookup.getOrThrow(Enchantments.SHARPNESS), 3);
      } else if (itemStack.is(Items.CROSSBOW)) {
         if (randomSource.nextInt(2) == 0) {
            enchantments.upgrade(registryLookup.getOrThrow(Enchantments.PIERCING), 4);
         } else {
            enchantments.upgrade(registryLookup.getOrThrow(Enchantments.MULTISHOT), 1);
         }
      } else if (itemStack.is(Items.TRIDENT)) {
         enchantments.upgrade(registryLookup.getOrThrow(Enchantments.IMPALING), 3);
      }

      EnchantmentHelper.setEnchantments(itemStack, enchantments.toImmutable());
   }
}
