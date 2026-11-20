package com.peterwolf.forestfire.rescue;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentMarker;
import com.peterwolf.forestfire.fire.incident.IncidentMarkerType;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RescueTicker {
   private RescueTicker() {
   }

   public static void tick(ServerLevel level, IncidentManager manager) {
      if (ForestFireConfig.get().rescueMissionsEnabled) {
         if (level.getGameTime() % 40L == 0L) {
            FireSimulation sim = FireSimulation.get(level);

            for (FireIncident incident : manager.openIncidents()) {
               AABB box = new AABB(incident.ignitionPos).inflate(48.0);
               List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);
               List<Animal> animals = level.getEntitiesOfClass(Animal.class, box);
               incident.endangeredLives = villagers.size() + animals.size();
               BlockPos safe = findEvacPoint(incident);
               if (ForestFireConfig.get().villagerPanicEnabled) {
                  for (Villager villager : villagers) {
                     pushAwayFromHeat(villager, sim, safe);
                  }
               }

               for (Animal animal : animals) {
                  pushAwayFromHeat(animal, sim, safe);
               }
            }
         }
      }
   }

   private static BlockPos findEvacPoint(FireIncident incident) {
      for (IncidentMarker marker : incident.markers) {
         if (marker.type() == IncidentMarkerType.EVACUATION_POINT) {
            return marker.pos();
         }
      }

      return incident.ignitionPos.offset(24, 0, 24);
   }

   private static void pushAwayFromHeat(Mob mob, FireSimulation sim, BlockPos safe) {
      BlockPos origin = mob.blockPosition();
      double nearestHeat = Double.MAX_VALUE;
      BlockPos heatPos = null;
      int checked = 0;

      for (Entry<Long, FireCell> entry : sim.cells().entrySet()) {
         if (checked++ > 80) {
            break;
         }

         FireCell cell = entry.getValue();
         if (cell.stage().hasVisibleFlames()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            double d = pos.distSqr(origin);
            if (d < nearestHeat && d < 256.0) {
               nearestHeat = d;
               heatPos = pos;
            }
         }
      }

      if (heatPos != null) {
         Vec3 away = Vec3.atCenterOf(origin).subtract(Vec3.atCenterOf(heatPos)).normalize();
         Vec3 towardSafe = Vec3.atCenterOf(safe).subtract(mob.position()).normalize().scale(0.35);
         Vec3 push = away.scale(0.25).add(towardSafe);
         mob.setDeltaMovement(mob.getDeltaMovement().add(push.x, 0.02, push.z));
         mob.needsSync = true;
      }
   }
}
