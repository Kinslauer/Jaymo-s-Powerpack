package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.fire.heat.HeatExposureSystem;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.firefighting.pump.PumpNetworkTicker;
import com.peterwolf.forestfire.rescue.RescueTicker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

public final class FireWorldTicker {
   private FireWorldTicker() {
   }

   public static void register() {
      ServerTickEvents.END_LEVEL_TICK.register(FireWorldTicker::onWorldTick);
   }

   private static void onWorldTick(ServerLevel level) {
      if (level.dimension() == ServerLevel.OVERWORLD || !level.players().isEmpty()) {
         IncidentManager manager = IncidentManager.get(level);
         manager.tick();
         PumpNetworkTicker.tick(level);
         HeatExposureSystem.tick(level);
         RescueTicker.tick(level, manager);
      }
   }
}
