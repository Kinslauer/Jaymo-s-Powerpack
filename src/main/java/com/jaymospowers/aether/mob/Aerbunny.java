package com.jaymospowers.aether.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The aerbunny, the Aether's small white rabbit of clouds. It hops about, drifts down slowly, and flees for a few
 * seconds when a player hits it. Pick it up with an empty hand (sneak and use to put it down) and it sits on your
 * head: your fall is slowed, and once you have dropped far enough you can hop again in mid-air (jump), which puffs it
 * up for a moment. The rider's side of that is {@link AerbunnyRide}, run on the rider's client.
 */
public class Aerbunny extends Animal {
	// ---- tuning ----
	public static final double HEALTH = 6.0, SPEED = 0.28;
	/** The most it falls a tick, when it falls by itself. */
	public static final double FALL = 0.1;
	/** How long it flees after a player hits it (ticks): 100 to 149. */
	public static final int FLEE_MIN = 100, FLEE_SPREAD = 50;
	/** Ticks it hops about between hops while walking. */
	public static final int HOP_EVERY = 12;
	/** After it is put down, it can't be picked up again for this long (ticks), so one click does not do both. */
	public static final int PICKUP_COOLDOWN = 10;

	private static final EntityDataAccessor<Integer> PUFF = SynchedEntityData.defineId(Aerbunny.class, EntityDataSerializers.INT);

	private int fleeTicks, putDownAt = -PICKUP_COOLDOWN;
	private double lastY;

	public Aerbunny(EntityType<? extends Aerbunny> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(PUFF, 0);
	}

	/** Ticks of puff left (it swells when its rider hops, then settles). */
	public int puff() {
		return this.entityData.get(PUFF);
	}

	public void setPuff(int ticks) {
		this.entityData.set(PUFF, ticks);
	}

	/** The player carrying it, if any. */
	public @Nullable Player carrier() {
		return this.getVehicle() instanceof Player player ? player : null;
	}

	// ---- life ----

	@Override
	public void tick() {
		super.tick();
		if (this.puff() > 0) {
			this.setPuff(this.puff() - 1);
		}
		Player carrier = this.carrier();
		if (carrier != null) {
			if (!this.level().isClientSide()) {
				// set down when its carrier sneaks or can't carry it
				if (carrier.isShiftKeyDown() || !carrier.isAlive() || carrier.isSpectator()) {
					this.stopRiding();
					this.putDownAt = this.tickCount;
				} else {
					// a drop it slows to a gentle one doesn't hurt
					double dy = carrier.getY() - this.lastY;
					if (dy > -AerbunnyRide.RIDER_FALL * 2.0) {
						carrier.resetFallDistance();
					}
					this.lastY = carrier.getY();
				}
			}
			return;
		}
		// it drifts down
		Vec3 motion = this.getDeltaMovement();
		if (!this.onGround() && motion.y < -FALL) {
			this.setDeltaMovement(motion.x, -FALL, motion.z);
		}
		this.resetFallDistance();
		if (this.fleeTicks > 0) {
			this.fleeTicks--;
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		// a rabbit's walk is a string of hops
		if (this.carrier() == null && this.onGround() && this.tickCount % HOP_EVERY == 0
			&& this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
			this.getJumpControl().jump();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player) {
			this.fleeTicks = FLEE_MIN + this.random.nextInt(FLEE_SPREAD);
			if (this.carrier() != null) {
				this.stopRiding();
				this.putDownAt = this.tickCount;
			}
		}
		return hurt;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive() && this.carrier() == null
			&& this.fleeTicks <= 0 && this.tickCount - this.putDownAt >= PICKUP_COOLDOWN && player.getPassengers().isEmpty()) {
			if (!this.level().isClientSide()) {
				if (this.startRiding(player, true, true)) {
					this.lastY = player.getY();
					this.playSound(SoundEvents.RABBIT_JUMP, 0.6F, 1.4F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RABBIT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RABBIT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RABBIT_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RABBIT_JUMP, 0.15F, 1.0F);
	}
}
