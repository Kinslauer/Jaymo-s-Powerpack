package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The tempest: a small blighted storm-beetle that floats above its prey and lobs thunderballs (each bursts into a field
 * of crackling that shocks and slows). It cannot stand the sun: after {@link #SUN_TICKS} ticks in daylight it starts to
 * hide, and nothing can stop that; it is gone {@link #HIDE_TICKS} ticks later.
 */
public class Tempest extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 28.0, FOLLOW = 20.0, SPEED = 0.1;
	public static final float THRUST = 0.035F;
	/** A volley is 65 ticks: the charge shows from tick 1, the sound at 10, the throw at 25. */
	public static final int VOLLEY = 65, CHARGE_ANIM = 1, CHARGE_SOUND = 10, THROW = 25;
	/** It hovers this far from its target, this high above it, and throws only inside {@link #MAX_RANGE}. */
	public static final double HOVER_DISTANCE = 9.0, HOVER_HEIGHT = 4.0, MAX_RANGE = 28.0;
	public static final float BALL_SPEED = 0.8F;
	/** Ticks in sunlight before it hides, and how long the hide takes. */
	public static final int SUN_TICKS = 200, HIDE_TICKS = 95;

	/** 1 while it charges a thunderball. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Tempest.class, EntityDataSerializers.INT);
	/** How far through hiding it is (ticks); 0 not hiding. */
	private static final EntityDataAccessor<Integer> HIDING = SynchedEntityData.defineId(Tempest.class, EntityDataSerializers.INT);

	private int sunTicks;

	public Tempest(EntityType<? extends Tempest> type, Level level) {
		super(type, level, 0, 0, -1);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FLYING_SPEED, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new VolleyGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		return navigation;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, 0);
		entityData.define(HIDING, 0);
	}

	public boolean charging() {
		return this.entityData.get(ACTION) == 1;
	}

	/** 0 to 1: how far through hiding from the sun it is. */
	public float hideProgress() {
		return Math.min(1.0F, this.entityData.get(HIDING) / (float)HIDE_TICKS);
	}

	@Override
	protected boolean blighted() {
		return true;
	}

	@Override
	protected float getFlyingSpeed() {
		return THRUST;
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// its own thunderballs and the fields they leave never hurt it
		if (source.getEntity() instanceof Tempest) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(AetherEffects.ELECTRIC_SHOCK) && super.canBeAffected(effect);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		int hiding = this.entityData.get(HIDING);
		if (hiding > 0) {
			// once it has started, it cannot be stopped
			this.entityData.set(HIDING, hiding + 1);
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.5, this.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
			this.setTarget(null);
			this.getNavigation().stop();
			if (hiding >= HIDE_TICKS) {
				this.discard();
			}
			return;
		}
		if (this.inSun()) {
			if (++this.sunTicks >= SUN_TICKS) {
				this.entityData.set(HIDING, 1);
			}
		} else if (this.sunTicks > 0) {
			this.sunTicks--;
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("sun_ticks", this.sunTicks);
		output.putInt("hiding", this.entityData.get(HIDING));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.sunTicks = input.getIntOr("sun_ticks", 0);
		this.entityData.set(HIDING, input.getIntOr("hiding", 0));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MonsterSounds.get("entity.vex.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.vex.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.vex.death");
	}

	/** Hover over the target and throw a thunderball every volley. (Aether II's hover aimed at a point near the world's origin; this aims at the target.) */
	private static final class VolleyGoal extends Goal {
		private final Tempest tempest;
		private int tick;

		VolleyGoal(Tempest tempest) {
			this.tempest = tempest;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.tempest.getTarget();
			return target != null && target.isAlive() && this.tempest.entityData.get(HIDING) == 0;
		}

		@Override
		public void start() {
			this.tick = 0;
		}

		@Override
		public void stop() {
			this.tempest.entityData.set(ACTION, 0);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Tempest t = this.tempest;
			LivingEntity target = t.getTarget();
			if (target == null) {
				return;
			}
			t.getLookControl().setLookAt(target, 30.0F, 30.0F);
			// a spot HOVER_DISTANCE away from the target on this side of it, up in the air
			Vec3 away = t.position().subtract(target.position());
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			Vec3 spot = target.position().add(flat.scale(HOVER_DISTANCE)).add(0.0, HOVER_HEIGHT, 0.0);
			t.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.6);
			double distance = t.distanceTo(target);
			if (distance > MAX_RANGE || !t.hasLineOfSight(target)) {
				this.tick = 0;
				t.entityData.set(ACTION, 0);
				return;
			}
			int at = this.tick++;
			if (at == CHARGE_ANIM) {
				t.entityData.set(ACTION, 1);
			}
			if (at == CHARGE_SOUND) {
				t.playSound(MonsterSounds.get("entity.evoker.cast_spell"), 1.0F, 1.5F);
			}
			if (at == THROW) {
				AetherShot.fire(AetherShot.Kind.THUNDERBALL, t, t.getEyePosition().add(t.getLookAngle().scale(0.8)), target.getEyePosition(), BALL_SPEED, 1.0F);
				t.playSound(MonsterSounds.get("entity.blaze.shoot"), 1.0F, 1.6F);
			}
			if (this.tick >= VOLLEY) {
				this.tick = 0;
				t.entityData.set(ACTION, 0);
			}
		}
	}
}
