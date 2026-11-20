package com.peterwolf.forestfire.fire.simulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record FireCellRecord(
   BlockPos pos,
   byte heat,
   byte moisture,
   byte fuel,
   byte stageOrdinal,
   short reignitionTimer,
   byte materialOrdinal,
   int incidentId,
   byte integrity,
   float heatRemainder,
   float moistureRemainder,
   float fuelRemainder
) {
   public static final Codec<FireCellRecord> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(FireCellRecord::pos),
            Codec.BYTE.fieldOf("heat").forGetter(FireCellRecord::heat),
            Codec.BYTE.fieldOf("moisture").forGetter(FireCellRecord::moisture),
            Codec.BYTE.fieldOf("fuel").forGetter(FireCellRecord::fuel),
            Codec.BYTE.fieldOf("stage").forGetter(FireCellRecord::stageOrdinal),
            Codec.SHORT.fieldOf("reignite").forGetter(FireCellRecord::reignitionTimer),
            Codec.BYTE.fieldOf("material").forGetter(FireCellRecord::materialOrdinal),
            Codec.INT.fieldOf("incident").forGetter(FireCellRecord::incidentId),
            Codec.BYTE.optionalFieldOf("integrity", (byte)100).forGetter(FireCellRecord::integrity),
            Codec.FLOAT.optionalFieldOf("heatRemainder", 0.0F).forGetter(FireCellRecord::heatRemainder),
            Codec.FLOAT.optionalFieldOf("moistureRemainder", 0.0F).forGetter(FireCellRecord::moistureRemainder),
            Codec.FLOAT.optionalFieldOf("fuelRemainder", 0.0F).forGetter(FireCellRecord::fuelRemainder)
         )
         .apply(instance, FireCellRecord::new)
   );

   public static FireCellRecord from(BlockPos pos, FireCell cell) {
      return new FireCellRecord(
         pos.immutable(),
         cell.heat,
         cell.moisture,
         cell.fuel,
         cell.stageOrdinal,
         cell.reignitionTimer,
         cell.materialOrdinal,
         cell.incidentId,
         cell.integrity,
         cell.heatRemainder,
         cell.moistureRemainder,
         cell.fuelRemainder
      );
   }

   public FireCell toCell() {
      FireCell cell = new FireCell();
      cell.heat = this.heat;
      cell.moisture = this.moisture;
      cell.fuel = this.fuel;
      cell.stageOrdinal = this.stageOrdinal;
      cell.reignitionTimer = this.reignitionTimer;
      cell.materialOrdinal = this.materialOrdinal;
      cell.incidentId = this.incidentId;
      cell.integrity = this.integrity;
      cell.heatRemainder = finiteRemainder(this.heatRemainder);
      cell.moistureRemainder = finiteRemainder(this.moistureRemainder);
      cell.fuelRemainder = Float.isFinite(this.fuelRemainder) ? Math.max(0.0F, Math.min(0.999F, this.fuelRemainder)) : 0.0F;
      return cell;
   }

   private static float finiteRemainder(float value) {
      return Float.isFinite(value) ? Math.max(-0.999F, Math.min(0.999F, value)) : 0.0F;
   }
}
