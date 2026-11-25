package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.DinoItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Foods {
   private Foods() {
   }

   public static float meatValue(ItemStack s) {
      if (s.is((Item)DinoItems.RAW_PRIME_MEAT)) {
         return 8.0F;
      } else if (s.is((Item)DinoItems.COOKED_PRIME_MEAT)) {
         return 5.0F;
      } else if (s.is((Item)DinoItems.RAW_DINO_MEAT)) {
         return 4.0F;
      } else if (s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON)) {
         return 3.0F;
      } else {
         return s.is(Items.ROTTEN_FLESH) ? 1.0F : 2.0F;
      }
   }

   public static float plantValue(ItemStack s) {
      if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) {
         return 12.0F;
      } else if (s.is(Items.GOLDEN_CARROT)) {
         return 8.0F;
      } else if (s.is(Items.HAY_BLOCK) || s.is(Items.MELON) || s.is(Items.PUMPKIN)) {
         return 6.0F;
      } else if (s.is(Items.GLOW_BERRIES)) {
         return 4.0F;
      } else {
         return !s.is(Items.APPLE) && !s.is(Items.MELON_SLICE) && !s.is(Items.SWEET_BERRIES) ? 2.0F : 3.0F;
      }
   }
}
