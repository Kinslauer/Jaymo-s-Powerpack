package com.jaymospowers.aether.dungeon;

import com.jaymospowers.aether.monster.AetherMonster;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A mimic: a sentry crate that was alive. It leaps out when a flagged crate is opened or broken, sits stunned for a second
 * while its lid slams, then comes for whoever opened it (3.5 damage, quick on its feet). Mimics never hurt one another.
 */
public class Mimic extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 32.0, SPEED = 0.33, DAMAGE = 3.5, FOLLOW = 12.0;
	/** Ticks it takes to unfold (it does nothing meanwhile). */
	public static final int SPAWN_TICKS = 20;

	private static final EntityDataAccessor<Integer> UNFOLD = SynchedEntityData.defineId(Mimic.class, EntityDataSerializers.INT);

	public Mimic(EntityType<? extends Mimic> type, Level level) {
		super(type, level, 1, 0, -1);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return Mimic.this.unfolded() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return Mimic.this.unfolded() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(UNFOLD, 0);
	}

	/** Ticks since it leapt out, up to {@link #SPAWN_TICKS} (for the lid on its model). */
	public int unfold() {
		return this.entityData.get(UNFOLD);
	}

	public boolean unfolded() {
		return this.unfold() >= SPAWN_TICKS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.unfold() < SPAWN_TICKS) {
			this.entityData.set(UNFOLD, this.unfold() + 1);
			this.getNavigation().stop();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// mimics never hurt each other
		if (source.getEntity() instanceof Mimic) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
			this.setTarget(player);
		}
		return hurt;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof Mimic) && super.canAttack(target);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DungeonSounds.get("block.chest.close");
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DungeonSounds.get("block.chest.close");
	}
}
