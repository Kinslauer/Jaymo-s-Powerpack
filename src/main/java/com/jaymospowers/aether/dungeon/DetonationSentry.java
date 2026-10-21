package com.jaymospowers.aether.dungeon;

import com.jaymospowers.aether.monster.AetherMonster;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A detonation sentry: a stone cube on short legs that the ruins' spawners and traps send out. It stands dead still until a
 * player has been in sight for {@link #WAKE_TICKS}, then runs at them, lights its fuse within {@link #IGNITE_RANGE} blocks, beeps
 * for {@link #FUSE_TICKS} ticks (it plants itself for the last twelve) and goes off: a blast of radius 2 that breaks nothing.
 */
public class DetonationSentry extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 14.0, SPEED = 0.6, FOLLOW = 10.0;
	/** The target may be this far above or below it (blocks). */
	public static final double VERTICAL_RANGE = 4.0;
	/** Ticks a target must be in range before it wakes; how close it must get to light the fuse. */
	public static final int WAKE_TICKS = 24;
	public static final double IGNITE_RANGE = 2.45;
	/** The fuse's length, and the tick it stops running (so you can run). */
	public static final int FUSE_TICKS = 60, PLANT_TICK = 48;
	public static final float BLAST = 2.0F;

	/** -1 unlit, else ticks since the fuse was lit. */
	private static final EntityDataAccessor<Integer> FUSE = SynchedEntityData.defineId(DetonationSentry.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(DetonationSentry.class, EntityDataSerializers.BOOLEAN);

	private int noticed;

	public DetonationSentry(EntityType<? extends DetonationSentry> type, Level level) {
		super(type, level, 0, -1, 1);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_DAMAGE, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new ChaseGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> Math.abs(target.getY() - this.getY()) <= VERTICAL_RANGE));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(FUSE, -1);
		entityData.define(AWAKE, false);
	}

	/** Ticks since the fuse was lit, or -1. */
	public int fuse() {
		return this.entityData.get(FUSE);
	}

	public boolean awake() {
		return this.entityData.get(AWAKE);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (!this.awake()) {
			this.noticed = target != null ? this.noticed + 1 : 0;
			if (this.noticed >= WAKE_TICKS) {
				this.entityData.set(AWAKE, true);
			}
			return;
		}
		int fuse = this.fuse();
		if (fuse < 0) {
			if (target != null && this.distanceToSqr(target) <= IGNITE_RANGE * IGNITE_RANGE) {
				this.entityData.set(FUSE, 0);
				this.playSound(DungeonSounds.get("entity.creeper.primed"), 1.0F, 0.9F);
			}
			return;
		}
		fuse++;
		this.entityData.set(FUSE, fuse);
		if (fuse >= PLANT_TICK) {
			this.getNavigation().stop();
		}
		if (fuse % 10 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("block.note_block.hat"), SoundSource.HOSTILE, 1.0F, 0.8F + fuse * 0.01F);
		}
		if (fuse >= FUSE_TICKS) {
			level.explode(this, this.getX(), this.getY(0.5), this.getZ(), BLAST, Level.ExplosionInteraction.NONE);
			this.discard();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DungeonSounds.get("entity.iron_golem.damage");
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DungeonSounds.get("entity.iron_golem.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(DungeonSounds.get("block.stone.step"), 0.5F, 1.2F);
	}

	/** Runs at its target once awake, until the last twelve ticks of the fuse. */
	private static final class ChaseGoal extends Goal {
		private final DetonationSentry sentry;

		ChaseGoal(DetonationSentry sentry) {
			this.sentry = sentry;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.sentry.awake() && this.sentry.getTarget() != null && this.sentry.fuse() < PLANT_TICK;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public void tick() {
			LivingEntity target = this.sentry.getTarget();
			if (target != null) {
				this.sentry.getNavigation().moveTo(target, 1.0);
			}
		}
	}
}
