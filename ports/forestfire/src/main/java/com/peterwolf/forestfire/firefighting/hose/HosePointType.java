package com.peterwolf.forestfire.firefighting.hose;

public enum HosePointType {
   PUMP_PORT,
   GROUND_ANCHOR,
   TERRAIN_POINT,
   SPLITTER_PORT,
   PLAYER_ENDPOINT,
   NOZZLE_ENDPOINT,
   WATER_ENDPOINT;

   public static HosePointType byName(String name) {
      if (name != null && !name.isEmpty()) {
         try {
            return valueOf(name);
         } catch (IllegalArgumentException var2) {
            return TERRAIN_POINT;
         }
      } else {
         return TERRAIN_POINT;
      }
   }
}
