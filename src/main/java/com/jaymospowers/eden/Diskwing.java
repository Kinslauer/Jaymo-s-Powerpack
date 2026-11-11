package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The diskwing (Eden Ring's): a flat, slow glider of the Verdant World's far country, two broad crescent wings and a
 * finned tail, lit along its back. It only drifts about; it neither fights nor drops anything. Its colour is its
 * country's ({@link #pickKind}).
 */
public class Diskwing extends PathfinderMob {
	/** The kinds, by index (skins are {@code diskwing_<name>}, each with a {@code _glow}). */
	public static final String[] KINDS = {"blue", "green", "purple", "red"};

	// ---- tuning ----
	public static final double HEALTH = 10.0, SPEED = 0.2, FLYING = 0.35;
	/** How fast it wanders, and how often (ticks, most) it calls. */
	public static final double WANDER_SPEED = 1.0;
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
		this.goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, WANDER_SPEED));
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setKind(input.getIntOr("kind", 0));
	}
}
