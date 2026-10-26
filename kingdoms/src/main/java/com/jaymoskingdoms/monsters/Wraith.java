package com.jaymoskingdoms.monsters;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) The wraith (after Mo' Creatures'): a hooded shade that drifts through walls and the ground at night, arms out,
 * and rakes whoever it reaches; daylight burns it. The flame wraith burns instead, in the Nether and over the badlands by
 * night, and sets what it touches alight.
 */
public class Wraith extends LegendMonster {
	static final double HEALTH = 14.0, FLAME_HEALTH = 18.0, DAMAGE = 4.0, FLYING_SPEED = 0.6;
	/** How long the flame wraith's touch burns (seconds). */
	static final float FIRE_SECONDS = 4.0F;
	/** How far it wanders when it has no one (blocks each way). */
	static final int WANDER = 7;

	private final boolean flame;

	public Wraith(EntityType<? extends Wraith> type, Level level, boolean flame) {
		super(type, level);
		this.flame = flame;
		this.moveControl = new DriftControl(this);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes(boolean flame) {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, flame ? FLAME_HEALTH : HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FLYING_SPEED, FLYING_SPEED).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	public boolean flame() {
		return this.flame;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(4, new Swoop());
		this.goalSelector.addGoal(8, new Drift());
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		// (0.7.217) villagers too, for less ({@link com.jaymoskingdoms.VillagerHunters#VILLAGER_DAMAGE})
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, false));
	}

	@Override
	public void tick() {
		// through walls and the ground, like a vex
		this.noPhysics = true;
		super.tick();
		this.noPhysics = false;
		this.setNoGravity(true);
		if (this.level().isClientSide() && this.flame && this.random.nextInt(3) == 0) {
			this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected boolean isAffectedByBlocks() {
		return !this.isRemoved();
	}

	@Override
	protected int nextArm() {
		return BOTH;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.flame) {
			target.igniteForSeconds(FIRE_SECONDS);
		}
		return hit;
	}

	@Override
	public boolean fireImmune() {
		return this.flame || super.fireImmune();
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, BlockPos pos) {
	}

	@Override
	public float getLightLevelDependentMagicValue() {
		return this.flame ? 1.0F : super.getLightLevelDependentMagicValue();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.flame ? SoundEvents.BLAZE_AMBIENT : SoundEvents.VEX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.flame ? SoundEvents.BLAZE_HURT : SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.flame ? SoundEvents.BLAZE_DEATH : SoundEvents.VEX_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.1F;
	}

	/** Flies straight at where it wants to be, through anything. */
	private static final class DriftControl extends MoveControl<Wraith> {
		DriftControl(Wraith wraith) {
			super(wraith);
		}

		@Override
		public void tick() {
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				return;
			}
			Vec3 delta = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
			double length = delta.length();
			if (length < this.mob.getBoundingBox().getSize()) {
				this.operation = MoveControl.Operation.WAIT;
				this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(0.5));
				return;
			}
			double speed = this.speedModifier * this.mob.getAttributeValue(Attributes.FLYING_SPEED);
			this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(delta.scale(speed * 0.05 / length)));
			LivingEntity target = this.mob.getTarget();
			Vec3 facing = target != null ? target.position().subtract(this.mob.position()) : this.mob.getDeltaMovement();
			this.mob.setYRot(-((float) Mth.atan2(facing.x, facing.z)) * Mth.RAD_TO_DEG);
			this.mob.yBodyRot = this.mob.getYRot();
		}
	}

	/** Swoops at its target's chest, and rakes it on reaching it. */
	private final class Swoop extends Goal {
		Swoop() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Wraith.this.getTarget();
			return target != null && target.isAlive() && !Wraith.this.getMoveControl().hasWanted();
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = Wraith.this.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Wraith.this.getTarget();
			if (target == null) {
				return;
			}
			Vec3 chest = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
			Wraith.this.moveControl.setWantedPosition(chest.x, chest.y - Wraith.this.getBbHeight() * 0.4, chest.z, 1.0);
			Wraith.this.getLookControl().setLookAt(target);
			if (Wraith.this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox()) && Wraith.this.level() instanceof ServerLevel level
				&& Wraith.this.swingingArm() == 0) {
				Wraith.this.doHurtTarget(level, target);
			}
		}
	}

	/** Wanders about where it is. */
	private final class Drift extends Goal {
		Drift() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return !Wraith.this.getMoveControl().hasWanted() && Wraith.this.random.nextInt(reducedTickDelay(7)) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void tick() {
			BlockPos home = Wraith.this.blockPosition();
			for (int tries = 0; tries < 3; tries++) {
				BlockPos to = home.offset(Wraith.this.random.nextInt(WANDER * 2 + 1) - WANDER, Wraith.this.random.nextInt(7) - 3,
					Wraith.this.random.nextInt(WANDER * 2 + 1) - WANDER);
				if (Wraith.this.level().isEmptyBlock(to)) {
					Wraith.this.moveControl.setWantedPosition(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, 0.3);
					break;
				}
			}
		}
	}
}
