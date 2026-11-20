package com.peterwolf.forestfire.firefighting.nozzle;

public enum NozzleMode {
   SHUTOFF(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, "Shutoff"),
   STRAIGHT_STREAM(18.0F, 0.75F, 1.15F, 0.8F, 0.55F, "Straight Stream"),
   NARROW_FOG(11.0F, 1.9F, 1.55F, 0.6F, 0.9F, "Narrow Fog"),
   WIDE_FOG(6.0F, 3.2F, 1.75F, 0.4F, 1.2F, "Wide Fog");

   public final float range;
   public final float radius;
   public final float cooling;
   public final float pressureDemand;
   public final float flowDemand;
   public final String label;

   private NozzleMode(float range, float radius, float cooling, float pressureDemand, float flowDemand, String label) {
      this.range = range;
      this.radius = radius;
      this.cooling = cooling;
      this.pressureDemand = pressureDemand;
      this.flowDemand = flowDemand;
      this.label = label;
   }

   public NozzleMode next() {
      NozzleMode[] values = values();
      return values[(this.ordinal() + 1) % values.length];
   }

   public NozzleMode cycleCombat() {
      return switch (this) {
         case SHUTOFF -> STRAIGHT_STREAM;
         case STRAIGHT_STREAM -> NARROW_FOG;
         case NARROW_FOG -> WIDE_FOG;
         case WIDE_FOG -> SHUTOFF;
      };
   }

   public static NozzleMode byOrdinal(int ordinal) {
      NozzleMode[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STRAIGHT_STREAM;
   }

   public static NozzleMode byName(String raw) {
      try {
         return valueOf(raw.toUpperCase());
      } catch (Exception var2) {
         return STRAIGHT_STREAM;
      }
   }

   public boolean allowsFlow() {
      return this != SHUTOFF && this.range > 0.0F;
   }
}
