package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.creatures.Beast;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Unburied: a giant of bones that gets up at night where many dead were left lying together
 * ({@code NightThings.unburied}: the bodies are what it is made of, and they are gone when it stands). A great foe
 * (its own bar, a posture to break). It is slow and its blows throw people. A field where the dead are buried or
 * burned never raises one.
 */
public class Unburied extends Beast {
	static final double HEALTH = 220.0, SPEED = 0.2, DAMAGE = 12.0, KNOCKBACK = 2.5;

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_12);

	public Unburied(EntityType<? extends Unburied> type, Level level) {
		super(type, level);
		this.xpReward = 100;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.ENTITY_INTERACTION_RANGE, 4.5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 5 == 0) {
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.4F;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}
}
