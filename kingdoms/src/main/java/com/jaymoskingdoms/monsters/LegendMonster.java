package com.jaymoskingdoms.monsters;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A monster of legend ({@link Monsters}): which of its looks it wears, and which arm it's swinging (left, right or both),
 * so its model can play the blow the way Mo' Creatures' did (a counter running up from the swing).
 */
public abstract class LegendMonster extends Monster {
	public static final int LEFT = 1, RIGHT = 2, BOTH = 3;
	/** How long a swing shows (ticks). */
	static final int SWING_TICKS = 10;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(LegendMonster.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> SWING = SynchedEntityData.defineId(LegendMonster.class, EntityDataSerializers.BYTE);

	private int swingTicks;
	/** (Client) how far each arm's swing has run. */
	private int leftCounter, rightCounter;

	protected LegendMonster(EntityType<? extends LegendMonster> type, Level level) {
		super(type, level);
	}

	/** How many looks it has. */
	protected int variants() {
		return 1;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
		entityData.define(SWING, (byte) 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, this.variants()));
	}

	/** Its look when it comes into the world. */
	protected int pickVariant(ServerLevelAccessor level) {
		return this.random.nextInt(this.variants());
	}

	/** Swings {@code arm} ({@link #LEFT}, {@link #RIGHT}, {@link #BOTH}). */
	protected void swingArm(int arm) {
		this.entityData.set(SWING, (byte) arm);
		this.swingTicks = SWING_TICKS;
	}

	/** Which arm is swinging, 0 for none. */
	public int swingingArm() {
		return this.entityData.get(SWING);
	}

	/** (Client) how far the left arm's blow has run, 0 when it isn't swinging. */
	public int leftCounter() {
		return this.leftCounter;
	}

	public int rightCounter() {
		return this.rightCounter;
	}

	/** The arm it hits with this time. */
	protected int nextArm() {
		return this.random.nextBoolean() ? LEFT : RIGHT;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.swingArm(this.nextArm());
		}
		return hit;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			if (this.swingTicks > 0 && --this.swingTicks == 0) {
				this.entityData.set(SWING, (byte) 0);
			}
			return;
		}
		int arm = this.swingingArm();
		this.leftCounter = (arm & LEFT) != 0 ? this.leftCounter + 1 : 0;
		this.rightCounter = (arm & RIGHT) != 0 ? this.rightCounter + 1 : 0;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setVariant(this.pickVariant(level));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
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
}
