package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The swet (blue, and the rarer golden: same animal, richer drops): a hopping gel cube that hunts players to latch onto
 * them and drink their food. Each latched swet slows you 15 percent (three at most) and costs you a hunger point a
 * second; it swells as it feeds and lets go when it is full, when you are starving, when you step into water or when
 * you hit it. Water and rain shrink it and rob its hop.
 */
public class Swet extends AetherMonster {
	// ---- tuning ----
	public static final double HEALTH = 16.0, SPEED = 0.4, FOLLOW = 14.0, KNOCKBACK_RESISTANCE = 0.5;
	/** Its size when it is born: from this to this (1.0 is the entity's own size). */
	public static final float SIZE_MIN = 0.6F, SIZE_MAX = 1.25F;
	/** Only a swet this big (or bigger) can latch on. */
	public static final float LATCH_SIZE = 0.95F;
	/** Ticks between hops when wandering (min, extra); hunting divides it by {@link #HUNT_DIVISOR}. */
	public static final int HOP_MIN = 10, HOP_SPREAD = 20, HUNT_DIVISOR = 6;
	/** At most this many swets on one player; a swet latches only while its saturation is this low or lower. */
	public static final int MAX_LATCHED = 3;
	public static final float LATCH_SATURATION = 3.0F, FULL_SATURATION = 8.0F;
	/** Each second latched: exhaustion taken from the player (4 is one hunger point) and saturation gained. */
	public static final float DRAIN = 4.0F, GAIN = 1.0F;
	/** It swells this much a tick while it feeds. */
	public static final float GROWTH = 0.0025F;
	/** Water: damage per tick wet, how fast it dries, and how far it shrinks when fully soaked (0.4 = 40 percent). */
	public static final float WET = 0.05F, DRY = 0.001F, SHRINK = 0.4F;
	/** After letting go it will not latch again for this long (ticks), and its saturation falls 1 per this many ticks. */
	public static final int RELATCH = 100, DIGEST = 200;

	private float size = 1.0F, wet, saturation;
	private @Nullable Player latched;
	private int relatch, latchTicks;

	public Swet(EntityType<? extends Swet> type, Level level) {
		super(type, level, 0, 0, -1);
		this.moveControl = new HopControl(this);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
	}

	/** The golden kind. */
	public boolean golden() {
		return this.getType() == AetherMonsters.GOLDEN_SWET;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new HuntGoal(this));
		this.goalSelector.addGoal(3, new WanderGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(entity, level) -> entity instanceof Player player && this.canLatch(player)));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.size = SIZE_MIN + this.random.nextFloat() * (SIZE_MAX - SIZE_MIN);
		this.applyScale();
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	private void applyScale() {
		float scale = this.size * (1.0F - SHRINK * this.wet);
		if (Math.abs(this.getScale() - scale) > 0.004F) {
			this.getAttribute(Attributes.SCALE).setBaseValue(scale);
		}
	}

	/** Its feeding level (0 hungry, {@link #FULL_SATURATION} full). */
	public float saturation() {
		return this.saturation;
	}

	/** Whether this swet may latch onto {@code player} right now. */
	public boolean canLatch(Player player) {
		return this.latched == null && this.relatch <= 0 && !player.isInWater() && !player.isCreative() && !player.isSpectator()
			&& player.getFoodData().getFoodLevel() > 0 && this.saturation <= LATCH_SATURATION && this.wet < 0.2F
			&& this.getScale() >= LATCH_SIZE && this.latchedOn(player).size() < MAX_LATCHED;
	}

	private List<Swet> latchedOn(Player player) {
		return this.level().getEntitiesOfClass(Swet.class, player.getBoundingBox().inflate(3.0), s -> s.latched == player);
	}

	private void latch(Player player) {
		this.latched = player;
		this.latchTicks = 0;
		this.setNoGravity(true);
		this.setTarget(null);
		this.playSound(MonsterSounds.get("entity.slime.squish"), 1.0F, 0.8F);
	}

	private void detach() {
		this.latched = null;
		this.relatch = RELATCH;
		this.setNoGravity(false);
		this.setDeltaMovement(this.random.nextDouble() * 0.3 - 0.15, 0.3, this.random.nextDouble() * 0.3 - 0.15);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.latched != null) {
			this.detach();
		}
		return hurt;
	}

	@Override
	protected float getJumpPower() {
		// wet, it can barely hop
		return super.getJumpPower() * (1.0F - 0.5F * this.wet);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// water and rain shrink it; it dries slowly
		boolean wetNow = this.isInWaterOrRain();
		this.wet = wetNow ? Math.min(1.0F, this.wet + WET) : Math.max(0.0F, this.wet - DRY);
		if (wetNow && this.tickCount % 10 == 0) {
			level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
		}
		if (this.relatch > 0) {
			this.relatch--;
		}
		if (this.latched == null && this.saturation > 0.0F && this.tickCount % DIGEST == 0) {
			this.saturation = Math.max(0.0F, this.saturation - 1.0F);
		}
		if (this.latched != null) {
			this.feed(level, this.latched);
		}
		this.applyScale();
	}

	/** Clings to its player's body and drinks. */
	private void feed(ServerLevel level, Player player) {
		if (!player.isAlive() || player.isInWater() || player.isSpectator() || player.level() != level || player.getFoodData().getFoodLevel() <= 0
			|| this.saturation >= FULL_SATURATION || this.wet > 0.5F || this.distanceToSqr(player) > 25.0) {
			this.detach();
			return;
		}
		List<Swet> all = this.latchedOn(player);
		int index = Math.max(0, all.indexOf(this));
		double angle = index * (Mth.TWO_PI / MAX_LATCHED) + player.yBodyRot * Mth.DEG_TO_RAD;
		double reach = player.getBbWidth() * 0.5 + this.getBbWidth() * 0.25;
		this.setPos(player.getX() + Math.cos(angle) * reach, player.getY() + player.getBbHeight() * (0.25 + 0.2 * index), player.getZ() + Math.sin(angle) * reach);
		this.setDeltaMovement(Vec3.ZERO);
		this.fallDistance = 0.0;
		this.size = Math.min(SIZE_MAX, this.size + GROWTH);
		if (++this.latchTicks % 20 == 0) {
			player.causeFoodExhaustion(DRAIN);
			this.saturation += GAIN;
			this.playSound(MonsterSounds.get("entity.slime.squish_small"), 0.8F, 0.7F);
		}
		// each swet on you takes 15 percent off your speed
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, Math.max(0, all.size() - 1), true, false), this);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("size", this.size);
		output.putFloat("wet", this.wet);
		output.putFloat("saturation", this.saturation);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.size = input.getFloatOr("size", 1.0F);
		this.wet = input.getFloatOr("wet", 0.0F);
		this.saturation = input.getFloatOr("saturation", 0.0F);
		this.applyScale();
		// the latch is not saved, so a reloaded swet must not stay weightless
		this.setNoGravity(false);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MonsterSounds.get("entity.slime.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MonsterSounds.get("entity.slime.death");
	}

	/** Hops: nothing while it sits, a jump with a push every {@link #HOP_MIN} to {@link #HOP_MIN} + {@link #HOP_SPREAD} ticks. */
	private static final class HopControl extends MoveControl<Swet> {
		private final Swet swet;
		private float yRot;
		private int delay;
		private boolean hunting;

		HopControl(Swet swet) {
			super(swet);
			this.swet = swet;
			this.yRot = swet.getYRot();
		}

		void setDirection(float yRot, boolean hunting) {
			this.yRot = yRot;
			this.hunting = hunting;
		}

		void setMovement(double speed) {
			this.speedModifier = speed;
			this.operation = Operation.MOVE_TO;
		}

		@Override
		public void tick() {
			Swet s = this.swet;
			s.setYRot(this.rotlerp(s.getYRot(), this.yRot, 90.0F));
			s.yHeadRot = s.getYRot();
			s.yBodyRot = s.getYRot();
			if (this.operation != Operation.MOVE_TO) {
				s.setZza(0.0F);
				return;
			}
			this.operation = Operation.WAIT;
			float speed = (float)(this.speedModifier * s.getAttributeValue(Attributes.MOVEMENT_SPEED));
			if (s.onGround()) {
				s.setSpeed(speed);
				if (this.delay-- <= 0) {
					this.delay = HOP_MIN + s.random.nextInt(HOP_SPREAD);
					if (this.hunting) {
						this.delay /= HUNT_DIVISOR;
					}
					s.getJumpControl().jump();
					s.playSound(MonsterSounds.get("entity.slime.jump"), 0.8F, (s.random.nextFloat() - s.random.nextFloat()) * 0.2F + 1.0F);
				} else {
					s.xxa = 0.0F;
					s.zza = 0.0F;
					s.setSpeed(0.0F);
				}
			} else {
				s.setSpeed(speed);
			}
		}
	}

	private static float yawTo(Swet from, LivingEntity to) {
		return (float)(Mth.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()) * Mth.RAD_TO_DEG) - 90.0F;
	}

	/** Hop at the target and latch on when it touches. */
	private static final class HuntGoal extends Goal {
		private final Swet swet;

		HuntGoal(Swet swet) {
			this.swet = swet;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.swet.getTarget();
			return this.swet.latched == null && target instanceof Player player && player.isAlive() && this.swet.canLatch(player);
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Swet s = this.swet;
			if (!(s.getTarget() instanceof Player player)) {
				return;
			}
			s.lookAt(player, 10.0F, 10.0F);
			if (s.getMoveControl() instanceof HopControl hop) {
				hop.setDirection(yawTo(s, player), true);
				hop.setMovement(1.2);
			}
			if (s.getBoundingBox().inflate(0.35).intersects(player.getBoundingBox())) {
				s.latch(player);
			}
		}
	}

	/** Hop about in a random direction, changing every few seconds. */
	private static final class WanderGoal extends Goal {
		private final Swet swet;
		private int change;
		private float yRot;

		WanderGoal(Swet swet) {
			this.swet = swet;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.swet.latched == null && (this.swet.onGround() || this.swet.isInWater());
		}

		@Override
		public void tick() {
			Swet s = this.swet;
			if (--this.change <= 0) {
				this.change = adjustedTickDelay(40 + s.getRandom().nextInt(60));
				this.yRot = s.getRandom().nextInt(360);
			}
			if (s.getMoveControl() instanceof HopControl hop) {
				hop.setDirection(this.yRot, false);
				hop.setMovement(1.0);
			}
		}
	}
}
