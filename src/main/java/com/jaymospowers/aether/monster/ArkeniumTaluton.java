package com.jaymospowers.aether.monster;

import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * The arkenium taluton: a hunched, steel-plated brute of the dark that walks up on you and hits for 8, hard enough to
 * throw you back. It barely flinches from blows (knockback 75 percent resisted) and shrugs off swords; picks and fists
 * hurt it most.
 */
public class ArkeniumTaluton extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 34.0, SPEED = 0.23, DAMAGE = 8.0, KNOCKBACK = 1.2, KNOCKBACK_RESISTANCE = 0.75, STEP = 1.0;
	/** Ticks between swings. */
	public static final int SWING_INTERVAL = 40;

	public ArkeniumTaluton(EntityType<? extends ArkeniumTaluton> type, Level level) {
		super(type, level, 1, -1, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.STEP_HEIGHT, STEP);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			protected int getAttackInterval() {
				return this.adjustedTickDelay(SWING_INTERVAL);
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.iron_golem.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.iron_golem.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MonsterSounds.get("entity.iron_golem.step"), 0.6F, 0.8F);
	}
}
