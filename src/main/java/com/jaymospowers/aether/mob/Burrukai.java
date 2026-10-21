package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.monster.AetherMonster;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The burrukai, a big armoured grazer. Left alone it is calm; hurt by anything living (not as a calf) it is angry for
 * {@link #ANGER_TICKS} and rams: it winds up for {@link #WIND_UP} ticks, then charges, and a hit that lands hurts hard,
 * throws its target and stuns it (heavy slowness) for a moment.
 */
public class Burrukai extends ZoneAnimal {
	// ---- tuning ----
	public static final double HEALTH = 26.0, SPEED = 0.24, ATTACK = 3.0, KNOCKBACK = 4.0, ARMOR = 6.0, STEP = 1.0;
	/** Anger lasts this long (ticks) after a hit. */
	public static final int ANGER_TICKS = 600;
	/** The ram: wind-up before the charge, the longest a charge runs without a hit, the charge speed, the rest after it (ticks). */
	public static final int WIND_UP = 30, CHARGE_TICKS = 50, REST_TICKS = 40;
	public static final double CHARGE_SPEED = 2.25;
	/** Stun after a ram: slowness level (0 = I) and how long (ticks). */
	public static final int STUN_LEVEL = 3, STUN_TICKS = 70;

	private static final EntityDataAccessor<Boolean> WINDING_UP = SynchedEntityData.defineId(Burrukai.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(Burrukai.class, EntityDataSerializers.BOOLEAN);

	private int anger;
	/** Pause after a missed ram before it may start another (ticks). */
	private int ramPause;
	private int windUp;

	/** Weak spots: -1 takes more from that kind of blow (see AetherMonster.factor). */
	public static final int SLASH = 0, IMPACT = -1, PIERCE = 0;

	public Burrukai(EntityType<? extends Burrukai> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, ATTACK)
			.add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK).add(Attributes.ARMOR, ARMOR).add(Attributes.STEP_HEIGHT, STEP).add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new CalfPanicGoal());
		this.goalSelector.addGoal(2, new RamGoal());
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.25, AetherAnimals::isAnimalFood, false));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(WINDING_UP, false);
		entityData.define(ANGRY, false);
	}

	/** Winding up to charge (it paws the ground), for the model (synced). */
	public boolean isWindingUp() {
		return this.entityData.get(WINDING_UP);
	}

	public boolean isAngry() {
		return this.entityData.get(ANGRY);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount * AetherMonster.factor(source, SLASH, IMPACT, PIERCE, false));
		if (hurt && !this.isBaby() && source.getEntity() instanceof LivingEntity attacker && attacker != this && !(attacker instanceof Burrukai b && b.isBaby())) {
			if (!(attacker instanceof Player player) || (!player.isCreative() && !player.isSpectator())) {
				this.anger = ANGER_TICKS;
				this.setTarget(attacker);
			}
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide()) {
			if (this.ramPause > 0) {
				this.ramPause--;
			}
			this.entityData.set(WINDING_UP, this.windUp > 0);
			this.entityData.set(ANGRY, this.anger > 0);
		}
		if (this.anger > 0 && --this.anger == 0) {
			this.setTarget(null);
			this.windUp = 0;
		}
		if (this.getTarget() != null && !this.getTarget().isAlive()) {
			this.setTarget(null);
			this.anger = 0;
		}
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Burrukai baby = AetherAnimals.BURRUKAI.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			baby.setZone(this.zone());
		}
		return baby;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("anger", this.anger);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.anger = input.getIntOr("anger", 0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.cow.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MobSounds.get("entity.cow.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MobSounds.get("entity.cow.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MobSounds.get("entity.cow.step"), 0.2F, 0.7F);
	}

	/** A calf bolts when hurt (the grown ones stand and fight). */
	private final class CalfPanicGoal extends PanicGoal {
		CalfPanicGoal() {
			super(Burrukai.this, 1.25);
		}

		@Override
		protected boolean shouldPanic() {
			return Burrukai.this.isBaby() && super.shouldPanic();
		}
	}

	/** Wind up, charge, and ram its target. */
	private final class RamGoal extends Goal {
		private int charge, rest;
		private boolean hit, missed;

		RamGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Burrukai.this.getTarget();
			return target != null && target.isAlive() && !Burrukai.this.isBaby() && Burrukai.this.anger > 0 && Burrukai.this.ramPause == 0;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = Burrukai.this.getTarget();
			boolean live = target != null && target.isAlive() && Burrukai.this.anger > 0;
			return live && (this.rest > 0 || this.charge < CHARGE_TICKS && !this.hit);
		}

		@Override
		public void start() {
			Burrukai.this.windUp = WIND_UP;
			this.charge = 0;
			this.rest = 0;
			this.hit = false;
			this.missed = false;
			Burrukai.this.getNavigation().stop();
			Burrukai.this.playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 1.4F);
		}

		@Override
		public void stop() {
			if (this.missed) {
				Burrukai.this.ramPause = REST_TICKS;
			}
			Burrukai.this.windUp = 0;
			Burrukai.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			Burrukai self = Burrukai.this;
			LivingEntity target = self.getTarget();
			if (target == null) {
				return;
			}
			self.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.rest > 0) {
				this.rest--;
				if (this.rest == 0 && !this.missed) {
					this.start();
				}
				return;
			}
			if (self.windUp > 0) {
				self.windUp--;
				self.getNavigation().stop();
				return;
			}
			this.charge++;
			self.getNavigation().moveTo(target, CHARGE_SPEED);
			if (self.distanceToSqr(target) <= (double)(self.getBbWidth() * self.getBbWidth()) && self.level() instanceof ServerLevel level) {
				if (self.doHurtTarget(level, target)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, STUN_LEVEL));
					level.playSound(null, self.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.NEUTRAL, 0.8F, 1.2F);
				}
				this.hit = true;
				this.rest = REST_TICKS;
				self.getNavigation().stop();
			} else if (this.charge >= CHARGE_TICKS) {
				// missed: a breather, then the ram ends
				this.missed = true;
				this.rest = REST_TICKS;
				self.getNavigation().stop();
			}
		}
	}
}
