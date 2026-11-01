package com.jaymoskingdoms.monsters;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) Ogres (after Mo' Creatures'): huge, slow brutes that hammer with their fists, or with a spiked club when they
 * have two heads. Now and then one brings both arms down on the ground: everyone near is hurt and thrown (no blocks are
 * broken). The green ogre roams the hills; the cave ogre (grey, tougher) the mountains by night; the fire ogre the Nether
 * and the badlands, and it sets what it strikes alight.
 */
public class Ogre extends LegendMonster {
	public static final int GREEN = 0, CAVE = 1, FIRE = 2;
	static final double HEALTH = 50.0, CAVE_HEALTH = 70.0, SPEED = 0.24, DAMAGE = 8.0, KNOCKBACK_RESISTANCE = 0.8;
	/** Its ground smash: how often it may (ticks), how near its target must be, its reach, damage and throw. */
	static final int SMASH_TICKS = 140;
	static final double SMASH_TRIGGER = 4.0, SMASH_RANGE = 5.0, SMASH_THROW = 1.2;
	static final float SMASH_DAMAGE = 7.0F;
	/** How long its fists take to come down in a smash (ticks). */
	static final int SMASH_WINDUP = 8;
	/** One ogre in this many has two heads (and a club). */
	static final int TWO_HEAD_ODDS = 2;
	/** How long the fire ogre's blows burn (seconds). */
	static final float FIRE_SECONDS = 5.0F;

	private static final EntityDataAccessor<Boolean> TWO_HEADS = SynchedEntityData.defineId(Ogre.class, EntityDataSerializers.BOOLEAN);

	private final int kind;
	private int smashCooldown = SMASH_TICKS, windup;

	public Ogre(EntityType<? extends Ogre> type, Level level, int kind) {
		super(type, level);
		this.kind = kind;
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes(int kind) {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, kind == CAVE ? CAVE_HEALTH : HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5).add(Attributes.STEP_HEIGHT, 1.0);
	}

	public int kind() {
		return this.kind;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && !com.jaymoskingdoms.InBalming.spares(this, target)
				&& !com.jaymoskingdoms.OgreHolds.spares(this, target)));
		// ((0.7.474) never a goblin or an orc: the ogres throw in with the greenskins, OgreHolds)
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true,
			(target, level) -> !com.jaymoskingdoms.OgreHolds.spares(this, target)));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(TWO_HEADS, false);
	}

	public boolean twoHeads() {
		return this.entityData.get(TWO_HEADS);
	}

	/** (0.7.426) Gives it two heads (and a club) or one: an outpost's brute, the king's guard. */
	public void setTwoHeads(boolean two) {
		this.entityData.set(TWO_HEADS, two);
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level) {
		this.entityData.set(TWO_HEADS, this.random.nextInt(TWO_HEAD_ODDS) == 0);
		return 0;
	}

	@Override
	protected int nextArm() {
		// a two-headed ogre favours its club arm
		return this.twoHeads() && this.random.nextInt(3) > 0 ? LEFT : this.random.nextBoolean() ? LEFT : RIGHT;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// (0.7.433) one Ambrose calmed: at its keeper's side
		com.jaymoskingdoms.InBalming.ogreStep(level, this);
		if (this.windup > 0) {
			if (--this.windup == 0) {
				this.smash(level);
			}
			return;
		}
		if (this.smashCooldown > 0) {
			this.smashCooldown--;
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive() && this.distanceToSqr(target) < SMASH_TRIGGER * SMASH_TRIGGER + this.getBbWidth()) {
			this.swingArm(BOTH);
			this.windup = SMASH_WINDUP;
			this.smashCooldown = SMASH_TICKS + this.random.nextInt(SMASH_TICKS / 2);
			this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.7F);
		}
	}

	/** Both fists into the ground: everything near is hurt and thrown up and away. */
	private void smash(ServerLevel level) {
		Vec3 at = this.position().add(this.getLookAngle().multiply(1.5, 0.0, 1.5));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 2, 0.6, 0.1, 0.6, 0.0);
		level.sendParticles(this.kind == FIRE ? ParticleTypes.FLAME : ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 30, SMASH_RANGE / 3, 0.1, SMASH_RANGE / 3, 0.05);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SMASH_RANGE),
			e -> e != this && !(e instanceof Ogre) && e.isAlive() && e.distanceToSqr(at) < SMASH_RANGE * SMASH_RANGE
				// ((0.7.474) the greenskins about it are spared the blow, so a fight in an orc camp doesn't turn them on each other)
				&& !com.jaymoskingdoms.OgreHolds.spares(this, e))) {
			if (hit instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			hit.hurtServer(level, this.damageSources().mobAttack(this), SMASH_DAMAGE);
			Vec3 away = hit.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize().scale(SMASH_THROW);
			hit.push(away.x, 0.6, away.z);
			hit.needsSync = true;
			if (this.kind == FIRE) {
				hit.igniteForSeconds(FIRE_SECONDS);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.kind == FIRE) {
			target.igniteForSeconds(FIRE_SECONDS);
		}
		return hit;
	}

	@Override
	public boolean fireImmune() {
		return this.kind == FIRE || super.fireImmune();
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
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 0.4F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.1F;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("two_heads", this.twoHeads());
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(TWO_HEADS, input.getBooleanOr("two_heads", false));
	}
}
