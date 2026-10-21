package com.jaymospowers.aether.mob;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Aether's small flying insects: the glitterwing (eleven kinds of wing) and the shroudwing (six). They hover about,
 * keep away from players who are not sneaking, and now and then land and rest. The kind is chosen from the biome where
 * it spawns ({@link #pickVariant}). Neither fights, drops anything or takes fall damage.
 */
public class Insect extends PathfinderMob {
	/** The glitterwing's kinds, by variant index (skins are {@code glitterwing_<name>}); {@code orange_mimic} turns its wings 40 degrees. */
	public static final String[] GLITTERWING = {"indigo", "dragonfly", "drapewing", "leaf_insect", "quicksoil_moth", "sunset_dragonfly", "orange_mimic",
		"amber_moth", "spotted", "blightfly", "phantomfly"};
	/** The shroudwing's kinds (skins are {@code shroudwing_<name>}). */
	public static final String[] SHROUDWING = {"scarab", "arctic", "fire", "moss", "ore", "purple"};

	// ---- tuning ----
	public static final double HEALTH = 2.0, SPEED = 0.2, FLYING = 0.4;
	/** A player this close (blocks) sends it off, at these speeds; it ignores sneaking players. */
	public static final float AVOID_RANGE = 6.0F;
	public static final double AVOID_SPEED = 1.0, AVOID_SPRINT = 1.1;
	/** It rests every so often: the wait (ticks) and how long a rest lasts. */
	public static final int REST_WAIT_MIN = 200, REST_WAIT_SPREAD = 400, REST_MIN = 100, REST_SPREAD = 200;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Insect.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(Insect.class, EntityDataSerializers.BOOLEAN);

	private final boolean glitter;
	private int restWait;

	/** Which kind of insect this is (a type is made for each). */
	public Insect(EntityType<? extends Insect> type, Level level, boolean glitter) {
		super(type, level);
		this.glitter = glitter;
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.restWait = REST_WAIT_MIN + this.random.nextInt(REST_WAIT_SPREAD);
	}

	public static Insect glitterwing(EntityType<? extends Insect> type, Level level) {
		return new Insect(type, level, true);
	}

	public static Insect shroudwing(EntityType<? extends Insect> type, Level level) {
		return new Insect(type, level, false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FLYING_SPEED, FLYING);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new RestGoal());
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, AVOID_RANGE, AVOID_SPEED, AVOID_SPRINT,
			p -> p instanceof Player pl && !pl.isCrouching() && !pl.isCreative() && !pl.isSpectator()));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
		entityData.define(RESTING, false);
	}

	public boolean isGlitterwing() {
		return this.glitter;
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, (this.glitter ? GLITTERWING : SHROUDWING).length));
	}

	public boolean isResting() {
		return this.entityData.get(RESTING);
	}

	public String variantName() {
		return (this.glitter ? GLITTERWING : SHROUDWING)[this.variant()];
	}

	// ---- the kind, from the biome ----

	private static String key(Holder<Biome> biome) {
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	/** Wet biomes: the lakes, swamps and rainforests. */
	private static boolean wet(String biome) {
		return biome.contains("swamp") || biome.contains("lake") || biome.contains("basin") || biome.contains("jungle") || biome.equals("shrouded_forest");
	}

	/** Lush biomes: the woods. */
	private static boolean lush(String biome) {
		return biome.contains("wood") || biome.contains("forest") || biome.contains("jungle");
	}

	/** Picks a glitterwing or shroudwing kind for the biome and light at {@code pos}. */
	private static int pickVariant(boolean glitter, ServerLevelAccessor level, BlockPos pos, net.minecraft.util.RandomSource random) {
		String biome = key(level.getBiome(pos));
		Zone zone = Zone.at(level, pos);
		boolean aether = Zone.in(level, pos) || biome.contains("irradiated") || biome.contains("battleground") || biome.contains("contaminated");
		if (glitter) {
			if (level.getRawBrightness(pos, 0) <= 8 && aether && random.nextFloat() < 0.85F) {
				return random.nextBoolean() ? 9 : 10;
			}
			if (zone == Zone.HIGHFIELDS && random.nextFloat() < 0.25F) {
				return 6;
			}
			if (wet(biome) && random.nextFloat() < 0.75F) {
				return random.nextBoolean() ? 4 : 5;
			}
			if (lush(biome) && random.nextFloat() < 0.5F) {
				return 1 + random.nextInt(3);
			}
			if (aether && random.nextFloat() < 0.5F) {
				return random.nextBoolean() ? 7 : 8;
			}
			return 0;
		}
		if (zone == Zone.ARCTIC) {
			return 1;
		}
		if (zone == Zone.HIGHFIELDS && random.nextFloat() < 0.5F) {
			return 2;
		}
		if ((zone == Zone.HIGHFIELDS || zone == Zone.MAGNETIC) && random.nextFloat() < 0.35F) {
			return 3;
		}
		if (zone == Zone.HIGHFIELDS && random.nextFloat() < 0.5F) {
			return 4;
		}
		if (random.nextFloat() < 0.75F) {
			return 5;
		}
		return 0;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setVariant(pickVariant(this.glitter, level, this.blockPosition(), this.random));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	// ---- life ----

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.restWait > 0) {
			this.restWait--;
		}
	}

	@Override
	protected float getFlyingSpeed() {
		return 0.02F;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("variant", 0));
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BEE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BEE_DEATH;
	}

	/** Lands on the ground below (if there is some close) and sits still for a while, then takes off again. */
	private final class RestGoal extends Goal {
		private int rest;

		RestGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		private boolean groundBelow() {
			Insect self = Insect.this;
			BlockPos pos = self.blockPosition();
			for (int i = 1; i <= 6; i++) {
				if (!self.level().getBlockState(pos.below(i)).getCollisionShape(self.level(), pos.below(i)).isEmpty()) {
					return !self.level().getFluidState(pos.below(i)).is(net.minecraft.tags.FluidTags.WATER);
				}
			}
			return false;
		}

		@Override
		public boolean canUse() {
			return Insect.this.restWait <= 0 && this.groundBelow();
		}

		@Override
		public boolean canContinueToUse() {
			Insect self = Insect.this;
			return this.rest > 0 && self.getLastHurtByMob() == null && !self.level().hasNearbyAlivePlayer(self.getX(), self.getY(), self.getZ(), 3.0);
		}

		@Override
		public void start() {
			this.rest = REST_MIN + Insect.this.getRandom().nextInt(REST_SPREAD);
			Insect.this.getNavigation().stop();
			Insect.this.entityData.set(RESTING, true);
		}

		@Override
		public void stop() {
			Insect.this.entityData.set(RESTING, false);
			Insect.this.setNoGravity(true);
			Insect.this.restWait = REST_WAIT_MIN + Insect.this.getRandom().nextInt(REST_WAIT_SPREAD);
		}

		@Override
		public void tick() {
			Insect self = Insect.this;
			self.setNoGravity(false);
			Vec3 motion = self.getDeltaMovement();
			if (!self.onGround()) {
				self.setDeltaMovement(motion.x * 0.5, Math.max(motion.y, -0.12), motion.z * 0.5);
			} else {
				this.rest--;
			}
		}
	}
}
