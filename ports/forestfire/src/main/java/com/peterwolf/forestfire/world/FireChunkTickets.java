package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.ForestFireMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public final class FireChunkTickets {
   private static final long SAFETY_TIMEOUT_TICKS = 200L;
   public static final TicketType ACTIVE_FIRE = (TicketType)Registry.register(
      BuiltInRegistries.TICKET_TYPE, ForestFireMod.id("active_fire"), new TicketType(200L, 2)
   );

   private FireChunkTickets() {
   }

   public static void register() {
   }
}
