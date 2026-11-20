package com.peterwolf.forestfire.fire.simulation;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.incident.IncidentStatus;
import com.peterwolf.forestfire.fire.spread.EmberSystem;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.FirestormState;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import com.peterwolf.forestfire.world.FireChunkTickets;
import com.peterwolf.forestfire.world.FirebreakTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class FireSimulation {
   private static final Map<ServerLevel, FireSimulation> CACHE = new HashMap<>();
   private static final Direction[] NEIGHBOURS = Direction.values();
   private static final int[][] HORIZONTAL_OFFSETS = new int[][]{{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
   private static final int[] HEIGHT_SEARCH = new int[]{0, 1, -1, 2, -2};
   private final ServerLevel level;
   private final Map<Long, FireCell> cells = new ConcurrentHashMap<>();
   private final EmberSystem embers = new EmberSystem();
   private final FirebreakTracker firebreaks = new FirebreakTracker();
   private final Map<Long, Integer> activeFireChunkTickets = new HashMap<>();
   private int tickCounter;
   private int activeCellCount;
   private FirestormState firestorm = FirestormState.INACTIVE;

   private FireSimulation(ServerLevel level) {
      this.level = level;
   }

   public static FireSimulation get(ServerLevel level) {
      return CACHE.computeIfAbsent(level, FireSimulation::new);
   }

   public static void invalidate(ServerLevel level) {
      FireSimulation simulation = CACHE.remove(level);
      if (simulation != null) {
         simulation.releaseChunkTickets();
      }
   }

   public static void clearAll() {
      for (FireSimulation simulation : CACHE.values()) {
         simulation.releaseChunkTickets();
      }

      CACHE.clear();
   }

   public FirebreakTracker firebreaks() {
      return this.firebreaks;
   }

   public Map<Long, FireCell> cells() {
      return this.cells;
   }

   public int activeFireChunkTicketCount() {
      return this.activeFireChunkTickets.size();
   }

   public int activeCellCount() {
      return this.activeCellCount;
   }

   public FireCell getCell(BlockPos pos) {
      return this.cells.get(pos.asLong());
   }

   public FireCell getOrCreate(BlockPos pos, FuelMaterial material, int incidentId) {
      long key = pos.asLong();
      FireCell existing = this.cells.get(key);
      if (existing != null) {
         return existing;
      } else {
         FireCell created = this.cells.computeIfAbsent(key, ignored -> {
            FireCell cell = new FireCell(material, incidentId);
            float initialMoisture = WeatherFireModel.initialFuelMoisture01(this.level, pos, material.baseMoisture);
            cell.setMoisture(Math.round(initialMoisture * 100.0F));
            return cell;
         });
         this.activeCellCount++;
         return created;
      }
   }

   public void loadCells(List<FireCellRecord> records) {
      this.cells.clear();

      for (FireCellRecord record : records) {
         this.cells.put(record.pos().asLong(), record.toCell());
      }

      this.recountActiveCells();
   }

   public List<FireCellRecord> exportCells() {
      List<FireCellRecord> list = new ArrayList<>(this.cells.size());

      for (Entry<Long, FireCell> entry : this.cells.entrySet()) {
         list.add(FireCellRecord.from(BlockPos.of(entry.getKey()), entry.getValue()));
      }

      return list;
   }

   public int igniteArea(BlockPos center, int radius, int incidentId) {
      int count = 0;
      int r = Math.max(1, radius);

      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz <= r * r) {
               int x = center.getX() + dx;
               int z = center.getZ() + dz;
               BlockPos columnBase = new BlockPos(x, center.getY(), z);
               if (this.level.isLoaded(columnBase)) {
                  BlockPos fuel = this.findLowestIgnitionFuel(x, z, center.getY());
                  if (fuel != null && this.tryIgnite(fuel, incidentId, 70 + this.level.getRandom().nextInt(25), true)) {
                     count++;
                  }
               }
            }
         }
      }

      if (count == 0) {
         BlockPos fuel = this.findLowestIgnitionFuel(center.getX(), center.getZ(), center.getY());
         if (fuel != null && this.tryIgnite(fuel, incidentId, 90, true)) {
            count++;
         }
      }

      return count;
   }

   private BlockPos findLowestIgnitionFuel(int x, int z, int referenceY) {
      BlockPos column = new BlockPos(x, referenceY, z);
      if (!this.level.isLoaded(column)) {
         return null;
      } else {
         int top = this.level.getHeight(Types.MOTION_BLOCKING, x, z);
         int yMin = Math.min(referenceY - 3, top - 24);
         int yMax = Math.max(referenceY + 5, top);
         MutableBlockPos cursor = new MutableBlockPos(x, yMin, z);

         for (int y = yMin; y <= yMax; y++) {
            cursor.setY(y);
            FuelMaterial material = FuelMaterial.of(this.level.getBlockState(cursor));
            if (this.isSpreadFuel(material)) {
               return cursor.immutable();
            }
         }

         return null;
      }
   }

   public boolean tryIgnite(BlockPos pos, int incidentId, int heat, boolean force) {
      FireCell tracked = this.cells.get(pos.asLong());
      boolean needsActiveSlot = tracked == null || tracked.stage() == BurnStage.CHARRED || tracked.stage() == BurnStage.EXTINGUISHED;
      if (this.activeCellCount >= ForestFireConfig.get().maxBurningBlocksPerWorld && needsActiveSlot) {
         return false;
      } else if (!this.level.isLoaded(pos)) {
         return false;
      } else {
         BlockState state = this.level.getBlockState(pos);
         FuelMaterial material = FuelMaterial.of(state);
         ForestFireConfig.Data cfg = ForestFireConfig.get();
         if (material == FuelMaterial.PEAT && !cfg.peatFireEnabled) {
            return false;
         } else if (!material.flammable()) {
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
               BlockPos below = pos.below();
               BlockState under = this.level.getBlockState(below);
               FuelMaterial underMat = FuelMaterial.of(under);
               if (underMat.flammable()) {
                  return this.tryIgnite(below, incidentId, heat, force);
               }
            }

            return false;
         } else if (this.firebreaks.isFirebreak(pos) && !force) {
            return false;
         } else {
            FireCell cell = this.getOrCreate(pos, material, incidentId);
            boolean wasTerminal = cell.stage() == BurnStage.CHARRED || cell.stage() == BurnStage.EXTINGUISHED;
            if (cell.stage() == BurnStage.WET && cell.moisture01() > 0.55F && !force) {
               return false;
            } else {
               cell.incidentId = incidentId > 0 ? incidentId : cell.incidentId;
               cell.addHeat(heat);
               if (force && (cell.fuel & 255) < material.initialFuelPercent() / 2) {
                  cell.setFuel(material.initialFuelPercent());
               }

               if (cell.stage() == BurnStage.UNBURNED
                  || cell.stage() == BurnStage.WET
                  || cell.stage() == BurnStage.EXTINGUISHED
                  || cell.stage() == BurnStage.CHARRED) {
                  cell.setStage(BurnStage.HEATING);
               }

               if (force) {
                  cell.setMoisture(Math.min(cell.moisture & 255, 20));
                  cell.setHeat(Math.max(cell.heat & 255, 75));
                  if (!cell.stage().hasVisibleFlames()) {
                     cell.setStage(BurnStage.FLAMING);
                  }

                  this.ensureFireVisual(pos);
               } else if ((cell.heat & 255) >= FireSpreadMath.ignitionThreshold(material.igniteEase, cell.moisture01())) {
                  cell.setStage(BurnStage.IGNITED);
                  cell.setHeat(Math.max(cell.heat & 255, 50));
               }

               if (wasTerminal && cell.stage() != BurnStage.CHARRED && cell.stage() != BurnStage.EXTINGUISHED) {
                  this.activeCellCount++;
               }

               return true;
            }
         }
      }
   }

   public float applyWater(BlockPos pos, float strength, float radius) {
      float used = 0.0F;
      int r = Math.max(0, Math.round(radius));
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               if (dx * dx + dy * dy + dz * dz <= r * r + 1) {
                  cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                  FireCell cell = this.cells.get(cursor.asLong());
                  BlockState state = this.level.getBlockState(cursor);
                  FuelMaterial material = FuelMaterial.of(state);
                  if (cell == null && material.flammable() && this.activeCellCount < cfg.maxBurningBlocksPerWorld) {
                     cell = this.getOrCreate(cursor.immutable(), material, 0);
                  }

                  if (cell != null) {
                     float falloff = 1.0F - Math.min(1.0F, Mth.sqrt(dx * dx + dy * dy + dz * dz) / (r + 1.0F));
                     float amount = strength * falloff;
                     int prevHeat = cell.heat & 255;
                     cell.addHeat(-cfg.waterHeatReduction * amount);
                     cell.addMoisture(cfg.waterMoistureGain * amount);
                     used += amount * 0.25F;
                     if ((cell.heat & 255) < 28 && cell.stage().hasVisibleFlames()) {
                        cell.setStage(BurnStage.SMOULDERING);
                     }

                     if ((cell.heat & 255) < 12 && cell.moisture01() > 0.45F) {
                        if (cell.material().longBurn && (cell.fuel & 255) > 20 && cell.moisture01() < 0.72F) {
                           cell.setStage(BurnStage.REIGNITION_RISK);
                           cell.reignitionTimer = (short)(160 + this.level.getRandom().nextInt(280));
                        } else {
                           cell.setStage(BurnStage.EXTINGUISHED);
                        }
                     }

                     if (cell.moisture01() > 0.82F && (cell.heat & 255) < 40) {
                        cell.setStage(BurnStage.EXTINGUISHED);
                        cell.addHeat(-30.0F);
                     }

                     if (cell.moisture01() > 0.6F) {
                        this.firebreaks.markWet(cursor.immutable(), cfg.wetnessDurationTicks);
                        if (!cell.stage().isBurning()) {
                           cell.setStage(BurnStage.WET);
                        }
                     }

                     if (prevHeat > 60 && (cell.heat & 255) < prevHeat) {
                        this.level.sendParticles(ParticleTypes.CLOUD, cursor.getX() + 0.5, cursor.getY() + 0.8, cursor.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.01);
                     }

                     BlockPos above = cursor.above();
                     if (this.level.getBlockState(above).is(Blocks.FIRE) || this.level.getBlockState(above).is(Blocks.SOUL_FIRE)) {
                        this.level.removeBlock(above, false);
                     }

                     if (this.level.getBlockState(cursor).is(Blocks.FIRE) || this.level.getBlockState(cursor).is(Blocks.SOUL_FIRE)) {
                        this.level.removeBlock(cursor, false);
                     }
                  }
               }
            }
         }
      }

      return used;
   }

   public void clearIncident(int incidentId) {
      Iterator<Entry<Long, FireCell>> it = this.cells.entrySet().iterator();

      while (it.hasNext()) {
         if (it.next().getValue().incidentId == incidentId) {
            it.remove();
         }
      }

      this.embers.clearIncident(incidentId);
      this.recountActiveCells();
   }

   public void extinguishIncident(int incidentId) {
      for (FireCell cell : this.cells.values()) {
         if (cell.incidentId == incidentId) {
            cell.setHeat(0);
            cell.addMoisture(40.0F);
            cell.setStage(BurnStage.EXTINGUISHED);
            cell.reignitionTimer = 0;
         }
      }

      this.embers.clearIncident(incidentId);
      this.recountActiveCells();
   }

   public void clearAllSparks() {
      this.embers.clearAll();
   }

   public FireSimulation.DouseResult extinguishInRadius(BlockPos center, int radius) {
      int r = Math.max(0, radius);
      int r2 = r * r;
      int extinguished = 0;

      for (Entry<Long, FireCell> entry : this.cells.entrySet()) {
         BlockPos pos = BlockPos.of(entry.getKey());
         int dx = pos.getX() - center.getX();
         int dy = pos.getY() - center.getY();
         int dz = pos.getZ() - center.getZ();
         if (dx * dx + dy * dy + dz * dz <= r2) {
            FireCell cell = entry.getValue();
            if (cell.stage().isBurning()
               || cell.stage().hasVisibleFlames()
               || cell.stage() == BurnStage.REIGNITION_RISK
               || cell.stage() == BurnStage.HEATING
               || cell.stage() == BurnStage.IGNITED
               || (cell.heat & 255) > 0) {
               extinguished++;
            }

            cell.setHeat(0);
            cell.addMoisture(60.0F);
            cell.setStage(BurnStage.EXTINGUISHED);
            cell.reignitionTimer = 0;
            this.clearFireVisual(pos);
            if (this.level.getBlockState(pos).is(Blocks.FIRE) || this.level.getBlockState(pos).is(Blocks.SOUL_FIRE)) {
               this.level.removeBlock(pos, false);
            }
         }
      }

      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               if (dx * dx + dy * dy + dz * dz <= r2) {
                  cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                  if (this.level.isLoaded(cursor)
                     && (this.level.getBlockState(cursor).is(Blocks.FIRE) || this.level.getBlockState(cursor).is(Blocks.SOUL_FIRE))) {
                     this.level.removeBlock(cursor, false);
                  }
               }
            }
         }
      }

      this.recountActiveCells();
      int sparks = this.embers.douseInRadius(center, r);
      return new FireSimulation.DouseResult(extinguished, sparks);
   }

   public void updateIncidentStats(FireIncident incident) {
      int burning = 0;
      int hotspots = 0;
      float intensity = 0.0F;
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (Entry<Long, FireCell> entry : this.cells.entrySet()) {
         FireCell cell = entry.getValue();
         if (cell.incidentId == incident.id) {
            BurnStage stage = cell.stage();
            if (stage.isBurning()) {
               burning++;
               intensity += cell.material().flameIntensity * cell.heat01();
               BlockPos pos = BlockPos.of(entry.getKey());
               minX = Math.min(minX, pos.getX());
               maxX = Math.max(maxX, pos.getX());
               minZ = Math.min(minZ, pos.getZ());
               maxZ = Math.max(maxZ, pos.getZ());
            }

            if (stage.isHotspotRisk() && (cell.heat & 255) > 10) {
               hotspots++;
            }
         }
      }

      incident.burningBlocks = burning;
      incident.hotspotCount = hotspots;
      incident.peakBurningBlocks = Math.max(incident.peakBurningBlocks, burning);
      incident.fireIntensity = burning == 0 ? 0.0F : intensity / burning;
      if (burning > 0) {
         incident.estimatedFireArea = (maxX - minX + 1) * (maxZ - minZ + 1);
         float controlled = 1.0F - (float)burning / Math.max(1, incident.peakBurningBlocks);
         incident.containmentPercent = Mth.clamp(controlled * 100.0F, 0.0F, 99.0F);
      } else if (hotspots == 0) {
         incident.containmentPercent = 100.0F;
         incident.estimatedFireArea = 0.0F;
      }

      int structures = 0;
      BlockPos center = incident.ignitionPos;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -24; dx <= 24; dx += 4) {
         for (int dz = -24; dz <= 24; dz += 4) {
            for (int dy = -2; dy <= 6; dy += 2) {
               cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               if (this.level.isLoaded(cursor)) {
                  BlockState state = this.level.getBlockState(cursor);
                  if (FuelMaterial.of(state) == FuelMaterial.WOODEN_STRUCTURE) {
                     structures++;
                  }
               }
            }
         }
      }

      incident.threatenedStructures = structures;
   }

   public FirestormState firestorm() {
      return this.firestorm;
   }

   public void tick(IncidentManager manager) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      this.tickCounter++;
      if (this.tickCounter % Math.max(1, cfg.fireTickInterval) == 0) {
         this.firebreaks.tick(Math.max(1, cfg.fireTickInterval));
         this.recountActiveCells();
         this.refreshChunkTickets(cfg);
         this.firestorm = FirestormState.evaluate(this.level, this.cells);
         WindSystem wind = manager.wind();
         wind.applyFirestorm(this.firestorm);
         RandomSource random = this.level.getRandom();
         int spreadBudget = cfg.maxSpreadChecksPerTick;
         int emberBudget = cfg.maxEmbersPerTick;
         float timeScale = FireSpreadMath.timeScale(cfg.fireTickInterval);
         List<Long> keys = new ArrayList<>(this.cells.keySet());
         int start = keys.isEmpty() ? 0 : (int)(this.level.getGameTime() % Math.max(1, keys.size()));

         for (int i = 0; i < keys.size(); i++) {
            long key = keys.get((i + start) % keys.size());
            BlockPos pos = BlockPos.of(key);
            if (this.level.isLoaded(pos)) {
               FireCell cell = this.cells.get(key);
               if (cell != null) {
                  boolean stormAffectsCell = this.firestorm.affects(key, cell);
                  float effectiveWindSpeed = stormAffectsCell ? wind.firestormSpeed() : wind.speed();
                  this.tickCell(pos, cell, wind, manager, random, cfg, timeScale, effectiveWindSpeed);
                  BurnStage stage = cell.stage();
                  if (stormAffectsCell && stage.hasVisibleFlames()) {
                     cell.addHeat(cfg.firestormSelfHeat * this.firestorm.intensity * timeScale);
                     cell.addMoisture(-0.6F * this.firestorm.intensity * timeScale);
                  }

                  if (stage.hasVisibleFlames() && spreadBudget > 0) {
                     spreadBudget -= this.spreadFrom(pos, cell, wind, cfg, timeScale, spreadBudget);
                  }

                  if (stage.hasVisibleFlames() && emberBudget > 0 && cfg.sparksEnabled) {
                     float emberBase = Math.max(0.08F, cell.material().emberRate);
                     float heatBonus = cell.heat01() * cfg.sparkHeatBonus;
                     float fullyInvolved = stage == BurnStage.FULLY_INVOLVED ? 1.35F : 1.0F;
                     float windFactor = 0.35F + effectiveWindSpeed * 0.9F;
                     float chance = emberBase * windFactor * fullyInvolved * (0.45F + heatBonus);
                     if (cell.material().crownFuel) {
                        chance *= 1.45F;
                     }

                     if (stormAffectsCell) {
                        chance *= this.firestorm.sparkMultiplier;
                     }

                     float stepChance = FireSpreadMath.probabilityForStep(Math.min(0.92F, chance), timeScale);
                     if (random.nextFloat() < stepChance
                        && this.embers
                           .spawn(
                              this.level, pos, wind, effectiveWindSpeed, cell.incidentId, cfg, cell.heat01(), cell.material().flameIntensity, cell.material()
                           )) {
                        emberBudget--;
                     }
                  }

                  if (cfg.smokeEnabled && stage.isBurning() && random.nextInt(stormAffectsCell ? 4 : 8) == 0) {
                     this.level
                        .sendParticles(
                           stage.hasVisibleFlames() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                           pos.getX() + 0.5,
                           pos.getY() + 1.1,
                           pos.getZ() + 0.5,
                           stormAffectsCell ? 2 : 1,
                           0.1,
                           0.25,
                           0.1,
                           0.01
                        );
                  }

                  if (stage == BurnStage.UNBURNED && (cell.heat & 255) == 0 && cell.reignitionTimer <= 0) {
                     this.cells.remove(key);
                     this.activeCellCount = Math.max(0, this.activeCellCount - 1);
                  }
               }
            }
         }

         this.embers.tick(this.level, this, cfg);
         BurningTreeCollapse.tickCleanup(this.level);
      }
   }

   private void refreshChunkTickets(ForestFireConfig.Data cfg) {
      int radius = cfg.chunkUnloadRetentionRadius;
      if (radius > 0 && !this.cells.isEmpty()) {
         Map<Long, FireSimulation.ChunkActivity> activeChunks = new HashMap<>();

         for (Entry<Long, FireCell> entry : this.cells.entrySet()) {
            FireCell cell = entry.getValue();
            int priority = chunkActivityPriority(cell);
            if (priority > 0) {
               long chunkKey = ChunkPos.pack(BlockPos.of(entry.getKey()));
               int heat = cell.heat & 255;
               activeChunks.merge(
                  chunkKey,
                  new FireSimulation.ChunkActivity(chunkKey, priority, heat),
                  (left, right) -> new FireSimulation.ChunkActivity(chunkKey, Math.max(left.priority, right.priority), Math.max(left.heat, right.heat))
               );
            }
         }

         if (activeChunks.isEmpty()) {
            this.releaseChunkTickets();
         } else {
            List<FireSimulation.ChunkActivity> ordered = new ArrayList<>(activeChunks.values());
            ordered.sort(
               Comparator.comparingInt(FireSimulation.ChunkActivity::priority)
                  .reversed()
                  .thenComparing(Comparator.comparingInt(FireSimulation.ChunkActivity::heat).reversed())
                  .thenComparingLong(FireSimulation.ChunkActivity::chunkKey)
            );
            int coverageMargin = Math.max(0, radius - 1);
            List<Long> selected = new ArrayList<>();
            Set<Long> covered = new HashSet<>();

            for (FireSimulation.ChunkActivity activity : ordered) {
               if (selected.size() >= cfg.maxFireChunkTickets) {
                  break;
               }

               if (!covered.contains(activity.chunkKey)) {
                  selected.add(activity.chunkKey);
                  int centreX = ChunkPos.getX(activity.chunkKey);
                  int centreZ = ChunkPos.getZ(activity.chunkKey);

                  for (int dx = -coverageMargin; dx <= coverageMargin; dx++) {
                     for (int dz = -coverageMargin; dz <= coverageMargin; dz++) {
                        covered.add(ChunkPos.pack(centreX + dx, centreZ + dz));
                     }
                  }
               }
            }

            Set<Long> desired = new HashSet<>(selected);
            Iterator<Entry<Long, Integer>> iterator = this.activeFireChunkTickets.entrySet().iterator();

            while (iterator.hasNext()) {
               Entry<Long, Integer> existing = iterator.next();
               if (!desired.contains(existing.getKey()) || existing.getValue() != radius) {
                  this.level.getChunkSource().removeTicketWithRadius(FireChunkTickets.ACTIVE_FIRE, ChunkPos.unpack(existing.getKey()), existing.getValue());
                  iterator.remove();
               }
            }

            for (long chunkKey : selected) {
               this.level.getChunkSource().addTicketWithRadius(FireChunkTickets.ACTIVE_FIRE, ChunkPos.unpack(chunkKey), radius);
               this.activeFireChunkTickets.put(chunkKey, radius);
            }
         }
      } else {
         this.releaseChunkTickets();
      }
   }

   private void releaseChunkTickets() {
      if (!this.activeFireChunkTickets.isEmpty()) {
         for (Entry<Long, Integer> entry : this.activeFireChunkTickets.entrySet()) {
            this.level.getChunkSource().removeTicketWithRadius(FireChunkTickets.ACTIVE_FIRE, ChunkPos.unpack(entry.getKey()), entry.getValue());
         }

         this.activeFireChunkTickets.clear();
      }
   }

   private static int chunkActivityPriority(FireCell cell) {
      BurnStage stage = cell.stage();
      if (stage.hasVisibleFlames()) {
         return 4;
      } else if (stage == BurnStage.IGNITED) {
         return 3;
      } else if (stage == BurnStage.HEATING && (cell.heat & 255) > 0) {
         return 2;
      } else {
         return stage != BurnStage.SMOULDERING && stage != BurnStage.REIGNITION_RISK && (stage != BurnStage.WET || (cell.heat & 0xFF) <= 0) ? 0 : 1;
      }
   }

   private void tickCell(
      BlockPos pos,
      FireCell cell,
      WindSystem wind,
      IncidentManager manager,
      RandomSource random,
      ForestFireConfig.Data cfg,
      float timeScale,
      float effectiveWindSpeed
   ) {
      FuelMaterial material = cell.material();
      BurnStage stage = cell.stage();
      if (WeatherFireModel.isRainingAt(this.level, pos) && stage.hasVisibleFlames()) {
         cell.addMoisture(0.6F * timeScale);
         cell.addHeat(-0.8F * timeScale);
      } else if (WeatherFireModel.isRainingAt(this.level, pos)) {
         cell.addMoisture(1.2F * timeScale);
         cell.addHeat(-1.5F * timeScale);
      }

      if (stage == BurnStage.WET) {
         float dryRate = 0.4F + effectiveWindSpeed * 0.6F + WeatherFireModel.temperature01(this.level, pos);
         if (!WeatherFireModel.isRainingAt(this.level, pos)) {
            cell.addMoisture(-dryRate * timeScale);
         }

         if (cell.moisture01() < 0.35F) {
            cell.setStage(BurnStage.UNBURNED);
         }
      } else if (stage == BurnStage.REIGNITION_RISK) {
         if (cell.reignitionTimer > 0) {
            cell.reignitionTimer--;
         }

         if ((cell.heat & 255) > 5) {
            cell.addHeat(-0.3F * timeScale);
         } else if (random.nextFloat() < FireSpreadMath.probabilityForStep(0.01F * (1.0F - cell.moisture01()), timeScale)) {
            cell.setStage(BurnStage.IGNITED);
            cell.setHeat(45);
         } else if (cell.reignitionTimer <= 0 && (cell.heat & 255) < 8) {
            cell.setStage(BurnStage.EXTINGUISHED);
         }
      } else if (stage == BurnStage.HEATING) {
         if (material == FuelMaterial.LIVING_LEAVES) {
            cell.addMoisture(-0.8F * timeScale);
         }

         float igniteThreshold = FireSpreadMath.ignitionThreshold(material.igniteEase, cell.moisture01());
         if ((cell.heat & 255) >= igniteThreshold) {
            cell.setStage(BurnStage.IGNITED);
         } else {
            cell.addHeat(-0.5F * timeScale);
            if ((cell.heat & 255) <= 0) {
               cell.setStage(BurnStage.UNBURNED);
            }
         }
      } else if (stage == BurnStage.IGNITED) {
         cell.addHeat((2.0F + material.flameIntensity * 3.0F) * timeScale);
         cell.addMoisture(-1.5F * timeScale);
         if ((cell.heat & 255) > 50) {
            cell.setStage(BurnStage.FLAMING);
            this.ensureFireVisual(pos);
         }
      } else if (stage == BurnStage.FLAMING || stage == BurnStage.FULLY_INVOLVED) {
         float burnRate = stage == BurnStage.FULLY_INVOLVED ? 1.25F : 1.0F;
         cell.consumeFuel(material.fuelConsumePerTick() * burnRate * timeScale);
         cell.addMoisture(-1.2F * timeScale);
         float heatDelta = material.flameIntensity * 1.2F + 0.4F;
         if ((cell.fuel & 255) < 15) {
            heatDelta -= 0.8F;
         }

         cell.addHeat(heatDelta * timeScale);
         if ((cell.heat & 255) > 75 && stage == BurnStage.FLAMING) {
            cell.setStage(BurnStage.FULLY_INVOLVED);
         }

         if (material == FuelMaterial.TRUNK || material == FuelMaterial.LOG) {
            int integrityLoss = stage == BurnStage.FULLY_INVOLVED ? 2 : 1;
            if (random.nextFloat() < FireSpreadMath.probabilityForStep(0.5F, timeScale)) {
               int integrity = Math.max(0, (cell.integrity & 255) - integrityLoss);
               cell.integrity = (byte)integrity;
            }

            int integrity = cell.integrity & 255;
            if (cfg.treeCollapseEnabled && integrity > 0 && integrity < 40 && random.nextFloat() < FireSpreadMath.probabilityForStep(0.025F, timeScale)) {
               this.level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.45F, 0.5F + integrity * 0.01F);
            }

            float collapseChance = cfg.treeCollapseChance;
            if (stage == BurnStage.FULLY_INVOLVED) {
               collapseChance *= 2.0F;
            }

            if (integrity < 15) {
               collapseChance *= 2.5F;
            }

            if (cfg.treeCollapseEnabled
               && integrity < 30
               && (cell.fuel & 255) < 70
               && random.nextFloat() < FireSpreadMath.probabilityForStep(collapseChance, timeScale)) {
               BurningTreeCollapse.tryCollapse(this.level, this, pos, cell, wind, random);
            }
         }

         this.ensureFireVisual(pos);
         if ((cell.fuel & 255) <= 0) {
            cell.setStage(material.longBurn ? BurnStage.SMOULDERING : BurnStage.CHARRED);
            cell.setHeat(material.longBurn ? 45 : 15);
            this.charBlock(pos, material);
            this.clearFireVisual(pos);
         }
      } else if (stage == BurnStage.SMOULDERING) {
         cell.addHeat(-0.8F * timeScale);
         cell.consumeFuel(0.2F * timeScale);
         if ((cell.heat & 255) < 12) {
            cell.setStage(BurnStage.REIGNITION_RISK);
            cell.reignitionTimer = (short)(300 + random.nextInt(600));
            this.charBlock(pos, material);
         } else if (random.nextFloat() < FireSpreadMath.probabilityForStep(0.02F, timeScale) && cell.moisture01() < 0.3F) {
            cell.setStage(BurnStage.FLAMING);
            cell.setHeat(55);
         }
      } else {
         if (stage == BurnStage.CHARRED) {
            cell.addHeat(-1.0F * timeScale);
         }
      }
   }

   private int spreadFrom(BlockPos pos, FireCell cell, WindSystem wind, ForestFireConfig.Data cfg, float timeScale, int maxChecks) {
      int checks = 0;
      boolean stormAffectsSource = this.firestorm.affects(pos.asLong(), cell);
      float effectiveWindSpeed = stormAffectsSource ? wind.firestormSpeed() : wind.speed();
      FireDangerLevel danger = WeatherFireModel.danger(this.level, pos, effectiveWindSpeed);
      float dangerMul = danger.spreadMultiplier() * (stormAffectsSource ? this.firestorm.spreadMultiplier : 1.0F);
      float humidity = WeatherFireModel.humidity01(this.level, pos);
      if (stormAffectsSource) {
         humidity *= 1.0F - 0.35F * this.firestorm.intensity;
      }

      boolean surfaceSource = isSurfaceFuel(cell.material());

      for (int[] offset : HORIZONTAL_OFFSETS) {
         if (checks >= maxChecks) {
            return checks;
         }

         BlockPos target = this.findHorizontalFuel(pos, offset[0], offset[1]);
         if (target != null) {
            checks++;
            this.attemptSpread(
               pos,
               target,
               cell,
               wind,
               effectiveWindSpeed,
               dangerMul,
               humidity,
               cfg,
               timeScale,
               surfaceSource ? FireSimulation.SpreadPath.SURFACE : FireSimulation.SpreadPath.CONTACT
            );
         }
      }

      BlockPos upward = this.findVerticalFuel(pos, Direction.UP, 4);
      if (upward != null && checks < maxChecks) {
         checks++;
         this.attemptSpread(pos, upward, cell, wind, effectiveWindSpeed, dangerMul, humidity, cfg, timeScale, FireSimulation.SpreadPath.CONVECTION_UP);
      }

      BlockPos downward = this.findVerticalFuel(pos, Direction.DOWN, 2);
      if (downward != null && checks < maxChecks) {
         checks++;
         this.attemptSpread(pos, downward, cell, wind, effectiveWindSpeed, dangerMul, humidity, cfg, timeScale, FireSimulation.SpreadPath.CONVECTION_DOWN);
      }

      if (cell.material().crownFuel && cell.heat01() >= 0.65F) {
         for (int[] offset : HORIZONTAL_OFFSETS) {
            if (checks >= maxChecks) {
               return checks;
            }

            BlockPos target = this.findCrownFuel(pos, offset[0] * 2, offset[1] * 2);
            if (target != null) {
               checks++;
               this.attemptSpread(pos, target, cell, wind, effectiveWindSpeed, dangerMul, humidity, cfg, timeScale, FireSimulation.SpreadPath.CROWN);
            }
         }
      }

      return checks;
   }

   private void attemptSpread(
      BlockPos from,
      BlockPos to,
      FireCell source,
      WindSystem wind,
      float effectiveWindSpeed,
      float dangerMul,
      float humidity,
      ForestFireConfig.Data cfg,
      float timeScale,
      FireSimulation.SpreadPath path
   ) {
      if (!to.equals(from) && this.level.isLoaded(to)) {
         BlockState state = this.level.getBlockState(to);
         FuelMaterial material = FuelMaterial.of(state);
         if (material.flammable() && (material != FuelMaterial.PEAT || cfg.peatFireEnabled)) {
            if (path != FireSimulation.SpreadPath.CROWN || material.crownFuel) {
               FireCell target = this.cells.get(to.asLong());
               if (target == null || !target.stage().hasVisibleFlames() && (target.fuel & 255) > 0) {
                  boolean needsActiveSlot = target == null || target.stage() == BurnStage.CHARRED || target.stage() == BurnStage.EXTINGUISHED;
                  if (!needsActiveSlot || this.activeCellCount < cfg.maxBurningBlocksPerWorld) {
                     float breakWind = effectiveWindSpeed + (this.firestorm.affects(from.asLong(), source) ? this.firestorm.intensity * 0.4F : 0.0F);
                     if (!this.firebreaks.blocksSpread(from, to, breakWind, source.heat01())) {
                        float travelX = to.getX() - from.getX();
                        float travelZ = to.getZ() - from.getZ();
                        float horizontalDistance = (float)Math.sqrt(travelX * travelX + travelZ * travelZ);
                        float distance = (float)Math.sqrt(travelX * travelX + (float)(to.getY() - from.getY()) * (to.getY() - from.getY()) + travelZ * travelZ);
                        float windMultiplier = horizontalDistance > 0.0F
                           ? FireSpreadMath.windFactor(effectiveWindSpeed, wind.dx(), wind.dz(), travelX, travelZ)
                           : 1.0F;
                        float slopeMultiplier = path == FireSimulation.SpreadPath.SURFACE
                           ? FireSpreadMath.slopeFactor(to.getY() - from.getY(), horizontalDistance, cfg.uphillSpreadBonus, cfg.downhillSpreadPenalty)
                           : 1.0F;
                        float targetMoisture = target == null
                           ? WeatherFireModel.initialFuelMoisture01(this.level, to, material.baseMoisture)
                           : target.moisture01();

                        float transferMultiplier = switch (path) {
                           case SURFACE -> cfg.surfaceSpreadMultiplier;
                           case CONTACT -> 0.72F;
                           case CONVECTION_UP -> 1.55F;
                           case CONVECTION_DOWN -> 0.25F;
                           case CROWN -> cfg.crownSpreadMultiplier * 1.1F;
                        };
                        if (material == FuelMaterial.PEAT) {
                           transferMultiplier *= Mth.clamp(cfg.peatSpreadChance * 10.0F, 0.02F, 0.25F);
                        }

                        float heatTransfer = FireSpreadMath.heatFlux(
                           source.heat01(),
                           source.material().flameIntensity,
                           material.igniteEase,
                           dangerMul,
                           windMultiplier,
                           slopeMultiplier,
                           humidity,
                           targetMoisture,
                           transferMultiplier,
                           distance,
                           WeatherFireModel.isRainingAt(this.level, to)
                        );
                        heatTransfer *= 0.35F + 0.65F * (float)Math.sqrt(source.fuel01());
                        heatTransfer *= cfg.radiantHeatMultiplier * timeScale;
                        if (!(heatTransfer < 0.05F)) {
                           if (target == null) {
                              target = this.getOrCreate(to, material, source.incidentId);
                           }

                           target.incidentId = source.incidentId > 0 ? source.incidentId : target.incidentId;
                           target.addMoisture(-heatTransfer * 0.025F);
                           target.addHeat(heatTransfer);
                           BurnStage targetStage = target.stage();
                           if (targetStage == BurnStage.UNBURNED
                              || targetStage == BurnStage.EXTINGUISHED
                              || targetStage == BurnStage.WET && target.moisture01() <= 0.55F) {
                              target.setStage(BurnStage.HEATING);
                              if (targetStage == BurnStage.EXTINGUISHED) {
                                 this.activeCellCount++;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private BlockPos findHorizontalFuel(BlockPos from, int dx, int dz) {
      for (int dy : HEIGHT_SEARCH) {
         BlockPos candidate = from.offset(dx, dy, dz);
         if (this.level.isLoaded(candidate)) {
            FuelMaterial material = FuelMaterial.of(this.level.getBlockState(candidate));
            if (this.isSpreadFuel(material) && this.canReceiveHeat(candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   private BlockPos findVerticalFuel(BlockPos from, Direction direction, int maxDistance) {
      for (int distance = 1; distance <= maxDistance; distance++) {
         BlockPos candidate = from.relative(direction, distance);
         if (!this.level.isLoaded(candidate)) {
            return null;
         }

         BlockState state = this.level.getBlockState(candidate);
         FuelMaterial material = FuelMaterial.of(state);
         if (this.isSpreadFuel(material) && this.canReceiveHeat(candidate)) {
            return candidate;
         }

         if (!state.isAir() && !state.canBeReplaced() && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
            return null;
         }
      }

      return null;
   }

   private BlockPos findCrownFuel(BlockPos from, int dx, int dz) {
      for (int dy : HEIGHT_SEARCH) {
         BlockPos candidate = from.offset(dx, dy, dz);
         if (this.level.isLoaded(candidate)) {
            FuelMaterial material = FuelMaterial.of(this.level.getBlockState(candidate));
            if (material.crownFuel && this.isSpreadFuel(material) && this.canReceiveHeat(candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   private boolean isSpreadFuel(FuelMaterial material) {
      return material.flammable() && (material != FuelMaterial.PEAT || ForestFireConfig.get().peatFireEnabled);
   }

   private boolean canReceiveHeat(BlockPos pos) {
      FireCell cell = this.cells.get(pos.asLong());
      return cell == null || !cell.stage().hasVisibleFlames() && (cell.fuel & 255) > 0;
   }

   private static boolean isSurfaceFuel(FuelMaterial material) {
      return material == FuelMaterial.GRASS || material == FuelMaterial.DEAD_BUSH || material == FuelMaterial.DRY_LEAVES || material == FuelMaterial.PEAT;
   }

   private void recountActiveCells() {
      int count = 0;

      for (FireCell cell : this.cells.values()) {
         BurnStage stage = cell.stage();
         if (stage != BurnStage.CHARRED && stage != BurnStage.EXTINGUISHED) {
            count++;
         }
      }

      this.activeCellCount = count;
   }

   private void ensureFireVisual(BlockPos pos) {
      BlockPos above = pos.above();
      if (this.level.isLoaded(above) && this.level.getBlockState(above).isAir()) {
         this.level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
      }
   }

   private void clearFireVisual(BlockPos pos) {
      BlockPos above = pos.above();
      if (this.level.isLoaded(above) && (this.level.getBlockState(above).is(Blocks.FIRE) || this.level.getBlockState(above).is(Blocks.SOUL_FIRE))) {
         this.level.removeBlock(above, false);
      }
   }

   private void charBlock(BlockPos pos, FuelMaterial material) {
      BlockState state = this.level.getBlockState(pos);
      if (material != FuelMaterial.GRASS && material != FuelMaterial.DEAD_BUSH) {
         if (material != FuelMaterial.DRY_LEAVES && material != FuelMaterial.LIVING_LEAVES) {
            if ((material == FuelMaterial.LOG || material == FuelMaterial.TRUNK) && state.is(BlockTags.LOGS) && this.level.getRandom().nextFloat() < 0.35F) {
               this.level.setBlock(pos, Blocks.COAL_BLOCK.defaultBlockState(), 3);
            }
         } else {
            if ((state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) && this.level.getRandom().nextBoolean()) {
               this.level.removeBlock(pos, false);
            }
         }
      } else {
         if (state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.DEAD_BUSH)
            || state.is(BlockTags.FLOWERS)) {
            this.level.removeBlock(pos, false);
         } else if (state.is(Blocks.GRASS_BLOCK)) {
            this.level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
         }
      }
   }

   public boolean shouldSuppressVanillaFire(BlockPos pos) {
      if (!ForestFireConfig.get().replaceVanillaFireInIncidents) {
         return false;
      } else {
         for (Direction dir : NEIGHBOURS) {
            if (this.cells.containsKey(pos.relative(dir).asLong()) || this.cells.containsKey(pos.asLong())) {
               return true;
            }
         }

         return this.cells.containsKey(pos.below().asLong());
      }
   }

   public void adoptVanillaFire(BlockPos firePos) {
      BlockPos fuel = firePos.below();
      FireCell managed = this.getCell(fuel);
      if (managed == null || !managed.stage().hasVisibleFlames()) {
         int incidentId = 0;
         IncidentManager manager = IncidentManager.get(this.level);
         double best = Double.MAX_VALUE;

         for (FireIncident incident : manager.openIncidents()) {
            double d = incident.ignitionPos.distSqr(fuel);
            if (d < best) {
               best = d;
               incidentId = incident.id;
            }
         }

         if (incidentId == 0
            && ForestFireConfig.get().allowNaturalIgnition
            && best > 4096.0
            && manager.openIncidents().size() < ForestFireConfig.get().maxActiveIncidents) {
            try {
               FireIncident created = manager.createAt(fuel, 2);
               incidentId = created.id;
               created.status = IncidentStatus.UNREPORTED;
            } catch (IllegalStateException var12) {
            }
         }

         this.tryIgnite(fuel, incidentId, 60, true);
         if (this.level.getBlockState(firePos).is(Blocks.FIRE) || this.level.getBlockState(firePos).is(Blocks.SOUL_FIRE)) {
            FireCell cell = this.getCell(fuel);
            if (cell == null || !cell.stage().hasVisibleFlames()) {
               this.level.removeBlock(firePos, false);
            }
         }
      }
   }

   private record ChunkActivity(long chunkKey, int priority, int heat) {
   }

   public record DouseResult(int cells, int sparks) {
   }

   private static enum SpreadPath {
      SURFACE,
      CONTACT,
      CONVECTION_UP,
      CONVECTION_DOWN,
      CROWN;
   }
}
