package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
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
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The diskwing (Eden Ring's): a flat, slow glider of the Verdant World's far country, two broad crescent wings and a
 * finned tail, lit along its back. It circles a spot of sky, as a glider rides a rising column of air, and now and then moves
 * to another; it neither fights nor drops anything. Its colour is its
 * country's ({@link #pickKind}).
 */
public class Diskwing extends PathfinderMob {
	/** The kinds, by index (skins are {@code diskwing_<name>}, each with a {@code _glow}). */
	public static final String[] KINDS = {"blue", "green", "purple", "red"};

	// ---- tuning ----
	public static final double HEALTH = 10.0, SPEED = 0.2, FLYING = 0.35;
	/** How fast it wanders, and how often (ticks, most) it calls. */
	public static final double WANDER_SPEED = 1.0;
	/** The circle it rides: radius (blocks), how far round it goes a tick (radians), and the rise and fall of its height. */
	public static final double CIRCLE_RADIUS = 7.0, CIRCLE_STEP = 0.045, CIRCLE_BOB = 2.0;
	/** Chance a tick it moves its circle elsewhere (one in this many), and how far (blocks). */
	public static final int MOVE_ODDS = 1800, MOVE_REACH = 24;
	public static final int CALL_INTERVAL = 240;

	public static final SoundEvent AMBIENT = SoundEvent.createVariableRangeEvent(JaymosPowers.id("eden.diskwing.ambient"));
	public static final SoundEvent HURT = SoundEvent.createVariableRangeEvent(JaymosPowers.id("eden.diskwing.hurt"));

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Diskwing.class, EntityDataSerializers.INT);

	public Diskwing(EntityType<? extends Diskwing> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 10, true);
		this.setNoGravity(true);
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
		this.goalSelector.addGoal(1, new CircleGoal(this));
	}

	/** The centre of the circle it flies (null until it has one), the angle round it, and which way round. */
	private @Nullable BlockPos roost;
	private double angle;
	private int turn = 1;

	/** Flies a slow circle round its roost, and takes a new roost where the way is blocked or by chance. */
	private static final class CircleGoal extends Goal {
		private final Diskwing wing;

		CircleGoal(Diskwing wing) {
			this.wing = wing;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Diskwing w = this.wing;
			RandomSource random = w.getRandom();
			if (w.roost == null || random.nextInt(MOVE_ODDS) == 0) {
				BlockPos here = w.blockPosition();
				w.roost = w.roost == null ? here : here.offset(random.nextInt(MOVE_REACH * 2 + 1) - MOVE_REACH, random.nextInt(9) - 4,
					random.nextInt(MOVE_REACH * 2 + 1) - MOVE_REACH);
				w.turn = random.nextBoolean() ? 1 : -1;
			}
			w.angle += CIRCLE_STEP * w.turn;
			double x = w.roost.getX() + 0.5 + Math.cos(w.angle) * CIRCLE_RADIUS;
			double z = w.roost.getZ() + 0.5 + Math.sin(w.angle) * CIRCLE_RADIUS;
			double y = w.roost.getY() + Math.sin(w.angle * 0.5) * CIRCLE_BOB;
			if (!w.level().getBlockState(BlockPos.containing(x, y, z)).isAir()) {
				// the way is shut: circle somewhere else, the other way round, a little higher
				w.roost = w.blockPosition().above();
				w.turn = -w.turn;
				return;
			}
			w.getMoveControl().setWantedPosition(x, y, z, WANDER_SPEED);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(KIND, 0);
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	public void setKind(int kind) {
		this.entityData.set(KIND, Math.floorMod(kind, KINDS.length));
	}

	/** Purple over the mushroom forests, red over the storm and the sand, green over stone and grass, blue elsewhere (the pulse forest). */
	private static int pickKind(ServerLevelAccessor level, BlockPos pos) {
		String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (biome.contains("mycotic")) {
			return 2;
		}
		if (biome.equals("brainstorm") || biome.equals("lakeside_desert")) {
			return 3;
		}
		if (biome.equals("stone_garden") || biome.equals("wind_valley") || biome.equals("verdant_plains")) {
			return 1;
		}
		return 0;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setKind(pickKind(level, this.blockPosition()));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public int getAmbientSoundInterval() {
		return CALL_INTERVAL;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return HURT;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("kind", this.kind());
		if (this.roost != null) {
			output.putInt("roost_x", this.roost.getX());
			output.putInt("roost_y", this.roost.getY());
			output.putInt("roost_z", this.roost.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setKind(input.getIntOr("kind", 0));
		if (input.getInt("roost_x").isPresent()) {
			this.roost = new BlockPos(input.getIntOr("roost_x", 0), input.getIntOr("roost_y", 0), input.getIntOr("roost_z", 0));
		}
	}
}
