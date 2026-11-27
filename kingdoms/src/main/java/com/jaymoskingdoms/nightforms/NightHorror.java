package com.jaymoskingdoms.nightforms;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The true shape of the night as the world sees it ({@link NightHorrors}): a gaunt, winged horror (our own model,
 * {@code geckolib/models/entity/night_horror.geo.json}) drawn in the place of a vampire player, the way a werewolf player's
 * beast is drawn in theirs. It is a puppet the server makes and moves with them (their own body is not drawn: the same
 * client mixins that hide a bat or a mist's wearer), it thinks nothing, cannot be touched, pushed or saved, and never lives
 * past the shape. A blow aimed at it lands on the player ({@link NightForms}).
 *
 * <p>Its own animations (idle, walk, run, leap, strike, roar) come off two synced numbers: who it is ({@link #host}) and how
 * it moves ({@link #motion}); the leap, the strike and the roar are played by the server when they happen.
 */
public class NightHorror extends Mob implements GeoEntity {
	/** How it moves, for its animation: still, walking, running. */
	public static final int STILL = 0, WALK = 1, RUN = 2;

	private static final EntityDataAccessor<Integer> HOST = SynchedEntityData.defineId(NightHorror.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MOTION = SynchedEntityData.defineId(NightHorror.class, EntityDataSerializers.INT);

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALKING = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("run");
	static final RawAnimation LEAP = RawAnimation.begin().thenPlay("leap");
	static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
	static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public NightHorror(EntityType<? extends NightHorror> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(HOST, -1);
		entityData.define(MOTION, STILL);
	}

	/** The entity id of the player wearing it (the client reads their skin from it), or -1. */
	public int host() {
		return this.entityData.get(HOST);
	}

	public void setHost(int id) {
		this.entityData.set(HOST, id);
	}

	/** How it moves now ({@link #STILL}, {@link #WALK}, {@link #RUN}). */
	public int motion() {
		return this.entityData.get(MOTION);
	}

	public void setMotion(int motion) {
		if (this.entityData.get(MOTION) != motion) {
			this.entityData.set(MOTION, motion);
		}
	}

	/** The server plays its leap, its strike or its roar on every client that sees it. */
	public void leap() {
		this.triggerAnim("action", "leap");
	}

	public void strike() {
		this.triggerAnim("action", "strike");
	}

	public void roar() {
		this.triggerAnim("action", "roar");
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<NightHorror>("move", 4, test -> {
			int motion = test.animatable().motion();
			return test.setAndContinue(motion == RUN ? RUNNING : motion == WALK ? WALKING : IDLE);
		}));
		controllers.add(new AnimationController<NightHorror>("action", test -> PlayState.STOP)
			.triggerableAnim("leap", LEAP).triggerableAnim("strike", STRIKE).triggerableAnim("roar", ROAR));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	// ---- a puppet: nothing touches it, it is never saved ----

	@Override
	public boolean isPickable() {
		// the player's own reach, and what is shot at them, must pass through to the body that is really there
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
