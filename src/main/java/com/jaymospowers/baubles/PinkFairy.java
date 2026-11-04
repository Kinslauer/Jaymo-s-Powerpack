package com.jaymospowers.baubles;

import java.util.EnumSet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.12) A pink fairy: a tiny winged mote of rose light that flits over the flowers of a forest at night. Our own
 * take on the idea of Paxo's Pink Fairy (ARR, Forge 1.20.1: nothing of it is used). She is shy: anyone who comes near
 * without sneaking ({@link #SHY_RANGE}) sends her darting off, so she has to be crept up on and caught in an empty glass
 * bottle ({@link Fairies#BOTTLED_FAIRY}). Any blow kills her, and whoever does it is cursed with bad luck. Daylight
 * thins her away. A fairy let out of her bottle ({@link #freed}) cannot be caught again: she circles once and is gone.
 * Drawn as a flat glowing sprite with flapping wings (an animated item texture, via vanilla's thrown-item renderer).
 */
public class PinkFairy extends PathfinderMob implements ItemSupplier {
	// ---- tuning ----
	public static final double HEALTH = 2.0, SPEED = 0.25;
	/** How hard she pushes through the air each tick (quick: she darts). */
	public static final float THRUST = 0.06F;
	/** How near a player may come before she flees, unless sneaking. */
	public static final double SHY_RANGE = 7.0;
	/** How far she darts when startled. */
	public static final double FLEE_DISTANCE = 7.0;
	/** In daylight, after this many ticks of life, she fades away (one in {@link #FADE_ODDS} each tick). */
	public static final int DAY_LIFE = 200, FADE_ODDS = 60;
	/** A freed fairy's last ticks in the world. */
	public static final int FREED_LIFE = 60;
	/** The bad luck put on whoever kills one (ticks: 20 minutes). */
	public static final int UNLUCK_TICKS = 20 * 60 * 20;
	/** Her rose glow (dust particles). */
	public static final int GLOW = 0xFF8AD0;

	private boolean freed;

	public PinkFairy(EntityType<? extends PinkFairy> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FLYING_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new ShyGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		return navigation;
	}

	@Override
	protected float getFlyingSpeed() {
		return THRUST;
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	/** The sprite the renderer shows. */
	@Override
	public ItemStack getItem() {
		return new ItemStack(Fairies.FAIRY_SPRITE);
	}

	/** Let out of a bottle (or loosed by a death she turned aside): she can't be caught, and is soon gone. */
	public void free() {
		this.freed = true;
		this.tickCount = 0;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.GLASS_BOTTLE) || this.freed) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			held.consume(1, player);
			ItemStack bottled = new ItemStack(Fairies.BOTTLED_FAIRY);
			if (!player.getInventory().add(bottled)) {
				player.drop(bottled, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.4F);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2F, 1.6F);
			level.sendParticles(new DustParticleOptions(GLOW, 0.8F), this.getX(), this.getY() + 0.2, this.getZ(), 14, 0.25, 0.25, 0.25, 0.0);
			this.discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			boolean fade = this.freed ? this.tickCount > FREED_LIFE
				: level.isBrightOutside() && this.tickCount > DAY_LIFE && this.random.nextInt(FADE_ODDS) == 0;
			if (fade) {
				level.sendParticles(new DustParticleOptions(GLOW, 0.7F), this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
				this.discard();
			} else if (this.freed) {
				// up and away, in a spiral
				double a = this.tickCount * 0.35;
				this.setDeltaMovement(Math.cos(a) * 0.18, 0.08, Math.sin(a) * 0.18);
			}
		} else {
			if (this.random.nextInt(2) == 0) {
				this.level().addParticle(new DustParticleOptions(GLOW, 0.5F), this.getRandomX(0.5), this.getY() + this.random.nextDouble() * 0.3,
					this.getRandomZ(0.5), 0.0, -0.02, 0.0);
			}
			if (this.random.nextInt(12) == 0) {
				this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(0.4), this.getY() + 0.15, this.getRandomZ(0.4), 0.0, -0.01, 0.0);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel && source.getEntity() instanceof Player killer) {
			killer.addEffect(new MobEffectInstance(MobEffects.UNLUCK, UNLUCK_TICKS, 1));
		}
		super.die(source);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	public float getVoicePitch() {
		return 1.8F + this.random.nextFloat() * 0.2F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("freed", this.freed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.freed = input.getBooleanOr("freed", false);
	}

	/** Darts away from a player who comes too near without sneaking. */
	private static final class ShyGoal extends Goal {
		private final PinkFairy fairy;
		private Player from;

		ShyGoal(PinkFairy fairy) {
			this.fairy = fairy;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.fairy.freed) {
				return false;
			}
			this.from = this.fairy.level().getNearestPlayer(this.fairy, SHY_RANGE);
			if (this.from == null || this.from.isSpectator() || this.from.isCreative()) {
				return false;
			}
			return !this.from.isShiftKeyDown();
		}

		@Override
		public boolean canContinueToUse() {
			return this.from != null && this.from.isAlive() && this.fairy.distanceTo(this.from) < SHY_RANGE + 2.0 && !this.from.isShiftKeyDown();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (this.from == null) {
				return;
			}
			Vec3 away = this.fairy.position().subtract(this.from.position());
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = this.fairy.position().add(away.scale(FLEE_DISTANCE)).add(0.0, 1.0 + this.fairy.random.nextDouble(), 0.0);
			this.fairy.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.6);
		}

		@Override
		public void stop() {
			this.from = null;
		}
	}
}
