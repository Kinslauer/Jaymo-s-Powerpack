package com.peterwolf.forestfire.fire.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record IncidentMarker(IncidentMarkerType type, BlockPos pos, String label) {
   public static final Codec<IncidentMarker> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.STRING.fieldOf("type").xmap(IncidentMarker::parseType, Enum::name).forGetter(IncidentMarker::type),
            BlockPos.CODEC.fieldOf("pos").forGetter(IncidentMarker::pos),
            Codec.STRING.optionalFieldOf("label", "").forGetter(IncidentMarker::label)
         )
         .apply(instance, IncidentMarker::new)
   );

   private static IncidentMarkerType parseType(String raw) {
      try {
         return IncidentMarkerType.valueOf(raw.toUpperCase());
      } catch (IllegalArgumentException var2) {
         return IncidentMarkerType.DANGER_ZONE;
      }
   }
}
