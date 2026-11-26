package com.jaymoskingdoms.illagers;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Archivist extends SpellcasterIllager {
   private AbstractIllager enchantTarget;
   private int levitateTargetsCooldown;
   private int enchantAlliesCooldown;

   public Archivist(EntityType<? extends Archivist> entityType, Level world) {
      super(entityType, world);
      this.xpReward = 10;
   }

   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new AvoidEntityGoal(this, Creaking.class, 8.0F, 1.0, 1.2));
      this.goalSelector.addGoal(2, new SpellcasterCastingSpellGoal());
      this.goalSelector.addGoal(3, new Archivist.EnchantAllyGoal());
      this.goalSelector.addGoal(4, new Archivist.LevitateTargetsGoal());
      this.goalSelector.addGoal(5, new AvoidEntityGoal(this, Player.class, 8.0F, 0.6, 1.0));
      this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}).setAlertOthers(new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
   }

   public SoundEvent getCelebrateSound() {
      return (SoundEvent)ModSoundEvents.ARCHIVIST_AMBIENT_SOUND_EVENT.value();
   }

   protected void customServerAiStep(ServerLevel serverLevel) {
      this.levitateTargetsCooldown--;
      this.enchantAlliesCooldown--;
      super.customServerAiStep(serverLevel);
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSoundEvents.ARCHIVIST_AMBIENT_SOUND_EVENT.value();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSoundEvents.ARCHIVIST_DEATH_SOUND_EVENT.value();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSoundEvents.ARCHIVIST_HURT_SOUND_EVENT.value();
   }

   @Nullable AbstractIllager getEnchantTarget() {
      return this.enchantTarget;
   }

   void setEnchantTarget(@Nullable AbstractIllager entity) {
      this.enchantTarget = entity;
   }

   protected SoundEvent getCastingSoundEvent() {
      return (SoundEvent)ModSoundEvents.SORCERER_COMPLETE_CAST_SOUND_EVENT.value();
   }

   public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
   }

   public class EnchantAllyGoal extends SpellcasterUseSpellGoal {
      private final TargetingConditions closeEnchantableMobPredicate = TargetingConditions.forNonCombat()
         .range(16.0)
         .selector((livingEntity, serverLevel) -> !(livingEntity instanceof Archivist));
      private int targetId;

      public EnchantAllyGoal() {
         super();
      }

      public boolean canEnchant() {
         AbstractIllager hostileEntity = Archivist.this.getEnchantTarget();
         return hostileEntity != null && EnchantToolUtil.eligibleForEnchant(hostileEntity);
      }

      public boolean canUse() {
         if (Archivist.this.getTarget() == null) {
            return false;
         }

         if (Archivist.this.enchantAlliesCooldown >= 0) {
            return false;
         }

         if (Archivist.this.isCastingSpell()) {
            return false;
         }

         List<AbstractIllager> list = ((ServerLevel)Archivist.this.level())
            .getNearbyEntities(
               AbstractIllager.class, this.closeEnchantableMobPredicate, Archivist.this, Archivist.this.getBoundingBox().inflate(16.0, 4.0, 16.0)
            );
         if (list.isEmpty()) {
            return false;
         }

         Archivist.this.setEnchantTarget(list.get(Archivist.this.random.nextInt(list.size())));
         AbstractIllager hostileEntity = Archivist.this.getEnchantTarget();
         return hostileEntity.getId() == this.targetId ? false : this.canEnchant();
      }

      public void stop() {
         super.stop();
         Archivist.this.setEnchantTarget(null);
      }

      protected void performSpellCasting() {
         Monster hostileEntity = Archivist.this.getEnchantTarget();
         if (hostileEntity != null) {
            this.targetId = hostileEntity.getId();
         }

         EnchantToolUtil.enchantHeldGear(hostileEntity);
         double x = hostileEntity.getX();
         double y = hostileEntity.getY() + 1.5;
         double z = hostileEntity.getZ();
         if (Archivist.this.level() instanceof ServerLevel) {
            ((ServerLevel)Archivist.this.level()).sendParticles(ParticleTypes.ENCHANT, x, y, z, 50, 1.0, 2.0, 1.0, 0.1);
         }

         Archivist.this.enchantAlliesCooldown = 300;
      }

      protected int getCastWarmupTime() {
         return 50;
      }

      protected int getCastingTime() {
         return 50;
      }

      protected int getCastingInterval() {
         return 400;
      }

      protected SoundEvent getSpellPrepareSound() {
         return (SoundEvent)ModSoundEvents.SORCERER_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.SUMMON_VEX;
      }
   }

   public class LevitateTargetsGoal extends SpellcasterUseSpellGoal {
      public LevitateTargetsGoal() {
         super();
      }

      public boolean canUse() {
         return Archivist.this.getTarget() == null ? false : Archivist.this.levitateTargetsCooldown < 0 && !this.getTargets().isEmpty();
      }

      private void knockBack(Entity entity) {
         double d = entity.getX() - Archivist.this.getX();
         double e = entity.getZ() - Archivist.this.getZ();
         double f = Math.max(d * d + e * e, 0.001);
         entity.push(d / f * 5.0, 0.6, e / f * 5.0);
      }

      protected void knockback(LivingEntity target) {
         this.knockBack(target);
         target.needsSync = true;
      }

      private List<LivingEntity> getTargets() {
         return Archivist.this.level()
            .getEntitiesOfClass(
               LivingEntity.class,
               Archivist.this.getBoundingBox().inflate(6.0),
               entity -> !(entity instanceof Monster) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
            );
      }

      protected void performSpellCasting() {
         Archivist.this.levitateTargetsCooldown = 220;
         this.getTargets().forEach(this::buff);
         double x = Archivist.this.getX();
         double y = Archivist.this.getY() + 1.0;
         double z = Archivist.this.getZ();
         ((ServerLevel)Archivist.this.level()).sendParticles(ParticleTypes.ENCHANT, x, y, z, 150, 3.0, 3.0, 3.0, 0.1);
      }

      private void buff(LivingEntity entity) {
         this.knockback(entity);
         entity.hurtServer((ServerLevel)Archivist.this.level(), Archivist.this.damageSources().indirectMagic(Archivist.this, Archivist.this), 4.0F);
      }

      protected int getCastWarmupTime() {
         return 50;
      }

      protected int getCastingTime() {
         return 50;
      }

      protected int getCastingInterval() {
         return 400;
      }

      protected SoundEvent getSpellPrepareSound() {
         return SoundEvents.ILLUSIONER_PREPARE_BLINDNESS;
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.FANGS;
      }
   }
}
