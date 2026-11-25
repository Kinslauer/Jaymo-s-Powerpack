package com.jaymoskingdoms.illagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Basher extends AbstractIllager implements Stunnable {
   private static final String TAG_STUN_TICKS = "Stunned";
   private static final String TAG_BLOCKED_COUNT = "BlockedCount";
   private static final EntityDataAccessor<Boolean> DATA_STUNNED = SynchedEntityData.defineId(Basher.class, EntityDataSerializers.BOOLEAN);
   private int stunTicks;
   private int blockedCount;

   public Basher(EntityType<? extends Basher> entityType, Level level) {
      super(entityType, level);
   }

   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new AvoidEntityGoal(this, Creaking.class, 8.0F, 1.0, 1.2));
      this.goalSelector.addGoal(2, new RaiderOpenDoorGoal(this));
      this.goalSelector.addGoal(3, new HoldGroundAttackGoal(this, 10.0F));
      this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
      this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}).setAlertOthers(new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, true));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
   }

   protected void customServerAiStep(ServerLevel serverLevel) {
      if (!this.isNoAi() && GoalUtils.hasGroundPathNavigation(this)) {
         boolean isRaided = serverLevel.isRaided(this.blockPosition());
         ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(isRaided);
         super.customServerAiStep(serverLevel);
      }
   }

   protected void addAdditionalSaveData(ValueOutput valueOutput) {
      valueOutput.putInt("Stunned", this.stunTicks);
      valueOutput.putInt("BlockedCount", this.blockedCount);
      super.addAdditionalSaveData(valueOutput);
   }

   protected void readAdditionalSaveData(ValueInput valueInput) {
      super.readAdditionalSaveData(valueInput);
      this.setStunTicks(valueInput.getIntOr("Stunned", 0));
      this.blockedCount = valueInput.getIntOr("BlockedCount", 0);
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_STUNNED, false);
   }

   @Override
   public boolean isStunned() {
      return (Boolean)this.entityData.get(DATA_STUNNED);
   }

   public void setStunTicks(int stunTicks) {
      this.stunTicks = Math.max(0, stunTicks);
      this.entityData.set(DATA_STUNNED, this.stunTicks > 0);
   }

   public float getWalkTargetValue(BlockPos pos, LevelReader level) {
      return 0.0F;
   }

   public int getMaxSpawnClusterSize() {
      return 1;
   }

   public void tick() {
      super.tick();
      if (this.isAlive() && this.stunTicks > 0) {
         this.setStunTicks(--this.stunTicks);
      }
   }

   protected boolean isImmobile() {
      return super.isImmobile() || this.isStunned();
   }

   public IllagerArmPose getArmPose() {
      if (this.isCelebrating()) {
         return IllagerArmPose.CELEBRATING;
      } else {
         return this.isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.CROSSED;
      }
   }

   public SoundEvent getCelebrateSound() {
      return (SoundEvent)ModSoundEvents.BASHER_CELEBRATE_SOUND_EVENT.value();
   }

   public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float damageAmount) {
      Entity attacker = damageSource.getEntity();
      boolean hasShield = this.getMainHandItem().is(Items.SHIELD);
      if (this.isAggressive()) {
         if (attacker instanceof LivingEntity) {
            ItemStack attackerMainHand = ((LivingEntity)attacker).getMainHandItem();
            ItemStack basherMainHand = this.getMainHandItem();
            if ((attackerMainHand.is(net.minecraft.tags.ItemTags.AXES) || attacker instanceof IronGolem || this.blockedCount >= 4) && basherMainHand.is(Items.SHIELD)
               )
             {
               this.playSound((SoundEvent)SoundEvents.SHIELD_BREAK.value(), 1.0F, 1.0F);
               this.setStunTicks(60);
               serverLevel.sendParticles(
                  new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(basherMainHand)),
                  this.getX(),
                  this.getY() + 1.5,
                  this.getZ(),
                  30,
                  0.3,
                  0.2,
                  0.3,
                  0.003
               );
               this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
               return super.hurtServer(serverLevel, damageSource, damageAmount);
            }
         }

         if (damageSource.getDirectEntity() instanceof AbstractArrow && hasShield) {
            this.playSound((SoundEvent)SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
            this.blockedCount++;
            return false;
         }

         if (damageSource.getDirectEntity() instanceof LivingEntity && hasShield) {
            this.playSound((SoundEvent)SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.0F);
            this.blockedCount++;
            return false;
         }
      }

      return super.hurtServer(serverLevel, damageSource, damageAmount);
   }

   public @Nullable SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
   ) {
      spawnGroupData = super.finalizeSpawn(level, difficulty, entitySpawnReason, spawnGroupData);
      ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
      this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
      this.populateDefaultEquipmentEnchantments(level, level.getRandom(), difficulty);
      return spawnGroupData;
   }

   protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficulty) {
      if (this.getCurrentRaid() == null) {
         this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
      }
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSoundEvents.BASHER_AMBIENT_SOUND_EVENT.value();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSoundEvents.BASHER_DEATH_SOUND_EVENT.value();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSoundEvents.BASHER_HURT_SOUND_EVENT.value();
   }

   public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
      ItemStack itemStack = new ItemStack(Items.SHIELD);
      Raid raid = this.getCurrentRaid();
      int enchantmentLevel = wave > raid.getNumGroups(Difficulty.NORMAL) ? 2 : 1;
      if (this.random.nextFloat() <= raid.getEnchantOdds()) {
         Holder<Enchantment> enchantment = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
         itemStack.enchant(enchantment, enchantmentLevel);
      }

      this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
   }
}
