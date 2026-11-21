package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireCell;
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
      // (Jaymo's port) fire is vanilla fire here: the simulation no longer takes over fires (FireBlockMixin is gone), so
      // whatever an older version left burning in a world is put out when the server starts, and the fire blocks over it
      // burn out by the vanilla rules
      ServerLifecycleEvents.SERVER_STARTED.register(server -> {
         for (ServerLevel level : server.getAllLevels()) {
            try {
               IncidentManager manager = IncidentManager.get(level);
               for (FireIncident incident : manager.openIncidents()) {
                  manager.extinguishAll(incident.id);
               }
               FireSimulation sim = FireSimulation.get(level);
               java.util.Set<Integer> left = new java.util.HashSet<>();
               for (FireCell cell : sim.cells().values()) {
                  left.add(cell.incidentId);
               }
               for (int id : left) {
                  sim.extinguishIncident(id);
               }
               manager.markDirty();
            } catch (Exception var7) {
            }
         }
      });
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
