package com.peterwolf.forestfire.fire.simulation;

public final class FireSpreadMath {
   private FireSpreadMath() {
   }

   public static float timeScale(int fireTickInterval) {
      return Math.max(1, fireTickInterval) / 5.0F;
   }

   public static float windFactor(float windSpeed, float windX, float windZ, float travelX, float travelZ) {
      float travelLength = (float)Math.sqrt(travelX * travelX + travelZ * travelZ);
      if (!(travelLength < 1.0E-4F) && !(windSpeed <= 0.0F)) {
         float windLength = (float)Math.sqrt(windX * windX + windZ * windZ);
         if (windLength < 1.0E-4F) {
            return 1.0F;
         } else {
            float alignment = (travelX * windX + travelZ * windZ) / (travelLength * windLength);
            alignment = clamp(alignment, -1.0F, 1.0F);
            float speed = clamp(windSpeed, 0.0F, 1.65F);
            return alignment >= 0.0F ? clamp(1.0F + alignment * speed * 1.6F, 1.0F, 3.0F) : clamp(1.0F / (1.0F + -alignment * speed * 1.6F), 0.35F, 1.0F);
         }
      } else {
         return 1.0F;
      }
   }

   public static float slopeFactor(int verticalDelta, float horizontalDistance, float uphillBonus, float downhillPenalty) {
      if (verticalDelta != 0 && !(horizontalDistance < 1.0E-4F)) {
         float grade = Math.abs(verticalDelta) / horizontalDistance;
         float normalizedGrade = Math.min(1.0F, grade);
         return verticalDelta > 0
            ? 1.0F + normalizedGrade * Math.max(0.0F, uphillBonus)
            : Math.max(0.35F, 1.0F - normalizedGrade * Math.max(0.0F, downhillPenalty));
      } else {
         return 1.0F;
      }
   }

   public static float heatFlux(
      float sourceHeat,
      float sourceFlameIntensity,
      float targetIgniteEase,
      float dangerMultiplier,
      float windMultiplier,
      float slopeMultiplier,
      float atmosphericHumidity,
      float targetMoisture,
      float transferMultiplier,
      float distance,
      boolean raining
   ) {
      float sourcePower = (0.35F + clamp(sourceHeat, 0.0F, 1.0F) * 0.65F) * (0.4F + clamp(sourceFlameIntensity, 0.0F, 1.0F) * 0.6F);
      float fuelAbsorption = 0.55F + clamp(targetIgniteEase, 0.0F, 1.0F) * 0.55F;
      float moistureResistance = 1.0F - clamp(atmosphericHumidity, 0.0F, 1.0F) * 0.25F - clamp(targetMoisture, 0.0F, 1.0F) * 0.6F;
      moistureResistance = clamp(moistureResistance, 0.08F, 1.0F);
      float rainFactor = raining ? 0.22F : 1.0F;
      float attenuation = 1.0F / Math.max(1.0F, distance);
      float heat = 4.0F
         * sourcePower
         * fuelAbsorption
         * clamp(dangerMultiplier, 0.25F, 3.0F)
         * clamp(windMultiplier, 0.25F, 3.0F)
         * clamp(slopeMultiplier, 0.35F, 2.0F)
         * moistureResistance
         * Math.max(0.0F, transferMultiplier)
         * attenuation
         * rainFactor;
      return clamp(heat, 0.0F, 12.0F);
   }

   public static float ignitionThreshold(float igniteEase, float moisture) {
      float raw = 30.0F + clamp(moisture, 0.0F, 1.0F) * 40.0F;
      return raw * (1.0F - clamp(igniteEase, 0.0F, 1.0F) * 0.4F);
   }

   public static float probabilityForStep(float baseProbability, float timeScale) {
      float probability = clamp(baseProbability, 0.0F, 1.0F);
      if (probability == 0.0F || timeScale <= 0.0F) {
         return 0.0F;
      } else {
         return probability == 1.0F ? 1.0F : (float)(1.0 - Math.pow(1.0 - probability, timeScale));
      }
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}
