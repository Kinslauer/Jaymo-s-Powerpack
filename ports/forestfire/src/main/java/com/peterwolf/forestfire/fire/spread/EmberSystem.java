package com.peterwolf.forestfire.fire.spread;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.simulation.FuelMaterial;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class EmberSystem {
   private final List<EmberSystem.Spark> sparks = new ArrayList<>();

   public boolean spawn(
      ServerLevel level,
      BlockPos origin,
      WindSystem wind,
      float effectiveWindSpeed,
      int incidentId,
      ForestFireConfig.Data cfg,
      float heat01,
      float flameIntensity,
      FuelMaterial material
   ) {
      if (!cfg.sparksEnabled) {
         return false;
      } else {
         int cap = Math.max(8, cfg.maxEmbersPerTick * cfg.sparkPoolMultiplier);
         if (this.sparks.size() >= cap) {
            return false;
         } else if (WeatherFireModel.isRainingAt(level, origin) && level.getRandom().nextFloat() < 0.75F) {
            return false;
         } else {
            RandomSource random = level.getRandom();
            float loft = 2.0F + heat01 * 6.0F + flameIntensity * 4.0F;
            if (material.crownFuel) {
               loft += 3.0F + random.nextFloat() * 4.0F;
            }

            if (material == FuelMaterial.GRASS || material == FuelMaterial.DEAD_BUSH) {
               loft *= 0.65F;
            }

            float windSpeed = Math.max(0.0F, effectiveWindSpeed);
            float windDist = windSpeed * (10.0F + loft * 1.2F + random.nextFloat() * 5.0F);
            float cross = (random.nextFloat() - 0.5F) * windSpeed * 6.0F;
            float dx = wind.dx();
            float dz = wind.dz();
            float px = -dz;
            float calmAngle = random.nextFloat() * (float) (Math.PI * 2);
            float calmRadius = (1.0F - Mth.clamp(windSpeed, 0.0F, 1.0F)) * (1.0F + random.nextFloat() * 4.0F);
            int tx = origin.getX() + Math.round(dx * windDist + px * cross + Mth.cos(calmAngle) * calmRadius);
            int tz = origin.getZ() + Math.round(dz * windDist + dx * cross + Mth.sin(calmAngle) * calmRadius);
            BlockPos targetColumn = new BlockPos(tx, origin.getY(), tz);
            if (!level.isLoaded(targetColumn)) {
               return false;
            } else {
               BlockPos targetSurface = level.getHeightmapPos(Types.MOTION_BLOCKING, targetColumn);
               int life = 18 + random.nextInt(22) + Math.round(loft * 2.0F);
               float ignitionPower = 0.35F + heat01 * 0.45F + flameIntensity * 0.35F;
               if (material.crownFuel) {
                  ignitionPower += 0.15F;
               }

               EmberSystem.SparkKind kind = EmberSystem.SparkKind.from(material);
               this.sparks
                  .add(
                     new EmberSystem.Spark(
                        origin.getX() + 0.5,
                        origin.getY() + 0.9,
                        origin.getZ() + 0.5,
                        tx + 0.5,
                        targetSurface.getY() + 0.2,
                        tz + 0.5,
                        incidentId,
                        life,
                        life,
                        loft,
                        ignitionPower,
                        kind
                     )
                  );
               spawnLaunchParticles(level, origin, kind, wind, windSpeed);
               return true;
            }
         }
      }
   }

   public boolean spawn(ServerLevel level, BlockPos origin, WindSystem wind, int incidentId, ForestFireConfig.Data cfg) {
      return this.spawn(level, origin, wind, wind.speed(), incidentId, cfg, 0.7F, 0.5F, FuelMaterial.DRY_LEAVES);
   }

   public int douseInRadius(BlockPos center, int radius) {
      if (!this.sparks.isEmpty() && radius >= 0) {
         int r2 = radius * radius;
         int removed = 0;
         Iterator<EmberSystem.Spark> it = this.sparks.iterator();

         while (it.hasNext()) {
            EmberSystem.Spark spark = it.next();
            if (inRadius(spark.ox, spark.oy, spark.oz, center, r2)
               || inRadius(spark.tx, spark.ty, spark.tz, center, r2)
               || inRadius(currentX(spark), currentY(spark), currentZ(spark), center, r2)) {
               it.remove();
               removed++;
            }
         }

         return removed;
      } else {
         return 0;
      }
   }

   public int clearIncident(int incidentId) {
      int removed = 0;
      Iterator<EmberSystem.Spark> it = this.sparks.iterator();

      while (it.hasNext()) {
         if (it.next().incidentId == incidentId) {
            it.remove();
            removed++;
         }
      }

      return removed;
   }

   public void clearAll() {
      this.sparks.clear();
   }

   public int size() {
      return this.sparks.size();
   }

   public void tick(ServerLevel level, FireSimulation simulation, ForestFireConfig.Data cfg) {
      if (cfg.sparksEnabled && !this.sparks.isEmpty()) {
         Iterator<EmberSystem.Spark> it = this.sparks.iterator();
         RandomSource random = level.getRandom();

         while (it.hasNext()) {
            EmberSystem.Spark spark = it.next();
            spark.life--;
            float t = 1.0F - (float)spark.life / Math.max(1, spark.maxLife);
            float heightCurve = 4.0F * t * (1.0F - t);
            double x = Mth.lerp(t, spark.ox, spark.tx);
            double z = Mth.lerp(t, spark.oz, spark.tz);
            double baseY = Mth.lerp(t, spark.oy, spark.ty);
            double y = baseY + heightCurve * spark.arcHeight;
            if (random.nextInt(2) == 0 && level.isLoaded(BlockPos.containing(x, y, z))) {
               emitTrail(level, x, y, z, spark.kind, random);
            }

            if (spark.life <= 0) {
               it.remove();
               BlockPos landHint = BlockPos.containing(spark.tx, spark.ty, spark.tz);
               if (level.isLoaded(landHint)) {
                  BlockPos surface = level.getHeightmapPos(Types.MOTION_BLOCKING, landHint);
                  if (level.isLoaded(surface)) {
                     BlockPos fuel = findLandingFuel(level, surface, cfg);
                     BlockPos impact = fuel != null ? fuel : surface.below();
                     level.sendParticles(ParticleTypes.LAVA, impact.getX() + 0.5, impact.getY() + 1.0, impact.getZ() + 0.5, 2, 0.08, 0.05, 0.08, 0.01);
                     level.sendParticles(ParticleTypes.SMOKE, impact.getX() + 0.5, impact.getY() + 1.1, impact.getZ() + 0.5, 3, 0.12, 0.08, 0.12, 0.01);
                     if (fuel != null) {
                        FuelMaterial targetMaterial = FuelMaterial.of(level.getBlockState(fuel));
                        float chance = cfg.emberSpotFireChance * spark.ignitionPower * spark.kind.spotMultiplier;
                        chance *= 0.25F + targetMaterial.igniteEase * 0.75F;
                        chance *= 0.35F + (1.0F - WeatherFireModel.humidity01(level, fuel)) * 0.65F;
                        if (WeatherFireModel.isRainingAt(level, fuel)) {
                           chance *= 0.15F;
                        }

                        if (random.nextFloat() < chance) {
                           simulation.tryIgnite(fuel, spark.incidentId, Math.round(28.0F + spark.ignitionPower * 40.0F), false);
                           if (spark.kind == EmberSystem.SparkKind.LEAF_FRAGMENT || spark.kind == EmberSystem.SparkKind.BARK_CHIP) {
                              FireCell nearby = simulation.getCell(fuel);
                              if (nearby != null) {
                                 nearby.addHeat(8.0F * spark.ignitionPower);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.sparks.clear();
      }
   }

   private static double currentT(EmberSystem.Spark spark) {
      return 1.0 - (double)spark.life / Math.max(1, spark.maxLife);
   }

   private static double currentX(EmberSystem.Spark spark) {
      return Mth.lerp(currentT(spark), spark.ox, spark.tx);
   }

   private static double currentZ(EmberSystem.Spark spark) {
      return Mth.lerp(currentT(spark), spark.oz, spark.tz);
   }

   private static double currentY(EmberSystem.Spark spark) {
      double t = currentT(spark);
      double baseY = Mth.lerp(t, spark.oy, spark.ty);
      return baseY + 4.0 * t * (1.0 - t) * spark.arcHeight;
   }

   private static boolean inRadius(double x, double y, double z, BlockPos center, int r2) {
      double dx = x - (center.getX() + 0.5);
      double dy = y - (center.getY() + 0.5);
      double dz = z - (center.getZ() + 0.5);
      return dx * dx + dy * dy + dz * dz <= r2;
   }

   private static BlockPos findLandingFuel(ServerLevel level, BlockPos surface, ForestFireConfig.Data cfg) {
      int[] verticalOffsets = new int[]{0, -1, 1};

      for (int radius = 0; radius <= 1; radius++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (radius <= 0 || Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                  for (int dy : verticalOffsets) {
                     BlockPos candidate = surface.offset(dx, dy, dz);
                     if (level.isLoaded(candidate)) {
                        FuelMaterial material = FuelMaterial.of(level.getBlockState(candidate));
                        if (material.flammable() && (material != FuelMaterial.PEAT || cfg.peatFireEnabled)) {
                           return candidate;
                        }
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static void spawnLaunchParticles(ServerLevel level, BlockPos origin, EmberSystem.SparkKind kind, WindSystem wind, float effectiveWindSpeed) {
      double x = origin.getX() + 0.5;
      double y = origin.getY() + 1.0;
      double z = origin.getZ() + 0.5;
      level.sendParticles(ParticleTypes.FLAME, x, y, z, 4, 0.15, 0.35, 0.15, 0.02);
      level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.1, 0.2, 0.1, 0.0);
      level.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 3, 0.12, 0.25, 0.12, 0.01);
      if (kind == EmberSystem.SparkKind.LEAF_FRAGMENT) {
         level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 0.3, z, 2, 0.2, 0.3, 0.2, 0.0);
      } else if (kind == EmberSystem.SparkKind.GRASS_AWN) {
         level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.2, z, 3, 0.15, 0.25, 0.15, 0.0);
      } else {
         level.sendParticles(ParticleTypes.ASH, x, y + 0.25, z, 2, 0.12, 0.2, 0.12, 0.0);
      }

      if (effectiveWindSpeed > 0.15F) {
         level.sendParticles(ParticleTypes.SMALL_FLAME, x + wind.dx() * 0.4, y + 0.4, z + wind.dz() * 0.4, 2, 0.05, 0.1, 0.05, 0.01);
      }
   }

   private static void emitTrail(ServerLevel level, double x, double y, double z, EmberSystem.SparkKind kind, RandomSource random) {
      level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
      if (random.nextBoolean()) {
         level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
      }

      if (random.nextInt(3) == 0) {
         switch (kind) {
            case LEAF_FRAGMENT:
               level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 1, 0.04, 0.04, 0.04, 0.0);
               break;
            case GRASS_AWN:
               level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 1, 0.04, 0.04, 0.04, 0.0);
               break;
            case BARK_CHIP:
               level.sendParticles(ParticleTypes.ASH, x, y, z, 1, 0.04, 0.04, 0.04, 0.0);
               break;
            case EMBER:
               level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
         }
      }
   }

   private static final class Spark {
      final double ox;
      final double oy;
      final double oz;
      final double tx;
      final double ty;
      final double tz;
      final int incidentId;
      final int maxLife;
      int life;
      final float arcHeight;
      final float ignitionPower;
      final EmberSystem.SparkKind kind;

      Spark(
         double ox,
         double oy,
         double oz,
         double tx,
         double ty,
         double tz,
         int incidentId,
         int life,
         int maxLife,
         float arcHeight,
         float ignitionPower,
         EmberSystem.SparkKind kind
      ) {
         this.ox = ox;
         this.oy = oy;
         this.oz = oz;
         this.tx = tx;
         this.ty = ty;
         this.tz = tz;
         this.incidentId = incidentId;
         this.life = life;
         this.maxLife = maxLife;
         this.arcHeight = arcHeight;
         this.ignitionPower = ignitionPower;
         this.kind = kind;
      }
   }

   public static enum SparkKind {
      LEAF_FRAGMENT(1.25F),
      GRASS_AWN(0.85F),
      BARK_CHIP(1.1F),
      EMBER(1.0F);

      public final float spotMultiplier;

      private SparkKind(float spotMultiplier) {
         this.spotMultiplier = spotMultiplier;
      }

      static EmberSystem.SparkKind from(FuelMaterial material) {
         return switch (material) {
            case DRY_LEAVES, LIVING_LEAVES -> LEAF_FRAGMENT;
            case GRASS, DEAD_BUSH -> GRASS_AWN;
            case LOG, TRUNK, WOODEN_STRUCTURE -> BARK_CHIP;
            default -> EMBER;
         };
      }
   }
}
