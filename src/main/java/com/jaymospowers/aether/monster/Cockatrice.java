package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The cockatrice: a tall, spined, blighted bird-lizard. It bites (3.5, and the bite is venomous) and every few seconds
 * stops to spit a volley of four venomous darts. It flees the sun and, caught in it, digs itself into the ground and
 * is gone.
 */
public class Cockatrice extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 32.0, SPEED = 0.27, FOLLOW = 24.0, BITE = 3.5;
	/** Venom from a bite (ticks) and the little lunge a bite carries. */
	public static final int BITE_VENOM = 120;
	public static final double LUNGE = 0.3;
	/** A volley: darts at these ticks, the whole thing 60 ticks; then 200-300 ticks (longer when far) before the next. */
	public static final int[] DART_TICKS = {25, 37, 48, 53};
	public static final int VOLLEY = 60, COOLDOWN_MIN = 200, COOLDOWN_SPREAD = 100;
	/** It must have seen its target this many ticks, from between these distances. */
	public static final int SIGHT_TICKS = 5;
	public static final double DART_MIN = 4.0, DART_MAX = 24.0;
	public static final float DART_SPEED = 1.25F, DART_SPREAD = 5.0F;
	/** Sun: it begins to dig after 200 ticks and is gone at 250. */
	public static final int DIG_BEGIN = 200, DIG_END = 250;

	/** 1 while it spits darts (the head goes back). */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Cockatrice.class, EntityDataSerializers.INT);
	/** Ticks it has been digging; 0 not digging. */
	private static final EntityDataAccessor<Integer> DIGGING = SynchedEntityData.defineId(Cockatrice.class, EntityDataSerializers.INT);

	private int sunTicks, cooldown = 60, sight;

	public Cockatrice(EntityType<? extends Cockatrice> type, Level level) {
		super(type, level, -1, 0, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_DAMAGE, BITE);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DartGoal(this));
		this.goalSelector.addGoal(2, new FleeSunGoal(this, 1.5));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, 0);
		entityData.define(DIGGING, 0);
	}

	public boolean spitting() {
		return this.entityData.get(ACTION) == 1;
	}

	/** 0 to 1: how far into the ground it has dug. */
	public float digProgress() {
		int dig = this.entityData.get(DIGGING);
		return dig <= 0 ? 0.0F : Mth.clamp((dig - 1) / (float)(DIG_END - DIG_BEGIN), 0.0F, 1.0F);
	}

	@Override
	protected boolean blighted() {
		return true;
	}

	@Override
	protected boolean isImmobile() {
		return this.entityData.get(DIGGING) > 0 || super.isImmobile();
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(AetherEffects.VENOM, BITE_VENOM), this);
			Vec3 toward = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (toward.lengthSqr() > 1.0E-4) {
				this.setDeltaMovement(this.getDeltaMovement().add(toward.normalize().scale(LUNGE)));
			}
		}
		return hit;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		LivingEntity target = this.getTarget();
		this.sight = target != null && this.hasLineOfSight(target) ? this.sight + 1 : 0;
		int dig = this.entityData.get(DIGGING);
		if (dig > 0) {
			this.entityData.set(DIGGING, dig + 1);
			if (this.tickCount % 3 == 0) {
				BlockState below = level.getBlockState(this.blockPosition().below());
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX(), this.getY(), this.getZ(), 6, 0.4, 0.1, 0.4, 0.1);
			}
			if (dig >= DIG_END - DIG_BEGIN + 1) {
				this.discard();
			}
		} else if (this.inSun()) {
			if (++this.sunTicks >= DIG_BEGIN) {
				this.entityData.set(DIGGING, 1);
				this.setTarget(null);
			}
		} else if (this.sunTicks > 0) {
			this.sunTicks--;
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("sun_ticks", this.sunTicks);
		output.putInt("digging", this.entityData.get(DIGGING));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.sunTicks = input.getIntOr("sun_ticks", 0);
		this.entityData.set(DIGGING, input.getIntOr("digging", 0));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MonsterSounds.get("entity.parrot.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.parrot.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.parrot.death");
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	/** Stand still and spit four darts. */
	private static final class DartGoal extends Goal {
		private final Cockatrice bird;
		private int tick;

		DartGoal(Cockatrice bird) {
			this.bird = bird;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Cockatrice b = this.bird;
			LivingEntity target = b.getTarget();
			if (target == null || !target.isAlive() || b.cooldown > 0 || b.sight < SIGHT_TICKS || b.entityData.get(DIGGING) > 0) {
				return false;
			}
			double distance = b.distanceTo(target);
			return distance >= DART_MIN && distance <= DART_MAX;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.bird.getTarget();
			return target != null && target.isAlive() && this.tick < VOLLEY && this.bird.entityData.get(DIGGING) == 0;
		}

		@Override
		public void start() {
			this.tick = 0;
			this.bird.getNavigation().stop();
			this.bird.entityData.set(ACTION, 1);
		}

		@Override
		public void stop() {
			Cockatrice b = this.bird;
			LivingEntity target = b.getTarget();
			double far = target == null ? 0.5 : Mth.clamp(b.distanceTo(target) / DART_MAX, 0.0, 1.0);
			b.cooldown = COOLDOWN_MIN + (int)(COOLDOWN_SPREAD * far);
			b.entityData.set(ACTION, 0);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Cockatrice b = this.bird;
			LivingEntity target = b.getTarget();
			if (target == null) {
				return;
			}
			b.getLookControl().setLookAt(target, 40.0F, 40.0F);
			this.tick++;
			for (int at : DART_TICKS) {
				if (this.tick == at) {
					Vec3 from = b.getEyePosition().add(b.getLookAngle().scale(0.9));
					AetherShot.fire(AetherShot.Kind.VENOMOUS_DART, b, from, target.getEyePosition(), DART_SPEED, DART_SPREAD);
					b.playSound(MonsterSounds.get("entity.llama.spit"), 1.0F, 1.3F);
				}
			}
		}
	}
}
