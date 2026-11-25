package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.Dino;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ModTriggers {
   private ModTriggers() {
   }

   public static void award(ServerPlayer player, String name) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         AdvancementHolder holder = server.getAdvancements().get(Dino.id("dino/" + name));
         if (holder != null) {
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) {
               for (String criterion : progress.getRemainingCriteria()) {
                  player.getAdvancements().award(holder, criterion);
               }
            }
         }
      }
   }
}
