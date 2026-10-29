package com.jaymoskingdoms.beasts;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.329) A monster that makes its moves as named animations ({@link Acting}): {@link #act} starts one, shown to every
 * client for {@code ticks}, and {@link #acting} says whether one is still running and for how long. Its moves are listed
 * by {@link #actions} (synced by their place in that list).
 */
public abstract class ActingMonster extends Monster implements Acting {
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ActingMonster.class, EntityDataSerializers.INT);

	/** (Server) how long the current move has left, and has run (ticks). */
	private int actionLeft, actionRun;
	/** (Client) the tick its move began. */
	private int actionStartedAt;

	protected ActingMonster(EntityType<? extends ActingMonster> type, Level level) {
		super(type, level);
	}

	/** Its moves' animations; a move is synced by its place here. */
	protected abstract String[] actions();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, -1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ACTION.equals(key)) {
			this.actionStartedAt = this.tickCount;
		}
	}

	/** Starts the move {@code anim} (one of {@link #actions}), running {@code ticks}. */
	protected void act(String anim, int ticks) {
		String[] all = this.actions();
		for (int i = 0; i < all.length; i++) {
			if (all[i].equals(anim)) {
				this.entityData.set(ACTION, i, true);
				this.actionLeft = ticks;
				this.actionRun = 0;
				return;
			}
		}
	}

	/** Ends the current move now. */
	protected void stopActing() {
		this.entityData.set(ACTION, -1);
		this.actionLeft = 0;
	}

	/** (Server) whether a move is running. */
	protected boolean acting() {
		return this.actionLeft > 0;
	}

	/** (Server) how many ticks the current move has run (its first tick is 1). */
	protected int actionRun() {
		return this.actionRun;
	}

	/** (Server) whether the current move is {@code anim}. */
	protected boolean acting(String anim) {
		return this.acting() && anim.equals(this.action());
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.actionLeft > 0) {
			this.actionRun++;
			if (--this.actionLeft == 0) {
				this.entityData.set(ACTION, -1);
				this.onActionEnd();
			}
		}
	}

	/** (Server) its move has run its course. */
	protected void onActionEnd() {
	}

	/**
	 * Its blow lands: {@code damage} on every living thing within {@code reach} (only those in front of it if
	 * {@code front}) that {@link #strikes} would hit, thrown back {@code knock} and up {@code up}. Returns how many it hit.
	 */
	protected int strike(ServerLevel level, float damage, double reach, boolean front, double knock, double up) {
		Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, reach * 0.5, reach),
			e -> e != this && e.isAlive() && this.strikes(e))) {
			Vec3 to = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (to.length() > reach + e.getBbWidth() / 2.0 + this.getBbWidth() / 2.0) {
				continue;
			}
			if (front && to.length() > 0.5 && to.normalize().dot(look) < 0.2) {
				continue;
			}
			DamageSource source = this.damageSources().mobAttack(this);
			if (e.hurtServer(level, source, damage)) {
				hit++;
				e.knockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ(), source, damage);
				if (up > 0.0) {
					e.setDeltaMovement(e.getDeltaMovement().add(0.0, up, 0.0));
					e.needsSync = true;
				}
			}
		}
		return hit;
	}

	/** Whether its blows land on {@code e}: by default its target and anything that would fight it. */
	protected boolean strikes(LivingEntity e) {
		return e == this.getTarget() || e instanceof Player player && !player.isCreative() && !player.isSpectator()
			|| e instanceof Mob mob && mob.getTarget() == this;
	}

	@Override
	public @Nullable String action() {
		int i = this.entityData.get(ACTION);
		String[] all = this.actions();
		return i >= 0 && i < all.length ? all[i] : null;
	}

	@Override
	public int actionAge() {
		return this.tickCount - this.actionStartedAt;
	}
}
