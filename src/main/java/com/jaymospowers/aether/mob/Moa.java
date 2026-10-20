package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The moa, the Aether's great flightless bird, in three kinds: blue (3 jumps in mid-air), white (4) and black (8). It
 * drifts down slowly, lays an egg now and then, and heals itself. A wild one cannot be saddled: only a moa a player
 * raised can ({@link MoaEggItem}: hatch the egg, feed the chick blue berries until it is grown, then saddle it).
 * Ridden, each jump in mid-air spends one of its jumps; they come back on the ground.
 */
public class Moa extends RideableAnimal {
	/** The three kinds and what sets them apart. */
	public enum Kind {
		BLUE("blue", 3, 100), WHITE("white", 4, 50), BLACK("black", 8, 25);

		public final String id;
		/** Jumps in mid-air, and the odds of meeting it in the wild (a weight). */
		public final int airJumps, weight;

		Kind(String id, int airJumps, int weight) {
			this.id = id;
			this.airJumps = airJumps;
			this.weight = weight;
		}

		public static Kind byIndex(int index) {
			return values()[Math.floorMod(index, values().length)];
		}
	}

	// ---- tuning ----
	public static final double HEALTH = 35.0, SPEED = 0.155, FOLLOW_RANGE = 16.0;
	/** Jump speeds (blocks a tick): from the ground, and in mid-air. */
	public static final double GROUND_JUMP = 0.55, AIR_JUMP = 0.5;
	/** Its rider sits this high above its feet (blocks), and it moves at this share of its speed under one. */
	public static final double SEAT = 1.2;
	public static final float RIDE_SPEED = 1.1F;
	/** Falls at most this fast loose or on a glide (the Aether's 0.1), and this fast ridden. */
	public static final double FREE_FALL = 0.1, RIDDEN_FALL = 0.5;
	/** An egg every so often (ticks): 5 to 10 minutes. */
	public static final int EGG_MIN = 6000, EGG_SPREAD = 6000;
	/** A chick grows up in this long (ticks), sooner when fed. */
	public static final int CHICK_TICKS = 24000;
	/** It mends one hit point this often (ticks). */
	public static final int HEAL_EVERY = 900;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Moa.class, EntityDataSerializers.INT);

	/** Raised by a player from an egg: the only kind that takes a saddle. */
	private boolean raised;
	private int eggTimer;

	public Moa(EntityType<? extends Moa> type, Level level) {
		super(type, level);
		this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE).add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new TemptGoal(this, 1.1, stack -> stack.is(AetherItems.BLUE_BERRY), false));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	// ---- kind ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(KIND, 0);
	}

	public Kind kind() {
		return Kind.byIndex(this.entityData.get(KIND));
	}

	public void setKind(Kind kind) {
		this.entityData.set(KIND, kind.ordinal());
	}

	/** A kind picked by weight. */
	public static Kind randomKind(net.minecraft.util.RandomSource random) {
		int total = 0;
		for (Kind kind : Kind.values()) {
			total += kind.weight;
		}
		int roll = random.nextInt(total);
		for (Kind kind : Kind.values()) {
			roll -= kind.weight;
			if (roll < 0) {
				return kind;
			}
		}
		return Kind.BLUE;
	}

	/** The egg it lays. */
	public static Item eggOf(Kind kind) {
		return switch (kind) {
			case BLUE -> AetherMobs.BLUE_MOA_EGG;
			case WHITE -> AetherMobs.WHITE_MOA_EGG;
			case BLACK -> AetherMobs.BLACK_MOA_EGG;
		};
	}

	public boolean isRaised() {
		return this.raised;
	}

	public void setRaised(boolean raised) {
		this.raised = raised;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setKind(randomKind(this.random));
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		// wild ones come grown
		this.setAge(0);
		return data;
	}

	// ---- riding ----

	@Override
	protected int maxAirJumps() {
		return this.kind().airJumps;
	}

	@Override
	protected double groundJump() {
		return GROUND_JUMP;
	}

	@Override
	protected double airJump() {
		return AIR_JUMP;
	}

	@Override
	protected double freeFall() {
		return FREE_FALL;
	}

	@Override
	protected double riddenFall() {
		return RIDDEN_FALL;
	}

	@Override
	protected float rideSpeed() {
		return RIDE_SPEED;
	}

	@Override
	protected double seatHeight() {
		return SEAT;
	}

	@Override
	protected boolean canBeSaddled() {
		return this.raised && !this.isBaby();
	}

	// ---- life ----

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.isAlive()) {
			if (!this.isBaby() && !this.isVehicle() && --this.eggTimer <= 0) {
				this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
				this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
				this.spawnAtLocation(level, new ItemStack(eggOf(this.kind())));
			}
			if (this.tickCount % HEAL_EVERY == 0 && this.getHealth() < this.getMaxHealth()) {
				this.heal(1.0F);
			}
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		// blue berries feed a chick; a grown moa has no breeding (it lays instead)
		return this.isBaby() && stack.is(AetherItems.BLUE_BERRY);
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("kind", this.kind().ordinal());
		output.putBoolean("raised", this.raised);
		output.putInt("egg_timer", this.eggTimer);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(KIND, Kind.byIndex(input.getIntOr("kind", 0)).ordinal());
		this.raised = input.getBooleanOr("raised", false);
		this.eggTimer = input.getIntOr("egg_timer", EGG_MIN);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.chicken.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MobSounds.get("entity.chicken.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MobSounds.get("entity.chicken.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.CHICKEN_STEP.value(), 0.15F, 0.8F);
	}
}
