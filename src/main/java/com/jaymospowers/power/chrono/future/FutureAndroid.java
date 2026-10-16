package com.jaymospowers.power.chrono.future;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * An android of the future: a machine in a person's shape, still on the patrol it was set long ago, shooting anyone
 * living and any of the dead it meets. Most carry a modern gun ({@link FutureGuns}) and keep their distance; the rest
 * close in to strike. They leave no body, only scrap: nuggets, redstone, now and then a copper ingot.
 */
public class FutureAndroid extends Monster {
	static final double HEALTH = 30.0, DAMAGE = 6.0, SPEED = 0.3, FOLLOW = 40.0, ARMOR = 6.0;
	/** The share that carry a gun. */
	static final float ARMED = 0.75F;

	public FutureAndroid(EntityType<? extends FutureAndroid> type, Level level) {
		super(type, level);
		this.xpReward = 10;
		addTag("jaymoskitchen.no_corpse");
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.ARMOR, ARMOR)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, FutureAndroid.class, FutureMech.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Zombie.class, true));
	}

	/** Sent out by {@link FutureMachines}: most get a gun. */
	public void outfit(ServerLevel level) {
		if (getRandom().nextFloat() < ARMED) {
			FutureGuns.arm(this, getRandom());
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel level && getHealth() < getMaxHealth() * 0.4F && this.tickCount % 12 == 0) {
			// damaged: it sparks
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 4, 0.25, 0.4, 0.25, 0.05);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Items.IRON_NUGGET, 1 + getRandom().nextInt(5)));
		if (getRandom().nextFloat() < 0.5F) {
			spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Items.REDSTONE, 1 + getRandom().nextInt(3)));
		}
		if (getRandom().nextFloat() < 0.15F) {
			spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Items.COPPER_INGOT));
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.6, 0.3, 0.2);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.6F + getRandom().nextFloat() * 0.2F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return distance > 128.0 * 128.0;
	}
}
