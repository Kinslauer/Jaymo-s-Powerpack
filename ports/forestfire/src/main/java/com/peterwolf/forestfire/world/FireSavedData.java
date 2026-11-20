package com.peterwolf.forestfire.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.simulation.FireCellRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class FireSavedData extends SavedData {
   private static final Codec<FireSavedData> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            FireIncident.CODEC.listOf().optionalFieldOf("incidents", List.of()).forGetter(FireSavedData::incidents),
            FireCellRecord.CODEC.listOf().optionalFieldOf("cells", List.of()).forGetter(FireSavedData::cells),
            Codec.FLOAT.optionalFieldOf("windSpeed", 0.25F).forGetter(FireSavedData::windSpeed),
            Codec.FLOAT.optionalFieldOf("windYaw", 0.0F).forGetter(FireSavedData::windYaw)
         )
         .apply(instance, FireSavedData::new)
   );
   public static final SavedDataType<FireSavedData> TYPE = new SavedDataType(ForestFireMod.id("incidents"), FireSavedData::new, CODEC, DataFixTypes.LEVEL);
   private final List<FireIncident> incidents = new ArrayList<>();
   private final List<FireCellRecord> cells = new ArrayList<>();
   private float windSpeed;
   private float windYaw;

   public FireSavedData() {
      this(List.of(), List.of(), 0.25F, 0.0F);
   }

   public FireSavedData(List<FireIncident> incidents, List<FireCellRecord> cells, float windSpeed, float windYaw) {
      this.incidents.addAll(incidents);
      this.cells.addAll(cells);
      this.windSpeed = windSpeed;
      this.windYaw = windYaw;
   }

   public static FireSavedData get(ServerLevel level) {
      return (FireSavedData)level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   public List<FireIncident> incidents() {
      return this.incidents;
   }

   public List<FireCellRecord> cells() {
      return this.cells;
   }

   public float windSpeed() {
      return this.windSpeed;
   }

   public float windYaw() {
      return this.windYaw;
   }

   public void replaceIncidents(List<FireIncident> next) {
      this.incidents.clear();
      this.incidents.addAll(next);
      this.setDirty();
   }

   public void replaceCells(List<FireCellRecord> next) {
      this.cells.clear();
      this.cells.addAll(next);
      this.setDirty();
   }

   public void setWind(float speed, float yaw) {
      this.windSpeed = speed;
      this.windYaw = yaw;
      this.setDirty();
   }
}
