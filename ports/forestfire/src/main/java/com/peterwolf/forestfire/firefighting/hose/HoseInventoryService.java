package com.peterwolf.forestfire.firefighting.hose;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.item.HoseRollItem;
import com.peterwolf.forestfire.item.ModItems;
import net.minecraft.util.Prediction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class HoseInventoryService {
   private HoseInventoryService() {
   }

   public static int countAvailable(ServerPlayer player, boolean intake) {
      if (player.isCreative() && ForestFireConfig.get().creativeModeInfiniteHose) {
         return 536870911;
      } else if (!ForestFireConfig.get().automaticHoseConsumesItems) {
         return 536870911;
      } else {
         int total = 0;
         Inventory inv = player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof HoseRollItem) {
               HoseRollItem.ensureInitialized(stack);
               total += HoseRollItem.getRemaining(stack);
            }
         }

         return total;
      }
   }

   public static boolean consume(ServerPlayer player, int amount, boolean intake) {
      if (amount <= 0) {
         return true;
      } else if (player.isCreative() && ForestFireConfig.get().creativeModeInfiniteHose) {
         return true;
      } else if (!ForestFireConfig.get().automaticHoseConsumesItems) {
         return true;
      } else if (countAvailable(player, intake) < amount) {
         return false;
      } else {
         int remaining = amount;
         Inventory inv = player.getInventory();

         for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof HoseRollItem) {
               HoseRollItem.ensureInitialized(stack);
               int have = HoseRollItem.getRemaining(stack);
               if (have > 0) {
                  int take = Math.min(have, remaining);
                  remaining -= take;
                  int left = have - take;
                  if (left <= 0) {
                     inv.setItem(i, ItemStack.EMPTY);
                  } else {
                     HoseRollItem.setRemaining(stack, left);
                  }
               }
            }
         }

         return remaining <= 0;
      }
   }

   public static void returnHose(ServerPlayer player, int amount, boolean intake) {
      if (amount > 0) {
         if (ForestFireConfig.get().automaticHoseRetraction || ForestFireConfig.get().returnFullHoseLength) {
            int left = amount;
            if (ForestFireConfig.get().hoseDamageLossEnabled) {
               left = Math.max(0, (int)(amount * 0.9));
            }

            while (left >= 64) {
               giveRoll(player, ModItems.HOSE_ROLL_LARGE, 64);
               left -= 64;
            }

            while (left >= 32) {
               giveRoll(player, ModItems.HOSE_ROLL_STANDARD, 32);
               left -= 32;
            }

            while (left >= 16) {
               giveRoll(player, ModItems.HOSE_ROLL_SMALL, 16);
               left -= 16;
            }

            if (left > 0) {
               ItemStack partial = new ItemStack(ModItems.HOSE_ROLL_SMALL);
               HoseRollItem.setRemaining(partial, left);
               if (!player.getInventory().add(partial)) {
                  player.drop(partial, false, Prediction.SERVER_ONLY);
               }
            }
         }
      }
   }

   public static void returnUnused(ServerPlayer player, int unused) {
      if (unused > 0) {
         returnHose(player, unused, false);
      }
   }

   private static void giveRoll(ServerPlayer player, Item item, int capacity) {
      ItemStack stack = new ItemStack(item);
      if (item instanceof HoseRollItem) {
         HoseRollItem.setRemaining(stack, capacity);
      }

      if (!player.getInventory().add(stack)) {
         player.drop(stack, false, Prediction.SERVER_ONLY);
      }
   }

   public static void dropHoseAt(ServerLevel level, BlockPos pos, int amount) {
      if (amount > 0 && level != null) {
         int left = amount;
         if (ForestFireConfig.get().hoseDamageLossEnabled) {
            left = Math.max(0, (int)(amount * 0.9));
         }

         double x = pos.getX() + 0.5;
         double y = pos.getY() + 0.5;
         double z = pos.getZ() + 0.5;

         while (left > 0) {
            int cap;
            Item item;
            if (left >= 64) {
               cap = 64;
               item = ModItems.HOSE_ROLL_LARGE;
            } else if (left >= 32) {
               cap = 32;
               item = ModItems.HOSE_ROLL_STANDARD;
            } else if (left >= 16) {
               cap = 16;
               item = ModItems.HOSE_ROLL_SMALL;
            } else {
               cap = left;
               item = ModItems.HOSE_ROLL_SMALL;
            }

            ItemStack stack = new ItemStack(item);
            if (item instanceof HoseRollItem) {
               HoseRollItem.setRemaining(stack, cap);
            }

            level.addFreshEntity(new ItemEntity(level, x, y, z, stack));
            left -= cap;
         }
      }
   }
}
