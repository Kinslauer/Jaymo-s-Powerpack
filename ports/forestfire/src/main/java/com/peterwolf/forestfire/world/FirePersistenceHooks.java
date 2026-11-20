package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import com.peterwolf.forestfire.firefighting.hose.HoseEndpointManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.BeforeSave;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.minecraft.server.level.ServerLevel;

public final class FirePersistenceHooks {
   private FirePersistenceHooks() {
   }

   public static void register() {
      ServerLifecycleEvents.BEFORE_SAVE.register((BeforeSave)(server, flush, force) -> {
         for (ServerLevel level : server.getAllLevels()) {
            try {
               IncidentManager.get(level).save();
               HoseEndpointManager.get(level).setDirty();
               HoseConnectionManager.get(level).setDirty();
            } catch (Exception var6) {
            }
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         for (ServerLevel level : server.getAllLevels()) {
            try {
               IncidentManager.get(level).save();
               HoseEndpointManager.get(level).setDirty();
               HoseConnectionManager.get(level).setDirty();
            } catch (Exception var4) {
            }
         }

         IncidentManager.clearAll();
         FireSimulation.clearAll();
         HoseEndpointManager.clearAll();
         HoseConnectionManager.clearAll();
      });
   }
}
