package com.peterwolf.forestfire.firefighting.nozzle;

public enum NozzleLocationState {
   GROUND,
   HELD,
   DISCONNECTED;

   public static NozzleLocationState byName(String raw) {
      try {
         return valueOf(raw.toUpperCase());
      } catch (Exception var2) {
         return DISCONNECTED;
      }
   }
}
