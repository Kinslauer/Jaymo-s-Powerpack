package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The gravitite taluton: a tall pillar of floating stone plates that falls slowly, climbs walls and hurls gravitite
 * debris (6 damage, speeding up as it flies) every 100 ticks while it has you in sight. It keeps its distance, walking
 * toward you only when you are far.
 */
public class GravititeTaluton extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 32.0, SPEED = 0.2, STEP = 1.0;
	/** The most it falls a tick. */
	public static final double MAX_FALL = 0.1;
	/** A shot every 100 ticks; the stones start to glow for the last 22. */
	public static final int CYCLE = 100, GLOW = 22;
	public static final float DEBRIS_SPEED = 0.5F;
	/** It walks to its target only when farther than this (blocks), and shoots only within this. */
	public static final double COME_CLOSER = 14.0, RANGE = 26.0;

	/** 1 while the stones glow before a shot. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(GravititeTaluton.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(GravititeTaluton.class, EntityDataSerializers.BOOLEAN);

	public GravititeTaluton(EntityType<? extends GravititeTaluton> type, Level level) {
		super(type, level, 0, 1, -1);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.STEP_HEIGHT, STEP);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new HurlGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WallClimberNavigation(this, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, 0);
		entityData.define(CLIMBING, false);
	}

	/** Whether the stones glow before a shot. */
	public boolean glowing() {
		return this.entityData.get(ACTION) == 1;
	}

	@Override
	public boolean onClimbable() {
		return this.entityData.get(CLIMBING);
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		Vec3 motion = this.getDeltaMovement();
		if (motion.y < -MAX_FALL && !this.isInWater()) {
			this.setDeltaMovement(motion.x, -MAX_FALL, motion.z);
		}
		this.fallDistance = 0.0;
		if (!this.level().isClientSide()) {
			this.entityData.set(CLIMBING, this.horizontalCollision);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("block.stone.hit");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("block.stone.break");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MonsterSounds.get("block.deepslate.step"), 0.5F, 0.8F);
	}

	/** Walk closer when the target is far; hurl a stone every {@link #CYCLE} ticks. */
	private static final class HurlGoal extends Goal {
		private final GravititeTaluton taluton;
		private int tick;

		HurlGoal(GravititeTaluton taluton) {
			this.taluton = taluton;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.taluton.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			this.taluton.entityData.set(ACTION, 0);
			this.taluton.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			GravititeTaluton t = this.taluton;
			LivingEntity target = t.getTarget();
			if (target == null || !(t.level() instanceof ServerLevel)) {
				return;
			}
			t.getLookControl().setLookAt(target, 30.0F, 30.0F);
			double distance = t.distanceTo(target);
			boolean sees = t.hasLineOfSight(target);
			if (distance > COME_CLOSER || !sees) {
				t.getNavigation().moveTo(target, 1.0);
			} else {
				t.getNavigation().stop();
			}
			if (distance > RANGE) {
				return;
			}
			if (this.tick < CYCLE - 1 || sees) {
				this.tick++;
			}
			if (this.tick == CYCLE - GLOW) {
				t.entityData.set(ACTION, 1);
			}
			if (this.tick >= CYCLE) {
				this.tick = 0;
				t.entityData.set(ACTION, 0);
				Vec3 from = t.getEyePosition().add(t.getLookAngle().scale(0.8));
				AetherShot.fire(AetherShot.Kind.DEBRIS, t, from, target.getEyePosition(), DEBRIS_SPEED, 0.5F);
				t.playSound(MonsterSounds.get("entity.shulker.shoot"), 1.0F, 0.6F);
			}
		}
	}
}
