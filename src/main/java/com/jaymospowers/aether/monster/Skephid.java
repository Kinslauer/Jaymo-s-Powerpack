package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The skephid: a flat, many-legged web-spitter of caves. It climbs walls like a spider and, seeing a player, leaps to
 * the ceiling above and hangs there upside down, spitting webbing balls (webbed, you cannot run or jump). It flees the
 * sun. Hanging, it creeps along the ceiling to keep its range.
 */
public class Skephid extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 16.0, SPEED = 0.24, FOLLOW = 16.0;
	/** Its push through the air while it hangs (it walks the ceiling by this). */
	public static final float CEILING_THRUST = 0.025F;
	/** A webbing ball is cast 5 ticks into each attack, then it rests 60 to 100 ticks (longer when far). */
	public static final int CAST = 5, COOLDOWN_MIN = 60, COOLDOWN_SPREAD = 40;
	public static final float BALL_SPEED = 0.8F, BALL_SPREAD = 4.0F;
	/** It tries to hang from a ceiling this close above it (blocks). */
	public static final int CEILING_MIN = 2, CEILING_MAX = 5;
	/** The leap it makes to reach the ceiling, and how long it waits before trying again (ticks). */
	public static final double LEAP = 0.85;
	public static final int LEAP_WAIT = 60;
	/** It likes to keep this far from its target when it hangs (min, max; blocks). */
	public static final double HOLD_MIN = 4.0, HOLD_MAX = 9.0;
	/** It lets go after this long without a target (ticks). */
	public static final int LET_GO = 120;

	private static final EntityDataAccessor<Boolean> CEILING = SynchedEntityData.defineId(Skephid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Skephid.class, EntityDataSerializers.BOOLEAN);
	/** Ticks since it began to cast; -1 not casting. */
	private static final EntityDataAccessor<Integer> CASTING = SynchedEntityData.defineId(Skephid.class, EntityDataSerializers.INT);

	private int leapWait, idle;

	public Skephid(EntityType<? extends Skephid> type, Level level) {
		super(type, level, 1, -1, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new CastGoal(this));
		this.goalSelector.addGoal(2, new CeilingGoal(this));
		this.goalSelector.addGoal(3, new FleeSunGoal(this, 1.0));
		this.goalSelector.addGoal(4, new ApproachGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
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
		entityData.define(CEILING, false);
		entityData.define(CLIMBING, false);
		entityData.define(CASTING, -1);
	}

	/** Whether it hangs from a ceiling (it is drawn upside down). */
	public boolean onCeiling() {
		return this.entityData.get(CEILING);
	}

	/** Ticks into a cast, or -1. */
	public int casting() {
		return this.entityData.get(CASTING);
	}

	@Override
	public boolean onClimbable() {
		return this.entityData.get(CLIMBING);
	}

	@Override
	protected float getFlyingSpeed() {
		return this.onCeiling() ? CEILING_THRUST : super.getFlyingSpeed();
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
		// webs are its own
		if (!state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
			super.makeStuckInBlock(state, speedMultiplier);
		}
	}

	private boolean solidAt(BlockPos pos) {
		return !this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty();
	}

	/** The free blocks between its head and the ceiling above it, or -1 if there is none within reach. */
	private int ceilingGap() {
		BlockPos head = BlockPos.containing(this.getX(), this.getBoundingBox().maxY, this.getZ());
		for (int i = 0; i <= CEILING_MAX; i++) {
			if (this.solidAt(head.above(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}
		if (this.leapWait > 0) {
			this.leapWait--;
		}
		boolean hanging = this.onCeiling();
		this.entityData.set(CLIMBING, !hanging && this.horizontalCollision);
		BlockPos above = BlockPos.containing(this.getX(), this.getBoundingBox().maxY + 0.1, this.getZ());
		if (hanging) {
			this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
			this.fallDistance = 0.0;
			this.idle = this.getTarget() == null ? this.idle + 1 : 0;
			if (!this.solidAt(above) || this.idle > LET_GO || this.inSun()) {
				this.entityData.set(CEILING, false);
				this.setNoGravity(false);
			}
		} else if (this.verticalCollision && !this.onGround() && this.solidAt(above) && this.getDeltaMovement().y >= -0.01) {
			// it has reached a ceiling: it takes hold
			this.entityData.set(CEILING, true);
			this.setNoGravity(true);
			this.idle = 0;
			this.getNavigation().stop();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("ceiling", this.onCeiling());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		boolean ceiling = input.getBooleanOr("ceiling", false);
		this.entityData.set(CEILING, ceiling);
		this.setNoGravity(ceiling);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MonsterSounds.get("entity.spider.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.spider.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.spider.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MonsterSounds.get("entity.spider.step"), 0.15F, 1.2F);
	}

	/** Seeing the target, spit a webbing ball every 60 to 100 ticks. */
	private static final class CastGoal extends Goal {
		private final Skephid skephid;
		private int cooldown = 30;

		CastGoal(Skephid skephid) {
			this.skephid = skephid;
			this.setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.skephid.getTarget();
			return target != null && target.isAlive() && this.skephid.hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse() || this.skephid.casting() >= 0;
		}

		@Override
		public void stop() {
			this.skephid.entityData.set(CASTING, -1);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Skephid s = this.skephid;
			LivingEntity target = s.getTarget();
			if (target == null) {
				s.entityData.set(CASTING, -1);
				return;
			}
			s.getLookControl().setLookAt(target, 40.0F, 40.0F);
			int casting = s.casting();
			if (casting >= 0) {
				casting++;
				if (casting == CAST) {
					double distance = s.distanceTo(target);
					Vec3 from = s.getEyePosition();
					Vec3 aim = target.getEyePosition().add(0.0, distance * 0.04, 0.0);
					AetherShot.fire(AetherShot.Kind.SKEPHID_WEB, s, from, aim, BALL_SPEED, BALL_SPREAD);
					s.playSound(MonsterSounds.get("entity.llama.spit"), 1.0F, 0.6F);
					this.cooldown = COOLDOWN_MIN + (int)(COOLDOWN_SPREAD * Mth.clamp(distance / 10.0, 0.1, 1.0));
				}
				s.entityData.set(CASTING, casting > CAST + 6 ? -1 : casting);
				return;
			}
			if (this.cooldown > 0) {
				this.cooldown--;
			} else if (s.hasLineOfSight(target)) {
				s.entityData.set(CASTING, 0);
			}
		}
	}

	/** Hanging: creep along the ceiling to keep a spitting range from the target. */
	private static final class CeilingGoal extends Goal {
		private final Skephid skephid;

		CeilingGoal(Skephid skephid) {
			this.skephid = skephid;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.skephid.onCeiling() && this.skephid.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Skephid s = this.skephid;
			LivingEntity target = s.getTarget();
			if (target == null) {
				return;
			}
			double dx = target.getX() - s.getX(), dz = target.getZ() - s.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz);
			if (flat > HOLD_MAX) {
				s.getMoveControl().setWantedPosition(target.getX(), s.getY(), target.getZ(), 1.0);
			} else if (flat < HOLD_MIN) {
				s.getMoveControl().setWantedPosition(s.getX() - dx, s.getY(), s.getZ() - dz, 1.0);
			}
		}
	}

	/** On the ground with a target: walk to it, climbing what is in the way, and leap for a ceiling above. */
	private static final class ApproachGoal extends Goal {
		private final Skephid skephid;

		ApproachGoal(Skephid skephid) {
			this.skephid = skephid;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !this.skephid.onCeiling() && this.skephid.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Skephid s = this.skephid;
			LivingEntity target = s.getTarget();
			if (target == null) {
				return;
			}
			double distance = s.distanceTo(target);
			if (s.onGround() && s.leapWait <= 0 && distance < FOLLOW) {
				int gap = s.ceilingGap();
				if (gap >= CEILING_MIN && gap <= CEILING_MAX && s.hasLineOfSight(target)) {
					s.leapWait = LEAP_WAIT;
					s.setDeltaMovement(s.getDeltaMovement().add(0.0, LEAP, 0.0));
					s.getNavigation().stop();
					return;
				}
			}
			if (distance > HOLD_MIN + 2.0 || !s.hasLineOfSight(target)) {
				s.getNavigation().moveTo(target, 1.0);
			} else {
				s.getNavigation().stop();
			}
		}
	}
}
