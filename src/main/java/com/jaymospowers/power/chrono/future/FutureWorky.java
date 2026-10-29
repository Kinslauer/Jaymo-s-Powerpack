package com.jaymospowers.power.chrono.future;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Worky drone: the little flier that works beside an Omen ({@link FutureOmen}) and turns up only with one, in the
 * future ({@link FutureOmens}). It hovers over its mech's shoulder, patches it up a little at a time, spots what the mech
 * is up against (lighting those foes up, and pointing the mech at the nearest if it has none), and bolts for cover behind
 * the mech the moment anything hostile comes close or it is hurt. It has no weapons and never goes for a player; it
 * waves at a player who is near and not at odds with it. A drone left without a mech for half a minute leaves.
 * <p>Its states ({@link #STATE_IDLE}, {@link #STATE_FLY}, {@link #STATE_WAVE}) are synced for the model's animations.
 */
public class FutureWorky extends PathfinderMob {
	static final double HEALTH = 14.0, FLY_SPEED = 0.6, FOLLOW = 48.0;
	/** Hover this far above the mech's feet (its head is about 4.5), orbiting at this radius. */
	static final double HOVER_ABOVE = 5.2, ORBIT = 2.4;
	/** It patches its mech by this much every this many ticks, from within this range. */
	static final float HEAL = 2.0F;
	static final int HEAL_EVERY = 50;
	static final double HEAL_RANGE = 8.0;
	/** It spots foes this far from its mech, this often (ticks), and lights each up for this long. */
	static final double SPOT_RANGE = 28.0;
	static final int SPOT_EVERY = 25, MARK_TICKS = 60, MARK_MAX = 3;
	/** Anything hostile this close, or a hit, sends it running for this long (ticks). */
	static final double DANGER_RANGE = 7.0;
	static final int FLEE_FOR = 60, HURT_MEMORY = 60;
	/** A drone without a mech this long (ticks) goes. */
	static final int ORPHAN_TICKS = 600;
	/** It waves at a player this close, for at least this long (ticks) once it starts. */
	static final double WAVE_RANGE = 12.0;
	static final int WAVE_TICKS = 70;

	public static final byte STATE_IDLE = 0, STATE_FLY = 1, STATE_WAVE = 2;
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(FutureWorky.class, EntityDataSerializers.BYTE);

	private @Nullable UUID mechId;
	private int orphanFor;
	private int fleeFor;
	private int waveFor;
	private int movingFor;
	private @Nullable LivingEntity threat;

	public FutureWorky(EntityType<? extends FutureWorky> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		setNoGravity(true);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, FLY_SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STATE_IDLE);
	}

	/** Which animation it is in: idle, fly or wave. */
	public byte state() {
		return this.entityData.get(DATA_STATE);
	}

	/** Bound to this mech by {@link FutureOmens} when it is sent out. */
	void follow(FutureOmen mech) {
		this.mechId = mech.getUUID();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.mechId != null) {
			output.putString("Mech", this.mechId.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.mechId = input.getString("Mech").map(s -> {
			try {
				return UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (level.dimension() != Future.FUTURE) {
			// only ever in the future
			leave(level);
			return;
		}
		FutureOmen mech = mech(level);
		if (mech == null) {
			if (++orphanFor > ORPHAN_TICKS) {
				leave(level);
				return;
			}
		} else {
			orphanFor = 0;
		}
		if (this.tickCount % 5 == 0) {
			sizeUpDanger(level);
		}
		if (fleeFor > 0) {
			fleeFor--;
		}
		boolean fleeing = fleeFor > 0;
		Player friend = fleeing ? null : friendNear(level, mech);
		if (friend != null) {
			waveFor = WAVE_TICKS;
			getLookControl().setLookAt(friend, 40.0F, 40.0F);
		} else if (waveFor > 0) {
			waveFor--;
		}
		if (this.tickCount % 5 == 0) {
			steer(level, mech, fleeing);
		}
		if (mech != null && !fleeing) {
			if (this.tickCount % HEAL_EVERY == 0) {
				repair(level, mech);
			}
			if (this.tickCount % SPOT_EVERY == 0) {
				spot(level, mech);
			}
		}
		double speed = getDeltaMovement().horizontalDistanceSqr();
		if (speed > 0.0009 || fleeing) {
			movingFor = 12;
		} else if (movingFor > 0) {
			movingFor--;
		}
		byte state = waveFor > 0 && !fleeing ? STATE_WAVE : movingFor > 0 ? STATE_FLY : STATE_IDLE;
		if (state != state()) {
			this.entityData.set(DATA_STATE, state);
		}
	}

	/** Its mech: the one it was sent out with, else the nearest other that is alive. */
	private @Nullable FutureOmen mech(ServerLevel level) {
		if (this.mechId != null && level.getEntity(this.mechId) instanceof FutureOmen mech && mech.isAlive()) {
			return mech;
		}
		if (this.tickCount % 20 != 0) {
			return null;
		}
		List<FutureOmen> near = level.getEntitiesOfClass(FutureOmen.class, getBoundingBox().inflate(48.0), FutureOmen::isAlive);
		FutureOmen best = near.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(this))).orElse(null);
		this.mechId = best == null ? null : best.getUUID();
		return best;
	}

	/** Anything hostile close by, or a recent hit, and it runs. */
	private void sizeUpDanger(ServerLevel level) {
		this.threat = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DANGER_RANGE), FutureOmen::hostileTo).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
		LivingEntity hitBy = getLastHurtByMob();
		if (this.threat == null && hitBy != null && this.tickCount - getLastHurtByMobTimestamp() < HURT_MEMORY) {
			this.threat = hitBy;
		}
		if (this.threat != null) {
			fleeFor = FLEE_FOR;
		}
	}

	/** A player near enough, and not at odds with the mech or the drone: the one it waves at. */
	private @Nullable Player friendNear(ServerLevel level, @Nullable FutureOmen mech) {
		Player player = level.getNearestPlayer(this, WAVE_RANGE);
		if (player == null || player.isSpectator() || !hasLineOfSight(player)) {
			return null;
		}
		if (mech != null && (mech.getTarget() == player || mech.getLastHurtByMob() == player)) {
			return null;
		}
		if (getLastHurtByMob() == player) {
			return null;
		}
		return player;
	}

	/** Where it wants to be: tucked behind its mech away from danger, else orbiting over its shoulder. */
	private void steer(ServerLevel level, @Nullable FutureOmen mech, boolean fleeing) {
		Vec3 to;
		double speed = 1.0;
		if (fleeing) {
			speed = 1.8;
			Vec3 from = this.threat != null ? this.threat.position() : position().add(getLookAngle());
			if (mech != null) {
				Vec3 behind = mech.position().subtract(from).multiply(1.0, 0.0, 1.0).normalize().scale(2.2);
				to = mech.position().add(behind.x, 3.0, behind.z);
			} else {
				Vec3 away = position().subtract(from).multiply(1.0, 0.0, 1.0).normalize().scale(10.0);
				to = position().add(away.x, 2.0, away.z);
			}
		} else if (mech != null) {
			double angle = this.tickCount * 0.03 + getId() * 1.7;
			to = mech.position().add(Math.cos(angle) * ORBIT, HOVER_ABOVE + Math.sin(this.tickCount * 0.11) * 0.4, Math.sin(angle) * ORBIT);
			speed = distanceToSqr(mech) > 100.0 ? 1.6 : 1.0;
		} else {
			return;
		}
		// not into the ceiling: come down to the first free air
		double y = to.y;
		for (int i = 0; i < 6 && !level.getBlockState(BlockPos.containing(to.x, y, to.z)).isAir(); i++) {
			y -= 1.0;
		}
		getMoveControl().setWantedPosition(to.x, y, to.z, speed);
	}

	/** Patches the mech: a little health, with a thread of sparks across to it. */
	private void repair(ServerLevel level, FutureOmen mech) {
		if (mech.getHealth() >= mech.getMaxHealth() || distanceTo(mech) > HEAL_RANGE) {
			return;
		}
		mech.heal(HEAL);
		Vec3 from = position().add(0.0, 0.3, 0.0);
		Vec3 to = mech.position().add(0.0, mech.getBbHeight() * 0.7, 0.0);
		Vec3 step = to.subtract(from);
		int n = Math.max(2, (int) (step.length() * 2.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(step.scale(i / (double) n));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y, to.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, mech.getX(), mech.getY() + 2.0, mech.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6F, 1.5F);
	}

	/** Lights up the foes round the mech that it can see (the nearest few), and points the mech at the nearest if it has none. */
	private void spot(ServerLevel level, FutureOmen mech) {
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, mech.getBoundingBox().inflate(SPOT_RANGE), FutureOmen::hostileTo).stream()
			.filter(this::hasLineOfSight)
			.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(mech)))
			.limit(MARK_MAX)
			.toList();
		if (foes.isEmpty()) {
			return;
		}
		for (LivingEntity foe : foes) {
			foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false));
		}
		LivingEntity nearest = foes.getFirst();
		if (mech.getTarget() == null || !mech.getTarget().isAlive()) {
			mech.setTarget(nearest);
		}
		getLookControl().setLookAt(nearest, 40.0F, 40.0F);
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.8, getZ(), 4, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.5F, 1.8F);
	}

	/** A player who hits it brings its mech down on them; and nothing its own side fires hurts it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity by = source.getEntity();
		if (by instanceof FutureOmen || by instanceof FutureWorky) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			fleeFor = FLEE_FOR;
			if (by instanceof Player player && !player.isCreative() && !player.isSpectator()) {
				FutureOmen mech = mech(level);
				if (mech != null) {
					mech.setTarget(player);
				}
			}
		}
		return hurt;
	}

	private void leave(ServerLevel level) {
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		discard();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 2 + getRandom().nextInt(5)));
		if (getRandom().nextFloat() < 0.5F) {
			spawnAtLocation(level, new ItemStack(Items.REDSTONE, 1 + getRandom().nextInt(3)));
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.6, getZ(), 14, 0.3, 0.3, 0.3, 0.15);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.9F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return distance > 160.0 * 160.0;
	}
}
