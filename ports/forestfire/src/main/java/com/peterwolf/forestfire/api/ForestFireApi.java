package com.peterwolf.forestfire.api;

import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ForestFireApi {
   private ForestFireApi() {
   }

   public static Collection<FireIncident> getOpenIncidents(ServerLevel level) {
      return IncidentManager.get(level).openIncidents();
   }

   public static Optional<FireIncident> getIncident(ServerLevel level, int id) {
      return IncidentManager.get(level).get(id);
   }

   public static FireIncident createIncident(ServerLevel level, BlockPos pos, int size) {
      return IncidentManager.get(level).createAt(pos, size);
   }

   public static boolean applyWater(ServerLevel level, BlockPos pos, float strength, float radius) {
      return FireSimulation.get(level).applyWater(pos, strength, radius) > 0.0F;
   }

   public static FireDangerLevel getFireDanger(ServerLevel level, BlockPos pos) {
      return WeatherFireModel.danger(level, pos, IncidentManager.get(level).wind());
   }

   public static int getActiveFireCellCount(ServerLevel level) {
      return FireSimulation.get(level).cells().size();
   }
}
