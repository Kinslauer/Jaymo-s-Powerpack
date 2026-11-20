package com.peterwolf.forestfire.fire.incident;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.weather.FirestormState;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import com.peterwolf.forestfire.mission.MissionScorer;
import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.ModNetworking;
import com.peterwolf.forestfire.network.WindSyncPayload;
import com.peterwolf.forestfire.world.FireSavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class IncidentManager {
   private static final Map<ServerLevel, IncidentManager> CACHE = new HashMap<>();
   private final ServerLevel level;
   private final Map<Integer, FireIncident> incidents = new HashMap<>();
   private int nextId = 1;
   private final WindSystem wind = new WindSystem();
   private boolean dirty;

   private IncidentManager(ServerLevel level) {
      this.level = level;
   }

   public static IncidentManager get(ServerLevel level) {
      return CACHE.computeIfAbsent(level, key -> {
         IncidentManager manager = new IncidentManager(key);
         manager.load();
         return manager;
      });
   }

   public static void invalidate(ServerLevel level) {
      CACHE.remove(level);
   }

   public static void clearAll() {
      CACHE.clear();
   }

   public WindSystem wind() {
      return this.wind;
   }

   public Collection<FireIncident> all() {
      return this.incidents.values();
   }

   public List<FireIncident> openIncidents() {
      List<FireIncident> list = new ArrayList<>();

      for (FireIncident incident : this.incidents.values()) {
         if (incident.status.isOpen()) {
            list.add(incident);
         }
      }

      list.sort(Comparator.comparingInt(i -> i.id));
      return list;
   }

   public Optional<FireIncident> get(int id) {
      return Optional.ofNullable(this.incidents.get(id));
   }

   public FireIncident createAt(BlockPos pos, int size) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      int open = 0;

      for (FireIncident incident : this.incidents.values()) {
         if (incident.status.isOpen()) {
            open++;
         }
      }

      if (open >= cfg.maxActiveIncidents) {
         throw new IllegalStateException("Maximum active incidents reached (" + cfg.maxActiveIncidents + ")");
      } else {
         int id = this.nextId++;
         String name = FireIncident.NAME_POOL[this.level.getRandom().nextInt(FireIncident.NAME_POOL.length)];
         FireIncident incidentx = new FireIncident(id, name, pos, this.level.getGameTime());
         incidentx.status = IncidentStatus.REPORTED;
         incidentx.humidity = WeatherFireModel.humidity01(this.level, pos);
         incidentx.windDirection = this.wind.yawDegrees();
         incidentx.windSpeed = this.wind.speed();
         this.incidents.put(id, incidentx);
         this.dirty = true;
         FireSimulation sim = FireSimulation.get(this.level);
         int ignited = sim.igniteArea(pos, Math.max(1, size), id);
         incidentx.burningBlocks = ignited;
         incidentx.peakBurningBlocks = ignited;
         incidentx.estimatedFireArea = Math.max(ignited, size * size);
         incidentx.fireIntensity = Math.min(1.0F, 0.35F + ignited / 80.0F);
         if (ignited > 0) {
            incidentx.status = IncidentStatus.ACTIVE;
         } else {
            incidentx.status = IncidentStatus.REPORTED;
         }

         this.alertNearbyPlayers(incidentx);
         this.markDirty();
         return incidentx;
      }
   }

   public boolean remove(int id) {
      FireIncident removed = this.incidents.remove(id);
      if (removed == null) {
         return false;
      } else {
         FireSimulation.get(this.level).clearIncident(id);
         this.markDirty();
         return true;
      }
   }

   public boolean setStatus(int id, IncidentStatus status) {
      FireIncident incident = this.incidents.get(id);
      if (incident == null) {
         return false;
      } else {
         incident.status = status;
         if (status == IncidentStatus.CLOSED || status == IncidentStatus.CONTROLLED) {
            incident.incidentScore = MissionScorer.score(incident);
         }

         if (status == IncidentStatus.CLOSED) {
         }

         this.markDirty();
         return true;
      }
   }

   public void extinguishAll(int id) {
      FireIncident incident = this.incidents.get(id);
      if (incident != null) {
         FireSimulation.get(this.level).extinguishIncident(id);
         incident.burningBlocks = 0;
         incident.hotspotCount = 0;
         incident.containmentPercent = 100.0F;
         incident.status = IncidentStatus.CONTROLLED;
         incident.incidentScore = MissionScorer.score(incident);
         this.markDirty();
      }
   }

   public void tick() {
      this.wind.tick(this.level);
      FireSimulation sim = FireSimulation.get(this.level);
      sim.tick(this);

      for (FireIncident incident : this.incidents.values()) {
         if (incident.status.isOpen()) {
            sim.updateIncidentStats(incident);
            incident.windDirection = this.wind.yawDegrees();
            incident.humidity = WeatherFireModel.humidity01(this.level, incident.ignitionPos);
            FirestormState storm = sim.firestorm();
            incident.windSpeed = storm.active && storm.incidentId == incident.id ? this.wind.firestormSpeed() : this.wind.speed();
            if (storm.active && storm.incidentId == incident.id) {
               incident.fireIntensity = Math.max(incident.fireIntensity, 0.55F + storm.intensity * 0.45F);
            }

            if (incident.burningBlocks == 0 && incident.hotspotCount == 0 && incident.status == IncidentStatus.ACTIVE) {
               incident.status = IncidentStatus.CONTAINED;
               incident.containmentPercent = 100.0F;
            } else if (incident.burningBlocks == 0
               && incident.hotspotCount > 0
               && (incident.status == IncidentStatus.ACTIVE || incident.status == IncidentStatus.CONTAINED)) {
               incident.status = IncidentStatus.OVERHAUL;
            }

            if (incident.burningBlocks > ForestFireConfig.get().maxBurningBlocksPerWorld * 0.9F && incident.status != IncidentStatus.ESCAPED) {
               incident.status = IncidentStatus.ESCAPED;
            }

            AABB box = new AABB(incident.ignitionPos).inflate(96.0);

            for (ServerPlayer player : this.level.getEntitiesOfClass(ServerPlayer.class, box)) {
               incident.addParticipant(player.getUUID());
            }
         }
      }

      if (this.level.getGameTime() % 20L == 0L) {
         this.syncHudToPlayers();
      }

      if (this.dirty) {
         this.save();
         this.dirty = false;
      } else if (this.level.getGameTime() % 200L == 0L) {
         this.save();
      }
   }

   private void syncHudToPlayers() {
      FireIncident primary = null;
      Iterator var2 = this.openIncidents().iterator();
      if (var2.hasNext()) {
         FireIncident incident = (FireIncident)var2.next();
         primary = incident;
      }

      for (ServerPlayer player : this.level.players()) {
         ModNetworking.sendWind(player, new WindSyncPayload(this.wind.speed(), this.wind.yawDegrees()));
         if (primary != null) {
            FirestormState storm = FireSimulation.get(this.level).firestorm();
            boolean stormAffectsPrimary = storm.active && storm.incidentId == primary.id;
            String nozzleHint = stormAffectsPrimary ? String.format("FIRESTORM x%.1f", storm.spreadMultiplier) : "";
            ModNetworking.sendHud(
               player,
               new IncidentHudPayload(
                  primary.id,
                  primary.name,
                  primary.status.name(),
                  primary.burningBlocks,
                  primary.containmentPercent,
                  primary.windSpeed,
                  primary.windDirection,
                  stormAffectsPrimary ? storm.intensity : 0.0F,
                  nozzleHint
               )
            );
         }
      }
   }

   public void markDirty() {
      this.dirty = true;
   }

   public void load() {
      FireSavedData data = FireSavedData.get(this.level);
      this.incidents.clear();
      this.nextId = 1;

      for (FireIncident incident : data.incidents()) {
         this.incidents.put(incident.id, incident);
         this.nextId = Math.max(this.nextId, incident.id + 1);
      }

      this.wind.load(data.windSpeed(), data.windYaw());
      FireSimulation.get(this.level).loadCells(data.cells());
   }

   public void save() {
      FireSavedData data = FireSavedData.get(this.level);
      data.replaceIncidents(new ArrayList<>(this.incidents.values()));
      data.setWind(this.wind.baseSpeed(), this.wind.yawDegrees());
      data.replaceCells(FireSimulation.get(this.level).exportCells());
      data.setDirty();
   }

   private void alertNearbyPlayers(FireIncident incident) {
      // (Jaymo's port) no chat or action-bar alert: Jaymo's Powers keeps world events out of chat. Players near the
      // fire are still counted as its responders.
      AABB box = new AABB(incident.ignitionPos).inflate(256.0);

      for (ServerPlayer player : this.level.getEntitiesOfClass(ServerPlayer.class, box)) {
         incident.addParticipant(player.getUUID());
      }
   }
}
