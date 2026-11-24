package com.jaymoskingdoms.illagers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;

public class Surrendered extends Vex {
   public Surrendered(EntityType<? extends Surrendered> entityType, Level level) {
      super(entityType, level);
      this.xpReward = 5;
   }

   public boolean isFlapping() {
      return false;
   }

   public void tick() {
      super.tick();
      com.jaymoskingdoms.mixin.illagers.VexAccessor life = (com.jaymoskingdoms.mixin.illagers.VexAccessor)this;
      if (life.jaymoskingdoms$limitedLifeTicks() > 20 && (this.getOwner() == null || !this.getOwner().isAlive())) {
         life.jaymoskingdoms$setLimitedLifeTicks(20);
      }
   }

   public boolean doHurtTarget(ServerLevel serverLevel, Entity entity) {
      if (!super.doHurtTarget(serverLevel, entity)) {
         return false;
      }

      if (entity instanceof LivingEntity livingEntity) {
         livingEntity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
      }

      return true;
   }

   public void aiStep() {
      if (this.level() instanceof ServerLevel serverLevel) {
         for (int i = 0; i < 2; i++) {
            serverLevel.sendParticles(ParticleTypes.WHITE_ASH, this.xo, this.yo + 1.2, this.zo, 2, 0.2, 0.0, 0.2, 0.025);
         }
      }

      super.aiStep();
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSoundEvents.SURRENDERED_AMBIENT_SOUND_EVENT.value();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSoundEvents.SURRENDERED_DEATH_SOUND_EVENT.value();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSoundEvents.SURRENDERED_HURT_SOUND_EVENT.value();
   }

   protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficulty) {
   }

   public void playSound(SoundEvent sound, float volume, float pitch) {
      if (sound == SoundEvents.VEX_CHARGE) {
         sound = (SoundEvent)ModSoundEvents.SURRENDERED_CHARGE_SOUND_EVENT.value();
      }

      super.playSound(sound, volume, pitch);
   }
}
