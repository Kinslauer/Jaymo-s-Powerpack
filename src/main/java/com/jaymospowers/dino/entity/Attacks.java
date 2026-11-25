package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.Dino;
import com.jaymospowers.dino.DinoEntities;
import com.jaymospowers.dino.DinoSounds;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class Attacks {
   private static final Identifier CHARGE_SPEED = Dino.id("charge_speed");

   private Attacks() {
   }

   public static Vec3 facing(Dinosaur d) {
      float yaw = d.yBodyRot * (float) (Math.PI / 180.0);
      return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
   }

   public static double gap(Entity a, Entity b) {
      double dx = Math.max(0.0, Math.abs(a.getX() - b.getX()) - (a.getBbWidth() + b.getBbWidth()) * 0.5);
      double dz = Math.max(0.0, Math.abs(a.getZ() - b.getZ()) - (a.getBbWidth() + b.getBbWidth()) * 0.5);
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static boolean heightInReach(Dinosaur d, Entity target, double extra) {
      return target.getY() < d.getY() + d.getBbHeight() + extra && target.getY() + target.getBbHeight() > d.getY() - extra;
   }

   public static boolean clear(Dinosaur d, Vec3 from, Entity target) {
      Vec3 to = target.getBoundingBox().getCenter();
      return d.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, d)).getType() == Type.MISS;
   }

   private static Vec3 chest(Dinosaur d) {
      return d.position().add(0.0, d.getBbHeight() * 0.6, 0.0);
   }

   private static boolean touchable(Entity e) {
      return e.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e);
   }

   private static boolean inFront(Dinosaur d, Entity target, double halfAngleCos) {
      Vec3 to = new Vec3(target.getX() - d.getX(), 0.0, target.getZ() - d.getZ());
      return to.lengthSqr() < 0.01 ? true : to.normalize().dot(facing(d)) >= halfAngleCos;
   }

   public static void landAttack(Dinosaur d, ServerLevel level) {
      Species s = d.species();
      float scale = d.getAgeScale();
      double reach = s.reach * scale + 0.6;
      int style = s.spec().attackStyle;
      if (style == 2) {
         Vec3 feet = d.position().add(facing(d).scale(d.getBbWidth() * 0.55));
         d.playSound((SoundEvent)DinoSounds.STOMP, 2.0F, 1.0F);
         d.groundBurst(level, feet, 3.5 * scale, 60);

         for (LivingEntity e : d.nearby(reach + 2.0)) {
            if (!d.isFriend(e)
               && !(e.distanceToSqr(feet) > (reach + 1.5) * (reach + 1.5))
               && heightInReach(d, e, 1.0)
               && clear(d, feet.add(0.0, 0.6, 0.0), e)
               && d.doHurtTarget(level, e)) {
               e.push(0.0, 0.45, 0.0);
               e.syncVelocity = true;
            }
         }
      } else {
         boolean any = false;
         LivingEntity target = d.getTarget();
         if (!s.big()) {
            if (target != null && target.isAlive() && gap(d, target) <= reach + 0.4 && heightInReach(d, target, 1.5) && clear(d, d.mouthPosition(), target)) {
               any = d.doHurtTarget(level, target);
            } else {
               LivingEntity best = null;
               double bestGap = reach;

               for (LivingEntity e : d.nearby(reach + 1.0)) {
                  double g = gap(d, e);
                  if (!d.isFriend(e) && g <= bestGap && inFront(d, e, 0.35) && heightInReach(d, e, 1.5) && clear(d, d.mouthPosition(), e)) {
                     best = e;
                     bestGap = g;
                  }
               }

               if (best != null) {
                  any = d.doHurtTarget(level, best);
               }
            }
         } else {
            double cos = style == 3 ? -0.1 : 0.45;

            for (LivingEntity e : d.nearby(reach + 2.0)) {
               if (!d.isFriend(e)
                  && !(gap(d, e) > reach)
                  && inFront(d, e, cos)
                  && heightInReach(d, e, 2.0)
                  && clear(d, chest(d), e)
                  && d.doHurtTarget(level, e)) {
                  any = true;
                  if (style == 1) {
                     e.push(0.0, 0.5, 0.0);
                     e.syncVelocity = true;
                  }
               }
            }
         }

         if (!any) {
            d.playSound(s.big() ? (SoundEvent)DinoSounds.BITE_BIG : (SoundEvent)DinoSounds.BITE, 0.6F, d.getVoicePitch() * 1.1F);
         }
      }
   }

   public static void landSpecial(Dinosaur d, ServerLevel level) {
      switch (d.species()) {
         case TYRANNOSAURUS:
            roarOfTerror(d, level);
            break;
         case DILOPHOSAURUS:
            spit(d, level);
            break;
         case BRACHIOSAURUS:
            slam(d, level, 9.0);
            break;
         case SPINOSAURUS:
            slam(d, level, 6.0);
            break;
         case PARASAUROLOPHUS:
            rallyCall(d, level);
      }
   }

   private static void roarOfTerror(Dinosaur d, ServerLevel level) {
      for (LivingEntity e : d.nearby(18.0)) {
         if (!d.isFriend(e) && !(e.getMaxHealth() >= d.getMaxHealth()) && touchable(e)) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), d);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, e instanceof Player ? 0 : 1), d);
            if (e instanceof PathfinderMob mob && !(e instanceof TamableAnimal t && t.isTame())) {
               mob.setTarget(null);
               Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 6, d.position());
               if (away != null) {
                  mob.getNavigation().moveTo(away.x, away.y, away.z, 1.6);
               }
            }
         }
      }

      level.sendParticles(ParticleTypes.SONIC_BOOM, d.mouthPosition().x, d.mouthPosition().y, d.mouthPosition().z, 1, 0.0, 0.0, 0.0, 0.0);
   }

   private static void spit(Dinosaur d, ServerLevel level) {
      Vec3 from = d.mouthPosition();
      LivingEntity rider = d.getControllingPassenger();
      LivingEntity target = d.getTarget();
      Vec3 aim;
      if (rider != null) {
         aim = rider.getLookAngle();
      } else if (target != null) {
         aim = target.getEyePosition().subtract(from);
         aim = aim.add(0.0, aim.horizontalDistance() * 0.12, 0.0).normalize();
      } else {
         aim = facing(d).add(0.0, 0.1, 0.0).normalize();
      }

      VenomSpit spit = new VenomSpit((EntityType<? extends VenomSpit>)DinoEntities.VENOM_SPIT, level);
      spit.setOwner((Entity)(rider != null ? rider : d));
      spit.setSpitter(d);
      spit.setPos(from.x, from.y, from.z);
      spit.shoot(aim.x, aim.y, aim.z, 1.3F, 3.0F);
      level.addFreshEntity(spit);
      d.playSound((SoundEvent)DinoSounds.SPIT, 1.0F, d.getVoicePitch());
   }

   private static void slam(Dinosaur d, ServerLevel level, double radius) {
      Vec3 feet = d.position().add(facing(d).scale(d.getBbWidth() * 0.6));
      d.playSound((SoundEvent)DinoSounds.STOMP, 4.0F, 0.8F);
      d.groundBurst(level, feet, radius * 0.78, (int)(radius * 18.0));
      float damage = (float)d.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F;

      for (LivingEntity e : d.nearby(radius + 2.0)) {
         double dist = Math.sqrt(e.distanceToSqr(feet.x, e.getY(), feet.z));
         if (!d.isFriend(e)
            && !(dist > radius)
            && !(e.getY() > d.getY() + 4.0)
            && !(e.getY() + e.getBbHeight() < d.getY() - 2.0)
            && clear(d, feet.add(0.0, 1.0, 0.0), e)) {
            float falloff = (float)(1.0 - 0.6 * dist / radius);
            if (e.hurtServer(level, d.damageSources().mobAttack(d), damage * falloff)) {
               Vec3 out = new Vec3(e.getX() - feet.x, 0.0, e.getZ() - feet.z);
               out = out.lengthSqr() < 0.01 ? facing(d) : out.normalize();
               double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
               e.push(out.x * 0.9 * falloff * resist, (0.5 + 0.4 * falloff) * resist, out.z * 0.9 * falloff * resist);
               e.syncVelocity = true;
            }
         }
      }
   }

   private static void rallyCall(Dinosaur d, ServerLevel level) {
      for (LivingEntity e : d.nearby(20.0)) {
         boolean friend = d.isTame() ? d.isFriend(e) : e instanceof Dinosaur other && !other.isTame() && !other.species().predator();
         if (friend) {
            e.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1), d);
            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), d);
         } else if (e instanceof Mob mob && mob.getTarget() != null && d.isFriend(mob.getTarget())) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0), d);
         }
      }

      d.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1));
      d.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
      level.sendParticles(ParticleTypes.NOTE, d.mouthPosition().x, d.mouthPosition().y + 0.5, d.mouthPosition().z, 6, 0.4, 0.3, 0.4, 0.5);
   }

   public static boolean startPounce(Dinosaur d) {
      if (!d.onGround() && !d.isInWater()) {
         return false;
      }

      LivingEntity rider = d.getControllingPassenger();
      LivingEntity target = d.getTarget();
      double power = 1.15;
      Vec3 dir;
      if (rider != null) {
         dir = Vec3.directionFromRotation(0.0F, rider.getYRot());
      } else if (target != null) {
         Vec3 to = new Vec3(target.getX() - d.getX(), 0.0, target.getZ() - d.getZ());
         dir = to.lengthSqr() < 0.01 ? facing(d) : to.normalize();
         power = Mth.clamp(to.length() * 0.16, 0.7, 1.3);
      } else {
         dir = facing(d);
      }

      d.setDeltaMovement(dir.x * power, 0.52, dir.z * power);
      d.syncVelocity = true;
      d.setLeaping(30);
      d.playSound(DinoSounds.roar(d.species()), 1.2F, d.getVoicePitch() * 1.15F);
      return true;
   }

   public static void leapHits(Dinosaur d, ServerLevel level) {
      for (LivingEntity e : d.level().getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(0.45), x -> touchable(x) && !d.isFriend(x))) {
         float damage = (float)d.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F;
         if (e.hurtServer(level, d.damageSources().mobAttack(d), damage)) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3), d);
            d.playSound((SoundEvent)DinoSounds.BITE, 1.0F, d.getVoicePitch());
            Vec3 v = d.getDeltaMovement();
            d.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
            d.syncVelocity = true;
            d.setLeaping(0);
            return;
         }
      }
   }

   public static boolean startCharge(Dinosaur d) {
      AttributeInstance speed = d.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null && !speed.hasModifier(CHARGE_SPEED)) {
         speed.addTransientModifier(new AttributeModifier(CHARGE_SPEED, 0.22, Operation.ADD_MULTIPLIED_TOTAL));
      }

      d.setCharging(90);
      d.playSound(DinoSounds.roar(d.species()), 2.0F, d.getVoicePitch());
      return true;
   }

   public static void endCharge(Dinosaur d) {
      AttributeInstance speed = d.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(CHARGE_SPEED);
      }

      if (d.isCharging()) {
         d.setCharging(0);
      }
   }

   public static void chargeHits(Dinosaur d, ServerLevel level) {
      if (!(d.getKnownMovement().horizontalDistanceSqr() < 0.012)) {
         Vec3 nose = d.position().add(facing(d).scale(d.getBbWidth() * 0.5 + 1.6));
         List<LivingEntity> hit = d.level()
            .getEntitiesOfClass(
               LivingEntity.class, d.getBoundingBox().move(facing(d).scale(1.6)).inflate(0.4), x -> touchable(x) && !d.isFriend(x) && clear(d, chest(d), x)
            );
         if (!hit.isEmpty()) {
            float damage = (float)d.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
            boolean landed = false;

            for (LivingEntity e : hit) {
               if (e.hurtServer(level, d.damageSources().mobAttack(d), damage)) {
                  landed = true;
                  Vec3 f = facing(d);
                  double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                  e.push(f.x * 1.3 * resist, 0.55 * resist, f.z * 1.3 * resist);
                  e.syncVelocity = true;
               }
            }

            if (landed) {
               d.playSound((SoundEvent)DinoSounds.BITE_BIG, 1.4F, 0.8F);
               d.groundBurst(level, nose, 1.5, 20);
               endCharge(d);
            }
         }
      }
   }

   public static boolean startSprint(Dinosaur d) {
      d.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 2));
      d.playSound(DinoSounds.roar(d.species()), 1.2F, d.getVoicePitch());
      return true;
   }

   public static boolean wildSpecial(Dinosaur d, LivingEntity target) {
      if (d.specialCooldownLeft() <= 0 && !d.isBusy()) {
         double gap = gap(d, target);

         return switch (d.species()) {
            case TYRANNOSAURUS -> gap > 5.0 && gap < 20.0 && d.getHealth() > d.getMaxHealth() * 0.3F && d.trySpecial();
            case DILOPHOSAURUS -> gap > 2.5 && gap < 10.0 && d.hasLineOfSight(target) && d.trySpecial();
            case BRACHIOSAURUS -> gap < 6.0 && d.trySpecial();
            case SPINOSAURUS -> gap < 4.5 && d.trySpecial();
            default -> false;
            case VELOCIRAPTOR -> gap > 3.0 && gap < 8.0 && d.onGround() && d.hasLineOfSight(target) && d.trySpecial();
            case TRICERATOPS, CARNOTAURUS -> gap > 5.0 && gap < 18.0 && d.hasLineOfSight(target) && d.trySpecial();
         };
      } else {
         return false;
      }
   }

   public static boolean isRoaring(Dinosaur d) {
      return d.getAction() == 2 || d.getAction() == 3 && d.species().spec().specialStyle == 0;
   }
}
