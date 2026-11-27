package com.jaymoskingdoms.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
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
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class Invoker extends SpellcasterIllager {
   private static final EntityDataAccessor<Boolean> DATA_IS_SHIELDED = SynchedEntityData.defineId(Invoker.class, EntityDataSerializers.BOOLEAN);
   private final ServerBossEvent bossBar = new ServerBossEvent(
      Mth.createInsecureUUID(this.random), this.getDisplayName(), BossBarColor.YELLOW, BossBarOverlay.PROGRESS
   );
   public int areaDamageCooldown;
   public int teleportCooldown;
   public boolean isAoeCasting = false;
   public int fangaoecooldown;

   public Invoker(EntityType<? extends Invoker> entityType, Level world) {
      super(entityType, world);
      this.xpReward = 50;
   }

   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new SpellcasterCastingSpellGoal());
      this.goalSelector.addGoal(3, new AvoidEntityGoal(this, Player.class, 8.0F, 0.6, 1.0));
      this.goalSelector.addGoal(5, new Invoker.AreaDamageGoal());
      this.goalSelector.addGoal(4, new Invoker.CastTeleportGoal());
      this.goalSelector.addGoal(5, new Invoker.SummonVexGoal());
      this.goalSelector.addGoal(5, new Invoker.ConjureAoeFangsGoal());
      this.goalSelector.addGoal(6, new Invoker.ConjureFangsGoal());
      this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
      this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}).setAlertOthers(new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false));
   }

   public boolean isPowered() {
      return this.isShielded();
   }

   public boolean doHurtTarget(ServerLevel serverLevel, Entity target) {
      if (!super.doHurtTarget(serverLevel, target)) {
         return false;
      }

      if (target instanceof LivingEntity) {
         ((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), this);
      }

      return true;
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_IS_SHIELDED, false);
   }

   protected void addAdditionalSaveData(ValueOutput valueOutput) {
      super.addAdditionalSaveData(valueOutput);
      valueOutput.putBoolean("Invul", this.isShielded());
   }

   protected void readAdditionalSaveData(ValueInput valueInput) {
      super.readAdditionalSaveData(valueInput);
      this.setShielded(valueInput.getBooleanOr("Invul", false));
      if (this.hasCustomName()) {
         this.bossBar.setName(this.getDisplayName());
      }
   }

   public void setCustomName(@Nullable Component name) {
      super.setCustomName(name);
      this.bossBar.setName(this.getDisplayName());
   }

   public SoundEvent getCelebrateSound() {
      return SoundEvents.EVOKER_CELEBRATE;
   }

   public boolean isShielded() {
      return (Boolean)this.entityData.get(DATA_IS_SHIELDED);
   }

   public void setShielded(boolean isShielded) {
      this.entityData.set(DATA_IS_SHIELDED, isShielded);
   }

   protected void customServerAiStep(ServerLevel serverLevel) {
      this.teleportCooldown--;
      this.areaDamageCooldown--;
      this.fangaoecooldown--;
      super.customServerAiStep(serverLevel);
      this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
      if (this.isAoeCasting && this.isCastingSpell()) {
         this.sendSpellParticles(serverLevel, ParticleTypes.SMOKE, 2, 0.06);
      }

      Vec3 deltaMovement = this.getDeltaMovement();
      if (!this.onGround() && deltaMovement.y < 0.0) {
         this.setDeltaMovement(deltaMovement.multiply(1.0, 0.6, 1.0));
      }

      serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.2, 0.2, 0.2, 0.005);
   }

   private void sendSpellParticles(ServerLevel serverLevel, ParticleOptions particleTypes, int count, double speed) {
      float g = this.yBodyRot * (float) (Math.PI / 180.0) + Mth.cos(this.tickCount * 0.6662F) * 0.25F;
      float h = Mth.cos(g);
      float i = Mth.sin(g);
      serverLevel.sendParticles(particleTypes, this.getX() + h * 0.6, this.getY() + 1.8, this.getZ() + i, count, 0.0, 0.0, 0.0, speed);
      serverLevel.sendParticles(particleTypes, this.getX() - h * 0.6, this.getY() + 1.8, this.getZ() - i, count, 0.0, 0.0, 0.0, speed);
   }

   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   public void startSeenByPlayer(ServerPlayer player) {
      super.startSeenByPlayer(player);
      if (true) {
         this.bossBar.addPlayer(player);
      }
   }

   public void stopSeenByPlayer(ServerPlayer player) {
      super.stopSeenByPlayer(player);
      if (true) {
         this.bossBar.removePlayer(player);
      }
   }

   protected void playStepSound(BlockPos pos, BlockState state) {
   }

   public boolean considersEntityAsAlly(Entity entity) {
      if (entity == this) {
         return true;
      } else if (super.considersEntityAsAlly(entity)) {
         return true;
      } else {
         return entity instanceof Vex vex && vex.getOwner() != null ? this.considersEntityAsAlly(vex.getOwner()) : false;
      }
   }

   public IllagerArmPose getArmPose() {
      if (this.isAggressive()) {
         return IllagerArmPose.ATTACKING;
      } else {
         return this.isCastingSpell() ? IllagerArmPose.SPELLCASTING : IllagerArmPose.CROSSED;
      }
   }

   public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float damageAmount) {
      if (damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
         Entity entity = damageSource.getDirectEntity();
         if (entity != null && entity.is(EntityTypeTags.IMPACT_PROJECTILES) && !this.isShielded() && this.random.nextInt(2) == 0) {
            this.playSound((SoundEvent)SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
            this.setShielded(true);
         }
      } else if (this.isShielded() && this.random.nextInt(3) == 0) {
         serverLevel.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 0.7, 0.5, 0.5);
         this.playSound((SoundEvent)ModSoundEvents.INVOKER_SHIELD_BREAK_SOUND_EVENT.value(), 1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
         this.setShielded(false);
      }

      return super.hurtServer(serverLevel, damageSource, damageAmount);
   }

   public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
      return super.isInvulnerableTo(serverLevel, damageSource)
         || damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)
         || this.isShielded() && damageSource.is(DamageTypeTags.IS_PROJECTILE);
   }

   protected SoundEvent getAmbientSound() {
      return (SoundEvent)ModSoundEvents.INVOKER_AMBIENT_SOUND_EVENT.value();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSoundEvents.INVOKER_DEATH_SOUND_EVENT.value();
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return (SoundEvent)ModSoundEvents.INVOKER_HURT_SOUND_EVENT.value();
   }

   protected SoundEvent getCastingSoundEvent() {
      return (SoundEvent)ModSoundEvents.INVOKER_COMPLETE_CAST_SOUND_EVENT.value();
   }

   public void applyRaidBuffs(ServerLevel level, int wave, boolean unused) {
   }

   public boolean canBeLeader() {
      return false;
   }

   public class AreaDamageGoal extends SpellcasterUseSpellGoal {
      public AreaDamageGoal() {
         super();
      }

      public boolean canUse() {
         if (Invoker.this.getTarget() == null) {
            return false;
         } else if (Invoker.this.areaDamageCooldown < 0) {
            Invoker.this.isAoeCasting = true;
            return true;
         } else {
            return false;
         }
      }

      private void knockBack(Entity entity) {
         double d = entity.getX() - Invoker.this.getX();
         double e = entity.getZ() - Invoker.this.getZ();
         double f = Math.max(d * d + e * e, 0.001);
         entity.push(d / f * 6.0, 0.65, e / f * 6.0);
      }

      protected void knockback(LivingEntity target) {
         this.knockBack(target);
         target.needsSync = true;
      }

      public void stop() {
         Invoker.this.isAoeCasting = false;
         super.stop();
      }

      private void buff(LivingEntity entity) {
         this.knockback(entity);
         entity.hurtServer(getServerLevel(Invoker.this.level()), Invoker.this.damageSources().indirectMagic(Invoker.this, Invoker.this), 11.0F);
         double x = entity.getX();
         double y = entity.getY() + 2.0;
         double z = entity.getZ();
         ((ServerLevel)Invoker.this.level()).sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.2, 0.2, 0.2, 0.015);
      }

      protected void performSpellCasting() {
         Invoker.this.areaDamageCooldown = 300;
         Invoker.this.level()
            .getEntitiesOfClass(
               LivingEntity.class,
               Invoker.this.getBoundingBox().inflate(6.0),
               entity -> !(entity instanceof AbstractIllager)
                  && !(entity instanceof Surrendered)
                  && !(entity instanceof Ravager)
                  && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
            )
            .forEach(this::buff);
         Invoker.this.isAoeCasting = false;
         double posx = Invoker.this.getX();
         double posy = Invoker.this.getY();
         double posz = Invoker.this.getZ();
         ((ServerLevel)Invoker.this.level()).sendParticles(ParticleTypes.LARGE_SMOKE, posx, posy + 1.0, posz, 350, 1.0, 0.8, 1.0, 0.3);
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
         return (SoundEvent)ModSoundEvents.INVOKER_BIG_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.BLINDNESS;
      }
   }

   public class CastTeleportGoal extends SpellcasterUseSpellGoal {
      public CastTeleportGoal() {
         super();
      }

      public boolean canUse() {
         if (Invoker.this.getTarget() == null) {
            return false;
         } else {
            return Invoker.this.isCastingSpell() ? false : Invoker.this.teleportCooldown < 0 && !this.getTargets().isEmpty();
         }
      }

      private List<LivingEntity> getTargets() {
         return Invoker.this.level()
            .getEntitiesOfClass(
               LivingEntity.class,
               Invoker.this.getBoundingBox().inflate(6.0),
               entity -> entity instanceof Player && !((Player)entity).getAbilities().instabuild || entity instanceof IronGolem
            );
      }

      public boolean canContinueToUse() {
         return !this.getTargets().isEmpty();
      }

      public void stop() {
         super.stop();
      }

      public void start() {
         super.start();
         Invoker.this.teleportCooldown = 180;
      }

      protected void performSpellCasting() {
         double x = Invoker.this.getX();
         double y = Invoker.this.getY() + 1.0;
         double z = Invoker.this.getZ();
         if (Invoker.this.level() instanceof ServerLevel) {
            ((ServerLevel)Invoker.this.level()).sendParticles(ParticleTypes.SMOKE, x, y, z, 30, 0.3, 0.5, 0.3, 0.015);
         }

         TeleportUtil.tryRandomTeleport(Invoker.this);
      }

      protected int getCastWarmupTime() {
         return 30;
      }

      protected int getCastingTime() {
         return 30;
      }

      protected int getCastingInterval() {
         return 400;
      }

      protected SoundEvent getSpellPrepareSound() {
         return (SoundEvent)ModSoundEvents.INVOKER_TELEPORT_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.BLINDNESS;
      }
   }

   public class ConjureAoeFangsGoal extends SpellcasterUseSpellGoal {
      public ConjureAoeFangsGoal() {
         super();
      }

      public boolean canUse() {
         if (Invoker.this.getTarget() == null) {
            return false;
         } else if (this.getTargets().isEmpty()) {
            return false;
         } else {
            return Invoker.this.isCastingSpell() ? false : Invoker.this.fangaoecooldown < 0;
         }
      }

      private List<LivingEntity> getTargets() {
         return Invoker.this.level()
            .getEntitiesOfClass(LivingEntity.class, Invoker.this.getBoundingBox().inflate(18.0), entity -> !(entity instanceof Monster));
      }

      private void conjureFangs(double x, double z, double maxY, double y, float yaw, int warmup) {
         ServerLevel serverLevel = getServerLevel(Invoker.this.level());
         BlockPos currentPos = BlockPos.containing(x, y, z);
         int minY = Mth.floor(maxY) - 1;

         while (currentPos.getY() >= minY) {
            BlockPos belowPos = currentPos.below();
            if (serverLevel.getBlockState(belowPos).isFaceSturdy(serverLevel, belowPos, Direction.UP)) {
               double verticalOffset = 0.0;
               if (!serverLevel.isEmptyBlock(currentPos)) {
                  VoxelShape shape = serverLevel.getBlockState(currentPos).getCollisionShape(serverLevel, currentPos);
                  if (!shape.isEmpty()) {
                     verticalOffset = shape.max(Axis.Y);
                  }
               }

               serverLevel.addFreshEntity(new InvokerFangs(serverLevel, x, currentPos.getY() + 0.2 + verticalOffset, z, yaw, warmup, Invoker.this));
               break;
            }

            currentPos = belowPos;
         }
      }

      protected void performSpellCasting() {
         for (LivingEntity livingEntity : this.getTargets()) {
            double d = Math.min(livingEntity.getY(), Invoker.this.getY());
            double e = Math.max(livingEntity.getY(), Invoker.this.getY()) + 1.0;
            float f = (float)Mth.atan2(livingEntity.getZ() - Invoker.this.getZ(), livingEntity.getX() - Invoker.this.getX());

            for (int i = 0; i < 5; i++) {
               float g = f + i * (float) Math.PI * 0.4F;
               this.conjureFangs(livingEntity.getX() + Mth.cos(g) * 1.5, livingEntity.getZ() + Mth.sin(g) * 1.5, d, e, g, 4);
            }
         }

         Invoker.this.fangaoecooldown = 100;
      }

      protected int getCastingTime() {
         return 40;
      }

      protected int getCastingInterval() {
         return 100;
      }

      protected SoundEvent getSpellPrepareSound() {
         return (SoundEvent)ModSoundEvents.INVOKER_FANGS_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.FANGS;
      }
   }

   class ConjureFangsGoal extends SpellcasterUseSpellGoal {
      ConjureFangsGoal() {
         super();
      }

      protected int getCastingTime() {
         return 40;
      }

      protected int getCastingInterval() {
         return 100;
      }

      protected void performSpellCasting() {
         LivingEntity livingEntity = Invoker.this.getTarget();
         double d = Math.min(livingEntity.getY(), Invoker.this.getY());
         double e = Math.max(livingEntity.getY(), Invoker.this.getY()) + 1.0;
         float f = (float)Mth.atan2(livingEntity.getZ() - Invoker.this.getZ(), livingEntity.getX() - Invoker.this.getX());
         if (Invoker.this.distanceToSqr(livingEntity) < 9.0) {
            for (int i = 0; i < 5; i++) {
               float g = f + i * (float) Math.PI * 0.4F;
               this.conjureFangs(Invoker.this.getX() + Mth.cos(g) * 1.5, Invoker.this.getZ() + Mth.sin(g) * 1.5, d, e, g, 0);
            }

            for (int var15 = 0; var15 < 8; var15++) {
               float g = f + var15 * (float) Math.PI * 2.0F / 8.0F + (float) (Math.PI * 2.0 / 5.0);
               this.conjureFangs(Invoker.this.getX() + Mth.cos(g) * 2.5, Invoker.this.getZ() + Mth.sin(g) * 2.5, d, e, g, 3);
            }

            for (int var16 = 0; var16 < 8; var16++) {
               float g = f + var16 * (float) Math.PI * 2.0F / 8.0F + (float) (Math.PI * 2.0 / 5.0);
               this.conjureFangs(Invoker.this.getX() + Mth.cos(g) * 3.5, Invoker.this.getZ() + Mth.sin(g) * 2.5, d, e, g, 3);
            }
         } else {
            for (int i = 0; i < 16; i++) {
               double h = 1.25 * (i + 1);
               this.conjureFangs(Invoker.this.getX() + Mth.cos(f) * h, Invoker.this.getZ() + Mth.sin(f) * h, d, e, f, i);
            }

            for (int i = 0; i < 16; i++) {
               double h = 1.25 * (i + 1);
               this.conjureFangs(Invoker.this.getX() + Mth.cos(f + 0.4F) * h, Invoker.this.getZ() + Mth.sin(f + 0.3F) * h, d, e, f, i);
            }

            for (int i = 0; i < 16; i++) {
               double h = 1.25 * (i + 1);
               this.conjureFangs(Invoker.this.getX() + Mth.cos(f - 0.4F) * h, Invoker.this.getZ() + Mth.sin(f - 0.3F) * h, d, e, f, i);
            }
         }
      }

      private void conjureFangs(double x, double z, double maxY, double y, float yaw, int warmup) {
         ServerLevel serverLevel = getServerLevel(Invoker.this.level());
         BlockPos blockPos = BlockPos.containing(x, y, z);
         int minY = Mth.floor(maxY) - 1;

         while (blockPos.getY() >= minY) {
            BlockPos blockPosBelow = blockPos.below();
            if (serverLevel.getBlockState(blockPosBelow).isFaceSturdy(serverLevel, blockPosBelow, Direction.UP)) {
               double verticalOffset = 0.0;
               if (!serverLevel.isEmptyBlock(blockPos)) {
                  VoxelShape shape = serverLevel.getBlockState(blockPos).getCollisionShape(serverLevel, blockPos);
                  if (!shape.isEmpty()) {
                     verticalOffset = shape.max(Axis.Y);
                  }
               }

               serverLevel.addFreshEntity(new InvokerFangs(serverLevel, x, blockPos.getY() + 0.2 + verticalOffset, z, yaw, warmup, Invoker.this));
               break;
            }

            blockPos = blockPosBelow;
         }
      }

      protected SoundEvent getSpellPrepareSound() {
         return (SoundEvent)ModSoundEvents.INVOKER_FANGS_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.FANGS;
      }
   }

   class SummonVexGoal extends SpellcasterUseSpellGoal {
      private static final TargetingConditions CLOSE_VEX_PREDICATE = TargetingConditions.forNonCombat()
         .range(16.0)
         .ignoreLineOfSight()
         .ignoreInvisibilityTesting();

      SummonVexGoal() {
         super();
      }

      public boolean canUse() {
         return !super.canUse()
            ? false
            : ((ServerLevel)Invoker.this.level())
                  .getNearbyEntities(Surrendered.class, CLOSE_VEX_PREDICATE, Invoker.this, Invoker.this.getBoundingBox().inflate(20.0))
                  .size()
               < 3;
      }

      protected int getCastingTime() {
         return 100;
      }

      protected int getCastingInterval() {
         return 340;
      }

      protected void performSpellCasting() {
         ServerLevel serverLevel = (ServerLevel)Invoker.this.level();

         for (int i = 0; i < 4; i++) {
            BlockPos blockPos = Invoker.this.blockPosition().offset(-2 + Invoker.this.random.nextInt(5), 1, -2 + Invoker.this.random.nextInt(5));
            Surrendered surrendered = (Surrendered)ModEntityTypes.SURRENDERED_ENTITY_TYPE
               .create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
            if (surrendered != null) {
               surrendered.snapTo(blockPos, 0.0F, 0.0F);
               surrendered.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(blockPos), EntitySpawnReason.MOB_SUMMONED, null);
               surrendered.setOwner(Invoker.this);
               surrendered.setBoundOrigin(blockPos);
               surrendered.setLimitedLife(20 * (30 + Invoker.this.random.nextInt(90)));
               serverLevel.addFreshEntityWithPassengers(surrendered);
            }
         }
      }

      protected SoundEvent getSpellPrepareSound() {
         return (SoundEvent)ModSoundEvents.INVOKER_SUMMON_CAST_SOUND_EVENT.value();
      }

      protected IllagerSpell getSpell() {
         return IllagerSpell.SUMMON_VEX;
      }
   }
}
