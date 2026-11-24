package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.DinoConfig;
import com.jaymospowers.dino.DinoEntities;
import com.jaymospowers.dino.DinoItems;
import com.jaymospowers.dino.DinoSounds;
import com.jaymospowers.dino.DinoTags;
import com.jaymospowers.dino.entity.ai.DinoAttackGoal;
import com.jaymospowers.dino.entity.ai.DinoFollowOwnerGoal;
import com.jaymospowers.dino.entity.ai.DinoHuntGoal;
import com.jaymospowers.dino.entity.ai.DinoSitGoal;
import com.jaymospowers.dino.entity.ai.HerdGoal;
import com.jaymospowers.dino.rig.DinoSpec;
import java.util.List;
import java.util.UUID;
import com.jaymospowers.dino.Dino;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Dinosaur extends TamableAnimal implements PlayerRideableJumping {
   public static final int MODE_FOLLOW = 0;
   public static final int MODE_STAY = 1;
   public static final int MODE_WANDER = 2;
   public static final int MAX_LEVEL = 100;
   private static final int KNOCKED_OUT = 1;
   private static final int CHARGING = 2;
   private static final int DISPLAY = 4;
   private static final int RUNNING = 8;
   private static final int LEAPING = 16;
   private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> XP = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> TORPOR = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> TAME_PROGRESS = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> EFFECTIVENESS = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Long> SPECIAL_READY = SynchedEntityData.defineId(Dinosaur.class, EntityDataSerializers.LONG);
   private Species species;
   private int actionTick;
   private boolean actionHitDone;
   private int attackCooldown;
   private int feedCooldown;
   private int eggTime = 6000;
   private long satedUntil;
   private int chargeTicks;
   private int leapTicks;
   private int displayTicks;
   private float damageWhileOut;
   private float torpor;
   private float playerJumpPendingScale;
   private int riderAttackQueued;
   private int roarCooldown;
   private @Nullable UUID tamer;
   public float sleepAmt;
   public float sleepAmtO;
   public float sitAmt;
   public float sitAmtO;
   public float displayAmt;
   public float displayAmtO;
   public float chargeAmt;
   public float chargeAmtO;
   public float airAmt;
   public float airAmtO;
   public float runAmt;
   public float runAmtO;
   public float hurtAmt;
   public float hurtAmtO;
   public float turnAmt;
   public float turnAmtO;
   public float walkPhase;
   public float walkPhaseO;
   public float walkAmt;
   public float walkAmtO;
   public int actionStartTick;
   private int lastActionValue;

   public Dinosaur(EntityType<? extends Dinosaur> type, Level level) {
      super(type, level);
      this.species = DinoEntities.speciesOf(type);
      this.xpReward = this.species.xp;
      this.setTame(false, false);
      this.setPathfindingMalus(PathType.LEAVES, this.species.breaksLeaves() ? 0.0F : -1.0F);
      this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
      this.setPathfindingMalus(PathType.DAMAGING, -1.0F);
      this.walkPhase = this.walkPhaseO = this.random.nextFloat();
   }

   public Species species() {
      if (this.species == null) {
         this.species = DinoEntities.speciesOf(this.getType());
      }

      return this.species;
   }

   public static Builder createAttributes(Species s) {
      return Animal.createAnimalAttributes()
         .add(Attributes.MAX_HEALTH, s.health)
         .add(Attributes.MOVEMENT_SPEED, s.speed)
         .add(Attributes.ATTACK_DAMAGE, s.damage)
         .add(Attributes.ATTACK_KNOCKBACK, s.big() ? 1.4 : 0.2)
         .add(Attributes.ARMOR, s.armor)
         .add(Attributes.KNOCKBACK_RESISTANCE, s.knockbackResistance)
         .add(Attributes.FOLLOW_RANGE, s.big() ? 40.0 : 28.0)
         .add(Attributes.STEP_HEIGHT, s.stepHeight)
         .add(Attributes.CAMERA_DISTANCE, s.cameraDistance())
         .add(Attributes.JUMP_STRENGTH, s.jumps() ? 0.75 : 0.42)
         .add(Attributes.SAFE_FALL_DISTANCE, s.big() ? 6.0 : 5.0)
         .add(Attributes.WATER_MOVEMENT_EFFICIENCY, s == Species.SPINOSAURUS ? 0.9 : 0.0);
   }

   protected void registerGoals() {
      Species s = this.species();
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new PanicGoal(this, s.runBoost) {
         protected boolean shouldPanic() {
            if (!Dinosaur.this.isTame() && !Dinosaur.this.isVehicle()) {
               return Dinosaur.this.fleesWhenHurt() ? super.shouldPanic() : this.mob.isOnFire() || this.mob.isFreezing();
            } else {
               return this.mob.isOnFire() || this.mob.isFreezing();
            }
         }
      });
      this.goalSelector.addGoal(2, new DinoSitGoal(this));
      this.goalSelector.addGoal(3, new DinoAttackGoal(this));
      this.goalSelector.addGoal(4, new DinoFollowOwnerGoal(this));
      if (!s.big() || !s.fightsBack()) {
         this.goalSelector
            .addGoal(
               5,
               new AvoidEntityGoal(
                  this,
                  Dinosaur.class,
                  14.0F,
                  1.0,
                  s.runBoost,
                  e -> e instanceof Dinosaur d && !d.isTame() && !this.isTame() && s.fears(d.species()) && !d.isKnockedOut()
               )
            );
      }

      if (s == Species.GALLIMIMUS) {
         this.goalSelector
            .addGoal(
               5,
               new AvoidEntityGoal(
                  this,
                  Player.class,
                  7.0F,
                  1.0,
                  s.runBoost,
                  e -> !this.isTame() && e instanceof Player p && !p.isShiftKeyDown() && !p.isCreative() && !p.isSpectator()
               )
            );
      }

      this.goalSelector.addGoal(6, new BreedGoal(this, 1.0));
      this.goalSelector.addGoal(7, new FollowParentGoal(this, 1.15));
      this.goalSelector.addGoal(8, new HerdGoal(this));
      this.goalSelector
         .addGoal(
            9,
            new WaterAvoidingRandomStrollGoal(this, 1.0) {
               public boolean canUse() {
                  return (
                        !Dinosaur.this.isTame()
                           || Dinosaur.this.getMode() != 0
                           || Dinosaur.this.getOwner() == null
                           || !(Dinosaur.this.distanceToSqr(Dinosaur.this.getOwner()) > 100.0)
                     )
                     && super.canUse();
               }
            }
         );
      this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, s.big() ? 16.0F : 8.0F));
      this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
      this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
         public boolean canUse() {
            return (Dinosaur.this.isTame() || !Dinosaur.this.fleesWhenHurt()) && super.canUse();
         }
      });
      this.targetSelector.addGoal(4, new DinoHuntGoal(this));
   }

   public boolean fleesWhenHurt() {
      return this.isBaby() || !this.species().fightsBack();
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder data) {
      super.defineSynchedData(data);
      data.define(LEVEL, 1);
      data.define(XP, 0);
      data.define(TORPOR, 0.0F);
      data.define(TAME_PROGRESS, 0.0F);
      data.define(EFFECTIVENESS, 1.0F);
      data.define(STATE, (byte)0);
      data.define(MODE, (byte)0);
      data.define(ACTION, 0);
      data.define(SPECIAL_READY, 0L);
   }

   protected void addAdditionalSaveData(ValueOutput out) {
      super.addAdditionalSaveData(out);
      out.putInt("DinoLevel", this.getDinoLevel());
      out.putInt("DinoXp", this.getXp());
      out.putFloat("Torpor", this.torpor);
      out.putBoolean("KnockedOut", this.isKnockedOut());
      out.putFloat("TameProgress", this.getTameProgress());
      out.putFloat("Effectiveness", this.getEffectiveness());
      out.putFloat("DamageWhileOut", this.damageWhileOut);
      out.putByte("Mode", (byte)this.getMode());
      out.putLong("SatedFor", Math.max(0L, this.satedUntil - this.level().getGameTime()));
      out.putInt("EggTime", this.eggTime);
      out.putInt("SpecialCooldown", this.specialCooldownLeft());
      if (this.tamer != null) {
         out.putString("Tamer", this.tamer.toString());
      }
   }

   protected void readAdditionalSaveData(ValueInput in) {
      super.readAdditionalSaveData(in);
      if (this.species().rideable() && this.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE)) {
         this.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(DinoItems.saddle(this.species())));
      }

      float health = this.getHealth();
      this.setDinoLevel(in.getIntOr("DinoLevel", 1), false);
      this.setHealth(Math.min(health, this.getMaxHealth()));
      this.entityData.set(XP, in.getIntOr("DinoXp", 0));
      this.setTorpor(in.getFloatOr("Torpor", 0.0F));
      this.setFlag(1, in.getBooleanOr("KnockedOut", false));
      this.entityData.set(TAME_PROGRESS, in.getFloatOr("TameProgress", 0.0F));
      this.entityData.set(EFFECTIVENESS, in.getFloatOr("Effectiveness", 1.0F));
      this.damageWhileOut = in.getFloatOr("DamageWhileOut", 0.0F);
      this.setMode(in.getByteOr("Mode", (byte)0));
      this.satedUntil = this.level().getGameTime() + Math.min(in.getLongOr("SatedFor", 0L), 2L * Math.max(20, DinoConfig.HUNT_REST_TICKS));
      this.eggTime = in.getIntOr("EggTime", 6000);
      this.entityData.set(SPECIAL_READY, this.level().getGameTime() + in.getIntOr("SpecialCooldown", 0));
      String savedTamer = in.getStringOr("Tamer", "");
      this.tamer = savedTamer.isEmpty() ? null : UUID.fromString(savedTamer);
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (STATE.equals(key) || MODE.equals(key) || DATA_FLAGS_ID.equals(key)) {
         this.refreshDimensions();
      }
   }

   private boolean flag(int bit) {
      return ((Byte)this.entityData.get(STATE) & bit) != 0;
   }

   private void setFlag(int bit, boolean on) {
      byte cur = (Byte)this.entityData.get(STATE);
      byte next = (byte)(on ? cur | bit : cur & ~bit);
      if (next != cur) {
         this.entityData.set(STATE, next);
      }
   }

   public boolean isKnockedOut() {
      return this.flag(1);
   }

   public boolean isCharging() {
      return this.flag(2);
   }

   public boolean isDisplaying() {
      return this.flag(4);
   }

   public boolean isRunning() {
      return this.flag(8);
   }

   public void setRunning(boolean on) {
      this.setFlag(8, on);
   }

   public boolean isLeaping() {
      return this.flag(16);
   }

   public int getDinoLevel() {
      return (Integer)this.entityData.get(LEVEL);
   }

   public int getXp() {
      return (Integer)this.entityData.get(XP);
   }

   public float getTorpor() {
      return (Float)this.entityData.get(TORPOR);
   }

   public float getTameProgress() {
      return (Float)this.entityData.get(TAME_PROGRESS);
   }

   public float getEffectiveness() {
      return (Float)this.entityData.get(EFFECTIVENESS);
   }

   public int getMode() {
      return (Byte)this.entityData.get(MODE);
   }

   public int getAction() {
      return (Integer)this.entityData.get(ACTION) & 0xFF;
   }

   public int getActionValue() {
      return (Integer)this.entityData.get(ACTION);
   }

   public int specialCooldownLeft() {
      return (int)Math.max(0L, (Long)this.entityData.get(SPECIAL_READY) - this.level().getGameTime());
   }

   public float maxTorpor() {
      return this.species().torpor * (1.0F + 0.05F * (this.getDinoLevel() - 1));
   }

   public float tameNeed() {
      return (float)(this.species().tameNeed * (1.0F + 0.04F * (this.getDinoLevel() - 1)) / Math.max(0.1, DinoConfig.TAMING_SPEED));
   }

   public int xpForNextLevel() {
      return 20 + 12 * this.getDinoLevel();
   }

   public void setDinoLevel(int level, boolean keepRatio) {
      level = Mth.clamp(level, 1, 100);
      float ratio = this.getMaxHealth() > 0.0F ? this.getHealth() / this.getMaxHealth() : 1.0F;
      this.entityData.set(LEVEL, level);
      Species s = this.species();
      AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
      if (hp != null) {
         hp.setBaseValue(s.health * (1.0 + 0.05 * (level - 1)));
      }

      AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
      if (dmg != null) {
         dmg.setBaseValue(s.damage * (1.0 + 0.03 * (level - 1)) * DinoConfig.DAMAGE_MULTIPLIER);
      }

      if (keepRatio) {
         this.setHealth(Math.max(1.0F, this.getMaxHealth() * ratio));
      }
   }

   private void gainXp(int amount) {
      if (this.isTame() && this.getDinoLevel() < 100) {
         int xp = this.getXp() + amount;

         boolean leveled;
         for (leveled = false; xp >= this.xpForNextLevel() && this.getDinoLevel() < 100; leveled = true) {
            xp -= this.xpForNextLevel();
            this.setDinoLevel(this.getDinoLevel() + 1, true);
            this.heal(this.getMaxHealth() * 0.15F);
         }

         this.entityData.set(XP, xp);
         if (leveled) {
            this.playSound((SoundEvent)DinoSounds.LEVEL_UP, 1.0F, 1.0F);
            if (this.level() instanceof ServerLevel sl) {
               sl.sendParticles(
                  ParticleTypes.HAPPY_VILLAGER,
                  this.getX(),
                  this.getY() + this.getBbHeight() * 0.6,
                  this.getZ(),
                  24,
                  this.getBbWidth() * 0.5,
                  this.getBbHeight() * 0.4,
                  this.getBbWidth() * 0.5,
                  0.0
               );
            }

            if (this.getOwner() instanceof ServerPlayer owner) {
               owner.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.level_up", new Object[]{this.getName(), this.getDinoLevel()}));
            }
         }
      }
   }

   public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
      boolean result = super.killedEntity(level, victim, source);
      this.gainXp(Math.max(2, (int)(victim.getMaxHealth() * 0.6F)));
      this.satedUntil = level.getGameTime() + DinoConfig.HUNT_REST_TICKS + this.random.nextInt(DinoConfig.HUNT_REST_TICKS + 1);
      return result;
   }

   public boolean isHungry() {
      return this.level().getGameTime() >= this.satedUntil;
   }

   public void makeHungry() {
      this.satedUntil = 0L;
   }

   public @Nullable SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData
   ) {
      RandomSource r = level.getRandom();
      int max = Math.max(1, DinoConfig.MAX_WILD_LEVEL);
      int lvl = 1 + (int)Math.floor(max * Math.pow(r.nextDouble(), 2.2));
      this.setDinoLevel(Math.min(max, lvl), false);
      this.setHealth(this.getMaxHealth());
      this.satedUntil = level.getLevel().getGameTime() + r.nextInt(100);
      this.eggTime = 3000 + r.nextInt(3000);
      return super.finalizeSpawn(level, difficulty, reason, groupData);
   }

   /** Light a natural spawn needs (raw light, so day or night under open sky alike). */
   private static final int SPAWN_LIGHT = 5;

   /**
    * Natural spawns (the world's own, and a chunk's first generation) happen only in the Verdant World, on firm dry ground in
    * some light; eggs, commands, spawners and breeding go anywhere.
    */
   public static boolean checkSpawnRules(EntityType<? extends Dinosaur> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
      if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
         if (!DinoConfig.NATURAL_SPAWNS || level.getLevel().dimension() != Dino.VERDANT_WORLD) {
            return false;
         }
         BlockPos below = pos.below();
         BlockState ground = level.getBlockState(below);
         // (a chunk being generated has no light yet, as with vanilla's animals)
         return ground.isFaceSturdy(level, below, Direction.UP) && ground.getFluidState().isEmpty() && !ground.is(BlockTags.LEAVES)
            && (reason == EntitySpawnReason.CHUNK_GENERATION || level.getRawBrightness(pos, 0) >= SPAWN_LIGHT);
      }
      return true;
   }

   public int getMaxSpawnClusterSize() {
      return this.species().packMax();
   }

   public float getAgeScale() {
      return this.isBaby() ? 0.4F : 1.0F;
   }

   protected EntityDimensions getDefaultDimensions(Pose pose) {
      EntityDimensions d = super.getDefaultDimensions(pose);
      if (this.isKnockedOut() || this.isDeadOrDying()) {
         return d.scale(1.0F, 0.5F);
      } else {
         return this.isInSittingPose() ? d.scale(1.0F, 0.7F) : d;
      }
   }

   protected boolean isImmobile() {
      return super.isImmobile() || this.isKnockedOut();
   }

   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         this.tickClientAnimation();
      }
   }

   public void aiStep() {
      super.aiStep();
      if (this.level() instanceof ServerLevel server) {
         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.feedCooldown > 0) {
            this.feedCooldown--;
         }

         if (this.species() == Species.DODO && !this.isBaby() && !this.isKnockedOut() && this.isAlive() && --this.eggTime <= 0) {
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 0.8F);
            this.spawnAtLocation(server, new ItemStack(Items.EGG));
            this.eggTime = 4000 + this.random.nextInt(4000);
         }

         this.tickTorpor();
         this.tickAction(server);
         this.tickCharge(server);
         this.tickLeap(server);
         if (this.displayTicks > 0 && --this.displayTicks == 0) {
            this.setFlag(4, false);
         }

         if (this.riderAttackQueued > 0) {
            this.riderAttackQueued--;
            if (this.tryStartAttack()) {
               this.riderAttackQueued = 0;
            }
         }

         if (this.isTame() && this.isAlive() && this.tickCount % 100 == 0 && this.getHealth() < this.getMaxHealth() && this.getTarget() == null) {
            this.heal(Math.max(1.0F, this.getMaxHealth() * 0.01F));
         }

         boolean pushing = this.getKnownMovement().horizontalDistanceSqr() > 4.0E-4 || this.horizontalCollision;
         if (this.species().breaksLeaves()
            && this.isAlive()
            && !this.isKnockedOut()
            && this.tickCount % 4 == 0
            && pushing
            && !this.isBaby()
            && server.getGameRules().get(GameRules.MOB_GRIEFING)) {
            Vec3 ahead = Attacks.facing(this).scale(0.8);
            AABB box = this.getBoundingBox().expandTowards(ahead.x, 0.0, ahead.z).inflate(0.2, 0.0, 0.2);

            for (BlockPos pos : BlockPos.betweenClosed(
               Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ)
            )) {
               BlockState state = server.getBlockState(pos);
               if (state.getBlock() instanceof LeavesBlock
                  && !(Boolean)state.getValue(LeavesBlock.PERSISTENT)) {
                  server.destroyBlock(pos, true, this);
               }
            }
         }

         if (this.roarCooldown > 0) {
            this.roarCooldown--;
         }
      }
   }

   private void tickTorpor() {
      if (!this.isTame() && this.isAlive() && (!(this.torpor <= 0.0F) || this.isKnockedOut())) {
         float max = this.maxTorpor();
         if (this.isKnockedOut()) {
            this.torpor -= max / 3600.0F;
            if (this.torpor <= 0.0F) {
               this.wakeUp();
               return;
            }
         } else {
            this.torpor = Math.max(0.0F, this.torpor - max / 1800.0F);
         }

         if (this.tickCount % 10 == 0 || this.torpor <= 0.0F) {
            this.entityData.set(TORPOR, this.torpor);
         }
      }
   }

   private void setTorpor(float value) {
      this.torpor = value;
      this.entityData.set(TORPOR, value);
   }

   private void tickClientAnimation() {
      this.sleepAmtO = this.sleepAmt;
      this.sitAmtO = this.sitAmt;
      this.displayAmtO = this.displayAmt;
      this.chargeAmtO = this.chargeAmt;
      this.airAmtO = this.airAmt;
      this.runAmtO = this.runAmt;
      this.hurtAmtO = this.hurtAmt;
      this.turnAmtO = this.turnAmt;
      this.walkPhaseO = this.walkPhase;
      this.walkAmtO = this.walkAmt;
      this.sleepAmt = approach(this.sleepAmt, this.isKnockedOut() ? 1.0F : 0.0F, 0.07F);
      this.sitAmt = approach(this.sitAmt, this.isInSittingPose() && !this.isKnockedOut() ? 1.0F : 0.0F, 0.08F);
      this.displayAmt = approach(this.displayAmt, this.isDisplaying() ? 1.0F : 0.0F, 0.2F);
      this.chargeAmt = approach(this.chargeAmt, this.isCharging() ? 1.0F : 0.0F, 0.15F);
      this.airAmt = approach(this.airAmt, this.isLeaping() && !this.onGround() ? 1.0F : 0.0F, 0.25F);
      double dx = this.getX() - this.xo;
      double dz = this.getZ() - this.zo;
      float moved = (float)Math.sqrt(dx * dx + dz * dz);
      float stride = this.species().spec().stride * this.getAgeScale();
      this.walkPhase = this.walkPhase + moved / Math.max(0.2F, stride);
      float perSecond = moved * 20.0F / this.getAgeScale();
      float walkSpeed = (float)this.species().walkBps;
      float runSpeed = (float)this.species().runBps;
      this.walkAmt = approach(this.walkAmt, Mth.clamp(perSecond / Math.max(0.3F, walkSpeed * 0.6F), 0.0F, 1.0F), 0.15F);
      this.runAmt = approach(this.runAmt, Mth.clamp((perSecond - walkSpeed * 1.25F) / Math.max(0.5F, runSpeed * 0.85F - walkSpeed * 1.25F), 0.0F, 1.0F), 0.12F);
      this.hurtAmt = this.hurtTime > 0 ? Math.max(this.hurtAmt, (float)this.hurtTime / Math.max(1, this.hurtDuration)) : approach(this.hurtAmt, 0.0F, 0.2F);
      this.turnAmt = Mth.lerp(0.3F, this.turnAmt, Mth.clamp(Mth.wrapDegrees(this.yBodyRotO - this.yBodyRot) / 8.0F, -1.0F, 1.0F));
      int action = this.getActionValue();
      if (action != this.lastActionValue) {
         this.lastActionValue = action;
         this.actionStartTick = this.tickCount;
      }
   }

   private static float approach(float cur, float target, float step) {
      return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
   }

   private static int ticks(float seconds) {
      return Math.max(1, Math.round(seconds * 20.0F));
   }

   public boolean isBusy() {
      return this.getAction() != 0 || this.isKnockedOut() || !this.isAlive();
   }

   public boolean startAction(int action) {
      if (!this.level().isClientSide() && !this.isKnockedOut() && this.isAlive() && this.getAction() == 0) {
         int counter = ((Integer)this.entityData.get(ACTION) >>> 8) + 1;
         this.entityData.set(ACTION, counter << 8 | action);
         this.actionTick = 0;
         this.actionHitDone = false;
         if (action == 2 || action == 3 && this.species().spec().specialStyle == 0) {
            this.playSound(DinoSounds.roar(this.species()), this.getSoundVolume() * 1.6F, this.getVoicePitch());
         }

         return true;
      } else {
         return false;
      }
   }

   private void endAction() {
      int counter = (Integer)this.entityData.get(ACTION) >>> 8;
      this.entityData.set(ACTION, counter << 8);
      this.actionTick = 0;
   }

   private void tickAction(ServerLevel level) {
      int action = this.getAction();
      if (action != 0) {
         if (!this.isKnockedOut() && this.isAlive()) {
            DinoSpec spec = this.species().spec();
            this.actionTick++;

            float length = switch (action) {
               case 1 -> spec.biteTime;
               case 2 -> spec.roarTime;
               case 3 -> spec.specialTime;
               default -> spec.eatTime;
            };
            float hitAt = action == 1 ? spec.biteHit : (action == 3 ? spec.specialHit : -1.0F);
            if (!this.actionHitDone && hitAt >= 0.0F && this.actionTick >= ticks(hitAt)) {
               this.actionHitDone = true;
               if (action == 1) {
                  Attacks.landAttack(this, level);
               } else {
                  Attacks.landSpecial(this, level);
               }
            }

            if (this.actionTick >= ticks(length)) {
               this.endAction();
            }
         } else {
            this.endAction();
         }
      }
   }

   public boolean tryStartAttack() {
      if (this.attackCooldown > 0 || this.isBusy()) {
         return false;
      }

      if (!this.startAction(1)) {
         return false;
      }

      this.attackCooldown = this.species().attackCooldown();
      return true;
   }

   public void riderAttack() {
      if (!this.tryStartAttack()) {
         this.riderAttackQueued = 8;
      }
   }

   public void riderRoar() {
      if (this.roarCooldown <= 0 && !this.isBusy() && this.startAction(2)) {
         this.roarCooldown = 60;
      }
   }

   public boolean trySpecial() {
      if (!this.level().isClientSide() && !this.isBusy() && this.specialCooldownLeft() <= 0 && !this.isBaby()) {
         boolean started = switch (this.species()) {
            case VELOCIRAPTOR -> Attacks.startPounce(this);
            case TRICERATOPS, CARNOTAURUS -> Attacks.startCharge(this);
            case DODO -> false;
            case GALLIMIMUS -> Attacks.startSprint(this);
            default -> this.startAction(3);
         };
         if (started) {
            this.entityData.set(SPECIAL_READY, this.level().getGameTime() + this.species().specialCooldown());
         }

         return started;
      } else {
         return false;
      }
   }

   public void setCharging(int ticks) {
      this.chargeTicks = ticks;
      this.setFlag(2, ticks > 0);
   }

   public void setLeaping(int ticks) {
      this.leapTicks = ticks;
      this.setFlag(16, ticks > 0);
   }

   public void display(int ticks) {
      if (!this.isDisplaying() && this.species() == Species.DILOPHOSAURUS) {
         this.playSound((SoundEvent)DinoSounds.FRILL, 1.0F, this.getVoicePitch());
      }

      this.displayTicks = Math.max(this.displayTicks, ticks);
      this.setFlag(4, true);
   }

   private void tickCharge(ServerLevel level) {
      if (this.chargeTicks > 0) {
         this.chargeTicks--;
         if (this.chargeTicks != 0 && !this.isKnockedOut() && this.isAlive()) {
            Attacks.chargeHits(this, level);
         } else {
            Attacks.endCharge(this);
         }
      }
   }

   private void tickLeap(ServerLevel level) {
      if (this.leapTicks > 0) {
         this.leapTicks--;
         boolean landed = this.onGround() && this.leapTicks < 16;
         if (this.leapTicks != 0 && !landed && !this.isKnockedOut() && this.isAlive() && !this.isInWater()) {
            Attacks.leapHits(this, level);
         } else {
            this.setLeaping(0);
         }
      }
   }

   public boolean doHurtTarget(ServerLevel level, Entity target) {
      boolean hit = super.doHurtTarget(level, target);
      if (hit) {
         this.playSound(
            this.species().big() ? (SoundEvent)DinoSounds.BITE_BIG : (SoundEvent)DinoSounds.BITE, this.getSoundVolume(), this.getVoicePitch()
         );
      }

      return hit;
   }

   public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
      if (this.isInvulnerableTo(level, source)) {
         return false;
      }

      Entity attacker = source.getEntity();
      if (attacker != null
         && (attacker == this.getControllingPassenger() || this.hasPassenger(attacker))
         && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return false;
      }

      if (source.is(DamageTypeTags.IS_FALL) && this.species().big()) {
         damage *= 0.5F;
      }

      if (source.getDirectEntity() instanceof TranqDart) {
         damage = Math.min(damage, 1.0F);
      }

      float before = this.getHealth();
      boolean hurt = super.hurtServer(level, source, damage);
      if (hurt) {
         if (!this.isTame() && !this.isKnockedOut() && !this.fleesWhenHurt() && attacker instanceof LivingEntity l && l.isAlive()) {
            this.alertPack(l);
         }

         if (this.isKnockedOut() && !this.isTame() && !(source.getDirectEntity() instanceof TranqDart)) {
            this.damageWhileOut = this.damageWhileOut + Math.max(0.0F, before - this.getHealth());
            this.entityData.set(EFFECTIVENESS, Mth.clamp(1.0F - 2.0F * this.damageWhileOut / this.getMaxHealth(), 0.0F, 1.0F));
         }
      }

      return hurt;
   }

   private void alertPack(LivingEntity attacker) {
      if (!(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
         double range = this.species().big() ? 24.0 : 16.0;

         for (Dinosaur other : this.level()
            .getEntitiesOfClass(
               Dinosaur.class,
               this.getBoundingBox().inflate(range, 8.0, range),
               d -> d != this && d.species() == this.species() && !d.isTame() && !d.isKnockedOut() && !d.isBaby() && d.getTarget() == null
            )) {
            if (other.canAttack(attacker)) {
               other.setTarget(attacker);
            }
         }
      }
   }

   public boolean addTorpor(float amount, @Nullable Entity source) {
      if (!this.level().isClientSide() && !this.isTame() && this.isAlive() && !(amount <= 0.0F)) {
         float max = this.maxTorpor();
         this.setTorpor(Math.min(max, this.torpor + amount));
         if (!this.isKnockedOut() && this.torpor >= max) {
            this.knockOut();
            if (source instanceof ServerPlayer player) {
               ModTriggers.award(player, "knock_out");
               player.sendOverlayMessage(this.feedMessage());
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private Component feedMessage() {
      return Component.translatable(
         this.species().eatsMeat()
            ? (this.species().eatsPlants() ? "message.jaymospowers.dino.feed_any" : "message.jaymospowers.dino.feed_meat")
            : "message.jaymospowers.dino.feed_plants"
      );
   }

   public void knockOut() {
      if (!this.isKnockedOut() && !this.isTame()) {
         this.setFlag(1, true);
         this.setTorpor(this.maxTorpor());
         this.entityData.set(TAME_PROGRESS, 0.0F);
         this.entityData.set(EFFECTIVENESS, 1.0F);
         this.damageWhileOut = 0.0F;
         this.endAction();
         this.setCharging(0);
         this.setLeaping(0);
         this.setFlag(4, false);
         this.setFlag(8, false);
         Attacks.endCharge(this);
         this.getNavigation().stop();
         this.setTarget(null);
         this.setLastHurtByMob(null);
         this.ejectPassengers();
         this.playSound((SoundEvent)DinoSounds.KNOCKED_OUT, this.getSoundVolume(), this.getVoicePitch());
      }
   }

   public void wakeForScene() {
      this.tamer = null;
      this.setFlag(1, false);
      this.setTorpor(0.0F);
      this.entityData.set(TAME_PROGRESS, 0.0F);
      this.damageWhileOut = 0.0F;
   }

   private void wakeUp() {
      this.tamer = null;
      this.setFlag(1, false);
      this.setTorpor(0.0F);
      this.entityData.set(TAME_PROGRESS, 0.0F);
      this.damageWhileOut = 0.0F;
      if (!this.isTame() && !this.fleesWhenHurt() && this.level() instanceof ServerLevel level) {
         Player near = level.getNearestPlayer(this, 12.0);
         if (near != null && !near.isCreative() && !near.isSpectator()) {
            this.setTarget(near);
         }

         this.startAction(2);
      }
   }

   public float foodValue(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0F;
      }

      float value = 0.0F;
      if (this.species().eatsMeat() && stack.is(DinoTags.CARNIVORE_FOOD)) {
         value = Math.max(value, Foods.meatValue(stack));
      }

      if (this.species().eatsPlants() && stack.is(DinoTags.HERBIVORE_FOOD)) {
         value = Math.max(value, Foods.plantValue(stack));
      }

      return value;
   }

   public boolean isFood(ItemStack stack) {
      return this.foodValue(stack) > 0.0F;
   }

   private void eatEffects(ItemStack stack) {
      this.playSound((SoundEvent)DinoSounds.EAT, this.getSoundVolume() * 0.8F, this.getVoicePitch());
      if (this.level() instanceof ServerLevel sl) {
         Vec3 at = this.mouthPosition();
         sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), at.x, at.y, at.z, 8, 0.15, 0.1, 0.15, 0.05);
      }
   }

   public Vec3 mouthPosition() {
      float[] m = this.species().mouth();
      float scale = this.getAgeScale();
      double y = this.isKnockedOut() ? this.species().spec().lieHeight * scale : m[1] * scale;
      return this.position().add(new Vec3(m[0] * scale, y, m[2] * scale).yRot(-this.yBodyRot * (float) (Math.PI / 180.0)));
   }

   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      boolean client = this.level().isClientSide();
      if (!this.isTame()) {
         if (!this.isKnockedOut()) {
            return InteractionResult.PASS;
         }

         if (stack.is((Item)DinoItems.NARCOTIC)) {
            if (!client) {
               this.addTorpor(DinoConfig.NARCOTIC_TORPOR, player);
               stack.consume(1, player);
               this.eatEffects(new ItemStack((ItemLike)DinoItems.NARCOTIC));
            }

            return InteractionResult.SUCCESS;
         } else {
            float value = this.foodValue(stack);
            if (value > 0.0F) {
               if (client) {
                  return InteractionResult.SUCCESS;
               }

               if (this.feedCooldown > 0) {
                  return InteractionResult.CONSUME;
               }

               if (this.tamer != null && !this.tamer.equals(player.getUUID()) && this.level().getPlayerByUUID(this.tamer) != null) {
                  if (player instanceof ServerPlayer sp) {
                     sp.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.not_your_tame"));
                  }

                  return InteractionResult.FAIL;
               } else {
                  this.tamer = player.getUUID();
                  this.feedCooldown = 24;
                  ItemStack eaten = stack.copyWithCount(1);
                  stack.consume(1, player);
                  this.eatEffects(eaten);
                  float progress = this.getTameProgress() + value / this.tameNeed();
                  if (progress >= 1.0F) {
                     this.finishTaming(player);
                  } else {
                     this.entityData.set(TAME_PROGRESS, progress);
                  }

                  return InteractionResult.SUCCESS_SERVER;
               }
            } else {
               if (!client && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                  sp.sendOverlayMessage(this.feedMessage());
               }

               return InteractionResult.PASS;
            }
         }
      } else {
         if (!this.isOwnedBy(player)) {
            return InteractionResult.PASS;
         }

         float value = this.foodValue(stack);
         if (value > 0.0F && this.getHealth() < this.getMaxHealth()) {
            if (!client) {
               ItemStack eaten = stack.copyWithCount(1);
               stack.consume(1, player);
               this.heal(Math.max(4.0F, value * 4.0F));
               this.eatEffects(eaten);
               if (!this.isInSittingPose()) {
                  this.startAction(4);
               }
            }

            return InteractionResult.SUCCESS;
         } else {
            if (value > 0.0F) {
               InteractionResult bred = super.mobInteract(player, hand);
               if (bred.consumesAction()) {
                  return bred;
               }
            }

            if (!stack.is(Items.SADDLE) && !DinoItems.isDinoSaddle(stack)) {
               if (stack.is(Items.SHEARS) || stack.is(Items.LEAD) || stack.is(Items.NAME_TAG)) {
                  return InteractionResult.PASS;
               }

               if (player.isSecondaryUseActive()) {
                  if (!client) {
                     int next = (this.getMode() + 1) % 3;
                     this.setMode(next);
                     if (player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.mode." + next, new Object[]{this.getName()}));
                     }
                  }

                  return InteractionResult.SUCCESS;
               } else {
                  if (this.isBaby() || !this.species().rideable()) {
                     return InteractionResult.PASS;
                  }

                  if (!this.isSaddled()) {
                     if (!client && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.needs_saddle", new Object[]{this.getName(), this.saddleName()}));
                     }

                     return InteractionResult.PASS;
                  } else {
                     if (player.getVehicle() == this) {
                        return InteractionResult.PASS;
                     }

                     if (!client) {
                        if (this.isInSittingPose()) {
                           this.setInSittingPose(false);
                        }

                        player.setYRot(this.getYRot());
                        player.setXRot(this.getXRot());
                        if (player.startRiding(this) && player instanceof ServerPlayer sp) {
                           ModTriggers.award(sp, "ride_dinosaur");
                        }
                     }

                     return InteractionResult.SUCCESS;
                  }
               }
            } else {
               boolean fits = this.species().rideable() && stack.is(DinoItems.saddle(this.species()));
               if (fits) {
                  return stack.interactLivingEntity(player, this, hand);
               }

               if (!client && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
                  sp.sendOverlayMessage(
                     this.species().rideable()
                        ? Component.translatable("message.jaymospowers.dino.wrong_saddle", new Object[]{this.getName(), this.saddleName()})
                        : Component.translatable("message.jaymospowers.dino.no_saddle_fits", new Object[]{this.getName()})
                  );
               }

               return InteractionResult.PASS;
            }
         }
      }
   }

   private void finishTaming(Player player) {
      this.tamer = null;
      int bonus = Math.round(this.getDinoLevel() * 0.5F * this.getEffectiveness());
      this.setFlag(1, false);
      this.setTorpor(0.0F);
      this.entityData.set(TAME_PROGRESS, 0.0F);
      this.tame(player);
      this.setDinoLevel(this.getDinoLevel() + bonus, false);
      this.setHealth(this.getMaxHealth());
      this.setMode(0);
      this.setTarget(null);
      this.setLastHurtByMob(null);
      this.getNavigation().stop();
      this.setPersistenceRequired();
      this.level().broadcastEntityEvent(this, (byte)7);
      this.playSound((SoundEvent)DinoSounds.TAMED, 1.2F, 1.0F);
      if (player instanceof ServerPlayer sp) {
         sp.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.tamed", new Object[]{this.getName(), this.getDinoLevel(), bonus}));
         if (DinoConfig.SHOW_HINTS) {
            sp.sendSystemMessage(
               this.species().rideable()
                  ? Component.translatable("message.jaymospowers.dino.tamed_hint", new Object[]{this.saddleName()})
                  : Component.translatable("message.jaymospowers.dino.tamed_hint_small")
            );
         }
      }
   }

   public void setMode(int mode) {
      mode = Mth.clamp(mode, 0, 2);
      this.entityData.set(MODE, (byte)mode);
      boolean stay = mode == 1 && this.isTame();
      this.setOrderedToSit(stay);
      if (!this.level().isClientSide()) {
         if (!stay) {
            this.setInSittingPose(false);
         }

         if (stay) {
            this.getNavigation().stop();
            this.setTarget(null);
         }
      }
   }

   public Component saddleName() {
      return (Component)(this.species().rideable() ? new ItemStack(DinoItems.saddle(this.species())).getHoverName() : Component.empty());
   }

   public boolean isEquippableInSlot(ItemStack stack, EquipmentSlot slot) {
      return slot != EquipmentSlot.SADDLE || this.species().rideable() && stack.is(DinoItems.saddle(this.species()))
         ? super.isEquippableInSlot(stack, slot)
         : false;
   }

   public boolean canUseSlot(EquipmentSlot slot) {
      return slot != EquipmentSlot.SADDLE ? super.canUseSlot(slot) : this.isAlive() && this.isTame() && !this.isBaby() && this.species().rideable();
   }

   protected boolean canShearEquipment(Player player) {
      return super.canShearEquipment(player) && this.isOwnedBy(player);
   }

   public boolean canBeLeashed() {
      return this.isTame() && !this.species().big();
   }

   public boolean canMate(Animal other) {
      return other != this
         && other instanceof Dinosaur d
         && d.species() == this.species()
         && this.isTame()
         && d.isTame()
         && this.isInLove()
         && d.isInLove()
         && !this.isKnockedOut()
         && !d.isKnockedOut();
   }

   public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
      Dinosaur baby = (Dinosaur)this.getType().create(level, EntitySpawnReason.BREEDING);
      if (baby != null) {
         int other = partner instanceof Dinosaur d ? d.getDinoLevel() : this.getDinoLevel();
         baby.setDinoLevel(Math.max(1, (this.getDinoLevel() + other) / 2), false);
         baby.setHealth(baby.getMaxHealth());
         if (this.getOwnerReference() != null) {
            baby.setOwnerReference(this.getOwnerReference());
            baby.setTame(true, true);
            baby.setPersistenceRequired();
         }
      }

      return baby;
   }

   public boolean canAttack(LivingEntity target) {
      if (target instanceof Dinosaur d) {
         if (this.isTame() && d.isTame() && d.getOwnerReference() != null && d.getOwnerReference().equals(this.getOwnerReference())) {
            return false;
         }

         if (!DinoConfig.DINOSAURS_FIGHT && !this.isTame() && !d.isTame()) {
            return false;
         }

         if (d.isKnockedOut()) {
            return false;
         }
      }

      return super.canAttack(target);
   }

   public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
      if (target instanceof Creeper || target instanceof ArmorStand) {
         return false;
      } else if (this.sameOwner(target)) {
         return false;
      } else {
         return target instanceof Player targetPlayer && owner instanceof Player ownerPlayer && !ownerPlayer.canHarmPlayer(targetPlayer)
            ? false
            : !(target instanceof Dinosaur d && d.isKnockedOut());
      }
   }

   public @Nullable LivingEntity getControllingPassenger() {
      return (LivingEntity)(this.isSaddled() && !this.isKnockedOut() && this.getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger());
   }

   protected void tickRidden(Player controller, Vec3 input) {
      super.tickRidden(controller, input);
      Vec2 rot = this.getRiddenRotation(controller);
      this.setRot(rot.y, rot.x);
      this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
      if (this.isLocalInstanceAuthoritative() && this.onGround()) {
         if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
            double impulse = this.getJumpPower(this.playerJumpPendingScale);
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x, impulse, movement.z);
            this.needsSync = true;
            if (input.z > 0.0) {
               float sin = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
               float cos = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
               this.setDeltaMovement(this.getDeltaMovement().add(-0.5F * sin * this.playerJumpPendingScale, 0.0, 0.5F * cos * this.playerJumpPendingScale));
            }
         }

         this.playerJumpPendingScale = 0.0F;
      }
   }

   protected Vec2 getRiddenRotation(LivingEntity controller) {
      float yaw = Mth.approachDegrees(this.getYRot(), controller.getYRot(), this.species().turnRate);
      return new Vec2(controller.getXRot() * 0.4F, yaw);
   }

   protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
      if (this.isBusyStandingStill()) {
         return Vec3.ZERO;
      }

      float sideways = this.species().big() ? 0.0F : controller.xxa * 0.4F;
      float forward = controller.zza;
      if (forward <= 0.0F) {
         forward *= 0.3F;
      }

      return new Vec3(sideways, 0.0, forward);
   }

   private boolean isBusyStandingStill() {
      int action = this.getAction();
      return action == 2 || action == 4 || action == 3 && this.species().spec().specialStyle != 1 || action == 1 && this.species() == Species.BRACHIOSAURUS;
   }

   protected float getRiddenSpeed(Player controller) {
      double bps = !controller.isSprinting() && !this.isCharging() ? this.species().runBps * 0.6 : this.species().runBps * 1.1;
      if (this.isCharging()) {
         bps *= 1.25;
      }

      if (this.hasEffect(MobEffects.SPEED)) {
         bps *= 1.0 + 0.2 * (this.getEffect(MobEffects.SPEED).getAmplifier() + 1);
      }

      return (float)(bps / 43.17);
   }

   protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
      float[] seat = this.species().seat();
      return new Vec3(seat[0] * scale, seat[1] * scale, seat[2] * scale).yRot(-this.yBodyRot * (float) (Math.PI / 180.0));
   }

   protected void positionRider(Entity passenger, MoveFunction move) {
      super.positionRider(passenger, move);
      if (passenger instanceof LivingEntity living && this.species().big()) {
         living.yBodyRot = Mth.rotateIfNecessary(living.yBodyRot, this.yBodyRot, 60.0F);
      }
   }

   public boolean canSprint() {
      return true;
   }

   public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
      double r = this.getBbWidth() * 0.5 + passenger.getBbWidth() * 0.5 + 0.4;
      float yaw = this.yBodyRot * (float) (Math.PI / 180.0);
      int[] quarter = new int[]{-1, 1, 2, 0};

      for (int i = 0; i < 8; i++) {
         double a = yaw + (Math.PI / 2) * quarter[i % 4] + (i >= 4 ? Math.PI / 4 : 0.0);
         double x = this.getX() - Math.sin(a) * r;
         double z = this.getZ() + Math.cos(a) * r;

         for (int dy = 1; dy >= -3; dy--) {
            BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
            double floor = this.level().getBlockFloorHeight(pos);
            if (DismountHelper.isBlockFloorValid(floor)) {
               Vec3 at = new Vec3(x, pos.getY() + floor, z);
               if (DismountHelper.canDismountTo(this.level(), passenger, passenger.getLocalBoundsForPose(Pose.STANDING).move(at))) {
                  return at;
               }
            }
         }
      }

      return super.getDismountLocationForPassenger(passenger);
   }

   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengers().isEmpty() && !this.isKnockedOut() && !this.isBaby() && this.species().rideable();
   }

   public void onPlayerJump(int jumpAmount) {
      if (this.isSaddled() && this.species().jumps()) {
         this.playerJumpPendingScale = jumpAmount < 0 ? 0.0F : (jumpAmount >= 90 ? 1.0F : 0.4F + 0.4F * jumpAmount / 90.0F);
      }
   }

   public boolean canJump() {
      return this.isSaddled() && this.species().jumps() && !this.isKnockedOut();
   }

   public void handleStartJump(int jumpScale) {
   }

   public void handleStopJump() {
   }

   public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
      return !this.isLeaping() && (!this.species().jumps() || !(fallDistance < 7.0)) ? super.causeFallDamage(fallDistance, multiplier, source) : false;
   }

   protected void tickDeath() {
      this.deathTime++;
      if (this.deathTime == 1) {
         this.refreshDimensions();
         this.ejectPassengers();
      }

      if (this.deathTime >= 60 && !this.level().isClientSide() && !this.isRemoved()) {
         this.level().broadcastEntityEvent(this, (byte)60);
         this.remove(RemovalReason.KILLED);
      }
   }

   protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
      super.dropCustomDeathLoot(level, source, killedByPlayer);
      int extra = this.getDinoLevel() / 10;
      if (extra > 0 && !this.isBaby()) {
         ItemStack meat = new ItemStack(this.species().big() ? (ItemLike)DinoItems.RAW_PRIME_MEAT : (ItemLike)DinoItems.RAW_DINO_MEAT, extra);
         this.spawnAtLocation(level, meat);
      }
   }

   protected @Nullable SoundEvent getAmbientSound() {
      return this.isKnockedOut() ? null : DinoSounds.ambient(this.species());
   }

   public int getAmbientSoundInterval() {
      return this.species().big() ? 320 : 220;
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return DinoSounds.hurt(this.species());
   }

   protected SoundEvent getDeathSound() {
      return DinoSounds.death(this.species());
   }

   protected float getSoundVolume() {
      return switch (this.species()) {
         case TRICERATOPS, CARNOTAURUS, PARASAUROLOPHUS -> 1.8F;
         case DODO -> 0.8F;
         default -> 1.0F;
         case TYRANNOSAURUS, BRACHIOSAURUS, SPINOSAURUS -> 2.6F;
      };
   }

   public float getVoicePitch() {
      float base = this.isBaby() ? 1.55F : 1.0F;
      return base + (this.random.nextFloat() - this.random.nextFloat()) * 0.08F;
   }

   protected void playStepSound(BlockPos pos, BlockState state) {
      if (this.species().big() && !this.isBaby()) {
         this.playSound(
            (SoundEvent)DinoSounds.STEP_HEAVY,
            this.species() == Species.BRACHIOSAURUS ? 1.6F : 1.0F,
            (this.species() == Species.BRACHIOSAURUS ? 0.75F : 1.0F) + this.random.nextFloat() * 0.1F
         );
      } else {
         super.playStepSound(pos, state);
      }
   }

   public void groundBurst(ServerLevel level, Vec3 at, double radius, int count) {
      BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
      BlockState ground = level.getBlockState(below);
      if (!ground.isAir()) {
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, radius * 0.5, 0.1, radius * 0.5, 0.15);
         level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, count / 6, radius * 0.4, 0.1, radius * 0.4, 0.01);
      }
   }

   public List<LivingEntity> nearby(double range) {
      return this.level()
         .getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range, range * 0.5, range), e -> e != this && e.isAlive() && !e.isSpectator());
   }

   private boolean sameOwner(Entity other) {
      EntityReference<LivingEntity> mine = this.getOwnerReference();
      if (mine == null) {
         return false;
      } else {
         return other instanceof Player p && p.getUUID().equals(mine.getUUID())
            ? true
            : other instanceof OwnableEntity o && o.getOwnerReference() != null && o.getOwnerReference().getUUID().equals(mine.getUUID());
      }
   }

   public boolean isFriend(Entity other) {
      if (other != this && !this.hasPassenger(other)) {
         if (other instanceof Dinosaur sleeping && sleeping.isKnockedOut()) {
            return true;
         } else {
            if (this.isTame()) {
               if (this.sameOwner(other)) {
                  return true;
               }

               LivingEntity owner = this.getOwner();
               if (other instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)) {
                  return true;
               }
            } else if (other instanceof Dinosaur d && !d.isTame()) {
               return d.species() == this.species() || !DinoConfig.DINOSAURS_FIGHT || this.getTarget() != d && d.getTarget() != this;
            }

            return other instanceof Dinosaur d && d.isKnockedOut();
         }
      } else {
         return true;
      }
   }

   public RandomSource rng() {
      return this.random;
   }
}
