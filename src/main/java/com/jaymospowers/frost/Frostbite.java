package com.jaymospowers.frost;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Frostbite, 26.4's frozen zombie (our own build of it, for 26.3): it walks on powder snow, can't be frozen, and its
 * bite gives {@link Freezing} for {@link #BITE_FREEZE} ticks. It carries a few ice balls and throws them at what it's
 * after from {@link #THROW_MIN} to {@link #THROW_MAX} blocks off, closing in to bite once they're gone or the target is
 * near. It still burns in the sun, and drowns back into a plain zombie (not a drowned). Zombies and husks left in powder
 * snow turn into Frostbites ({@code ZombieFrostMixin}). Never a baby (the skin is the grown zombie's).
 */
public class Frostbite extends Zombie {
	// ---- tuning ----
	/** How long a bite freezes (ticks). */
	public static final int BITE_FREEZE = 20 * 7;
	/** Ice balls carried at spawn (this many, plus up to {@link #EXTRA_BALLS} more). */
	public static final int MIN_BALLS = 2, EXTRA_BALLS = 3;
	/** Throwing range (blocks), the wind-up and the wait between throws (ticks). */
	public static final double THROW_MIN = 4.0, THROW_MAX = 12.0;
	public static final int WIND_UP = 8, THROW_COOLDOWN = 50;
	/** Throw speed and spread (a snow golem's are 1.6 and 12). */
	public static final float THROW_SPEED = 1.5F, THROW_SPREAD = 8.0F;
	/** How long a zombie or husk stays in powder snow before it's a Frostbite (ticks). */
	public static final int SNOW_TICKS = 20 * 15;

	private int iceBalls;
	private int throwCooldown;

	public Frostbite(EntityType<? extends Frostbite> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addBehaviourGoals() {
		super.addBehaviourGoals();
		this.goalSelector.addGoal(2, new ThrowIceGoal(this));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		this.iceBalls = MIN_BALLS + this.random.nextInt(EXTRA_BALLS + 1);
		return result;
	}

	/** A zombie or husk frozen into one: it comes with a handful of ice too. */
	void stockUp() {
		this.iceBalls = MIN_BALLS + this.random.nextInt(EXTRA_BALLS + 1);
	}

	/**
	 * A tick of {@code zombie}'s time in powder snow ({@code ticks} so far, kept by {@code ZombieFrostMixin}): after
	 * {@link #SNOW_TICKS} a zombie or husk becomes a Frostbite and a drowned a zombie. Returns the new count.
	 */
	public static int snowTick(Zombie zombie, int ticks) {
		EntityType<?> type = zombie.getType();
		boolean freezes = type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK, thaws = type == EntityTypes.DROWNED;
		if (!freezes && !thaws || zombie.level().isClientSide() || !zombie.isAlive() || zombie.isNoAi() || !zombie.isInPowderSnow) {
			return 0;
		}
		if (++ticks < SNOW_TICKS) {
			return ticks;
		}
		if (freezes) {
			zombie.convertTo(Frost.FROSTBITE, ConversionParams.single(zombie, true, true), Frostbite::stockUp);
		} else {
			zombie.convertTo(EntityTypes.ZOMBIE, ConversionParams.single(zombie, true, true), converted -> {
			});
		}
		zombie.playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 0.7F);
		return 0;
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	protected EntityType<? extends Zombie> convertsToWhenDrowning() {
		return EntityTypes.ZOMBIE;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(Freezing.FREEZING, BITE_FREEZE), this);
		}
		return hit;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.throwCooldown > 0) {
			this.throwCooldown--;
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("IceBalls", this.iceBalls);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.iceBalls = input.getIntOr("IceBalls", 0);
	}

	/** Throws an ice ball at whatever it's after (runs beside the zombie's own melee goal, so it takes no flags). */
	static final class ThrowIceGoal extends Goal {
		private final Frostbite mob;
		private int windUp;

		ThrowIceGoal(Frostbite mob) {
			this.mob = mob;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.mob.getTarget();
			if (this.mob.iceBalls <= 0 || this.mob.throwCooldown > 0 || target == null || !target.isAlive()) {
				return false;
			}
			double distance = this.mob.distanceToSqr(target);
			return distance >= THROW_MIN * THROW_MIN && distance <= THROW_MAX * THROW_MAX && this.mob.getSensing().hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return this.windUp > 0 && this.mob.getTarget() != null && this.mob.getTarget().isAlive();
		}

		@Override
		public void start() {
			this.windUp = WIND_UP;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.mob.getTarget();
			if (target == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.windUp > 0) {
				return;
			}
			ServerLevel level = getServerLevel(this.mob);
			ItemStack stack = new ItemStack(Frost.ICE_BALL);
			ThrownIceBall ball = new ThrownIceBall(level, this.mob, stack);
			double dx = target.getX() - this.mob.getX(), dz = target.getZ() - this.mob.getZ();
			double dy = target.getY(0.33) - ball.getY();
			double flat = Math.sqrt(dx * dx + dz * dz);
			Projectile.spawnProjectileUsingShoot(ball, level, stack, dx, dy + flat * 0.2, dz, THROW_SPEED, THROW_SPREAD);
			this.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
			this.mob.playSound(SoundEvents.SNOWBALL_THROW, 1.0F, 0.4F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
			this.mob.iceBalls--;
			this.mob.throwCooldown = THROW_COOLDOWN + this.mob.getRandom().nextInt(20);
		}
	}
}
