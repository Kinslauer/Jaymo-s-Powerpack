package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.monsters.Mimic;
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
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A living brazier: one of the braziers that light the labyrinths' halls ({@link LabyrinthKeep}) is not a brazier at all.
 * It is the chest mimic's cousin ({@link Mimic}: still as a brazier until something comes close, touches or strikes it),
 * but awake it hops after you only to {@link #KEEP} blocks, and spits fire: every {@link #SPIT_EVERY} ticks a gout of
 * flame crosses to anyone it can see within {@link #SPIT_RANGE} blocks and sets them alight. It is fire-proof, and sleeps
 * again when left alone ({@link Mimic}'s own rule).
 */
public class BrazierMimic extends Mimic {
	static final double HEALTH = 34.0, SPEED = 0.26, SPIT_DAMAGE = 4.0;
	/** How far it spits (blocks), how often (ticks), how long it sets you alight (seconds), and how near it comes (blocks). */
	static final double SPIT_RANGE = 12.0;
	static final int SPIT_EVERY = 50, ALIGHT = 3;
	static final float KEEP = 6.0F;
	/** The flame's colour. */
	static final int FLAME = 0xFF8A20;

	/** Ticks to its next gout. */
	private int spit = SPIT_EVERY;

	public BrazierMimic(EntityType<? extends BrazierMimic> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, SPIT_DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MoveTowardsTargetGoal(this, 1.0, KEEP) {
			@Override
			public boolean canUse() {
				return !BrazierMimic.this.shut() && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> !this.shut() && level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.shut()) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || --this.spit > 0) {
			return;
		}
		if (this.distanceTo(target) > SPIT_RANGE || !this.hasLineOfSight(target)) {
			this.spit = 10;
			return;
		}
		this.spit = SPIT_EVERY;
		Vec3 from = this.position().add(0.0, this.getBbHeight() * 0.9, 0.0);
		Vec3 to = target.getBoundingBox().getCenter();
		Fx.beam(level, from, to, 0.18F, FLAME, 8);
		level.playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.9F);
		if (target.hurtServer(level, this.damageSources().mobAttack(this), (float) SPIT_DAMAGE)) {
			target.setRemainingFireTicks(ALIGHT * 20);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.shut() ? null : SoundEvents.FIRE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLAZE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLAZE_DEATH;
	}
}
