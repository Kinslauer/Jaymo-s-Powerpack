package com.peterwolf.forestfire.fire.weather;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.BurnStage;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public final class FirestormState {
   public static final FirestormState INACTIVE = new FirestormState(0, 0, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, false, Set.of());
   public final int incidentId;
   public final int flamingBlocks;
   public final float averageHeat;
   public final float intensity;
   public final float spreadMultiplier;
   public final float windBoost;
   public final float sparkMultiplier;
   public final boolean active;
   private final Set<Long> clusterCells;

   private FirestormState(
      int incidentId,
      int flamingBlocks,
      float averageHeat,
      float intensity,
      float spreadMultiplier,
      float windBoost,
      float sparkMultiplier,
      boolean active,
      Set<Long> clusterCells
   ) {
      this.incidentId = incidentId;
      this.flamingBlocks = flamingBlocks;
      this.averageHeat = averageHeat;
      this.intensity = intensity;
      this.spreadMultiplier = spreadMultiplier;
      this.windBoost = windBoost;
      this.sparkMultiplier = sparkMultiplier;
      this.active = active;
      this.clusterCells = clusterCells;
   }

   public static FirestormState evaluate(ServerLevel level, Map<Long, FireCell> cells) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      if (cfg.firestormEnabled && !cells.isEmpty()) {
         Set<Long> visited = new HashSet<>();
         ArrayDeque<Long> queue = new ArrayDeque<>();
         int largestCluster = 0;
         int dominantIncident = 0;
         float dominantHeat = 0.0F;
         int dominantFullyInvolved = 0;
         Set<Long> dominantCells = Set.of();

         for (Entry<Long, FireCell> entry : cells.entrySet()) {
            FireCell seed = entry.getValue();
            if (seed.stage().hasVisibleFlames() && visited.add(entry.getKey())) {
               int seedIncident = seed.incidentId;
               int clusterSize = 0;
               float clusterHeat = 0.0F;
               int clusterFullyInvolved = 0;
               Set<Long> clusterCells = new HashSet<>();
               queue.add(entry.getKey());

               while (!queue.isEmpty()) {
                  long key = queue.removeFirst();
                  BlockPos pos = BlockPos.of(key);
                  FireCell cell = cells.get(key);
                  if (cell != null && cell.stage().hasVisibleFlames() && level.isLoaded(pos)) {
                     clusterSize++;
                     clusterCells.add(key);
                     clusterHeat += cell.heat01();
                     if (cell.stage() == BurnStage.FULLY_INVOLVED) {
                        clusterFullyInvolved++;
                     }

                     for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                           for (int dz = -1; dz <= 1; dz++) {
                              if (dx != 0 || dy != 0 || dz != 0) {
                                 long neighbourKey = BlockPos.asLong(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                                 FireCell neighbour = cells.get(neighbourKey);
                                 if (neighbour != null
                                    && neighbour.stage().hasVisibleFlames()
                                    && neighbour.incidentId == seedIncident
                                    && visited.add(neighbourKey)) {
                                    queue.addLast(neighbourKey);
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (clusterSize > largestCluster || clusterSize == largestCluster && clusterHeat > dominantHeat) {
                  largestCluster = clusterSize;
                  dominantIncident = seedIncident;
                  dominantHeat = clusterHeat;
                  dominantFullyInvolved = clusterFullyInvolved;
                  dominantCells = Set.copyOf(clusterCells);
               }
            }
         }

         int threshold = Math.max(1, cfg.firestormMinBurningBlocks);
         if (largestCluster < threshold) {
            return new FirestormState(
               dominantIncident, largestCluster, largestCluster == 0 ? 0.0F : dominantHeat / largestCluster, 0.0F, 1.0F, 0.0F, 1.0F, false, Set.of()
            );
         } else {
            float avgHeat = dominantHeat / largestCluster;
            float sizeTerm = (float)(largestCluster - threshold) / threshold;
            float heatTerm = avgHeat * 1.2F + (float)dominantFullyInvolved / Math.max(1, largestCluster) * 0.5F;
            float rawIntensity = sizeTerm * 0.55F + heatTerm * 0.45F + sizeTerm * heatTerm * 0.25F;
            float intensity = Mth.clamp(rawIntensity, 0.0F, 1.0F);
            float maximumSpread = Math.max(1.0F, cfg.firestormSpreadMultiplierMax);
            float thresholdBonus = Mth.clamp(cfg.firestormThresholdSpreadBonus, 0.0F, maximumSpread - 1.0F);
            float spreadMul = 1.0F + thresholdBonus + intensity * Math.max(0.0F, maximumSpread - 1.0F - thresholdBonus);
            spreadMul = Math.min(maximumSpread, spreadMul);
            float windBoost = intensity * cfg.firestormWindBoostMax + cfg.firestormThresholdWindBonus * Math.min(1.0F, sizeTerm);
            float sparkMul = 1.0F + intensity * (cfg.firestormSparkMultiplier - 1.0F);
            return new FirestormState(dominantIncident, largestCluster, avgHeat, intensity, spreadMul, windBoost, sparkMul, true, dominantCells);
         }
      } else {
         return INACTIVE;
      }
   }

   public boolean affects(long cellKey, FireCell cell) {
      return this.active && cell != null && cell.incidentId == this.incidentId && this.clusterCells.contains(cellKey);
   }
}
