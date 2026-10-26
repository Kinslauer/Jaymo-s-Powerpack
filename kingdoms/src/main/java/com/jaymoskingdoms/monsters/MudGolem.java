package com.jaymoskingdoms.monsters;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.5.56) The mud golem (Kingdoms' own, for the swamps and mangroves): a slow, heavy heap of living mud that drags you
 * down with every blow (slowness), throws off knockback, and soaks itself whole again in water or the rain; out of the
 * wet, fire bakes it brittle (it takes more from fire). Drops clay and mud.
 */
public class MudGolem extends LegendMonster {
	static final double HEALTH = 40.0, SPEED = 0.22, DAMAGE = 6.0, KNOCKBACK_RESISTANCE = 0.7;
	/** The slowness a blow leaves (ticks, amplifier). */
	static final int SLOW_TICKS = 60, SLOW_LEVEL = 1;
	/** How much it mends each second in water or rain. */
	static final float SOAK_HEAL = 1.0F;
	/** How much more fire hurts it. */
	static final float FIRE_WEAKNESS = 2.0F;

	public MudGolem(EntityType<? extends MudGolem> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected int variants() {
		return 2;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		// (0.7.217) villagers too, for less ({@link com.jaymoskingdoms.VillagerHunters#VILLAGER_DAMAGE})
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, true));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL), this);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), target.getX(), target.getY() + 1.0,
				target.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
		}
		return hit;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 20 == 0 && this.isInWaterOrRain() && this.getHealth() < this.getMaxHealth()) {
			this.heal(SOAK_HEAL);
			level.sendParticles(ParticleTypes.DRIPPING_WATER, this.getX(), this.getY() + 1.5, this.getZ(), 4, 0.4, 0.6, 0.4, 0.0);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && !this.isInWaterOrRain()) {
			damage *= FIRE_WEAKNESS;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.MUD_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.MUD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.MUD_BREAK;
	}

	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.MUD_STEP, 0.8F, 0.6F);
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}
}
