package com.jaymospowers.alchemy.gear;

import com.jaymospowers.core.LockOn;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.47) A clockwork hornet, loosed from a {@link ClockworkHornets Clockwork Hornet Hive}: a small brass drone that harries
 * its owner's lock-on target ({@link LockOn#target}), else the last living thing that hurt the owner, stinging it every
 * {@link ClockworkHornets#STING_EVERY} ticks; with nothing to chase it circles its owner. It lasts {@link ClockworkHornets#LIFE_TICKS}
 * ticks, goes with its owner (logging out, dying, another dimension), pops when struck, and is never saved. Drawn as a small
 * item sprite ({@link ItemSupplier}).
 */
public final class ClockworkHornet extends Entity implements ItemSupplier {
	private @Nullable UUID owner;
	/** The tick (of its own age) it may sting again, and the tick its retreat after a sting ends. */
	private int nextSting, retreatUntil;
	/** Where it circles its owner, and its own phase round the foe, so three drones aren't one. */
	private final float phase;

	public ClockworkHornet(EntityType<? extends ClockworkHornet> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.phase = this.random.nextFloat() * (float)Math.PI * 2.0F;
	}

	void setOwner(Player player) {
		this.owner = player.getUUID();
	}

	/** Whose it is, if they are still about. */
	/** Whether {@code player} let this one loose. */
	boolean ownedBy(Player player) {
		return player.getUUID().equals(this.owner);
	}

	private @Nullable ServerPlayer owner(ServerLevel level) {
		return this.owner != null && level.getPlayerByUUID(this.owner) instanceof ServerPlayer player && player.isAlive() ? player : null;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(ClockworkHornets.HORNET);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = this.owner(level);
		if (owner == null || owner.level() != level || this.tickCount >= ClockworkHornets.LIFE_TICKS) {
			this.pop(level);
			return;
		}
		LivingEntity target = chase(owner);
		Vec3 goal;
		double speed;
		if (target != null && this.tickCount >= this.retreatUntil) {
			// round the foe's middle, a little above it, never still
			double a = this.tickCount * 0.35 + this.phase;
			goal = target.getBoundingBox().getCenter().add(Math.cos(a) * 0.5, 0.2 + Math.sin(a * 1.7) * 0.3, Math.sin(a) * 0.5);
			speed = ClockworkHornets.SPEED_CHASE;
		} else if (target != null) {
			// it has stung: back off a little
			goal = target.getBoundingBox().getCenter().add(Math.cos(this.phase) * 2.0, 1.2, Math.sin(this.phase) * 2.0);
			speed = ClockworkHornets.SPEED_CHASE;
		} else {
			double a = this.tickCount * 0.12 + this.phase;
			goal = owner.position().add(Math.cos(a) * 1.6, owner.getBbHeight() + 0.2 + Math.sin(a * 2.0) * 0.25, Math.sin(a) * 1.6);
			speed = ClockworkHornets.SPEED_IDLE;
		}
		Vec3 to = goal.subtract(this.position());
		double d = to.length();
		Vec3 step = d < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(speed, d) / d);
		// a far hornet (left behind by a fast owner) catches up
		if (target == null && d > ClockworkHornets.LEASH) {
			step = to.scale(Math.min(1.5, d) / d);
		}
		Vec3 next = this.position().add(step);
		this.setPos(next);
		this.setDeltaMovement(step);
		if (step.lengthSqr() > 1.0E-4) {
			this.setYRot((float)(Math.atan2(-step.x, step.z) * 57.29577951308232));
		}
		if (this.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (this.tickCount % 12 == 0) {
			level.playSound(null, next.x, next.y, next.z, SoundEvents.BEE_LOOP, SoundSource.NEUTRAL, 0.25F, 1.6F);
		}
		if (target != null && this.tickCount >= this.nextSting && this.tickCount >= this.retreatUntil
			&& this.getBoundingBox().inflate(0.35).intersects(target.getBoundingBox())) {
			this.sting(level, owner, target);
		}
	}

	/** The owner's lock-on target, else the last one that hurt them lately; never the owner or another player. */
	private @Nullable LivingEntity chase(ServerPlayer owner) {
		LivingEntity locked = LockOn.target(owner);
		if (valid(owner, locked)) {
			return locked;
		}
		LivingEntity hurter = owner.getLastHurtByMob();
		if (valid(owner, hurter) && owner.tickCount - owner.getLastHurtByMobTimestamp() < ClockworkHornets.RECENT_TICKS) {
			return hurter;
		}
		return null;
	}

	private static boolean valid(ServerPlayer owner, @Nullable LivingEntity foe) {
		return foe != null && foe != owner && foe.isAlive() && !(foe instanceof Player) && foe.level() == owner.level()
			&& foe.distanceToSqr(owner) < ClockworkHornets.CHASE_RANGE * ClockworkHornets.CHASE_RANGE;
	}

	private void sting(ServerLevel level, ServerPlayer owner, LivingEntity foe) {
		this.nextSting = this.tickCount + ClockworkHornets.STING_EVERY;
		this.retreatUntil = this.tickCount + ClockworkHornets.RETREAT_TICKS;
		foe.hurtServer(level, level.damageSources().playerAttack(owner), ClockworkHornets.STING_DAMAGE);
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.BEE_STING, SoundSource.NEUTRAL, 0.5F, 1.8F);
	}

	/** Gone, in a puff of brass-dust and sparks. */
	private void pop(ServerLevel level) {
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 6, 0.15, 0.15, 0.15, 0.1);
		this.discard();
	}

	/** Struck by a living thing, it pops. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity && source.getEntity() != level.getPlayerByUUID(this.owner == null ? new UUID(0L, 0L) : this.owner)) {
			this.pop(level);
			return true;
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
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
