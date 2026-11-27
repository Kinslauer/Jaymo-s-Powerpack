package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An NPC angler's float ({@link Anglers}): it bobs where the cast put it, dips when a fish takes, and rides in the hooked
 * fish's mouth through the fight. Its line runs from the angler's rod (drawn by its renderer, slack while it waits and
 * drawn straight in a fight). Moved by {@link Anglers}; gone if nobody's line holds it. Never saved.
 */
public final class AnglerFloat extends Entity {
	public static EntityType<AnglerFloat> TYPE;
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(AnglerFloat.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> TAUT = SynchedEntityData.defineId(AnglerFloat.class, EntityDataSerializers.BOOLEAN);

	public AnglerFloat(EntityType<? extends AnglerFloat> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("angler_float"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<AnglerFloat>of(AnglerFloat::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.sized(0.25F, 0.25F)
			.clientTrackingRange(6)
			.updateInterval(1)
			.build(key));
	}

	/** A float for {@code owner}'s line, put in the water at {@code at}. */
	static AnglerFloat at(ServerLevel level, Entity owner, Vec3 at) {
		AnglerFloat bob = new AnglerFloat(TYPE, level);
		bob.entityData.set(OWNER, owner.getId());
		bob.snapTo(at.x, at.y - 0.1, at.z, 0.0F, 0.0F);
		level.addFreshEntity(bob);
		return bob;
	}

	/** Whose line it's on (null: nobody's, or not loaded here). */
	public @Nullable Entity owner() {
		int id = this.entityData.get(OWNER);
		return id < 0 ? null : this.level().getEntity(id);
	}

	/** Whether its line is drawn straight (a fish on it). */
	public boolean taut() {
		return this.entityData.get(TAUT);
	}

	void setTaut(boolean taut) {
		this.entityData.set(TAUT, taut);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			Entity owner = this.owner();
			if (owner == null || !Anglers.angling(owner)) {
				this.discard();
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, -1);
		builder.define(TAUT, false);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
