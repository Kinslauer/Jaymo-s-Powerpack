package com.jaymospowers.core.wounds;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A player out of their body ({@link Limbo}): the pale shape their friends see while the body lies left for dead. It does
 * nothing by itself: it is put where its owner drifts, and looks where they look. Nothing touches it, it touches nothing,
 * no mob sees it, and it is never saved. On its owner's own client a second one, never added to the world, is the camera
 * they look out of ({@link #looksWith}).
 */
public class LimboGhost extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(LimboGhost.class, EntityDataSerializers.INT);

	/** Client only: the camera's ghost turns its view with this entity (the player), frame by frame. */
	public @Nullable Entity looksWith;

	public LimboGhost(EntityType<? extends LimboGhost> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
		this.setSilent(true);
		this.setNoAi(true);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, -1);
	}

	/** The entity id of the player whose ghost this is (-1 for none). */
	public int owner() {
		return this.entityData.get(DATA_OWNER);
	}

	void owner(Entity owner) {
		this.entityData.set(DATA_OWNER, owner.getId());
	}

	@Override
	public float getViewYRot(float partialTicks) {
		return this.looksWith != null ? this.looksWith.getViewYRot(partialTicks) : super.getViewYRot(partialTicks);
	}

	@Override
	public float getViewXRot(float partialTicks) {
		return this.looksWith != null ? this.looksWith.getViewXRot(partialTicks) : super.getViewXRot(partialTicks);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// (nothing touches it; only the void and commands unmake it)
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canBeSeenByAnyone() {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}
}
