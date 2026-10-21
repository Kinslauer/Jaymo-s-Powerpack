package com.jaymospowers.aether.dungeon;

import com.jaymospowers.aether.monster.AetherMonster;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sentry golem: the ruins' heavy, with a demolition hammer. It clubs what is beside it (4 damage) and lobs demolition bombs
 * at a target 7 to 16 blocks away that it has seen for a second (one every 40 ticks). Every blow it takes sends it strolling
 * away for 100 to 199 ticks to think. Slashes bounce off its plates; picks and fists dent it.
 */
public class SentryGolem extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 35.0, SPEED = 0.25, DAMAGE = 4.0, FOLLOW = 15.0;
	/** It lobs from this far to this far (blocks), after seeing the target this long (ticks), then waits this long. */
	public static final double THROW_MIN = 7.2, THROW_MAX = 16.0;
	public static final int SEE_TICKS = 20, THROW_COOLDOWN = 40, WINDUP = 20;
	public static final float BOMB_SPEED = 1.1F;
	/** After a blow it walks away for this many ticks, at least and at most. */
	public static final int AVOID_MIN = 100, AVOID_MAX = 199;

	/** Ticks into a throw (the arm is up), 0 when idle. */
	private static final EntityDataAccessor<Integer> WIND = SynchedEntityData.defineId(SentryGolem.class, EntityDataSerializers.INT);

	private int avoid, cooldown, seen;

	public SentryGolem(EntityType<? extends SentryGolem> type, Level level) {
		super(type, level, 1, -1, 0);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new AvoidGoal(this));
		this.goalSelector.addGoal(2, new LobGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return SentryGolem.this.avoid <= 0 && super.canUse();
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
		entityData.define(WIND, 0);
	}

	/** Ticks into a throw (its arm is raised), 0 when idle. */
	public int wind() {
		return this.entityData.get(WIND);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.avoid = AVOID_MIN + this.random.nextInt(AVOID_MAX - AVOID_MIN + 1);
			this.entityData.set(WIND, 0);
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.avoid > 0) {
			this.avoid--;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		LivingEntity target = this.getTarget();
		this.seen = target != null && this.hasLineOfSight(target) ? this.seen + 1 : 0;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DungeonSounds.get("entity.iron_golem.hurt");
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DungeonSounds.get("entity.iron_golem.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(DungeonSounds.get("entity.iron_golem.step"), 0.8F, 0.9F);
	}

	/** After a blow: strolls away from whatever is hurting it. */
	private static final class AvoidGoal extends Goal {
		private final SentryGolem golem;

		AvoidGoal(SentryGolem golem) {
			this.golem = golem;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.golem.avoid > 0 && this.golem.getTarget() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse() && !this.golem.getNavigation().isDone();
		}

		@Override
		public void start() {
			LivingEntity from = this.golem.getTarget();
			if (from == null) {
				return;
			}
			Vec3 spot = DefaultRandomPos.getPosAway(this.golem, 10, 4, from.position());
			if (spot != null) {
				this.golem.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
			}
		}
	}

	/** Lobs bombs at a target in the throwing band that it has seen for a while. */
	private static final class LobGoal extends Goal {
		private final SentryGolem golem;
		private int wind;

		LobGoal(SentryGolem golem) {
			this.golem = golem;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private boolean inBand(LivingEntity target) {
			double d = this.golem.distanceTo(target);
			return d >= THROW_MIN && d <= THROW_MAX;
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.golem.getTarget();
			return target != null && this.golem.avoid <= 0 && this.golem.cooldown <= 0 && this.golem.seen >= SEE_TICKS && this.inBand(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.golem.getTarget();
			return target != null && this.golem.avoid <= 0 && this.wind <= WINDUP && this.inBand(target);
		}

		@Override
		public void start() {
			this.wind = 0;
			this.golem.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = this.golem.getTarget();
			if (target == null) {
				return;
			}
			this.golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
			this.wind++;
			this.golem.entityData.set(WIND, this.wind);
			if (this.wind == WINDUP) {
				DemolitionBomb.lob(this.golem, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), BOMB_SPEED);
				this.golem.cooldown = THROW_COOLDOWN;
				this.golem.playSound(DungeonSounds.get("entity.snowball.throw"), 1.0F, 0.6F);
			}
		}

		@Override
		public void stop() {
			this.wind = 0;
			this.golem.entityData.set(WIND, 0);
		}
	}
}
