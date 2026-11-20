package com.peterwolf.forestfire.mission;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;

public final class MissionScorer {
   private MissionScorer() {
   }

   public static float score(FireIncident incident) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      float structuresSaved = Math.max(0, incident.threatenedStructures - incident.structuresLost);
      float livesSaved = Math.max(0, incident.endangeredLives - incident.livesLost);
      float structureScore = structuresSaved * cfg.scoreStructuresSavedWeight;
      float lifeScore = livesSaved * cfg.scoreLivesSavedWeight;
      float containmentSpeed = incident.containmentPercent / 100.0F;
      if (incident.peakBurningBlocks > 0 && incident.burningBlocks < incident.peakBurningBlocks) {
         containmentSpeed *= 1.0F + (1.0F - (float)incident.burningBlocks / incident.peakBurningBlocks);
      }

      float speedScore = containmentSpeed * cfg.scoreContainmentSpeedWeight * 10.0F;
      float extinguished = Math.max(1, incident.peakBurningBlocks - incident.burningBlocks);
      float waterPerBlock = incident.waterUsed / extinguished;
      float efficiency = Math.max(0.0F, 1.0F - Math.min(1.0F, waterPerBlock / 80.0F));
      float waterScore = efficiency * cfg.scoreWaterEfficiencyWeight * 10.0F;
      float teamScore = Math.min(10, incident.participants.size()) * cfg.scoreTeamworkWeight;
      float penalty = incident.structuresLost * 20.0F + incident.livesLost * 50.0F;
      if (incident.hotspotCount > 0) {
         penalty += incident.hotspotCount * 2.0F;
      }

      return Math.max(0.0F, structureScore + lifeScore + speedScore + waterScore + teamScore - penalty);
   }

   public static String grade(float score) {
      if (score >= 400.0F) {
         return "OUTSTANDING";
      } else if (score >= 250.0F) {
         return "EXCELLENT";
      } else if (score >= 150.0F) {
         return "GOOD";
      } else {
         return score >= 75.0F ? "FAIR" : "NEEDS IMPROVEMENT";
      }
   }
}
