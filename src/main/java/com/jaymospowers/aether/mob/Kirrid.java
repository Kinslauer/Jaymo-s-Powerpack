package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.monster.AetherMonster;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The kirrid, a hopping, wool-shelled grazer of the Aether's three climates. It moves in hops, grazes aether grass (the
 * wool grows back sooner for it), carries a head plate that two kirrids ram each other for (a plate can break off and
 * drops, then grows back), and gives cloudwool to shears. Its wool is dyed with dye like a sheep's; the wild colours
 * depend on the zone (see {@link #onZone}).
 */
public class Kirrid extends ZoneAnimal {
	// ---- tuning ----
	public static final double HEALTH = 16.0, SPEED = 0.26;
	/** Hops: how far up and forward, and the wait on the ground after one (ticks). */
	public static final double HOP_UP = 0.46, HOP_FORWARD = 0.27;
	public static final int HOP_WAIT = 10;
	/** At or above this move speed a hop waits 1 tick after landing instead of HOP_WAIT. */
	public static final double FAST_HOP_SPEED = 0.6;
	/** Wool: how long it takes to grow back (ticks), what each patch grazed takes off that, and the shearing yield. */
	public static final int WOOL_REGROW = 2400, GRAZE_SHAVE_MIN = 30, GRAZE_SHAVE_SPREAD = 30, WOOL_MIN = 1, WOOL_MAX = 3;
	/** Grazing: the wait between meals (ticks), how long a meal is, and the tick the grass goes. */
	public static final int GRAZE_WAIT_MIN = 600, GRAZE_WAIT_SPREAD = 600, GRAZE_TICKS = 60, GRAZE_EVENT = 15;
	/** Ramming: the charge lasts this long (ticks), starts when two plated adults are within this many blocks, and the wait before the next. */
	public static final int RAM_TICKS = 80, RAM_COOLDOWN_MIN = 600, RAM_COOLDOWN_SPREAD = 1800;
	public static final double RAM_RANGE = 10.0, RAM_CONTACT_SQ = 4.0, RAM_SPEED = 1.5;
	/** A bump knocks the plate off this often, and the plate grows back after this long (ticks). */
	public static final float PLATE_LOSS_CHANCE = 0.01F;
	public static final int PLATE_REGROW = 6000;
	/** Colour odds by zone (a share each), and the one-in-this-many rare colour. */
	public static final float ODDS = 0.05F;
	public static final int RARE_ODDS = 500;

	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Kirrid.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(Kirrid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> PLATED = SynchedEntityData.defineId(Kirrid.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> GRAZING = SynchedEntityData.defineId(Kirrid.class, EntityDataSerializers.BOOLEAN);

	private int hopWait, woolTimer, plateTimer, ramCooldown = RAM_COOLDOWN_MIN, grazeWait = GRAZE_WAIT_MIN;

	/** Weak spots: -1 takes more from that kind of blow (see AetherMonster.factor). */
	public static final int SLASH = 0, IMPACT = 0, PIERCE = -1;

	public Kirrid(EntityType<? extends Kirrid> type, Level level) {
		super(type, level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount * AetherMonster.factor(source, SLASH, IMPACT, PIERCE, false));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 2.0));
		this.goalSelector.addGoal(2, new RamGoal());
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.25, AetherAnimals::isAnimalFood, false));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25));
		this.goalSelector.addGoal(6, new GrazeGoal());
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.5));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	// ---- state ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(COLOR, Wool.UNDYED);
		entityData.define(SHEARED, false);
		entityData.define(PLATED, true);
		entityData.define(GRAZING, false);
	}

	/** Its wool colour: an index into {@link Wool#NAMES}, or {@link Wool#UNDYED}. */
	public int color() {
		return this.entityData.get(COLOR);
	}

	public void setColor(int color) {
		this.entityData.set(COLOR, Math.max(0, Math.min(Wool.UNDYED, color)));
	}

	public boolean isSheared() {
		return this.entityData.get(SHEARED);
	}

	public boolean hasPlate() {
		return this.entityData.get(PLATED);
	}

	public boolean isGrazing() {
		return this.entityData.get(GRAZING);
	}

	/** The wild colours: highfields undyed (white, brown or black 5% each, lime 1 in 500), magnetic light blue (gray, lime, green; purple), arctic white (brown, magenta, pink; blue). */
	@Override
	protected void onZone(Zone zone) {
		String[] picks;
		String base, rare;
		switch (zone) {
			case MAGNETIC -> {
				picks = new String[] {"gray", "lime", "green"};
				base = "light_blue";
				rare = "purple";
			}
			case ARCTIC -> {
				picks = new String[] {"brown", "magenta", "pink"};
				base = "white";
				rare = "blue";
			}
			default -> {
				picks = new String[] {"white", "brown", "black"};
				base = null;
				rare = "lime";
			}
		}
		int color = base == null ? Wool.UNDYED : Wool.index(base);
		if (Wool.rare(this.random, RARE_ODDS)) {
			color = Wool.index(rare);
		} else {
			float roll = this.random.nextFloat();
			for (int i = 0; i < picks.length; i++) {
				if (roll < ODDS * (i + 1) && roll >= ODDS * i) {
					color = Wool.index(picks[i]);
				}
			}
		}
		this.setColor(color);
	}

	// ---- life ----

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel && this.isAlive()) {
			if (this.isSheared() && --this.woolTimer <= 0) {
				this.entityData.set(SHEARED, false);
			}
			if (!this.hasPlate() && --this.plateTimer <= 0) {
				this.entityData.set(PLATED, true);
			}
			if (this.ramCooldown > 0) {
				this.ramCooldown--;
			}
			if (this.grazeWait > 0) {
				this.grazeWait--;
			}
		}
		// a walk is a string of hops: wait on the ground, then spring forward
		if (this.onGround() && !this.isVehicle() && !this.getNavigation().isDone() && !this.isInWater()) {
			if (this.hopWait > 0) {
				this.hopWait--;
			} else {
				Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
				float scale = 0.5F + this.random.nextFloat() * 0.5F;
				Vec3 motion = this.getDeltaMovement();
				this.setDeltaMovement(motion.x + look.x * HOP_FORWARD * scale * 2, HOP_UP * (0.7 + 0.3 * scale), motion.z + look.z * HOP_FORWARD * scale * 2);
				this.hopWait = this.getMoveControl().getSpeedModifier() < FAST_HOP_SPEED ? HOP_WAIT : 1;
			}
		}
	}

	@Override
	public void travel(Vec3 input) {
		// it does not walk, it hops: no steering on the ground between hops
		if (this.onGround() && this.hopWait > 0 && !this.isVehicle()) {
			super.travel(Vec3.ZERO);
			return;
		}
		super.travel(input);
	}

	/** It dropped its plate: someone's ram knocked it off. */
	private void losePlate(ServerLevel level) {
		this.entityData.set(PLATED, false);
		this.plateTimer = PLATE_REGROW;
		this.spawnAtLocation(level, new ItemStack(AetherAnimals.KIRRID_PLATE));
		level.playSound(null, this.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
	}

	// ---- interaction: shears and dye ----

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.SHEARS) && !this.isBaby() && !this.isSheared()) {
			if (this.level() instanceof ServerLevel level) {
				level.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
				this.entityData.set(SHEARED, true);
				this.woolTimer = WOOL_REGROW;
				this.spawnAtLocation(level, new ItemStack(Wool.item(this.color()), WOOL_MIN + this.random.nextInt(WOOL_MAX - WOOL_MIN + 1)));
				if (player instanceof ServerPlayer sp) {
					held.hurtAndBreak(1, sp, hand);
				}
			}
			return InteractionResult.SUCCESS;
		}
		DyeColor dye = held.get(DataComponents.DYE);
		if (dye != null && !this.isBaby() && Wool.index(dye) != this.color()) {
			if (!this.level().isClientSide()) {
				this.setColor(Wool.index(dye));
				held.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && !this.isBaby() && !this.isSheared()) {
			this.spawnAtLocation(level, new ItemStack(Wool.item(this.color())));
		}
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Kirrid baby = AetherAnimals.KIRRID.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			baby.setZone(this.zone());
			baby.setColor(partner instanceof Kirrid other && this.random.nextBoolean() ? other.color() : this.color());
		}
		return baby;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("color", this.color());
		output.putBoolean("sheared", this.isSheared());
		output.putInt("wool_timer", this.woolTimer);
		output.putBoolean("plated", this.hasPlate());
		output.putInt("plate_timer", this.plateTimer);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setColor(input.getIntOr("color", Wool.UNDYED));
		this.entityData.set(SHEARED, input.getBooleanOr("sheared", false));
		this.woolTimer = input.getIntOr("wool_timer", 0);
		this.entityData.set(PLATED, input.getBooleanOr("plated", true));
		this.plateTimer = input.getIntOr("plate_timer", 0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SHEEP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SHEEP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SHEEP_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 1.0F);
	}

	// ---- goals ----

	/** Grazes the aether grass under it now and then (it turns to aether dirt) and its wool comes back sooner. */
	private final class GrazeGoal extends Goal {
		private int ticks;

		GrazeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		private boolean grassBelow() {
			return Kirrid.this.level().getBlockState(Kirrid.this.blockPosition().below()).getBlock() == AetherBlocks.AETHER_GRASS_BLOCK;
		}

		@Override
		public boolean canUse() {
			return Kirrid.this.grazeWait <= 0 && Kirrid.this.onGround() && this.grassBelow() && Kirrid.this.getRandom().nextInt(40) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return this.ticks > 0;
		}

		@Override
		public void start() {
			this.ticks = GRAZE_TICKS;
			Kirrid.this.entityData.set(GRAZING, true);
			Kirrid.this.getNavigation().stop();
		}

		@Override
		public void stop() {
			this.ticks = 0;
			Kirrid.this.grazeWait = GRAZE_WAIT_MIN + Kirrid.this.getRandom().nextInt(GRAZE_WAIT_SPREAD);
			Kirrid.this.entityData.set(GRAZING, false);
		}

		@Override
		public void tick() {
			this.ticks = Math.max(0, this.ticks - 1);
			if (this.ticks == GRAZE_EVENT && this.grassBelow() && Kirrid.this.level() instanceof ServerLevel level) {
				BlockPos pos = Kirrid.this.blockPosition().below();
				level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
				level.setBlockAndUpdate(pos, AetherBlocks.AETHER_DIRT.defaultBlockState());
				// the wool grows back sooner
				Kirrid.this.woolTimer -= GRAZE_SHAVE_MIN + Kirrid.this.getRandom().nextInt(GRAZE_SHAVE_SPREAD);
				Kirrid.this.playEatingSound();
			}
		}
	}

	/** Two plated adults charge each other: the charger runs the other down, and each bump may knock the plate off. */
	private final class RamGoal extends Goal {
		private Kirrid target;
		private int ticks, bump;

		RamGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Kirrid self = Kirrid.this;
			if (self.isBaby() || !self.hasPlate() || self.ramCooldown > 0 || self.getRandom().nextInt(60) != 0) {
				return false;
			}
			List<Kirrid> near = self.level().getEntitiesOfClass(Kirrid.class, self.getBoundingBox().inflate(RAM_RANGE), k -> k != self && !k.isBaby() && k.hasPlate());
			if (near.isEmpty()) {
				return false;
			}
			this.target = near.get(self.getRandom().nextInt(near.size()));
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && this.target.isAlive() && this.ticks < RAM_TICKS && Kirrid.this.hasPlate();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.bump = 0;
		}

		@Override
		public void stop() {
			Kirrid.this.ramCooldown = RAM_COOLDOWN_MIN + Kirrid.this.getRandom().nextInt(RAM_COOLDOWN_SPREAD);
			Kirrid.this.getNavigation().stop();
			this.target = null;
		}

		@Override
		public void tick() {
			this.ticks++;
			Kirrid self = Kirrid.this;
			self.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
			self.getNavigation().moveTo(this.target, RAM_SPEED);
			if (this.bump > 0) {
				this.bump--;
			}
			if (self.distanceToSqr(this.target) < RAM_CONTACT_SQ && this.bump == 0 && self.level() instanceof ServerLevel level) {
				this.bump = 10;
				Vec3 away = this.target.position().subtract(self.position()).normalize();
				this.target.push(away.x * 0.6, 0.25, away.z * 0.6);
				this.target.syncVelocity = true;
				level.playSound(null, self.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 0.8F, 1.3F);
				if (this.target.hasPlate() && self.getRandom().nextFloat() < PLATE_LOSS_CHANCE) {
					this.target.losePlate(level);
				}
			}
		}
	}
}
