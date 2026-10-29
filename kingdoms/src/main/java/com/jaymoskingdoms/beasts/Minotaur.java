package com.jaymoskingdoms.beasts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.329) The minotaur, a monster of legend ({@code monsters/Monsters}): a bull-headed giant in gold-banded plate with
 * a great hammer, drawn as eanncrod's Minotaur (Blockbench Workshop, CC BY 4.0). It roams the mountains and comes as a
 * wilderness encounter there. It swings its hammer at anyone close, brings it down in a slam that throws everyone about
 * it off their feet, and from farther off lowers its horns and charges, goring whoever it runs into.
 */
public class Minotaur extends ActingMonster {
	static final double HEALTH = 140.0, ARMOR = 10.0, DAMAGE = 14.0, SPEED = 0.27, KNOCKBACK_RESIST = 0.8;
	/** Its swing: how long (ticks), when it lands, its reach. */
	static final int SWING_TICKS = 15, SWING_HIT = 8;
	static final double SWING_REACH = 2.5;
	/** Its slam: how long, when it lands, how far it reaches, how hard, how often (ticks). */
	static final int SLAM_TICKS = 40, SLAM_HIT = 22, SLAM_COOLDOWN = 140;
	static final double SLAM_REACH = 4.5;
	static final float SLAM_DAMAGE = 16.0F;
	/** Its charge: the most it runs (ticks), how fast (blocks a tick), its gore, how often, and the range it charges from. */
	static final int CHARGE_TICKS = 40, CHARGE_COOLDOWN = 200;
	static final double CHARGE_SPEED = 0.55, CHARGE_MIN = 6.0, CHARGE_MAX = 16.0;
	static final float GORE_DAMAGE = 12.0F;
	/** Ticks between blows. */
	static final int GAP = 12;

	private static final String[] ACTIONS = {"swing_1", "slam", "running"};

	private int gap, slamCooldown = SLAM_COOLDOWN / 2, chargeCooldown = CHARGE_COOLDOWN / 2;
	private Vec3 chargeDir = Vec3.ZERO;

	public Minotaur(EntityType<? extends Minotaur> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESIST).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.slamCooldown--;
		this.chargeCooldown--;
		LivingEntity target = this.getTarget();
		if (this.acting("running")) {
			this.charge(level);
			return;
		}
		if (this.acting()) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			if (this.acting("swing_1") && this.actionRun() == SWING_HIT) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), SWING_REACH, true, 0.8, 0.1);
				this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
			} else if (this.acting("slam") && this.actionRun() == SLAM_HIT) {
				this.slam(level);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = this.distanceTo(target);
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (--this.gap > 0) {
			this.getNavigation().moveTo(target, 1.1);
			return;
		}
		if (d > CHARGE_MIN && d < CHARGE_MAX && this.chargeCooldown <= 0 && this.hasLineOfSight(target)) {
			Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			this.chargeDir = to.normalize();
			this.chargeCooldown = CHARGE_COOLDOWN;
			this.act("running", CHARGE_TICKS);
			this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.8F);
		} else if (d < SLAM_REACH && this.slamCooldown <= 0) {
			this.slamCooldown = SLAM_COOLDOWN;
			this.act("slam", SLAM_TICKS);
		} else if (d < SWING_REACH + 1.0) {
			this.act("swing_1", SWING_TICKS);
		} else {
			this.getNavigation().moveTo(target, 1.1);
			return;
		}
		this.gap = GAP;
	}

	/** Charging: head down along its line, goring whoever it meets; a wall stops it dazed. */
	private void charge(ServerLevel level) {
		this.getNavigation().stop();
		this.setYRot((float) (Math.atan2(this.chargeDir.z, this.chargeDir.x) * 180.0 / Math.PI) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.setDeltaMovement(this.chargeDir.x * CHARGE_SPEED, this.getDeltaMovement().y, this.chargeDir.z * CHARGE_SPEED);
		if (this.strike(level, GORE_DAMAGE, 0.6, true, 2.0, 0.4) > 0) {
			this.playSound(SoundEvents.RAVAGER_ATTACK, 1.5F, 0.8F);
			this.stopActing();
			this.gap = GAP;
		} else if (this.horizontalCollision && this.actionRun() > 4) {
			this.playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
			this.stopActing();
			this.gap = GAP * 3;
		}
	}

	/** The slam: the ground breaks round it, everyone near thrown up. */
	private void slam(ServerLevel level) {
		this.strike(level, SLAM_DAMAGE, SLAM_REACH, false, 1.2, 0.6);
		BlockPos below = this.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1, this.getZ(), 60, 2.0, 0.2, 2.0, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 3, 1.5, 0.1, 1.5, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0F, 0.6F);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		// (its blows are its own moves)
		return false;
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
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.9F);
	}
}
