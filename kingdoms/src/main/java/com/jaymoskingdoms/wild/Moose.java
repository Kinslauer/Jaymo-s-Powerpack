package com.jaymoskingdoms.wild;

import java.util.EnumSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.55) The moose (after Better Animals Plus): a huge, short-tempered bull of the swamps and taiga. It browses
 * alone, and charges any player who comes within {@link #TEMPER_RANGE} blocks. Four looks.
 *
 * <p>With Cube Animals in the pack, ours is the bull and Cube's moose are the cows (Andy): a bull that spawns on its own
 * brings {@link #COWS_MIN} to {@link #COWS_MAX} of them, their antlers scaled away, and keeps by them ({@link KeepByCows}).
 */
public class Moose extends WildBeast {
	static final double HEALTH = 52.0, SPEED = 0.3, DAMAGE = 7.0, KNOCKBACK_RESISTANCE = 0.7, ATTACK_KNOCKBACK = 1.5;
	/** How close a player can come before it charges (blocks). */
	static final double TEMPER_RANGE = 15.0;
	/** Cube Animals' moose, the cows, and how many come with a bull. */
	static final String COW = "cubeanimals:moose";
	static final int COWS_MIN = 1, COWS_MAX = 2;
	/** Cube's antler scale is added to 1, so this leaves a cow none. */
	static final float COW_ANTLERS = -1.0F;
	/** How often the bull looks for its cows (ticks), how far it sees them, and how far off it lets them get before it walks back to within the near distance (blocks). */
	static final int COW_CHECK_TICKS = 100;
	static final double COW_SIGHT = 32.0, COW_FAR = 10.0, COW_NEAR = 5.0;

	public Moose(EntityType<? extends Moose> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
			.add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	static @Nullable EntityType<?> cowType() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(COW)).orElse(null);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(5, new KeepByCows(this));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		EntityType<?> cow = cowType();
		if (cow != null && (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION)) {
			for (int i = COWS_MIN + this.random.nextInt(COWS_MAX - COWS_MIN + 1); i > 0; i--) {
				this.bringCow(cow, level, difficulty, reason);
			}
		}
		return data;
	}

	/** A cow where the bull stands (they step apart), without antlers. */
	private void bringCow(EntityType<?> type, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason) {
		if (!(type.create(level.getLevel(), reason) instanceof Mob cow)) {
			return;
		}
		cow.snapTo(this.getX(), this.getY(), this.getZ(), this.random.nextFloat() * 360.0F, 0.0F);
		cow.finalizeSpawn(level, difficulty, reason, null);
		try {
			cow.getClass().getMethod("setAntlersScale", float.class).invoke(cow, COW_ANTLERS);
		} catch (ReflectiveOperationException changed) {
			// a Cube Animals without that setter: the cow keeps the antlers it rolled
		}
		level.addFreshEntityWithPassengers(cow);
	}

	/** The bull walks back to its nearest cow when she has got more than {@link #COW_FAR} blocks off, unless it is after someone. */
	private static final class KeepByCows extends Goal {
		private final Moose bull;
		private @Nullable Entity cow;

		KeepByCows(Moose bull) {
			this.bull = bull;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.bull.getTarget() != null || this.bull.getRandom().nextInt(reducedTickDelay(COW_CHECK_TICKS)) != 0) {
				return false;
			}
			EntityType<?> type = cowType();
			if (type == null) {
				return false;
			}
			Entity nearest = null;
			for (Entity e : this.bull.level().getEntities(this.bull, this.bull.getBoundingBox().inflate(COW_SIGHT), e -> e.getType() == type && e.isAlive())) {
				if (nearest == null || e.distanceToSqr(this.bull) < nearest.distanceToSqr(this.bull)) {
					nearest = e;
				}
			}
			this.cow = nearest;
			return nearest != null && nearest.distanceToSqr(this.bull) > COW_FAR * COW_FAR;
		}

		@Override
		public boolean canContinueToUse() {
			return this.cow != null && this.cow.isAlive() && this.bull.getTarget() == null && !this.bull.getNavigation().isDone()
				&& this.cow.distanceToSqr(this.bull) > COW_NEAR * COW_NEAR;
		}

		@Override
		public void start() {
			this.bull.getNavigation().moveTo(this.cow, 1.0);
		}

		@Override
		public void stop() {
			this.cow = null;
			this.bull.getNavigation().stop();
		}
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
	protected boolean hunting(LivingEntity target) {
		return target.distanceTo(this) < TEMPER_RANGE;
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
