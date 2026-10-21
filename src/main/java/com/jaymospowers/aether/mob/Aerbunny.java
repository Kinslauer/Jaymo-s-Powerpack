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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.server.level.ServerPlayer;
import com.jaymospowers.aether.AetherFlora;
import net.minecraft.world.entity.EntitySpawnReason;
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
 * up for a moment. The rider's side of that is {@link AerbunnyRide}, run on the rider's client. Give a wild one an orange
 * and it may take to you (one try in three): it then follows you, sits when you sneak-use it with an empty hand, and
 * mends from brettl grass, which is also what it breeds on (a wild pair's young are tame to whoever fed them).
 */
public class Aerbunny extends TamableAnimal {
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
	/** Taming: one orange in this many works; brettl grass heals this much. */
	public static final int TAME_ODDS = 3;
	public static final float GRASS_HEAL = 2.0F;

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
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new PanicGoal(this, 1.3));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(4, new TemptGoal(this, 1.2, stack -> stack.is(AetherFlora.BRETTL_GRASS), false));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 10.0F, 2.0F));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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
		if (this.carrier() == null && !this.isInSittingPose() && this.onGround() && this.tickCount % HOP_EVERY == 0
			&& this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
			this.getJumpControl().jump();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.carrier() != null && source.getEntity() == this.carrier()) {
			return false;
		}
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
		if (held.is(AetherFlora.ORANGE) && !this.isTame()) {
			if (!this.level().isClientSide()) {
				if (!player.isCreative()) {
					held.shrink(1);
				}
				if (this.random.nextInt(TAME_ODDS) == 0) {
					this.tame(player);
					this.setOrderedToSit(true);
					this.level().broadcastEntityEvent(this, (byte)7);
				} else {
					this.level().broadcastEntityEvent(this, (byte)6);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(AetherFlora.BRETTL_GRASS) && this.isTame() && this.getHealth() < this.getMaxHealth()) {
			if (!this.level().isClientSide()) {
				this.heal(GRASS_HEAL);
				held.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && this.isTame() && this.isOwnedBy(player) && player.isSecondaryUseActive() && this.carrier() == null) {
			if (!this.level().isClientSide()) {
				this.setOrderedToSit(!this.isOrderedToSit());
			}
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && !player.isSecondaryUseActive() && this.carrier() == null
			&& this.fleeTicks <= 0 && this.tickCount - this.putDownAt >= PICKUP_COOLDOWN && player.getPassengers().isEmpty()) {
			if (!this.level().isClientSide()) {
				if (this.startRiding(player, true, true)) {
					this.setOrderedToSit(false);
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
		return stack.is(AetherFlora.BRETTL_GRASS);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		Aerbunny baby = AetherMobs.AERBUNNY.create(level, EntitySpawnReason.BREEDING);
		ServerPlayer cause = this.getLoveCause();
		if (baby != null && cause != null) {
			baby.tame(cause);
		}
		return baby;
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
