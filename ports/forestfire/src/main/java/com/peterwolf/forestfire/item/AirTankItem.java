package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;

public class AirTankItem extends Item {
   public static final int MAX_AIR = 1200;

   public AirTankItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public static int getAir(ItemStack stack) {
      return (Integer)stack.getOrDefault(ModDataComponents.AIR_AMOUNT, 1200);
   }

   public static void setAir(ItemStack stack, int amount) {
      stack.set(ModDataComponents.AIR_AMOUNT, Math.max(0, Math.min(1200, amount)));
   }

   public static boolean consume(ItemStack stack, int amount) {
      int air = getAir(stack);
      if (air <= 0) {
         return false;
      } else {
         setAir(stack, air - amount);
         return true;
      }
   }
}
