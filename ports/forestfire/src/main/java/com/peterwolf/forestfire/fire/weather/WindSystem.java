package com.peterwolf.forestfire.fire.weather;

import com.peterwolf.forestfire.config.ForestFireConfig;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class WindSystem {
   private float speed;
   private float yawDegrees;
   private float targetSpeed;
   private float targetYaw;
   private int ticksUntilChange;
   private long lastGameTime = Long.MIN_VALUE;
   private float firestormBoost;
   private float targetFirestormBoost;

   public WindSystem() {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      this.speed = cfg.configuredWindSpeed;
      this.yawDegrees = Mth.wrapDegrees(cfg.configuredWindYawDegrees);
      this.targetSpeed = this.speed;
      this.targetYaw = this.yawDegrees;
      this.ticksUntilChange = cfg.windChangeIntervalTicks;
   }

   public void tick(ServerLevel level) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      if (!cfg.windEnabled) {
         this.firestormBoost = 0.0F;
         this.targetFirestormBoost = 0.0F;
      } else {
         long time = level.getGameTime();
         if (this.lastGameTime != time) {
            this.lastGameTime = time;
            RandomSource random = level.getRandom();
            if (!cfg.dynamicWind) {
               this.targetSpeed = cfg.configuredWindSpeed;
               this.targetYaw = Mth.wrapDegrees(cfg.configuredWindYawDegrees);
               this.ticksUntilChange = cfg.windChangeIntervalTicks;
            } else if (--this.ticksUntilChange <= 0) {
               this.ticksUntilChange = Math.max(200, cfg.windChangeIntervalTicks + random.nextInt(cfg.windChangeIntervalTicks / 2 + 1));
               float delta = (random.nextFloat() * 2.0F - 1.0F) * cfg.windChangeStrength;
               this.targetSpeed = Mth.clamp(this.targetSpeed + delta, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
               this.targetYaw = Mth.wrapDegrees(this.targetYaw + (random.nextFloat() * 2.0F - 1.0F) * cfg.windDirectionChangeDegrees);
            }

            this.speed = Mth.lerp(cfg.windSmoothingFactor, this.speed, this.targetSpeed);
            float yawDiff = Mth.wrapDegrees(this.targetYaw - this.yawDegrees);
            this.yawDegrees = Mth.wrapDegrees(this.yawDegrees + yawDiff * cfg.windSmoothingFactor);
            this.firestormBoost = Mth.lerp(0.08F, this.firestormBoost, this.targetFirestormBoost);
         }
      }
   }

   public void applyFirestorm(FirestormState storm) {
      if (storm != null && storm.active) {
         this.targetFirestormBoost = storm.windBoost;
      } else {
         this.targetFirestormBoost = 0.0F;
      }
   }

   public float baseSpeed() {
      return this.speed;
   }

   public float speed() {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      return !cfg.windEnabled ? 0.0F : Mth.clamp(this.speed, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
   }

   public float firestormSpeed() {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      if (!cfg.windEnabled) {
         return 0.0F;
      } else {
         float cap = cfg.maximumWindSpeed + cfg.firestormWindBoostMax;
         return Mth.clamp(this.speed + this.firestormBoost, cfg.minimumWindSpeed, cap);
      }
   }

   public float firestormBoost() {
      return this.firestormBoost;
   }

   public float yawDegrees() {
      return this.yawDegrees;
   }

   public float dx() {
      float rad = this.yawDegrees * (float) (Math.PI / 180.0);
      return -Mth.sin(rad);
   }

   public float dz() {
      float rad = this.yawDegrees * (float) (Math.PI / 180.0);
      return Mth.cos(rad);
   }

   public Direction cardinal() {
      float yaw = Mth.wrapDegrees(this.yawDegrees);
      if (yaw >= -45.0F && yaw < 45.0F) {
         return Direction.SOUTH;
      } else if (yaw >= 45.0F && yaw < 135.0F) {
         return Direction.WEST;
      } else {
         return yaw >= -135.0F && yaw < -45.0F ? Direction.EAST : Direction.NORTH;
      }
   }

   public String compassLabel() {
      float yaw = Mth.wrapDegrees(this.yawDegrees);
      String[] labels = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      int mapped = Math.floorMod(Math.round(yaw / 45.0F), 8);
      return labels[mapped];
   }

   public void setWind(float speed, float yaw) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      float safeSpeed = Float.isFinite(speed) ? speed : cfg.configuredWindSpeed;
      float safeYaw = Float.isFinite(yaw) ? yaw : cfg.configuredWindYawDegrees;
      this.speed = Mth.clamp(safeSpeed, cfg.minimumWindSpeed, cfg.maximumWindSpeed);
      this.yawDegrees = Mth.wrapDegrees(safeYaw);
      this.targetSpeed = this.speed;
      this.targetYaw = this.yawDegrees;
      this.ticksUntilChange = cfg.windChangeIntervalTicks;
   }

   public void setSpeed(float speed) {
      this.setWind(speed, this.yawDegrees);
   }

   public void setYawDegrees(float yaw) {
      this.setWind(this.speed, yaw);
   }

   public void resetToConfigured() {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      this.setWind(cfg.configuredWindSpeed, cfg.configuredWindYawDegrees);
   }

   public void load(float speed, float yaw) {
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      if (!cfg.dynamicWind) {
         this.setWind(cfg.configuredWindSpeed, cfg.configuredWindYawDegrees);
      } else {
         this.setWind(speed, yaw);
      }
   }
}
