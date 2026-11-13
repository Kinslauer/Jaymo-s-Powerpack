package com.jaymoskingdoms.creatures;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The piercer: a cave hunter that hangs from the ceiling and looks like a stalactite. It holds still, and drops on whoever
 * walks under it: the drop hurts by the height it falls (a point of the fall, and its weight behind it), and a piercer that
 * misses is a slow, fat grub on the floor with a hard spike, which cannot climb back up. A hanging one can be shot or struck
 * like any, and a watchful eye picks it out from the dripstone ({@link Haunts}).
 */
public class Piercer extends Beast {
	static final double HEALTH = 16.0, SPEED = 0.1, DAMAGE = 3.0;
	/** It drops on something this near sideways (blocks) and between this far and this far below (blocks). */
	static final double UNDER = 1.6, DROP_MIN = 1.5, DROP_MAX = 14.0;
	/** Landing on someone: this much and this much more for each block fallen (capped). */
	static final float LAND_HARM = 3.0F, LAND_PER_BLOCK = 1.2F, LAND_MOST = 16.0F;
	/** Looks: hanging, falling, on the ground. */
	public static final int HANGING = 0, FALLING = 1, GROUND = 2;
	private double dropFrom;

	public Piercer(EntityType<? extends Piercer> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ARMOR, 6.0).add(Attributes.FOLLOW_RANGE, 8.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.5));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL
			&& this.look() == GROUND));
	}

	/** Whether it is up there still, waiting. */
	public boolean hanging() {
		return this.look() == HANGING;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		switch (this.look()) {
			case HANGING -> {
				this.setNoGravity(true);
				this.setDeltaMovement(0.0, 0.0, 0.0);
				this.getNavigation().stop();
				BlockPos roof = BlockPos.containing(this.getX(), this.getBoundingBox().maxY + 0.1, this.getZ());
				if (level.getBlockState(roof).isAir()) {
					this.drop();
					return;
				}
				if (this.tickCount % 5 == 0) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.getX() - UNDER, this.getY() - DROP_MAX, this.getZ() - UNDER,
						this.getX() + UNDER, this.getY() - DROP_MIN, this.getZ() + UNDER), this::prey)) {
						if (e.isAlive()) {
							this.drop();
							break;
						}
					}
				}
			}
			case FALLING -> {
				if (this.onGround()) {
					this.land(level);
				}
			}
			default -> {
			}
		}
	}

	private boolean prey(LivingEntity e) {
		return !(e instanceof Enemy) && !e.is(EntityTypeTags.UNDEAD) && !(e instanceof Piercer) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
			&& !e.isSpectator() && e.getBbHeight() > 0.4F;
	}

	private void drop() {
		this.setLook(FALLING);
		this.setNoGravity(false);
		this.dropFrom = this.getY();
		this.playSound(SoundEvents.POINTED_DRIPSTONE_LAND, 1.0F, 0.7F);
	}

	private void land(ServerLevel level) {
		this.setLook(GROUND);
		float fell = (float) Math.max(0.0, this.dropFrom - this.getY());
		float harm = Math.min(LAND_MOST, LAND_HARM + LAND_PER_BLOCK * fell);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.4), this::prey)) {
			e.hurtServer(level, level.damageSources().stalagmite(), harm);
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DRIPSTONE_BLOCK.defaultBlockState()), this.getX(), this.getY(0.3), this.getZ(), 14, 0.3, 0.2, 0.3, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.hanging() && this.isAlive()) {
			this.drop();
		}
		return hurt;
	}

	@Override
	public boolean isPushable() {
		return !this.hanging();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.POINTED_DRIPSTONE_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.POINTED_DRIPSTONE_BREAK;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Pose", this.look());
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setLook(input.getIntOr("Pose", HANGING));
		this.setNoGravity(this.look() == HANGING);
	}
}
