package com.jaymoskingdoms.illagers;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public class Provoker extends SpellcasterIllager implements RangedAttackMob {
   private int buffAllyCooldown;

   public Provoker(EntityType<? extends Provoker> entityType, Level level) {
      super(entityType, level);
      this.xpReward = 10;
   }

   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new AvoidEntityGoal(this, Creaking.class, 8.0F, 1.0, 1.2));
      this.goalSelector.addGoal(2, new SpellcasterCastingSpellGoal());
      this.goalSelector.addGoal(3, new Provoker.BuffAllyGoal());
      this.goalSelector.addGoal(4, new RangedBowAttackWithoutStrafingGoal(this, 0.85, 30, 15.0F));
      this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}).setAlertOthers(new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
   }

   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
   ) {
      spawnGroupData = super.finalizeSpawn(level, difficulty, entitySpawnReason, spawnGroupData);
      RandomSource randomSource = level.getRandom();
      this.populateDefaultEquipmentSlots(randomSource, difficulty);
      return spawnGroupData;
   }

   protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
      super.populateDefaultEquipmentSlots(random, difficulty);
      this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
   }

   public void performRangedAttack(LivingEntity target, float pullProgress) {
      ItemStack itemInHand = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
      ItemStack itemStack = this.getProjectile(itemInHand);
      AbstractArrow abstractArrow = ProjectileUtil.getMobArrow(this, itemStack, pullProgress, itemInHand);
      double d = target.getX() - this.getX();
      double e = target.getY(0.3333333333333333) - abstractArrow.getY();
      double f = target.getZ() - this.getZ();
      double g = Math.sqrt(d * d + f * f);
      if (this.level() instanceof ServerLevel serverLevel) {
         Projectile.spawnProjectileUsingShoot(abstractArrow, serverLevel, itemStack, d, e + g * 0.2F, f, 1.6F, 14 - serverLevel.getDifficulty().getId() * 4);
      }

      this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
   }

   public float getWalkTargetValue(BlockPos pos, LevelReader level) {
      return 0.0F;
   }

   public int getMaxSpawnClusterSize() {
      return 1;
   }

   public SoundEvent getCelebrateSound() {
      return (SoundEvent)ModSoundEvents.PROVOKER_CELEBRATE_SOUND_EVENT.value();
   }

   protected void customServerAiStep(ServerLevel serverLevel) {
      this.buffAllyCooldown--;
      super.customServerAiStep(serverLevel);
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSoundEvents.PROVOKER_AMBIENT_SOUND_EVENT.value();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSoundEvents.PROVOKER_DEATH_SOUND_EVENT.value();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSoundEvents.PROVOKER_HURT_SOUND_EVENT.value();
   }

   protected SoundEvent getCastingSoundEvent() {
      return SoundEvents.EVOKER_CAST_SPELL;
   }

   public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
   }

   public IllagerArmPose getArmPose() {
      if (this.isCastingSpell()) {
         return IllagerArmPose.SPELLCASTING;
      } else if (this.isAggressive()) {
         return IllagerArmPose.BOW_AND_ARROW;
      } else {
         return this.isCelebrating() ? IllagerArmPose.CELEBRATING : IllagerArmPose.CROSSED;
      }
   }

   public class BuffAllyGoal extends SpellcasterUseSpellGoal {
      public BuffAllyGoal() {
         super();
      }

      public boolean canUse() {
         return Provoker.this.getTarget() == null ? false : Provoker.this.buffAllyCooldown < 0;
      }

      protected void performSpellCasting() {
         Provoker.this.buffAllyCooldown = 300;
         this.getTargets().forEach(this::buff);
      }

      private List<LivingEntity> getTargets() {
         return Provoker.this.level()
            .getEntitiesOfClass(LivingEntity.class, Provoker.this.getBoundingBox().inflate(12.0), entity -> entity instanceof AbstractIllager);
      }

      private void buff(LivingEntity livingEntity) {
         livingEntity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 120, 0));
         livingEntity.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 0));
         double x = livingEntity.getX();
         double y = livingEntity.getY() + 1.0;
         double z = livingEntity.getZ();
         ((ServerLevel)Provoker.this.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z, 10, 0.4, 0.4, 0.4, 0.15);
      }

      protected int getCastWarmupTime() {
         return 40;
      }

      protected int getCastingTime() {
         return 60;
      }

      protected int getCastingInterval() {
         return 140;
      }

      protected SoundEvent getSpellPrepareSound() {
         return SoundEvents.ILLUSIONER_PREPARE_BLINDNESS;
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.WOLOLO;
      }
   }
}
