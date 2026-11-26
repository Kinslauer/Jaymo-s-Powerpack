package com.jaymoskingdoms.illagers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public class Marauder extends AbstractIllager implements RangedAttackMob {
   private static final Identifier SPEED_MODIFIER_AGGRESSIVE_ID = Identifier.withDefaultNamespace("aggressive");
   private static final AttributeModifier SPEED_MODIFIER_AGGRESSIVE = new AttributeModifier(SPEED_MODIFIER_AGGRESSIVE_ID, -0.06, Operation.ADD_VALUE);
   private static final EntityDataAccessor<Integer> RANGED_ATTACK_TIME = SynchedEntityData.defineId(Marauder.class, EntityDataSerializers.INT);
   private static final int RANGED_ATTACK_INTERVAL = 40;
   private int rangedAttackTimeO;

   public Marauder(EntityType<? extends Marauder> entityType, Level level) {
      super(entityType, level);
   }

   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new AvoidEntityGoal(this, Creaking.class, 8.0F, 1.0, 1.2));
      this.goalSelector.addGoal(2, new Marauder.MarauderMeleeAttackGoal(this, 1.35, false));
      this.goalSelector.addGoal(3, new Marauder.MarauderRangedAttackGoal(this, 1.0, 40, 8.0F));
      this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}).setAlertOthers(new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(RANGED_ATTACK_TIME, 40);
   }

   public void aiStep() {
      super.aiStep();
      this.rangedAttackTimeO = (Integer)this.entityData.get(RANGED_ATTACK_TIME);
   }

   public float getChargingProgress(float partialTick) {
      float rangedAttackTime = Mth.lerp(partialTick, this.rangedAttackTimeO, ((Integer)this.entityData.get(RANGED_ATTACK_TIME)).intValue());
      return 1.0F - Mth.clamp(rangedAttackTime / 40.0F, 0.0F, 1.0F);
   }

   public void setRangedAttackTime(int attackTime) {
      this.entityData.set(RANGED_ATTACK_TIME, attackTime);
   }

   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
   ) {
      spawnGroupData = super.finalizeSpawn(level, difficulty, entitySpawnReason, spawnGroupData);
      RandomSource randomSource = level.getRandom();
      this.populateDefaultEquipmentSlots(randomSource, difficulty);
      this.populateDefaultEquipmentEnchantments(level, randomSource, difficulty);
      return spawnGroupData;
   }

   protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
      super.populateDefaultEquipmentSlots(random, difficulty);
      this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
   }

   public void performRangedAttack(LivingEntity target, float pullProgress) {
      ItemStack itemInHand = this.getMainHandItem();
      ItemStack itemStack = itemInHand.is(Items.IRON_AXE) ? itemInHand : new ItemStack(Items.IRON_AXE);
      ThrownHatchet thrownHatchet = new ThrownHatchet(this.level(), this, itemStack);
      double d = target.getX() - this.getX();
      double e = target.getY(0.3333333333333333) - thrownHatchet.getY();
      double f = target.getZ() - this.getZ();
      double g = Math.sqrt(d * d + f * f);
      if (this.level() instanceof ServerLevel serverLevel) {
         Projectile.spawnProjectileUsingShoot(thrownHatchet, serverLevel, itemStack, d, e + g * 0.2F, f, 1.6F, 14 - this.level().getDifficulty().getId() * 4);
      }

      this.playSound((SoundEvent)SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
   }

   public SoundEvent getCelebrateSound() {
      return SoundEvents.PILLAGER_CELEBRATE;
   }

   public float getWalkTargetValue(BlockPos pos, LevelReader level) {
      return 0.0F;
   }

   public int getMaxSpawnClusterSize() {
      return 1;
   }

   protected void customServerAiStep(ServerLevel serverLevel) {
      super.customServerAiStep(serverLevel);
      AttributeInstance attributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
      if (!this.isAggressive()) {
         attributeInstance.removeModifier(SPEED_MODIFIER_AGGRESSIVE_ID);
      } else if (!attributeInstance.hasModifier(SPEED_MODIFIER_AGGRESSIVE_ID)) {
         attributeInstance.addTransientModifier(SPEED_MODIFIER_AGGRESSIVE);
      }
   }

   protected SoundEvent getAmbientSound() {
      return SoundEvents.PILLAGER_AMBIENT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.PILLAGER_DEATH;
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PILLAGER_HURT;
   }

   public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
   }

   public IllagerArmPose getArmPose() {
      return this.isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.NEUTRAL;
   }

   static class MarauderMeleeAttackGoal extends MeleeAttackGoal {
      public MarauderMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
         super(mob, speedModifier, followingTargetEvenIfNotSeen);
      }

      public boolean canUse() {
         return super.canUse() && this.mob.distanceToSqr(this.mob.getTarget()) < 16.0;
      }

      public boolean canContinueToUse() {
         return super.canContinueToUse() && this.mob.distanceToSqr(this.mob.getTarget()) < 36.0;
      }
   }

   class MarauderRangedAttackGoal extends RangedAttackGoal {
      public MarauderRangedAttackGoal(RangedAttackMob rangedAttackMob, double speedModifier, int attackInterval, float attackRadius) {
         super(rangedAttackMob, speedModifier, attackInterval, attackRadius);
      }

      public boolean canUse() {
         return super.canUse() && Marauder.this.getMainHandItem().is(Items.IRON_AXE);
      }

      public void start() {
         super.start();
         Marauder.this.setAggressive(true);
         Marauder.this.startUsingItem(InteractionHand.MAIN_HAND);
      }

      public void stop() {
         super.stop();
         Marauder.this.stopUsingItem();
         Marauder.this.setAggressive(false);
         Marauder.this.setRangedAttackTime(40);
      }

      public void tick() {
         super.tick();
         Marauder.this.setRangedAttackTime(((com.jaymoskingdoms.mixin.illagers.RangedAttackGoalAccessor)this).jaymoskingdoms$attackTime());
      }
   }
}
