package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherItems;
import com.jaymospowers.aether.monster.AetherMonsters;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The moa, the Aether's great flightless bird, in three kinds: blue, white and black. Wild grown ones are territorial: they
 * guard their nest and attack a player who comes within {@link #GUARD_BLOCKS} blocks of it. A moa hatches from an egg
 * ({@link MoaEggBlock}, on woven skyroot sticks, or {@link MoaEggItem}); a chick a player raised is hungry now and then,
 * grows a stage each time it is fed (or when the hunger goes unanswered), and how well it was fed sets its stamina when it
 * grows up. Only a grown moa a player raised takes a saddle and shears. Ridden, each jump in mid-air costs one point of
 * stamina; it comes back some while after the last jump, quickly on the ground.
 */
public class Moa extends RideableAnimal {
	/** The three kinds: the stamina they start from (jumps in mid-air) and the odds of meeting one in the wild (a weight). */
	public enum Kind {
		BLUE("blue", 3, 100), WHITE("white", 4, 50), BLACK("black", 8, 25);

		public final String id;
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
	public static final double HEALTH = 35.0, SPEED = 0.155, FOLLOW_RANGE = 18.0, ATTACK = 2.0, KNOCKBACK = 2.0, STEP = 1.0;
	/** Jump speeds (blocks a tick): from the ground, and in mid-air. */
	public static final double GROUND_JUMP = 0.55, AIR_JUMP = 0.5;
	/** Its rider sits this high above its feet (blocks), and it moves at this share of its speed under one. */
	public static final double SEAT = 1.2;
	public static final float RIDE_SPEED = 1.1F;
	/** Falls at most this fast loose or on a glide, and this fast ridden. */
	public static final double FREE_FALL = 0.1, RIDDEN_FALL = 0.5;
	/** A moa a player raised lays an egg this often (ticks): 5 to 10 minutes. */
	public static final int EGG_MIN = 6000, EGG_SPREAD = 6000;
	/** A chick takes this long (ticks) to grow up with no feeding; feeding moves it a stage (a third) on. */
	public static final int CHICK_TICKS = 24000, STAGES = 3;
	/** A chick is hungry for this long (ticks), once every so often; fed with more than this left it counts double. */
	public static final int HUNGRY_TICKS = 8000, HUNGRY_DOUBLE = 4000, HUNGER_WAIT_MIN = 2400, HUNGER_WAIT_SPREAD = 2400;
	/** What a grown moa's stamina shifts by for how well it was fed: nothing fed, exactly three, and otherwise. */
	public static final int SHIFT_STARVED = -4, SHIFT_THREE = -1, SHIFT_FED = 1, STAMINA_MIN = 3;
	/** A mid-air jump starts this long (ticks) of waiting before stamina is back; it counts down 12 a tick on the ground, 1 in the air. */
	public static final int STAMINA_COOLDOWN = 300, GROUND_DRAIN = 12;
	/** Territory: a player this many blocks (Manhattan) from its home, and this near it, is run down; the anger lasts this long (ticks) and spreads this far. */
	public static final int GUARD_BLOCKS = 5, GUARD_REACH = 18, ANGER_TICKS = 600, ANGER_SPREAD = 16, HOME_RANGE = 20;
	/** It mends one hit point this often (ticks), and an adult heals this much from food. */
	public static final int HEAL_EVERY = 900;
	public static final float FOOD_HEAL = 5.0F;
	/** Shearing: feathers, durability lost, and the longest wait before the next (ticks). */
	public static final int FEATHERS = 4, SHEAR_DAMAGE = 32, SHEAR_WAIT = 2000;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Moa.class, EntityDataSerializers.INT);

	/** Raised by a player from an egg: the only kind that takes a saddle. */
	private boolean raised;
	private int eggTimer, anger, hungerWait, hungry, fed, staminaShift, shearWait, staminaCooldown;

	public Moa(EntityType<? extends Moa> type, Level level) {
		super(type, level);
		this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
		this.hungerWait = HUNGER_WAIT_MIN + this.random.nextInt(HUNGER_WAIT_SPREAD);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
			.add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL).add(Attributes.ATTACK_DAMAGE, ATTACK).add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK)
			.add(Attributes.STEP_HEIGHT, STEP);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4) {
			@Override
			protected boolean shouldPanic() {
				// only a chick runs when hurt; a grown moa fights or stands
				return Moa.this.isBaby() && super.shouldPanic();
			}
		});
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, AetherAnimals::isMoaFood, false));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new GuardGoal());
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

	/** The egg item of a kind. */
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

	/** Its nest: a wild one stays near it and guards it (a raised one is free to roam). */
	public void setHome(BlockPos pos) {
		if (!this.raised) {
			this.setHomeTo(pos, HOME_RANGE);
		}
	}

	/** The jumps in mid-air it has in all: its kind's, shifted by how it was fed, and never below the minimum. */
	public int maxStamina() {
		return Math.max(STAMINA_MIN, this.kind().airJumps + this.staminaShift);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setKind(randomKind(this.random));
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		// wild ones come grown, with a nest where they stand
		this.setAge(0);
		this.setHome(this.blockPosition());
		return data;
	}

	// ---- riding ----

	@Override
	protected int maxAirJumps() {
		return this.maxStamina();
	}

	@Override
	protected int refill(int jumpsLeft) {
		return this.staminaCooldown <= 0 ? this.maxStamina() : jumpsLeft;
	}

	@Override
	protected void airJumped() {
		this.staminaCooldown = STAMINA_COOLDOWN;
	}

	@Override
	protected void rideTick(boolean onGround) {
		if (this.staminaCooldown > 0) {
			this.staminaCooldown = Math.max(0, this.staminaCooldown - (onGround ? GROUND_DRAIN : 1));
		}
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
			if (this.anger > 0 && --this.anger == 0) {
				this.setTarget(null);
			}
			if (this.shearWait > 0) {
				this.shearWait--;
			}
			if (this.isBaby()) {
				this.growthTick(level);
			} else if (this.raised && !this.isVehicle() && --this.eggTimer <= 0) {
				this.eggTimer = EGG_MIN + this.random.nextInt(EGG_SPREAD);
				this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
				this.spawnAtLocation(level, new ItemStack(eggOf(this.kind())));
			}
			if (this.tickCount % HEAL_EVERY == 0 && this.getHealth() < this.getMaxHealth()) {
				this.heal(1.0F);
			}
		}
	}

	/** A chick: now and then it is hungry for a long while; a feeding (or the wait running out) moves it a stage toward grown. */
	private void growthTick(ServerLevel level) {
		if (this.hungry > 0) {
			if (this.tickCount % 40 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + this.getBbHeight() + 0.2, this.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
			if (--this.hungry == 0) {
				this.advance(level);
			}
		} else if (--this.hungerWait <= 0) {
			this.hungry = HUNGRY_TICKS;
		}
	}

	/** One stage on: a third of the way to grown, and grown at the third. */
	private void advance(ServerLevel level) {
		this.hungry = 0;
		this.hungerWait = HUNGER_WAIT_MIN + this.random.nextInt(HUNGER_WAIT_SPREAD);
		int done = (CHICK_TICKS + Math.min(0, this.getAge())) / (CHICK_TICKS / STAGES);
		int next = Math.min(STAGES, done + 1);
		if (next >= STAGES) {
			this.setAge(0);
			this.staminaShift = this.fed == 0 ? SHIFT_STARVED : this.fed == 3 ? SHIFT_THREE : SHIFT_FED;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
		} else {
			this.setAge(-CHICK_TICKS + next * (CHICK_TICKS / STAGES));
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (AetherAnimals.isMoaFood(held)) {
			if (this.isBaby()) {
				if (this.hungry > 0 && this.level() instanceof ServerLevel level) {
					this.fed += this.hungry > HUNGRY_DOUBLE ? 2 : 1;
					this.advance(level);
					held.consume(1, player);
					this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.2F);
				}
				return InteractionResult.SUCCESS;
			}
			if (this.getHealth() < this.getMaxHealth()) {
				if (!this.level().isClientSide()) {
					this.heal(FOOD_HEAL);
					held.consume(1, player);
					this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (held.is(Items.SHEARS) && this.raised && !this.isBaby() && this.shearWait <= 0) {
			if (this.level() instanceof ServerLevel level) {
				level.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
				this.spawnAtLocation(level, new ItemStack(AetherAnimals.MOA_FEATHER, FEATHERS));
				this.shearWait = this.random.nextInt(SHEAR_WAIT);
				if (player instanceof ServerPlayer sp) {
					held.hurtAndBreak(SHEAR_DAMAGE, sp, hand);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !this.isBaby() && !this.raised && source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
			this.angryAt(level, player);
		}
		return hurt;
	}

	/** Angry at {@code player} for the next while, and the grown wild moas around it join in. */
	private void angryAt(ServerLevel level, Player player) {
		this.anger = ANGER_TICKS;
		this.setTarget(player);
		List<Moa> kin = level.getEntitiesOfClass(Moa.class, this.getBoundingBox().inflate(ANGER_SPREAD), m -> m != this && !m.isBaby() && !m.raised && m.isAlive());
		for (Moa other : kin) {
			other.anger = ANGER_TICKS;
			other.setTarget(player);
		}
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return AetherAnimals.isMoaFood(stack);
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
		output.putInt("anger", this.anger);
		output.putInt("hungry", this.hungry);
		output.putInt("hunger_wait", this.hungerWait);
		output.putInt("fed", this.fed);
		output.putInt("stamina_shift", this.staminaShift);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(KIND, Kind.byIndex(input.getIntOr("kind", 0)).ordinal());
		this.raised = input.getBooleanOr("raised", false);
		this.eggTimer = input.getIntOr("egg_timer", EGG_MIN);
		this.anger = input.getIntOr("anger", 0);
		this.hungry = input.getIntOr("hungry", 0);
		this.hungerWait = input.getIntOr("hunger_wait", HUNGER_WAIT_MIN);
		this.fed = input.getIntOr("fed", 0);
		this.staminaShift = input.getIntOr("stamina_shift", 0);
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

	/** A wild grown moa picks a player near its nest as its target. */
	private final class GuardGoal extends Goal {
		private Player player;

		GuardGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			Moa self = Moa.this;
			if (self.raised || self.isBaby() || !self.hasHome() || self.getTarget() != null || self.tickCount % 5 != 0) {
				return false;
			}
			BlockPos home = self.getHomePosition();
			this.player = null;
			double best = Double.MAX_VALUE;
			for (Player candidate : self.level().players()) {
				if (!candidate.isAlive() || candidate.isCreative() || candidate.isSpectator()) {
					continue;
				}
				double d = self.distanceToSqr(candidate);
				BlockPos at = candidate.blockPosition();
				int manhattan = Math.abs(at.getX() - home.getX()) + Math.abs(at.getY() - home.getY()) + Math.abs(at.getZ() - home.getZ());
				if (manhattan <= GUARD_BLOCKS && d <= GUARD_REACH * GUARD_REACH && d < best) {
					best = d;
					this.player = candidate;
				}
			}
			return this.player != null;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			if (Moa.this.level() instanceof ServerLevel level) {
				Moa.this.angryAt(level, this.player);
			}
		}
	}
}
