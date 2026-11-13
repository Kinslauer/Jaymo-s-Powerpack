package com.jaymospowers.fishing;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Track C's critters that don't swim like a fish: crabs, lobsters and crayfish walk the bottom (a crab sideways), out at
 * night and tucked away by day, and come to a baited crab pot ({@link CrabPot}) or nip the bait off a ledger rod's hook;
 * a jellyfish drifts and pulses near the top, and stings what brushes it. A landing net scoops any of them. Their own
 * weights are drawn on the size curve, like a fish's.
 */
public class Critter extends WaterAnimal {
	/** A walker's pace (blocks per tick) and how long it keeps one heading (ticks), at least and up to this many more. */
	static final double WALK_SPEED = 0.025;
	static final int HEADING_LEAST = 40, HEADING_MORE = 100;
	/** By day a walker mostly sits still (chance each heading that it rests instead). */
	static final float DAY_REST = 0.75F, NIGHT_REST = 0.2F;
	/** A jellyfish pulses up every this many ticks, this hard, and sinks slowly between. */
	static final int PULSE_TICKS = 40;
	static final double PULSE = 0.06, SINK = 0.003;
	/** A jellyfish stings: poison this long (ticks), this much harm, no more than once a second per victim. */
	static final int STING_POISON = 80;
	static final float STING_DAMAGE = 1.0F;

	private static final EntityDataAccessor<Float> WEIGHT = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.FLOAT);

	private float heading;
	private int headingFor;
	private boolean resting;
	private @Nullable BlockPos goingTo;

	public Critter(EntityType<? extends Critter> type, Level level) {
		super(type, level);
	}

	public Species species() {
		Species sp = FishSpecies.of(getType());
		return sp == null ? Species.CRAB : sp;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(WEIGHT, 0.0F);
	}

	public float weight() {
		float kg = this.entityData.get(WEIGHT);
		if (kg <= 0.0F && !level().isClientSide()) {
			kg = species().rollKg(getRandom(), false);
			setWeight(kg);
		}
		return Math.max(0.005F, kg);
	}

	public void setWeight(float kg) {
		this.entityData.set(WEIGHT, kg);
		AttributeInstance scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(species().scaleFor(kg));
		}
	}

	/** Head for {@code pos} (a baited pot), or wander again with null. */
	public void goTo(@Nullable BlockPos pos) {
		this.goingTo = pos;
	}

	public boolean jelly() {
		return species() == Species.JELLYFISH;
	}

	@Override
	public boolean isNoGravity() {
		return jelly() && isInWater();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		weight();
		if (jelly()) {
			drift(level);
		} else {
			walk(level);
		}
	}

	private void walk(ServerLevel level) {
		if (isInWater() && !onGround()) {
			// sink to the bottom
			addDeltaMovement(new Vec3(0.0, -0.02, 0.0));
		}
		if (goingTo != null) {
			Vec3 to = Vec3.atBottomCenterOf(goingTo).subtract(position());
			if (to.horizontalDistanceSqr() < 0.04) {
				goingTo = null;
			} else {
				heading = (float) Mth.atan2(to.z, to.x);
				resting = false;
				headingFor = 20;
			}
		}
		if (--headingFor <= 0) {
			headingFor = HEADING_LEAST + getRandom().nextInt(HEADING_MORE);
			heading = getRandom().nextFloat() * Mth.TWO_PI;
			long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
			boolean night = day >= 13000 && day < 23000;
			resting = getRandom().nextFloat() < (night ? NIGHT_REST : DAY_REST);
		}
		if (resting || !onGround() && !isInWater()) {
			return;
		}
		double dx = Math.cos(heading) * WALK_SPEED, dz = Math.sin(heading) * WALK_SPEED;
		setDeltaMovement(dx, getDeltaMovement().y, dz);
		// a crab faces across its way and scuttles sideways; the others walk head first
		float face = (float) (heading * Mth.RAD_TO_DEG) - 90.0F + (species() == Species.CRAB ? 90.0F : 0.0F);
		setYRot(face);
		this.yBodyRot = face;
		this.yHeadRot = face;
		if (horizontalCollision) {
			headingFor = 0;
		}
	}

	private void drift(ServerLevel level) {
		if (!isInWater()) {
			return;
		}
		Vec3 v = getDeltaMovement().scale(0.96);
		if (tickCount % PULSE_TICKS == 0 && level.getFluidState(blockPosition().above()).is(FluidTags.WATER)) {
			v = v.add((getRandom().nextFloat() - 0.5F) * 0.02, PULSE, (getRandom().nextFloat() - 0.5F) * 0.02);
		}
		setDeltaMovement(v.add(0.0, -SINK, 0.0));
		if (tickCount % 20 == 0) {
			List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.2),
				e -> e != this && !(e instanceof WaterAnimal) && e.isAlive());
			for (LivingEntity e : touching) {
				e.addEffect(new MobEffectInstance(MobEffects.POISON, STING_POISON, 0), this);
				e.hurtServer(level, damageSources().mobAttack(this), STING_DAMAGE);
			}
		}
	}

	/** A walker can stand a while out of water (crabs on the shore); a jellyfish can't. */
	@Override
	protected void handleAirSupply(ServerLevel level, int preTickAirSupply) {
		if (!jelly() && isAlive() && !isInWater()) {
			setAirSupply(Math.max(-19, preTickAirSupply - (species() == Species.CRAB ? 0 : 1)));
			if (getAirSupply() <= -20) {
				hurtServer(level, damageSources().drown(), 1.0F);
			}
			return;
		}
		super.handleAirSupply(level, preTickAirSupply);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("Weight", this.entityData.get(WEIGHT));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		float kg = input.getFloatOr("Weight", 0.0F);
		if (kg > 0.0F) {
			setWeight(kg);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return jelly() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.TURTLE_HURT_BABY;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return jelly() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.TURTLE_DEATH_BABY;
	}
}
