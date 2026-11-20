package com.peterwolf.forestfire.fire.simulation;

public enum BurnStage {
   UNBURNED,
   HEATING,
   IGNITED,
   FLAMING,
   FULLY_INVOLVED,
   SMOULDERING,
   CHARRED,
   EXTINGUISHED,
   WET,
   REIGNITION_RISK;

   public static BurnStage byOrdinal(int ordinal) {
      BurnStage[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNBURNED;
   }

   public boolean isBurning() {
      return this == IGNITED || this == FLAMING || this == FULLY_INVOLVED || this == SMOULDERING;
   }

   public boolean hasVisibleFlames() {
      return this == FLAMING || this == FULLY_INVOLVED;
   }

   public boolean isHotspotRisk() {
      return this == SMOULDERING || this == REIGNITION_RISK || this == CHARRED;
   }
}
