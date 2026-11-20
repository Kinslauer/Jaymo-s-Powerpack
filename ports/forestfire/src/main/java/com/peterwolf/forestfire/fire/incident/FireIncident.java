package com.peterwolf.forestfire.fire.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class FireIncident {
   public static final String[] NAME_POOL = new String[]{
      "Pine Ridge Fire",
      "North Valley Fire",
      "Old Forest Fire",
      "Riverbank Fire",
      "Western Hills Fire",
      "Cedar Hollow Fire",
      "Smoke Creek Fire",
      "Ashwood Fire"
   };
   private static final Codec<FireIncident.CoreData> CORE_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(FireIncident.CoreData::id),
            Codec.STRING.fieldOf("name").forGetter(FireIncident.CoreData::name),
            BlockPos.CODEC.fieldOf("ignition").forGetter(FireIncident.CoreData::ignition),
            Codec.LONG.fieldOf("discoveryTime").forGetter(FireIncident.CoreData::discoveryTime),
            Codec.STRING.fieldOf("status").forGetter(FireIncident.CoreData::status),
            Codec.FLOAT.optionalFieldOf("estimatedArea", 0.0F).forGetter(FireIncident.CoreData::estimatedArea),
            Codec.INT.optionalFieldOf("burningBlocks", 0).forGetter(FireIncident.CoreData::burningBlocks),
            Codec.INT.optionalFieldOf("threatenedStructures", 0).forGetter(FireIncident.CoreData::threatenedStructures),
            Codec.INT.optionalFieldOf("endangeredLives", 0).forGetter(FireIncident.CoreData::endangeredLives),
            Codec.FLOAT.optionalFieldOf("intensity", 0.0F).forGetter(FireIncident.CoreData::intensity),
            Codec.FLOAT.optionalFieldOf("windDir", 0.0F).forGetter(FireIncident.CoreData::windDir),
            Codec.FLOAT.optionalFieldOf("windSpeed", 0.0F).forGetter(FireIncident.CoreData::windSpeed),
            Codec.FLOAT.optionalFieldOf("humidity", 0.5F).forGetter(FireIncident.CoreData::humidity),
            Codec.FLOAT.optionalFieldOf("containment", 0.0F).forGetter(FireIncident.CoreData::containment),
            Codec.FLOAT.optionalFieldOf("waterUsed", 0.0F).forGetter(FireIncident.CoreData::waterUsed),
            Codec.FLOAT.optionalFieldOf("score", 0.0F).forGetter(FireIncident.CoreData::score)
         )
         .apply(instance, FireIncident.CoreData::new)
   );
   private static final Codec<FireIncident.ExtraData> EXTRA_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.INT.optionalFieldOf("peakBurning", 0).forGetter(FireIncident.ExtraData::peakBurning),
            Codec.INT.optionalFieldOf("structuresLost", 0).forGetter(FireIncident.ExtraData::structuresLost),
            Codec.INT.optionalFieldOf("livesLost", 0).forGetter(FireIncident.ExtraData::livesLost),
            Codec.INT.optionalFieldOf("hotspots", 0).forGetter(FireIncident.ExtraData::hotspots),
            Codec.STRING.listOf().optionalFieldOf("participants", List.of()).forGetter(FireIncident.ExtraData::participants),
            IncidentMarker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(FireIncident.ExtraData::markers),
            FireSector.CODEC.listOf().optionalFieldOf("sectors", List.of()).forGetter(FireIncident.ExtraData::sectors)
         )
         .apply(instance, FireIncident.ExtraData::new)
   );
   public static final Codec<FireIncident> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            CORE_CODEC.fieldOf("core").forGetter(FireIncident::toCore),
            EXTRA_CODEC.optionalFieldOf("extra", FireIncident.ExtraData.EMPTY).forGetter(FireIncident::toExtra)
         )
         .apply(instance, FireIncident::fromParts)
   );
   public final int id;
   public String name;
   public BlockPos ignitionPos;
   public long discoveryTime;
   public IncidentStatus status;
   public float estimatedFireArea;
   public int burningBlocks;
   public int threatenedStructures;
   public int endangeredLives;
   public float fireIntensity;
   public float windDirection;
   public float windSpeed;
   public float humidity;
   public float containmentPercent;
   public float waterUsed;
   public float incidentScore;
   public int peakBurningBlocks;
   public int structuresLost;
   public int livesLost;
   public int hotspotCount;
   public final Set<UUID> participants = new HashSet<>();
   public final List<IncidentMarker> markers = new ArrayList<>();
   public final List<FireSector> sectors = new ArrayList<>();

   public FireIncident(int id, String name, BlockPos ignitionPos, long discoveryTime) {
      this.id = id;
      this.name = name;
      this.ignitionPos = ignitionPos.immutable();
      this.discoveryTime = discoveryTime;
      this.status = IncidentStatus.REPORTED;
      this.ensureDefaultSectors();
   }

   private FireIncident.CoreData toCore() {
      return new FireIncident.CoreData(
         this.id,
         this.name,
         this.ignitionPos,
         this.discoveryTime,
         this.status.name(),
         this.estimatedFireArea,
         this.burningBlocks,
         this.threatenedStructures,
         this.endangeredLives,
         this.fireIntensity,
         this.windDirection,
         this.windSpeed,
         this.humidity,
         this.containmentPercent,
         this.waterUsed,
         this.incidentScore
      );
   }

   private FireIncident.ExtraData toExtra() {
      List<String> parts = new ArrayList<>(this.participants.size());

      for (UUID uuid : this.participants) {
         parts.add(uuid.toString());
      }

      return new FireIncident.ExtraData(this.peakBurningBlocks, this.structuresLost, this.livesLost, this.hotspotCount, parts, this.markers, this.sectors);
   }

   private static FireIncident fromParts(FireIncident.CoreData core, FireIncident.ExtraData extra) {
      FireIncident incident = new FireIncident(core.id, core.name, core.ignition, core.discoveryTime);
      incident.status = parseStatus(core.status);
      incident.estimatedFireArea = core.estimatedArea;
      incident.burningBlocks = core.burningBlocks;
      incident.threatenedStructures = core.threatenedStructures;
      incident.endangeredLives = core.endangeredLives;
      incident.fireIntensity = core.intensity;
      incident.windDirection = core.windDir;
      incident.windSpeed = core.windSpeed;
      incident.humidity = core.humidity;
      incident.containmentPercent = core.containment;
      incident.waterUsed = core.waterUsed;
      incident.incidentScore = core.score;
      incident.peakBurningBlocks = extra.peakBurning;
      incident.structuresLost = extra.structuresLost;
      incident.livesLost = extra.livesLost;
      incident.hotspotCount = extra.hotspots;
      incident.participants.clear();

      for (String raw : extra.participants) {
         try {
            incident.participants.add(UUID.fromString(raw));
         } catch (IllegalArgumentException var6) {
         }
      }

      incident.markers.clear();
      incident.markers.addAll(extra.markers);
      if (!extra.sectors.isEmpty()) {
         incident.sectors.clear();
         incident.sectors.addAll(extra.sectors);
      }

      return incident;
   }

   private static IncidentStatus parseStatus(String raw) {
      try {
         return IncidentStatus.valueOf(raw.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return IncidentStatus.ACTIVE;
      }
   }

   public void ensureDefaultSectors() {
      if (this.sectors.isEmpty()) {
         this.sectors.add(new FireSector("Sector Alpha"));
         this.sectors.add(new FireSector("Sector Bravo"));
         this.sectors.add(new FireSector("Sector Charlie"));
         this.sectors.add(new FireSector("Sector Delta"));
      }
   }

   public void addParticipant(UUID playerId) {
      this.participants.add(playerId);
   }

   public void recordWater(float units) {
      this.waterUsed = this.waterUsed + Math.max(0.0F, units);
   }

   public String summaryLine() {
      return String.format(
         Locale.ROOT,
         "#%d %s [%s] burn=%d area=%.1f cont=%.0f%% score=%.0f @ %s",
         this.id,
         this.name,
         this.status,
         this.burningBlocks,
         this.estimatedFireArea,
         this.containmentPercent,
         this.incidentScore,
         this.ignitionPos.toShortString()
      );
   }

   public List<String> infoLines() {
      List<String> lines = new ArrayList<>();
      lines.add("Incident #" + this.id + " — " + this.name);
      lines.add("Status: " + this.status);
      lines.add("Ignition: " + this.ignitionPos.toShortString());
      lines.add("Burning blocks: " + this.burningBlocks + " (peak " + this.peakBurningBlocks + ")");
      lines.add(String.format(Locale.ROOT, "Estimated area: %.1f blocks²", this.estimatedFireArea));
      lines.add(String.format(Locale.ROOT, "Intensity: %.2f", this.fireIntensity));
      lines.add(String.format(Locale.ROOT, "Containment: %.1f%%", this.containmentPercent));
      lines.add(String.format(Locale.ROOT, "Wind: %.2f @ %.0f°", this.windSpeed, this.windDirection));
      lines.add(String.format(Locale.ROOT, "Humidity: %.0f%%", this.humidity * 100.0F));
      lines.add("Threatened structures: " + this.threatenedStructures);
      lines.add("Endangered lives: " + this.endangeredLives);
      lines.add("Hotspots: " + this.hotspotCount);
      lines.add(String.format(Locale.ROOT, "Water used: %.0f", this.waterUsed));
      lines.add("Firefighters: " + this.participants.size());
      lines.add(String.format(Locale.ROOT, "Score: %.0f", this.incidentScore));
      lines.add("Sectors: " + this.sectors.size() + " | Markers: " + this.markers.size());
      return lines;
   }

   private record CoreData(
      int id,
      String name,
      BlockPos ignition,
      long discoveryTime,
      String status,
      float estimatedArea,
      int burningBlocks,
      int threatenedStructures,
      int endangeredLives,
      float intensity,
      float windDir,
      float windSpeed,
      float humidity,
      float containment,
      float waterUsed,
      float score
   ) {
   }

   private record ExtraData(
      int peakBurning, int structuresLost, int livesLost, int hotspots, List<String> participants, List<IncidentMarker> markers, List<FireSector> sectors
   ) {
      static final FireIncident.ExtraData EMPTY = new FireIncident.ExtraData(0, 0, 0, 0, List.of(), List.of(), List.of());
   }
}
