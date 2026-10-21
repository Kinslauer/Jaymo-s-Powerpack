package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The bladeshroom hunter: a low, quick fungus-beast with two blade-edged mushroom caps on its back. Left alone it buries
 * itself in the ground until only the caps show, and springs out when a player comes close or when it is struck. Its
 * attack is a slow, wide lunge with a 4-damage cut. Buried, it cannot be moved.
 */
public class BladeshroomHunter extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 18.0, SPEED = 0.3, FOLLOW = 16.0, DAMAGE = 4.0;
	/** Left idle with no target for this long (200 to 399 ticks) it buries itself; burying takes 36 ticks, unburying 28. */
	public static final int IDLE_MIN = 200, IDLE_SPREAD = 200, BURY_TICKS = 36, UNBURY_TICKS = 28;
	/** A buried hunter springs out when a player is this close (blocks). */
	public static final double WAKE_RANGE = 6.0;
	/** The attack: starts this close, takes 38 ticks, cuts at tick 18 (reach, and a little lunge forward). */
	public static final double STRIKE_START = 3.2, REACH = 3.4, LUNGE = 0.3;
	public static final int SWING = 38, CUT = 18;

	/** 0 up, 1 burying, 2 buried, 3 unburying. */
	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BladeshroomHunter.class, EntityDataSerializers.INT);
	/** Ticks into the burying or unburying. */
	private static final EntityDataAccessor<Integer> PROGRESS = SynchedEntityData.defineId(BladeshroomHunter.class, EntityDataSerializers.INT);
	/** Ticks into a swing; -1 not swinging. */
	private static final EntityDataAccessor<Integer> STRIKE = SynchedEntityData.defineId(BladeshroomHunter.class, EntityDataSerializers.INT);

	private int idle, hideAfter;

	public BladeshroomHunter(EntityType<? extends BladeshroomHunter> type, Level level) {
		super(type, level, -1, 2, 0);
		this.hideAfter = IDLE_MIN + this.random.nextInt(IDLE_SPREAD);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_DAMAGE, DAMAGE);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new CutGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(STATE, 0);
		entityData.define(PROGRESS, 0);
		entityData.define(STRIKE, -1);
	}

	/** 0 up, 1 burying, 2 buried, 3 unburying. */
	public int burrow() {
		return this.entityData.get(STATE);
	}

	/** 0 (up) to 1 (only the caps showing). */
	public float depth() {
		int progress = this.entityData.get(PROGRESS);
		return switch (this.burrow()) {
			case 1 -> Math.min(1.0F, progress / (float)BURY_TICKS);
			case 2 -> 1.0F;
			case 3 -> 1.0F - Math.min(1.0F, progress / (float)UNBURY_TICKS);
			default -> 0.0F;
		};
	}

	/** Ticks into a swing, or -1. */
	public int strike() {
		return this.entityData.get(STRIKE);
	}

	@Override
	protected boolean isImmobile() {
		return this.burrow() != 0 || super.isImmobile();
	}

	private void setBurrow(int state) {
		this.entityData.set(STATE, state);
		this.entityData.set(PROGRESS, 0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean down = this.burrow() != 0;
		boolean hurt = super.hurtServer(level, source, amount);
		if (down) {
			// buried (or burying), it does not budge, and the blow wakes it
			this.setDeltaMovement(Vec3.ZERO);
			if (hurt && this.burrow() != 3 && this.isAlive()) {
				this.setBurrow(3);
			}
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		int progress = this.entityData.get(PROGRESS);
		switch (this.burrow()) {
			case 0 -> {
				if (this.getTarget() == null && this.onGround() && this.strike() < 0) {
					if (++this.idle >= this.hideAfter) {
						this.setBurrow(1);
						this.playSound(MonsterSounds.get("block.fungus.break"), 1.0F, 0.7F);
					}
				} else {
					this.idle = 0;
				}
			}
			case 1 -> {
				this.dirt(level);
				if (progress + 1 >= BURY_TICKS) {
					this.setBurrow(2);
				} else {
					this.entityData.set(PROGRESS, progress + 1);
				}
			}
			case 2 -> {
				this.setDeltaMovement(Vec3.ZERO);
				Player near = level.getNearestPlayer(this, WAKE_RANGE);
				if (near != null && !near.isSpectator() && !near.isCreative() && this.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
					this.setBurrow(3);
					this.setTarget(near);
					this.playSound(MonsterSounds.get("block.fungus.hit"), 1.0F, 1.2F);
				}
			}
			default -> {
				this.dirt(level);
				if (progress + 1 >= UNBURY_TICKS) {
					this.setBurrow(0);
					this.idle = 0;
					this.hideAfter = IDLE_MIN + this.random.nextInt(IDLE_SPREAD);
				} else {
					this.entityData.set(PROGRESS, progress + 1);
				}
			}
		}
	}

	private void dirt(ServerLevel level) {
		if (this.tickCount % 2 == 0) {
			BlockState below = level.getBlockState(this.blockPosition().below());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX(), this.getY(), this.getZ(), 5, 0.4, 0.05, 0.4, 0.1);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("burrow", this.burrow());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(STATE, input.getIntOr("burrow", 0) == 0 ? 0 : 2);
		this.entityData.set(PROGRESS, 0);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("block.fungus.hit");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("block.fungus.break");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MonsterSounds.get("block.fungus.step"), 0.3F, 1.1F);
	}

	/** Run at the target; close enough, wind up and cut (at tick 18 of 38), with a little lunge. */
	private static final class CutGoal extends Goal {
		private final BladeshroomHunter hunter;

		CutGoal(BladeshroomHunter hunter) {
			this.hunter = hunter;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.hunter.getTarget();
			return this.hunter.burrow() == 0 && target != null && target.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			// a swing in progress finishes only while its target still lives
			LivingEntity target = this.hunter.getTarget();
			if (target == null || !target.isAlive()) {
				return false;
			}
			return this.canUse() || this.hunter.strike() >= 0;
		}

		@Override
		public void stop() {
			this.hunter.entityData.set(STRIKE, -1);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BladeshroomHunter h = this.hunter;
			LivingEntity target = h.getTarget();
			if (target == null || !(h.level() instanceof ServerLevel level)) {
				h.entityData.set(STRIKE, -1);
				return;
			}
			h.getLookControl().setLookAt(target, 40.0F, 40.0F);
			int strike = h.strike();
			if (strike < 0) {
				if (h.distanceTo(target) <= STRIKE_START && h.hasLineOfSight(target)) {
					h.getNavigation().stop();
					h.entityData.set(STRIKE, 0);
				} else {
					h.getNavigation().moveTo(target, 1.2);
				}
				return;
			}
			h.getNavigation().stop();
			strike++;
			if (strike == CUT) {
				Vec3 toward = target.position().subtract(h.position()).multiply(1.0, 0.0, 1.0);
				if (toward.lengthSqr() > 1.0E-4) {
					h.setDeltaMovement(h.getDeltaMovement().add(toward.normalize().scale(LUNGE)));
				}
				if (h.distanceTo(target) <= REACH) {
					h.doHurtTarget(level, target);
				}
				h.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
			}
			h.entityData.set(STRIKE, strike >= SWING ? -1 : strike);
		}
	}
}
