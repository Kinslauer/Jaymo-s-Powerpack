package com.peterwolf.forestfire.fire.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FireSector {
   public static final Codec<FireSector> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(s -> s.name),
            Codec.STRING.optionalFieldOf("objective", "").forGetter(s -> s.objective),
            Codec.STRING.optionalFieldOf("status", "OPEN").forGetter(s -> s.status),
            Codec.STRING.optionalFieldOf("danger", "MODERATE").forGetter(s -> s.danger),
            Codec.STRING.optionalFieldOf("waterSupply", "UNKNOWN").forGetter(s -> s.waterSupply),
            Codec.FLOAT.optionalFieldOf("containment", 0.0F).forGetter(s -> s.containment),
            Codec.STRING.listOf().optionalFieldOf("assignees", List.of()).forGetter(FireSector::assigneeStrings)
         )
         .apply(instance, FireSector::fromCodec)
   );
   public final String name;
   public String objective;
   public String status;
   public String danger;
   public String waterSupply;
   public float containment;
   public final List<UUID> assignees = new ArrayList<>();

   public FireSector(String name) {
      this.name = name;
      this.objective = "";
      this.status = "OPEN";
      this.danger = "MODERATE";
      this.waterSupply = "UNKNOWN";
      this.containment = 0.0F;
   }

   private static FireSector fromCodec(
      String name, String objective, String status, String danger, String waterSupply, float containment, List<String> assignees
   ) {
      FireSector sector = new FireSector(name);
      sector.objective = objective;
      sector.status = status;
      sector.danger = danger;
      sector.waterSupply = waterSupply;
      sector.containment = containment;

      for (String raw : assignees) {
         try {
            sector.assignees.add(UUID.fromString(raw));
         } catch (IllegalArgumentException var11) {
         }
      }

      return sector;
   }

   private List<String> assigneeStrings() {
      List<String> list = new ArrayList<>(this.assignees.size());

      for (UUID id : this.assignees) {
         list.add(id.toString());
      }

      return list;
   }
}
