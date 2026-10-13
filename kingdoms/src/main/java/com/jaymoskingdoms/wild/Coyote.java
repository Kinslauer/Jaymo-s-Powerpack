package com.jaymoskingdoms.wild;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * (0.5.55) The coyote (after Better Animals Plus): a scrawny dog of the plains and badlands. By day it keeps its
 * distance and hunts rabbits and chickens; at night it bares its fangs, its eyes glow and it goes for players, in
 * twos and threes.
 */
public class Coyote extends WildBeast {
	static final double HEALTH = 10.0, SPEED = 0.33, DAMAGE = 4.0;

	public Coyote(EntityType<? extends Coyote> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Rabbit.class, false));
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Chicken.class, false));
	}

	@Override
	protected int variants() {
		return 1;
	}

	/** Night, when coyotes hunt people. */
	public boolean night() {
		return this.level().isDarkOutside();
	}

	@Override
	protected boolean hunting(Player player) {
		return this.night();
	}

	@Override
	public boolean hostileLook() {
		return this.night() || this.getTarget() instanceof Player;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.night() ? SoundEvents.FOX_SCREECH : SoundEvents.FOX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}
}
