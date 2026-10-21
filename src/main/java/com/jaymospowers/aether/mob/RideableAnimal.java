package com.jaymospowers.aether.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An Aether animal a player can saddle and ride (the moa, the phyg, the flying cow). The rider steers with the usual
 * keys and the jump key: a jump from the ground, then {@link #maxAirJumps} more in mid-air, and holding jump while
 * falling glides. The ride is simulated on the rider's own client (as with any mount); the server only sees the
 * result, so none of it needs a packet. Falls never hurt it, and it never drops quicker than {@link #freeFall}
 * (loose) or {@link #riddenFall} (ridden).
 */
public abstract class RideableAnimal extends Animal {
	// ---- tuning ----
	/** Cooldown between mid-air jumps (ticks). */
	public static final int AIR_JUMP_COOLDOWN = 10;
	/** Safe fall distance (blocks): these animals shrug off any drop. */
	public static final double SAFE_FALL = 1024.0;
	/** The rider sideways and backwards moves at this share of forward speed. */
	public static final float STRAFE = 0.5F, REVERSE = 0.25F;
	/** How long (ticks) it bolts after its rider stops holding the calm item. */
	public static final int SPOOK_TICKS = 100;

	/** Wing and leg flap: the phase and how hard (0 on the ground, 1 in the air), with last tick's for smoothing. */
	public float flap, oFlap, flapSpeed, oFlapSpeed;

	private int airJumps, airCooldown, spook;
	private boolean jumpWasHeld, glideHeld;

	protected RideableAnimal(EntityType<? extends RideableAnimal> type, Level level) {
		super(type, level);
	}

	// ---- what a kind sets ----

	/** Jumps it gives in mid-air once its feet have left the ground. */
	protected abstract int maxAirJumps();

	/** Upward speed of a jump from the ground, and of one in mid-air (blocks a tick). */
	protected abstract double groundJump();

	protected abstract double airJump();

	/** The most it falls a tick: loose, ridden, and ridden with jump held. */
	protected double freeFall() {
		return 0.1;
	}

	protected double riddenFall() {
		return 0.5;
	}

	protected double glideFall() {
		return 0.1;
	}

	/** Its speed under a rider, as a multiple of its walking speed. */
	protected float rideSpeed() {
		return 1.0F;
	}

	/** How high the rider sits above its feet (blocks). */
	protected abstract double seatHeight();

	/** The item that calms it: a player holding it can climb on without a saddle (null for none). */
	protected @Nullable Item calmItem() {
		return null;
	}

	/** Whether {@code player} holds the calm item. */
	protected boolean calms(Player player) {
		Item calm = this.calmItem();
		return calm != null && (player.getMainHandItem().is(calm) || player.getOffhandItem().is(calm));
	}

	/** What a kind does with its jumps: the jumps it has on the ground (default: all), after a jump in mid-air, and each ride tick. */
	protected int refill(int jumpsLeft) {
		return this.maxAirJumps();
	}

	protected void airJumped() {
	}

	protected void rideTick(boolean onGround) {
	}

	/** Whether this one may be saddled (the moa only if a player raised it). */
	protected boolean canBeSaddled() {
		return !this.isBaby();
	}

	public boolean isSaddled() {
		return !this.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
	}

	// ---- riding ----

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (this.isFood(held)) {
			return super.mobInteract(player, hand);
		}
		Item calm = this.calmItem();
		if (calm != null && held.is(calm) && !this.isBaby() && !this.isVehicle() && !player.isSecondaryUseActive()) {
			if (!this.level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.isSaddled() && held.is(Items.SADDLE) && this.canBeSaddled()) {
			if (!this.level().isClientSide()) {
				this.setItemSlot(EquipmentSlot.SADDLE, held.copyWithCount(1));
				held.consume(1, player);
				this.playSound(SoundEvents.PIG_SADDLE.value(), 0.5F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isSaddled() && !this.isBaby() && !this.isVehicle() && !player.isSecondaryUseActive()) {
			if (!this.level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player && (this.isSaddled() || this.calms(player)) ? player : null;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		moveFunction.accept(passenger, this.getX(), this.getY() + this.seatHeight(), this.getZ());
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player rider && this.isAlive()) {
			// it faces where its rider looks
			this.setYRot(rider.getYRot());
			this.yRotO = this.getYRot();
			this.setXRot(rider.getXRot() * 0.5F);
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
			this.setSpeed((float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.rideSpeed());
			this.rideJump();
			float forward = rider.zza < 0.0F ? rider.zza * REVERSE : rider.zza;
			super.travel(new Vec3(rider.xxa * STRAFE, input.y, forward));
		} else {
			this.glideHeld = false;
			super.travel(input);
		}
		this.limitFall();
	}

	/** The jump key, read where the ride is simulated (the rider's own client). */
	private void rideJump() {
		boolean held = AetherMobs.jumpHeld.getAsBoolean();
		this.rideTick(this.onGround());
		boolean pressed = held && !this.jumpWasHeld;
		this.jumpWasHeld = held;
		Vec3 motion = this.getDeltaMovement();
		if (this.onGround()) {
			this.airJumps = this.refill(this.airJumps);
			this.glideHeld = false;
			if (held) {
				this.setDeltaMovement(motion.x, this.groundJump(), motion.z);
			}
		} else {
			if (pressed && this.airJumps > 0 && this.airCooldown <= 0) {
				this.airJumps--;
				this.airJumped();
				this.airCooldown = AIR_JUMP_COOLDOWN;
				this.setDeltaMovement(motion.x, this.airJump(), motion.z);
				this.playSound(SoundEvents.PARROT_FLY, 0.6F, 1.2F);
			}
			this.glideHeld = held;
		}
		if (this.airCooldown > 0) {
			this.airCooldown--;
		}
	}

	/** Caps the fall; a drop this gentle also lets it land without a scratch. */
	private void limitFall() {
		if (this.onGround()) {
			return;
		}
		Vec3 motion = this.getDeltaMovement();
		double cap = this.getControllingPassenger() == null ? this.freeFall() : this.glideHeld ? this.glideFall() : this.riddenFall();
		if (motion.y < -cap) {
			this.setDeltaMovement(motion.x, -cap, motion.z);
		}
		this.resetFallDistance();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel && this.calmItem() != null) {
			// a rider without a saddle needs the calm item in hand: put it away and the mount bolts
			if (!this.isSaddled() && this.getFirstPassenger() instanceof Player rider && !this.calms(rider)) {
				rider.stopRiding();
				this.spook = SPOOK_TICKS;
			}
			if (this.spook > 0) {
				this.spook--;
			}
		}
	}

	/** Runs for its life when spooked (use instead of a plain {@link PanicGoal}). */
	public class SpookPanicGoal extends PanicGoal {
		public SpookPanicGoal(double speed) {
			super(RideableAnimal.this, speed);
		}

		@Override
		protected boolean shouldPanic() {
			return RideableAnimal.this.spook > 0 || super.shouldPanic();
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.oFlap = this.flap;
		this.oFlapSpeed = this.flapSpeed;
		boolean rising = this.getDeltaMovement().y > 0.0;
		this.flapSpeed += ((this.onGround() ? 0.0F : 1.0F) - this.flapSpeed) * 0.3F;
		this.flap += this.flapSpeed * (rising ? 1.3F : 0.6F);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		// its saddle comes off whole, not at a drop chance
		ItemStack saddle = this.getItemBySlot(EquipmentSlot.SADDLE);
		if (!saddle.isEmpty()) {
			this.setItemSlot(EquipmentSlot.SADDLE, ItemStack.EMPTY);
		}
		super.die(source);
		if (!saddle.isEmpty() && this.level() instanceof ServerLevel level) {
			this.spawnAtLocation(level, saddle);
		}
	}
}
