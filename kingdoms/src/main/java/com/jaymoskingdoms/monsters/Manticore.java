package com.jaymoskingdoms.monsters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The manticore (after Mo' Creatures' manticore, GPL-3, see {@code licenses/mocreatures}): a lion with a mane, a pair of
 * bat's wings and a scorpion's tail, that keeps to the halls of the Aether's Sentry Ruins (Jaymo's Powers; see
 * {@link MocBeasts}). It flies low, a few blocks off the floor, and stings with a poison that lasts. The spines of its tail
 * are its own: it sheds them as it is worn down (a quarter of its health at a time), so a patient hunter picks them up off
 * the floor, and what is left drops with it.
 */
public class Manticore extends Monster {
	static final double HEALTH = 60.0, SPEED = 0.3, FLYING_SPEED = 0.6, DAMAGE = 7.0, ARMOR = 4.0;
	/** The sting's poison (ticks), a long one. */
	static final int POISON_TICKS = 200, POISON_LEVEL = 1;
	/** How far above the floor it keeps (blocks) before it comes down. */
	static final double CEILING = 3.5;
	/** How many spines it sheds each time it passes a quarter of its health, and how many it leaves at its death. */
	static final int SHED_AT_QUARTERS = 4, SHED_MIN = 1, SHED_MORE = 2, DEATH_MIN = 2, DEATH_MORE = 3;
	static final int TAIL_TICKS = 14;

	private static final EntityDataAccessor<Boolean> STING = SynchedEntityData.defineId(Manticore.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(Manticore.class, EntityDataSerializers.BOOLEAN);

	private int tailTicks;
	/** How many quarters of its health it has already shed spines for. */
	private int shedQuarters;

	public Manticore(EntityType<? extends Manticore> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FLYING_SPEED, FLYING_SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.FOLLOW_RANGE, 28.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.9));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(STING, false);
		entityData.define(FLYING, false);
	}

	/** Its tail is raised to strike. */
	public boolean stinging() {
		return this.entityData.get(STING);
	}

	/** It is off the floor. */
	public boolean airborne() {
		return this.entityData.get(FLYING);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.tailTicks = TAIL_TICKS;
			this.entityData.set(STING, true);
			if (target instanceof LivingEntity victim) {
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_LEVEL), this);
			}
		}
		return hit;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			return;
		}
		if (this.tailTicks > 0 && --this.tailTicks == 0) {
			this.entityData.set(STING, false);
		}
		boolean up = !this.onGround() && !this.isInWater();
		if (up != this.airborne()) {
			this.entityData.set(FLYING, up);
		}
		// it flies low: held under a few blocks over the floor, and never falls out of the air when it is hunting
		this.setNoGravity(this.getTarget() != null || up && this.floorDistance() < CEILING);
		if (this.getTarget() == null && this.floorDistance() > CEILING && !this.onGround()) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
		}
	}

	/** How far it is over the first solid block below it (up to the limit searched). */
	private double floorDistance() {
		BlockPos at = this.blockPosition();
		for (int i = 0; i < 12; i++) {
			if (!this.level().getBlockState(at.below(i)).getCollisionShape(this.level(), at.below(i)).isEmpty()) {
				return i + (this.getY() - at.getY());
			}
		}
		return 12.0;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive()) {
			int lost = (int)((1.0F - this.getHealth() / this.getMaxHealth()) * SHED_AT_QUARTERS);
			if (lost > this.shedQuarters) {
				this.shedQuarters = lost;
				this.shed(level, SHED_MIN + this.random.nextInt(SHED_MORE + 1));
				level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.6F);
			}
		}
		return hurt;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.shed(level, DEATH_MIN + this.random.nextInt(DEATH_MORE + 1));
	}

	/** Spines fall from its tail. */
	private void shed(ServerLevel level, int count) {
		for (int i = 0; i < count; i++) {
			ItemEntity spine = new ItemEntity(level, this.getX(), this.getY() + 0.8, this.getZ(), new ItemStack(MocBeasts.MANTICORE_SPINE));
			spine.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.3, 0.25, (this.random.nextDouble() - 0.5) * 0.3);
			level.addFreshEntity(spine);
		}
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.9, this.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("shed_quarters", this.shedQuarters);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shedQuarters = input.getIntOr("shed_quarters", 0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.9F;
	}
}
