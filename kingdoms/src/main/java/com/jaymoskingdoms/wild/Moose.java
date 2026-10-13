package com.jaymoskingdoms.wild;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * (0.5.55) The moose (after Better Animals Plus): a huge, short-tempered bull of the swamps and taiga. It browses
 * alone, and charges any player who comes within {@link #TEMPER_RANGE} blocks. Four looks.
 */
public class Moose extends WildBeast {
	static final double HEALTH = 52.0, SPEED = 0.3, DAMAGE = 7.0, KNOCKBACK_RESISTANCE = 0.7, ATTACK_KNOCKBACK = 1.5;
	/** How close a player can come before it charges (blocks). */
	static final double TEMPER_RANGE = 15.0;

	public Moose(EntityType<? extends Moose> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected int variants() {
		return 4;
	}

	@Override
	protected double chaseSpeed() {
		return 1.5;
	}

	@Override
	protected boolean hunting(Player player) {
		return player.distanceTo(this) < TEMPER_RANGE;
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
	public float getVoicePitch() {
		return 1.3F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
