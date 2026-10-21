package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The zephyr: a big, soft, wind-bellied flier of the open sky. It has no bite. Close in (under {@link #BLOW_RANGE}
 * blocks) it circles you, then draws a breath and blows a gust that shoves everything in a long box ahead of it off its
 * feet (a webbed thing far harder). Farther out it spits webbing balls at you instead, unless you are already webbed.
 */
public class Zephyr extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 12.0, FOLLOW = 30.0, SPEED = 0.1;
	/** What it flies by: how hard it pushes through the air each tick. */
	public static final float THRUST = 0.03F;
	/** Blow Away: a 100-tick cycle. 0-40 it circles the target, 41 the breath begins, 50 the sound, 65-89 the gust. */
	public static final double BLOW_RANGE = 8.0, CIRCLE = 4.0;
	public static final int BLOW_CYCLE = 100, CIRCLE_END = 40, BLOW_BEGIN = 41, BLOW_SOUND = 50, GUST_BEGIN = 65, GUST_END = 89;
	/** The gust's box: width, height and length (blocks) ahead of it. */
	public static final double GUST_WIDTH = 5.0, GUST_HEIGHT = 2.0, GUST_LENGTH = 10.5;
	/** The shove each tick of the gust: ordinary, on something webbed, and the lift that goes with it. */
	public static final double PUSH = 0.2, WEBBED_PUSH = 1.2, LIFT = 0.04;
	/** Webbing shot: every 60 ticks from this far to this far; sound at 50, the spit at 60. */
	public static final double SHOOT_MIN = 8.0, SHOOT_MAX = 40.0;
	public static final int SHOOT_CYCLE = 60, SHOOT_SOUND = 50, SHOOT_ANIM = 53;
	public static final float BALL_SPEED = 0.9F;

	/** 0 resting, 1 blowing (the breath and the gust), 2 spitting. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Zephyr.class, EntityDataSerializers.INT);

	public Zephyr(EntityType<? extends Zephyr> type, Level level) {
		super(type, level, 0, 0, -1);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FLYING_SPEED, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new AttackGoal(this));
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
	}

	/** What it is doing, for its model: 0 resting, 1 blowing, 2 spitting. */
	public int action() {
		return this.entityData.get(ACTION);
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
	protected SoundEvent getAmbientSound() {
		return MonsterSounds.get("entity.breeze.idle_air");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.breeze.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.breeze.death");
	}

	private static final class AttackGoal extends Goal {
		private final Zephyr zephyr;
		private int blow, shoot;
		private Vec3 gust = Vec3.ZERO;

		AttackGoal(Zephyr zephyr) {
			this.zephyr = zephyr;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.zephyr.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public void stop() {
			this.blow = 0;
			this.shoot = 0;
			this.zephyr.entityData.set(ACTION, 0);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.zephyr.getTarget();
			if (target == null || !(this.zephyr.level() instanceof ServerLevel level)) {
				return;
			}
			this.zephyr.getLookControl().setLookAt(target, 30.0F, 30.0F);
			double distance = this.zephyr.distanceTo(target);
			if (this.blow > 0 || distance <= BLOW_RANGE) {
				this.shoot = 0;
				this.blow(level, target);
			} else {
				this.shoot(level, target, distance);
			}
		}

		/** Circle, breathe in, blow. */
		private void blow(ServerLevel level, LivingEntity target) {
			Zephyr z = this.zephyr;
			int t = this.blow++;
			if (t < CIRCLE_END) {
				if (t % 8 == 0) {
					z.getMoveControl().setWantedPosition(target.getX() + (z.random.nextDouble() - 0.5) * 2.0 * CIRCLE,
						target.getY() + 1.0 + z.random.nextDouble() * 2.0, target.getZ() + (z.random.nextDouble() - 0.5) * 2.0 * CIRCLE, 0.7);
				}
			} else {
				// holding still for the breath and the gust
				z.getMoveControl().setWantedPosition(z.getX(), z.getY(), z.getZ(), 0.0);
				z.setDeltaMovement(z.getDeltaMovement().scale(0.8));
			}
			if (t == BLOW_BEGIN) {
				z.entityData.set(ACTION, 1);
			}
			if (t == BLOW_SOUND) {
				z.playSound(MonsterSounds.get("entity.breeze.inhale"), 1.5F, 0.8F);
			}
			if (t == GUST_BEGIN) {
				Vec3 toward = target.position().subtract(z.position());
				this.gust = new Vec3(toward.x, 0.0, toward.z).normalize();
				z.playSound(MonsterSounds.get("entity.breeze.wind_burst"), 2.0F, 0.9F);
			}
			if (t >= GUST_BEGIN && t <= GUST_END) {
				this.gust(level);
			}
			if (this.blow >= BLOW_CYCLE) {
				this.blow = 0;
				z.entityData.set(ACTION, 0);
			}
		}

		/** The gust: everything in the box ahead of it is pushed along, and lifted a little. */
		private void gust(ServerLevel level) {
			Zephyr z = this.zephyr;
			Vec3 mid = z.position().add(this.gust.scale(GUST_LENGTH * 0.5 + 1.0));
			double half = Math.max(GUST_WIDTH, GUST_LENGTH) * 0.5;
			AABB box = new AABB(mid.x - half, z.getY() - 0.5, mid.z - half, mid.x + half, z.getY() + GUST_HEIGHT + 0.5, mid.z + half);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != z && !(e instanceof Zephyr) && inGust(e))) {
				double push = victim.hasEffect(AetherEffects.WEBBED) ? WEBBED_PUSH : PUSH;
				victim.push(this.gust.x * push, LIFT, this.gust.z * push);
				victim.syncVelocity = true;
			}
			Vec3 at = z.position().add(this.gust.scale(1.5 + z.random.nextDouble() * (GUST_LENGTH - 1.5)));
			level.sendParticles(ParticleTypes.CLOUD, at.x, z.getY() + 0.8, at.z, 3, 0.8, 0.5, 0.8, 0.05);
		}

		/** Whether {@code e} is inside the gust's long box (width across, length along). */
		private boolean inGust(LivingEntity e) {
			Vec3 rel = e.position().subtract(this.zephyr.position());
			double along = rel.x * this.gust.x + rel.z * this.gust.z;
			double across = Math.abs(rel.x * -this.gust.z + rel.z * this.gust.x);
			return along > 0.0 && along < GUST_LENGTH && across < GUST_WIDTH * 0.5;
		}

		/** Keep its distance and spit webbing balls. */
		private void shoot(ServerLevel level, LivingEntity target, double distance) {
			Zephyr z = this.zephyr;
			boolean can = distance <= SHOOT_MAX && z.hasLineOfSight(target) && !target.hasEffect(AetherEffects.WEBBED);
			if (distance > SHOOT_MIN * 1.5) {
				z.getMoveControl().setWantedPosition(target.getX(), target.getY() + 2.5, target.getZ(), 0.6);
			}
			if (!can) {
				this.shoot = 0;
				z.entityData.set(ACTION, 0);
				return;
			}
			int t = this.shoot++;
			if (t == SHOOT_SOUND) {
				z.playSound(MonsterSounds.get("entity.breeze.shoot"), 1.0F, 0.8F);
			}
			if (t == SHOOT_ANIM) {
				z.entityData.set(ACTION, 2);
			}
			if (t >= SHOOT_CYCLE) {
				this.shoot = 0;
				z.entityData.set(ACTION, 0);
				Vec3 from = z.getEyePosition().add(z.getLookAngle().scale(1.55));
				AetherShot.fire(AetherShot.Kind.ZEPHYR_WEB, z, from, target.getEyePosition(), BALL_SPEED, 1.0F);
			}
		}
	}
}
