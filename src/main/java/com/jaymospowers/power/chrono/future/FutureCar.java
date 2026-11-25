package com.jaymospowers.power.chrono.future;

import com.jaymospowers.core.HudBar;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One of the future's cars ({@link CarKind}; Patch 8: four of the Cars mod's, found only in the ruins, fuelled from the
 * future's stores). Right-click one to get in (a second seat beside the driver), feed the tank redstone, gunpowder, coal,
 * charcoal or blaze powder (what the future's crates still hold, {@link #worth}), and drive: forward and back are the
 * throttle and the brake (then reverse), left and right steer, jump is the handbrake (the rear lets go and it slides). The
 * drive is the Cars mod's tyre model ({@link CarPhysics}): it holds the road well, ice hardly at all, and it needs speed
 * to turn. A hard stop hurts the driver, and a car at speed hurts what it runs down ({@link #RAM}). It can't be broken or
 * carried off.
 */
public class FutureCar extends Entity {
	/** Fuel held (ticks of full throttle), and what each fuel is worth. */
	public static final int MAX_FUEL = 12000;
	static final int REDSTONE = 1500, GUNPOWDER = 1000, COAL = 3000, CHARCOAL = 2200, BLAZE = 3600;
	/** Gravity a tick, and the lift of the little hop that takes a car over a one-block step. */
	static final double GRAVITY = 0.08, HOP = 0.42;
	/** Past this speed (blocks a tick) it hurts what it hits, this much a block a tick, and it throws them this far. */
	static final double RAM_SPEED = 0.35, RAM = 18.0, RAM_KICK = 0.8;
	/** A sudden loss of speed over this (blocks a tick) hurts the driver, this much for each block a tick over it. */
	static final double CRASH_SPEED = 0.45, CRASH = 14.0;
	/** The engine's hum, how often (ticks). */
	static final int HUM_EVERY = 14;

	private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(FutureCar.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(FutureCar.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(FutureCar.class, EntityDataSerializers.FLOAT);

	private final CarKind kind;
	private final CarPhysics physics = new CarPhysics();

	public FutureCar(EntityType<? extends FutureCar> type, Level level) {
		super(type, level);
		this.kind = FutureCars.kindOf(type);
	}

	public CarKind kind() {
		return this.kind;
	}

	public int fuel() {
		return this.entityData.get(FUEL);
	}

	public void setFuel(int fuel) {
		this.entityData.set(FUEL, Mth.clamp(fuel, 0, MAX_FUEL));
	}

	/** Forward speed (blocks a tick, signed) as the client sees it, for the wheels. */
	public float speed() {
		return this.entityData.get(SPEED);
	}

	/** How far the front wheels are turned, -1 (left) to 1 (right). */
	public float steer() {
		return this.entityData.get(STEER);
	}

	/** How much fuel {@code stack} puts in the tank; 0 if it isn't fuel. */
	static int worth(ItemStack stack) {
		if (stack.is(Items.REDSTONE)) {
			return REDSTONE;
		}
		if (stack.is(Items.GUNPOWDER)) {
			return GUNPOWDER;
		}
		if (stack.is(Items.COAL)) {
			return COAL;
		}
		if (stack.is(Items.CHARCOAL)) {
			return CHARCOAL;
		}
		return stack.is(Items.BLAZE_POWDER) ? BLAZE : 0;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = level();
		if (level.isClientSide()) {
			return;
		}
		boolean engine = false, handbrake = false;
		double throttle = 0.0, steer = 0.0;
		if (getControllingPassenger() instanceof ServerPlayer rider) {
			Input input = rider.getLastClientInput();
			throttle = (input.forward() ? 1.0 : 0.0) - (input.backward() ? 1.0 : 0.0);
			steer = (input.right() ? 1.0 : 0.0) - (input.left() ? 1.0 : 0.0);
			handbrake = input.jump();
			engine = fuel() > 0;
			if (this.tickCount % 5 == 0) {
				bar(rider);
			}
		}
		if (engine && throttle > 0.0) {
			setFuel(fuel() - 1);
		}
		float friction = level.getBlockState(getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
		double yaw = getYRot() * Mth.DEG_TO_RAD;
		double before = this.physics.forwardSpeed(yaw);
		yaw = this.physics.step(yaw, this.kind, engine, throttle, steer, handbrake, CarPhysics.grip(friction));
		setYRot((float) (yaw * Mth.RAD_TO_DEG));
		double vy = onGround() ? -0.01 : (getDeltaMovement().y - GRAVITY) * 0.98;
		setDeltaMovement(this.physics.velocity.x, vy, this.physics.velocity.z);
		move(MoverType.SELF, getDeltaMovement());
		Vec3 moved = getDeltaMovement();
		// what a wall took off it (a step of one block it hops instead)
		if (this.horizontalCollision && onGround() && Math.abs(before) > 0.1 && canHop(moved)) {
			setDeltaMovement(this.physics.velocity.x, HOP, this.physics.velocity.z);
			move(MoverType.SELF, getDeltaMovement());
			moved = getDeltaMovement();
		} else {
			this.physics.velocity = new Vec3(moved.x, 0.0, moved.z);
		}
		double after = this.physics.forwardSpeed(yaw);
		if (before - after > CRASH_SPEED && getControllingPassenger() instanceof LivingEntity driver && level instanceof ServerLevel server) {
			driver.hurtServer(server, damageSources().flyIntoWall(), (float) ((before - after - CRASH_SPEED) * CRASH));
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.5F, 1.4F);
		}
		resetFallDistance();
		if (level instanceof ServerLevel server) {
			ram(server, after);
			this.entityData.set(SPEED, (float) after);
			this.entityData.set(STEER, (float) steer);
			if (engine && this.tickCount % HUM_EVERY == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.5F, 0.5F + (float) Math.abs(after));
			}
			if (Math.abs(after) > 0.15 && handbrake && this.tickCount % 2 == 0) {
				server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.1, getZ(), 1, 0.4, 0.0, 0.4, 0.0);
			}
		}
	}

	/** Whether the way ahead is a one-block step with room over it. */
	private boolean canHop(Vec3 moved) {
		Vec3 ahead = this.physics.velocity.normalize().scale(0.6);
		AABB up = getBoundingBox().move(ahead.x, 1.05, ahead.z);
		return level().noCollision(this, up);
	}

	/** At speed it hurts and throws what it runs into. */
	private void ram(ServerLevel level, double speed) {
		if (Math.abs(speed) < RAM_SPEED) {
			return;
		}
		Vec3 heading = this.physics.velocity.normalize();
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3, 0.0, 0.3), e -> e.isAlive() && !hasPassenger(e)
			&& e.getVehicle() != this);
		for (LivingEntity e : hit) {
			Entity driver = getControllingPassenger();
			DamageSource source = driver instanceof Player p ? damageSources().playerAttack(p) : damageSources().generic();
			if (e.hurtServer(level, source, (float) (Math.abs(speed) * RAM))) {
				e.setDeltaMovement(e.getDeltaMovement().add(heading.x * RAM_KICK, 0.4, heading.z * RAM_KICK));
			}
		}
	}

	/** The fuel gauge, shown to {@code player}: gold, yellow under half, red under a fifth. */
	void bar(Player player) {
		if (player instanceof ServerPlayer sp) {
			HudBar.gauge(sp, "car_fuel", (float) fuel() / MAX_FUEL, fuel() > MAX_FUEL / 2 ? HudBar.GOLD : fuel() > MAX_FUEL / 5 ? HudBar.YELLOW : HudBar.RED,
				Component.translatable("bar.jaymospowers.fuel"), null);
		}
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && getPassengers().size() < 2;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		// the driver at the wheel (left), a passenger beside
		double side = getPassengers().indexOf(passenger) == 0 ? 0.4 : -0.4;
		float radians = getYRot() * Mth.DEG_TO_RAD;
		double along = -0.1;
		double x = getX() + Mth.cos(radians) * side - Mth.sin(radians) * along;
		double z = getZ() + Mth.sin(radians) * side + Mth.cos(radians) * along;
		moveFunction.accept(passenger, x, getY() + 0.3, z);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity living ? living : null;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		float radians = getYRot() * Mth.DEG_TO_RAD;
		return position().add(Mth.cos(radians) * (this.kind.width * 0.5 + 0.8), 0.1, Mth.sin(radians) * (this.kind.width * 0.5 + 0.8));
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	/** Nothing breaks one but what bypasses invulnerability (/kill). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!isRemoved() && source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			ejectPassengers();
			discard();
			return true;
		}
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level() instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		int worth = worth(held);
		if (worth > 0 && !player.isSecondaryUseActive()) {
			if (fuel() < MAX_FUEL) {
				setFuel(fuel() + worth);
				held.consume(1, player);
				server.playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.7F, 0.8F);
			}
			bar(player);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!player.isPassenger() && getPassengers().size() < 2) {
			player.startRiding(this);
			bar(player);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FUEL, 0);
		builder.define(SPEED, 0.0F);
		builder.define(STEER, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setFuel(input.getIntOr("fuel", 0));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("fuel", fuel());
	}
}
