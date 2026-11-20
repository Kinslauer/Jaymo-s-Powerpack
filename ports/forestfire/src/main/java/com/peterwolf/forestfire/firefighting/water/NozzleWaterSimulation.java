package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class NozzleWaterSimulation {
   private static final int MAX_BLOCKS_PER_TICK = 96;

   private NozzleWaterSimulation() {
   }

   public static float spray(ServerLevel level, ServerPlayer player, NozzleMode mode, float pressure01) {
      if (mode.allowsFlow() && !(pressure01 <= 0.05F)) {
         ForestFireConfig.Data cfg = ForestFireConfig.get();
         float pumpBoost = Math.max(1.0F, cfg.pumpNozzleExtinguishMultiplier);
         float effectivePressure = Mth.clamp(pressure01, 0.05F, 1.0F);
         float range = mode.range * (0.55F + 0.55F * effectivePressure);
         float cooling = mode.cooling * (0.65F + 0.55F * effectivePressure) * pumpBoost;
         float radius = mode.radius * (0.85F + 0.35F * effectivePressure);
         Vec3 eye = player.getEyePosition();
         Vec3 look = player.getLookAngle();
         FireSimulation sim = FireSimulation.get(level);
         float used = 0.0F;
         int budget = 96;
         if (mode == NozzleMode.STRAIGHT_STREAM) {
            HitResult hit = player.pick(range, 0.0F, false);
            Vec3 end = hit.getType() == Type.BLOCK ? hit.getLocation() : eye.add(look.scale(range));
            int steps = Math.max(1, (int)Math.ceil(range * 1.35));

            for (int i = 1; i <= steps && budget > 0; i++) {
               double t = (double)i / steps;
               Vec3 p = eye.lerp(end, t);
               BlockPos pos = BlockPos.containing(p);
               float falloff = 1.0F - (float)t * 0.3F;
               used += sim.applyWater(pos, cooling * falloff * 1.35F, Math.max(0.75F, radius * 0.85F));
               if (i % 2 == 0) {
                  used += sim.applyWater(pos.below(), cooling * falloff * 0.55F, 0.6F);
               }

               budget -= 2;
            }

            spawnStreamParticles(level, eye, end, effectivePressure, false);
         } else {
            int rays = mode == NozzleMode.WIDE_FOG ? 14 : 10;

            for (int ray = 0; ray < rays && budget > 0; ray++) {
               float yawOff = ((float)ray / rays - 0.5F) * radius * 20.0F;
               float pitchOff = (ray % 3 - 1) * radius * 7.0F;
               Vec3 dir = rotateLook(look, yawOff, pitchOff);
               int steps = Math.max(2, (int)Math.ceil(range));

               for (int i = 1; i <= steps && budget > 0; i++) {
                  if (i % 2 == 0 || i == steps) {
                     double dist = (double)i / steps * range;
                     Vec3 p = eye.add(dir.scale(dist));
                     BlockPos pos = BlockPos.containing(p);
                     float falloff = 1.0F - (float)(dist / range) * 0.35F;
                     float r = radius * (0.55F + (float)(dist / range) * 0.9F);
                     used += sim.applyWater(pos, cooling * falloff * 1.05F, r);
                     budget--;
                  }
               }
            }

            Vec3 end = eye.add(look.scale(range * 0.85));
            spawnStreamParticles(level, eye, end, effectivePressure, true);
         }

         if (mode == NozzleMode.WIDE_FOG) {
            sim.applyWater(player.blockPosition(), 0.35F * effectivePressure * pumpBoost, 1.6F);
            sim.applyWater(player.blockPosition().above(), 0.2F * effectivePressure * pumpBoost, 1.2F);
         }

         float waterUsed = used;
         IncidentManager manager = IncidentManager.get(level);
         manager.openIncidents().stream().findFirst().ifPresent(incident -> {
            incident.recordWater(waterUsed * 10.0F);
            incident.addParticipant(player.getUUID());
            manager.markDirty();
         });
         if (level.getGameTime() % 12L == 0L) {
            float volume = 0.12F + effectivePressure * 0.18F;
            float pitch = 0.85F + effectivePressure * 0.15F;
            level.playSound(null, player.blockPosition(), SoundEvents.WEATHER_RAIN, SoundSource.AMBIENT, volume, pitch);
            if (effectivePressure > 0.45F && level.getGameTime() % 24L == 0L) {
               level.playSound(
                  null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.AMBIENT, 0.08F + effectivePressure * 0.1F, 1.1F
               );
            }
         }

         return used;
      } else {
         return 0.0F;
      }
   }

   private static Vec3 rotateLook(Vec3 look, float yawDeg, float pitchDeg) {
      double yaw = Math.toRadians(yawDeg);
      double pitch = Math.toRadians(pitchDeg);
      double cosY = Math.cos(yaw);
      double sinY = Math.sin(yaw);
      double cosP = Math.cos(pitch);
      double sinP = Math.sin(pitch);
      double x = look.x * cosY - look.z * sinY;
      double z = look.x * sinY + look.z * cosY;
      double y = look.y * cosP - Math.sqrt(x * x + z * z) * sinP * 0.15;
      return new Vec3(x, y, z).normalize();
   }

   private static void spawnStreamParticles(ServerLevel level, Vec3 start, Vec3 end, float pressure, boolean fog) {
      int steps = fog ? 8 : 6;

      for (int i = 0; i < steps; i++) {
         double t = (double)i / steps;
         double px = start.x + (end.x - start.x) * t;
         double py = start.y + (end.y - start.y) * t;
         double pz = start.z + (end.z - start.z) * t;
         double spread = fog ? 0.22 + t * 0.28 : 0.05;
         level.sendParticles(ParticleTypes.SPLASH, px, py, pz, fog ? 6 : 3, spread, spread * 0.5, spread, 0.02);
         level.sendParticles(ParticleTypes.RAIN, px, py, pz, fog ? 5 : 2, spread * 0.6, spread * 0.35, spread * 0.6, 0.0);
         if (pressure > 0.5F && i > steps / 3) {
            level.sendParticles(ParticleTypes.FALLING_WATER, px, py, pz, fog ? 2 : 1, spread * 0.3, 0.05, spread * 0.3, 0.0);
         }
      }
   }
}
