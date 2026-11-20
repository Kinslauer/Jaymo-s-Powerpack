package com.peterwolf.forestfire.fire.simulation;

public final class FireCell {
   public byte heat;
   public byte moisture;
   public byte fuel;
   public byte stageOrdinal;
   public short reignitionTimer;
   public byte materialOrdinal;
   public int incidentId;
   public byte integrity;
   public float heatRemainder;
   public float moistureRemainder;
   public float fuelRemainder;

   public FireCell() {
   }

   public FireCell(FuelMaterial material, int incidentId) {
      this.materialOrdinal = (byte)material.ordinal();
      this.moisture = (byte)Math.round(material.baseMoisture * 100.0F);
      this.fuel = (byte)material.initialFuelPercent();
      this.heat = 0;
      this.stageOrdinal = (byte)BurnStage.UNBURNED.ordinal();
      this.reignitionTimer = 0;
      this.incidentId = incidentId;
      this.integrity = 100;
   }

   public BurnStage stage() {
      return BurnStage.byOrdinal(this.stageOrdinal & 255);
   }

   public void setStage(BurnStage stage) {
      this.stageOrdinal = (byte)stage.ordinal();
   }

   public FuelMaterial material() {
      FuelMaterial[] values = FuelMaterial.values();
      int index = this.materialOrdinal & 255;
      return index >= 0 && index < values.length ? values[index] : FuelMaterial.NON_FLAMMABLE;
   }

   public float heat01() {
      return (this.heat & 255) / 100.0F;
   }

   public float moisture01() {
      return (this.moisture & 255) / 100.0F;
   }

   public float fuel01() {
      return (this.fuel & 255) / 100.0F;
   }

   public void addHeat(float amount) {
      if (Float.isFinite(amount) && amount != 0.0F) {
         float accumulated = this.heatRemainder + amount;
         int whole = wholeUnitsTowardZero(accumulated);
         this.heatRemainder = accumulated - whole;
         int current = this.heat & 255;
         int next = Math.min(100, Math.max(0, current + whole));
         if (next != current + whole) {
            this.heatRemainder = 0.0F;
         }

         if (next == 100 && this.heatRemainder > 0.0F || next == 0 && this.heatRemainder < 0.0F) {
            this.heatRemainder = 0.0F;
         }

         this.heat = (byte)next;
      }
   }

   public void addMoisture(float amount) {
      if (Float.isFinite(amount) && amount != 0.0F) {
         float accumulated = this.moistureRemainder + amount;
         int whole = wholeUnitsTowardZero(accumulated);
         this.moistureRemainder = accumulated - whole;
         int current = this.moisture & 255;
         int next = Math.min(100, Math.max(0, current + whole));
         if (next != current + whole) {
            this.moistureRemainder = 0.0F;
         }

         if (next == 100 && this.moistureRemainder > 0.0F || next == 0 && this.moistureRemainder < 0.0F) {
            this.moistureRemainder = 0.0F;
         }

         this.moisture = (byte)next;
      }
   }

   public void consumeFuel(float amount) {
      if (Float.isFinite(amount) && !(amount <= 0.0F)) {
         float accumulated = this.fuelRemainder + amount;
         int whole = (int)Math.floor(accumulated);
         this.fuelRemainder = accumulated - whole;
         int next = Math.max(0, (this.fuel & 255) - whole);
         if (next == 0) {
            this.fuelRemainder = 0.0F;
         }

         this.fuel = (byte)next;
      }
   }

   public void setHeat(int value) {
      this.heat = (byte)Math.min(100, Math.max(0, value));
      this.heatRemainder = 0.0F;
   }

   public void setMoisture(int value) {
      this.moisture = (byte)Math.min(100, Math.max(0, value));
      this.moistureRemainder = 0.0F;
   }

   public void setFuel(int value) {
      this.fuel = (byte)Math.min(100, Math.max(0, value));
      this.fuelRemainder = 0.0F;
   }

   private static int wholeUnitsTowardZero(float value) {
      return value >= 0.0F ? (int)Math.floor(value) : (int)Math.ceil(value);
   }

   public FireCell.ThermalReading thermalReading() {
      int h = this.heat & 255;
      if (h < 15) {
         return FireCell.ThermalReading.COLD;
      } else if (h < 35) {
         return FireCell.ThermalReading.WARM;
      } else {
         return h < 65 ? FireCell.ThermalReading.HOT : FireCell.ThermalReading.CRITICAL;
      }
   }

   public FireCell copy() {
      FireCell copy = new FireCell();
      copy.heat = this.heat;
      copy.moisture = this.moisture;
      copy.fuel = this.fuel;
      copy.stageOrdinal = this.stageOrdinal;
      copy.reignitionTimer = this.reignitionTimer;
      copy.materialOrdinal = this.materialOrdinal;
      copy.incidentId = this.incidentId;
      copy.integrity = this.integrity;
      copy.heatRemainder = this.heatRemainder;
      copy.moistureRemainder = this.moistureRemainder;
      copy.fuelRemainder = this.fuelRemainder;
      return copy;
   }

   public static enum ThermalReading {
      COLD,
      WARM,
      HOT,
      CRITICAL;
   }
}
