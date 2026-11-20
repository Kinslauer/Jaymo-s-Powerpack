package com.peterwolf.forestfire.fire.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class WeatherFireModel {
   private WeatherFireModel() {
   }

   public static float humidity01(ServerLevel level, BlockPos pos) {
      boolean raining = level.isRaining();
      boolean thundering = level.isThundering();
      Biome biome = (Biome)level.getBiome(pos).value();
      float temperature = Math.min(1.0F, Math.max(0.0F, biome.getBaseTemperature()));
      float base = 0.2F + (biome.hasPrecipitation() ? 0.14F : 0.0F) + (1.0F - temperature) * 0.1F;
      if (raining) {
         base += 0.3F;
      }

      if (thundering) {
         base += 0.1F;
      }

      if (level.isRainingAt(pos)) {
         base += 0.15F;
      }

      return Math.min(1.0F, base);
   }

   public static float initialFuelMoisture01(ServerLevel level, BlockPos pos, float materialBaseMoisture) {
      float moisture = materialBaseMoisture * 0.72F + humidity01(level, pos) * 0.34F - temperature01(level, pos) * 0.05F;
      if (isRainingAt(level, pos)) {
         moisture += 0.18F;
      }

      return Math.min(0.98F, Math.max(0.03F, moisture));
   }

   public static float temperature01(ServerLevel level, BlockPos pos) {
      Biome biome = (Biome)level.getBiome(pos).value();
      float temp = biome.getBaseTemperature();
      long dayTime = level.getOverworldClockTime() % 24000L;
      boolean day = dayTime > 0L && dayTime < 12000L;
      if (day) {
         temp += 0.15F;
      }

      return Math.min(1.0F, Math.max(0.0F, temp));
   }

   public static boolean isRainingAt(ServerLevel level, BlockPos pos) {
      return level.isRaining() && level.isRainingAt(pos);
   }

   public static String recentRainfallLabel(ServerLevel level, BlockPos pos) {
      if (level.isRainingAt(pos)) {
         return "active";
      } else {
         return level.isRaining() ? "nearby" : "none";
      }
   }

   public static FireDangerLevel danger(ServerLevel level, BlockPos pos, WindSystem wind) {
      return danger(level, pos, wind.speed());
   }

   public static FireDangerLevel danger(ServerLevel level, BlockPos pos, float windSpeed) {
      float humidity = humidity01(level, pos);
      float temperature = temperature01(level, pos);
      float windFactor = Math.max(0.0F, windSpeed);
      float dryness = 1.0F - humidity;
      float score = dryness * 0.45F + temperature * 0.3F + windFactor * 0.25F;
      if (level.isRaining()) {
         score *= 0.45F;
      }

      if (score < 0.25F) {
         return FireDangerLevel.LOW;
      } else if (score < 0.4F) {
         return FireDangerLevel.MODERATE;
      } else if (score < 0.55F) {
         return FireDangerLevel.HIGH;
      } else {
         return score < 0.72F ? FireDangerLevel.VERY_HIGH : FireDangerLevel.EXTREME;
      }
   }

   public static float slopeFactor(ServerLevel level, BlockPos from, int dx, int dz) {
      int y0 = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ());
      int y1 = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, from.getX() + dx, from.getZ() + dz);
      int delta = y1 - y0;
      if (delta > 0) {
         return Math.min(1.5F, 1.0F + delta * 0.12F);
      } else {
         return delta < 0 ? Math.max(0.4F, 1.0F + delta * 0.08F) : 1.0F;
      }
   }
}
