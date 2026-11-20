package com.peterwolf.forestfire.fire.weather;

public enum FireDangerLevel {
   LOW,
   MODERATE,
   HIGH,
   VERY_HIGH,
   EXTREME;

   public float spreadMultiplier() {
      return switch (this) {
         case LOW -> 0.45F;
         case MODERATE -> 0.75F;
         case HIGH -> 1.0F;
         case VERY_HIGH -> 1.35F;
         case EXTREME -> 1.8F;
      };
   }
}
