package com.jaymospowers.aether.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An Aether animal that comes in the three climates (kirrid, burrukai, taegore): the zone is picked from the biome it
 * spawns in and shown by its skin. They all feed on oranges and blue berries.
 */
public abstract class ZoneAnimal extends Animal {
	private static final EntityDataAccessor<Integer> ZONE = SynchedEntityData.defineId(ZoneAnimal.class, EntityDataSerializers.INT);

	protected ZoneAnimal(EntityType<? extends ZoneAnimal> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ZONE, 0);
	}

	public Zone zone() {
		return Zone.byIndex(this.entityData.get(ZONE));
	}

	public void setZone(Zone zone) {
		this.entityData.set(ZONE, zone.ordinal());
	}

	/** What the wild one looks like in its first zone: hook for the colour odds. */
	protected void onZone(Zone zone) {
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		Zone zone = Zone.at(level, this.blockPosition());
		this.setZone(zone);
		this.onZone(zone);
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return AetherAnimals.isAnimalFood(stack);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("zone", this.zone().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(ZONE, Zone.byIndex(input.getIntOr("zone", 0)).ordinal());
	}
}
