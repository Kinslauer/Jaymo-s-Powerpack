package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Sits disguised as a big toadstool. When a player comes within 5 blocks in sight (or it is hurt) it unfolds six legs
 * and hunts, climbing walls like a spider; its bite roots you for a moment. With no prey for 10 seconds it settles back
 * into its disguise.
 */
public class MimicapEntity extends Monster {
	public static final int HIDDEN = 0;
	public static final int WAKING = 1;
	public static final int AWAKE = 2;
	public static final int SETTLING = 3;
	public static final byte EVENT_BITE = 105;
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(MimicapEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(MimicapEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(MimicapEntity.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDimensions HIDDEN_SIZE = EntityDimensions.scalable(1.0F, 0.62F).withEyeHeight(0.4F);

	public final AnimationState wakeState = new AnimationState();
	public final AnimationState settleState = new AnimationState();
	public final AnimationState biteState = new AnimationState();
	private int phaseTicks;
	private int boredTicks;

	public MimicapEntity(EntityType<? extends MimicapEntity> type, Level level) {
		super(type, level);
		this.xpReward = 7;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22.0).add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 20.0).add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, HIDDEN);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_CLIMBING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true) {
			@Override
			public boolean canUse() {
				return MimicapEntity.this.isAwake() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return MimicapEntity.this.isAwake() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return MimicapEntity.this.isAwake() && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F) {
			@Override
			public boolean canUse() {
				return MimicapEntity.this.isAwake() && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WallClimberNavigation(this, level);
	}

	// ---- phase --------------------------------------------------------------------------------------------------

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public boolean isAwake() {
		return getPhase() == AWAKE;
	}

	private void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
		this.phaseTicks = 0;
	}

	public int getVariant() {
		return this.entityData.get(DATA_VARIANT);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_PHASE.equals(key)) {
			this.refreshDimensions();
			if (this.level().isClientSide()) {
				int phase = getPhase();
				if (phase == WAKING) {
					settleState.stop();
					wakeState.start(this.tickCount);
				} else if (phase == SETTLING) {
					wakeState.stop();
					settleState.start(this.tickCount);
				} else if (phase == HIDDEN) {
					wakeState.stop();
					settleState.stop();
				}
			}
		}
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return getPhase() == HIDDEN ? HIDDEN_SIZE : super.getDefaultDimensions(pose);
	}

	/**
	 * Holds still unless awake. (Not isImmobile(): that would also skip serverAiStep, and with it the wake check.)
	 */
	@Override
	public void aiStep() {
		if (!this.level().isClientSide() && getPhase() != AWAKE) {
			this.getNavigation().stop();
			this.xxa = 0.0F;
			this.zza = 0.0F;
			this.jumping = false;
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		}
		super.aiStep();
	}

	public void wake() {
		if (getPhase() == HIDDEN || getPhase() == SETTLING) {
			setPhase(WAKING);
			this.playSound(GlowSounds.MIMICAP_WAKE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		phaseTicks++;
		LivingEntity target = this.getTarget();
		boolean prey = target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
		switch (getPhase()) {
			case HIDDEN -> {
				this.getNavigation().stop();
				if (prey && this.distanceToSqr(target) < 25.0 && this.getSensing().hasLineOfSight(target)) {
					wake();
				}
			}
			case WAKING -> {
				if (phaseTicks >= 16) {
					setPhase(AWAKE);
					boredTicks = 0;
				}
			}
			case AWAKE -> {
				boredTicks = prey ? 0 : boredTicks + 1;
				if (boredTicks > 200 && this.onGround() && !this.isClimbing()) {
					setPhase(SETTLING);
					this.getNavigation().stop();
				}
			}
			case SETTLING -> {
				if (prey && this.distanceToSqr(target) < 25.0) {
					wake();
				} else if (phaseTicks >= 20) {
					setPhase(HIDDEN);
				}
			}
			default -> setPhase(HIDDEN);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			this.entityData.set(DATA_CLIMBING, this.isAwake() && this.horizontalCollision);
		}
		if (getPhase() == HIDDEN) {
			// Disguised: it does not turn to look at anything.
			this.yHeadRot = this.yBodyRot;
			this.setXRot(0.0F);
		}
	}

	public boolean isClimbing() {
		return this.entityData.get(DATA_CLIMBING);
	}

	@Override
	public boolean onClimbable() {
		return this.isClimbing();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !this.level().isClientSide() && this.isAlive()) {
			wake();
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.level().broadcastEntityEvent(this, EVENT_BITE);
		this.playSound(GlowSounds.MIMICAP_BITE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(GlowCreatures.ROOTED, 30), this);
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_BITE) {
			biteState.start(this.tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType,
										@Nullable SpawnGroupData groupData) {
		this.entityData.set(DATA_VARIANT, level.getRandom().nextInt(10) < 7 ? 0 : 1);
		return super.finalizeSpawn(level, difficulty, spawnType, groupData);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		this.spawnAtLocation(level, new ItemStack(getVariant() == 0 ? Items.RED_MUSHROOM : Items.BROWN_MUSHROOM, 1 + this.random.nextInt(2)));
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Disguised", getPhase() == HIDDEN || getPhase() == SETTLING);
		tag.putInt("Variant", getVariant());
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_PHASE, tag.getBooleanOr("Disguised", false) ? HIDDEN : AWAKE);
		this.entityData.set(DATA_VARIANT, tag.getIntOr("Variant", 0));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isAwake() ? GlowSounds.MIMICAP_AMBIENT : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GlowSounds.MIMICAP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GlowSounds.MIMICAP_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(GlowSounds.MIMICAP_STEP, 0.4F, 1.0F);
	}
}
