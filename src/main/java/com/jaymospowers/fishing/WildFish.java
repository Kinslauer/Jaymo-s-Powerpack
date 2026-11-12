package com.jaymospowers.fishing;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Track C: one of our fish, of whichever {@link Species} its entity type is ({@link FishSpecies#of}). A real fish in the
 * water (Andy: "they have to ACTUALLY be there"), so the living things rules hold:
 * <ul>
 * <li><b>sympathy</b>: it keeps to its own depth (a tench grubs on the bottom, a herring shoal near the top), schools as its
 * kind does, and its own weight, drawn on the size curve when it hatches, is the weight you land it at and how big you see
 * it swim;</li>
 * <li><b>feedback loops</b>: a hungry predator hunts smaller fish ({@link Hunt}), eats them, and is fed and lazy for a
 * while ({@link #FED_TICKS}): where predators are hunting the water boils and fish feed harder
 * ({@link FishSchools#frenzy}), and a fed one hardly looks at a lure;</li>
 * <li><b>stewardship</b>: a fish put back ({@link CatchRelease}) is the same fish, its weight kept, and grows on.</li>
 * </ul>
 * One water's legend ({@link FishLegends}) is a fish of this class too, only far bigger, wary, and never despawning.
 */
public class WildFish extends AbstractSchoolingFish {
	/** A predator that has eaten is fed for this long (ticks), and won't hunt or chase lures much. */
	static final int FED_TICKS = 3600;
	/** A hungry predator looks for prey this far off (blocks), prey at most this share of its weight. */
	static final double HUNT_RANGE = 8.0;
	static final float PREY_SHARE = 0.3F;
	/** How fast it chases (navigation speed). */
	static final double CHASE_SPEED = 1.6;
	/** A fish keeping to the top or bottom drifts back toward it this hard (blocks per tick). */
	static final double DEPTH_PULL = 0.012;
	/** A released fish grows this much heavier each day it lives on (a share of its weight). */
	static final float GROWTH_PER_DAY = 0.01F;

	private static final EntityDataAccessor<Float> WEIGHT = SynchedEntityData.defineId(WildFish.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> LEGEND = SynchedEntityData.defineId(WildFish.class, EntityDataSerializers.BOOLEAN);

	private long fedUntil;
	private long bornDay = -1;
	private String legendName = "";
	private boolean released;

	public WildFish(EntityType<? extends WildFish> type, Level level) {
		super(type, level);
	}

	public Species species() {
		Species sp = FishSpecies.of(getType());
		return sp == null ? Species.OTHER : sp;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(WEIGHT, 0.0F);
		entityData.define(LEGEND, false);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(3, new Hunt(this));
	}

	/** Its weight in kg (drawn the first time it's asked for). */
	public float weight() {
		float kg = this.entityData.get(WEIGHT);
		if (kg <= 0.0F && !level().isClientSide()) {
			kg = species().rollKg(getRandom(), false);
			setWeight(kg);
		}
		return Math.max(0.01F, kg);
	}

	public void setWeight(float kg) {
		this.entityData.set(WEIGHT, kg);
		AttributeInstance scale = getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(species().scaleFor(kg));
		}
	}

	public boolean legend() {
		return this.entityData.get(LEGEND);
	}

	public String legendName() {
		return legendName;
	}

	/**
	 * Make this one its water's legend: named and huge. It isn't saved with the chunk: the water remembers it
	 * ({@link FishLegends}), and it comes up again when someone fishes there.
	 */
	public void makeLegend(String name, float kg) {
		this.legendName = name;
		this.entityData.set(LEGEND, true);
		setWeight(kg);
	}

	@Override
	public boolean shouldBeSaved() {
		return !legend() && super.shouldBeSaved();
	}

	/** It was caught and put back. */
	public void markReleased() {
		this.released = true;
		setPersistenceRequired();
	}

	public boolean released() {
		return released;
	}

	public boolean hungry() {
		return !species().predator() || level().getGameTime() >= fedUntil;
	}

	/** How keen it is on bait just now, beyond its kind's hours and seasons: a fed predator barely looks; a legend is wary. */
	public float appetite() {
		float f = 1.0F;
		if (species().predator()) {
			f *= hungry() ? 1.4F : 0.4F;
		}
		if (legend()) {
			f *= FishLegends.WARY;
		}
		return f;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !legend() && !released && super.removeWhenFarAway(distSqr);
	}

	@Override
	public int getMaxSchoolSize() {
		return Math.max(1, species().schoolMax * 2);
	}

	@Override
	public boolean canBeFollowed() {
		return species().schoolMax > 1 && super.canBeFollowed();
	}

	@Override
	public boolean isFollower() {
		return species().schoolMax > 1 && super.isFollower();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (legend() && level() instanceof ServerLevel server && tickCount % 100 == 0
			&& server.getNearestPlayer(this, FishLegends.STAY_RANGE) == null) {
			// nobody about: the old giant goes back down where no one sees it
			discard();
			return;
		}
		if (level() instanceof ServerLevel server && isInWater() && getTarget() == null) {
			weight();
			keepDepth(server);
			if (released && bornDay >= 0 && tickCount % 1200 == 0) {
				// a fish put back grows on
				long day = server.getOverworldClockTime() / 24000L;
				if (day > bornDay) {
					setWeight(Math.min(species().kgMax * 1.2F, weight() * (1.0F + GROWTH_PER_DAY * (day - bornDay))));
					bornDay = day;
				}
			}
		}
	}

	/** Bottom fish drift down to the bed, surface fish up to the top. */
	private void keepDepth(ServerLevel level) {
		BlockPos at = blockPosition();
		switch (species().depth) {
			case BOTTOM -> {
				if (level.getFluidState(at.below()).is(FluidTags.WATER) && level.getFluidState(at.below(2)).is(FluidTags.WATER)) {
					addDeltaMovement(new net.minecraft.world.phys.Vec3(0.0, -DEPTH_PULL, 0.0));
				}
			}
			case SURFACE -> {
				if (level.getFluidState(at.above(2)).is(FluidTags.WATER)) {
					addDeltaMovement(new net.minecraft.world.phys.Vec3(0.0, DEPTH_PULL, 0.0));
				}
			}
			default -> {
			}
		}
	}

	/** Too big for a bucket, and a bucket would lose its weight: use the landing net or the rod. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(Items.WATER_BUCKET);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("Weight", this.entityData.get(WEIGHT));
		output.putBoolean("Legend", legend());
		output.putString("LegendName", legendName);
		output.putBoolean("Released", released);
		output.putLong("FedUntil", fedUntil);
		output.putLong("BornDay", bornDay);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		float kg = input.getFloatOr("Weight", 0.0F);
		if (kg > 0.0F) {
			setWeight(kg);
		}
		this.entityData.set(LEGEND, input.getBooleanOr("Legend", false));
		legendName = input.getStringOr("LegendName", "");
		released = input.getBooleanOr("Released", false);
		fedUntil = input.getLongOr("FedUntil", 0L);
		bornDay = input.getLongOr("BornDay", -1L);
	}

	void setBornDay(long day) {
		this.bornDay = day;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.COD_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COD_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COD_HURT;
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.COD_FLOP;
	}

	/** Prey for {@code hunter}: a smaller fish of another kind (ours, vanilla or a skipjack), free and in the water. */
	static boolean prey(WildFish hunter, LivingEntity e) {
		if (e == hunter || !e.isAlive() || !e.isInWater() || FishingReal.onLine(e)) {
			return false;
		}
		int kind = FishBites.kindOf(e);
		if (kind < 0 || kind == hunter.species().kind()) {
			return false;
		}
		Species sp = Species.of(kind);
		if (sp == null || sp == Species.JELLYFISH || sp.walker()) {
			return false;
		}
		float preyKg = e instanceof WildFish w ? w.weight() : sp.usualKg();
		return preyKg <= hunter.weight() * PREY_SHARE && !(e instanceof WildFish w && w.legend());
	}

	/** A hungry predator's hunt: chase a smaller fish, catch it, eat it, and be fed for a while. */
	static final class Hunt extends Goal {
		private final WildFish fish;
		private @Nullable LivingEntity prey;
		private int chased;

		Hunt(WildFish fish) {
			this.fish = fish;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!fish.species().predator() || !fish.hungry() || !fish.isInWater() || fish.getRandom().nextInt(40) != 0
				|| FishingReal.onLine(fish)) {
				return false;
			}
			List<LivingEntity> near = fish.level().getEntitiesOfClass(LivingEntity.class, fish.getBoundingBox().inflate(HUNT_RANGE),
				e -> prey(fish, e));
			if (near.isEmpty()) {
				return false;
			}
			prey = near.get(fish.getRandom().nextInt(near.size()));
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return prey != null && prey.isAlive() && prey.isInWater() && chased < 200 && fish.distanceTo(prey) < HUNT_RANGE * 2
				&& !FishingReal.onLine(fish);
		}

		@Override
		public void start() {
			chased = 0;
		}

		@Override
		public void stop() {
			prey = null;
		}

		@Override
		public void tick() {
			if (prey == null) {
				return;
			}
			chased++;
			fish.getNavigation().moveTo(prey, CHASE_SPEED);
			if (fish.level() instanceof ServerLevel level) {
				if (chased % 20 == 0) {
					// the shoal scatters at the top: a boil the angler can see
					FishSchools.frenzy(level, fish.blockPosition());
					level.sendParticles(ParticleTypes.SPLASH, prey.getX(), prey.getY() + 0.5, prey.getZ(), 6, 0.4, 0.1, 0.4, 0.1);
				}
				if (fish.distanceTo(prey) < 0.9 + fish.getBbWidth() / 2) {
					level.sendParticles(ParticleTypes.BUBBLE, prey.getX(), prey.getY(), prey.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
					level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.4F, 1.4F);
					prey.discard();
					fish.fedUntil = level.getGameTime() + FED_TICKS;
					prey = null;
				}
			}
		}
	}
}
