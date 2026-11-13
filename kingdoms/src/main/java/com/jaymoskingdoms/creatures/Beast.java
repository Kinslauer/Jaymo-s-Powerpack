package com.jaymoskingdoms.creatures;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** A monster that shows a pose number and a fullness to its renderer ({@link Looks}). The base of this pack's new creatures. */
public abstract class Beast extends Monster implements Looks {
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Beast.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> FULL = SynchedEntityData.defineId(Beast.class, EntityDataSerializers.FLOAT);

	protected Beast(EntityType<? extends Beast> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(LOOK, 0);
		entityData.define(FULL, 0.0F);
	}

	@Override
	public int look() {
		return this.entityData.get(LOOK);
	}

	@Override
	public float fullness() {
		return this.entityData.get(FULL);
	}

	protected void setLook(int look) {
		if (this.entityData.get(LOOK) != look) {
			this.entityData.set(LOOK, look);
		}
	}

	protected void setFullness(float full) {
		float f = Math.max(0.0F, Math.min(1.0F, full));
		if (Math.abs(this.entityData.get(FULL) - f) > 0.02F) {
			this.entityData.set(FULL, f);
		}
	}
}
