package com.jaymoskingdoms.monsters;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The zebra (after Mo' Creatures' zebra, GPL-3, see {@code licenses/mocreatures}) and its cross with the horse, the zorse
 * (the original's mule-like cross): the striped herds of the savannas. They do what the real ones do ({@link MocBeasts}):
 * <ul>
 * <li><b>Skittish.</b> A wild zebra bolts from anyone who comes near, from far off if they walk upright and not until they
 * are close if they creep up crouching. A herd keeps together ({@link HerdGoal}).</li>
 * <li><b>Kicks hard.</b> Hurt, or thrown off by a rider, it lashes out with both hind legs ({@link #KICK}), rearing first.</li>
 * <li><b>Nearly impossible to break.</b> Its temper tops {@link #MAX_TEMPER} (a horse's is 100): every ride that ends in the
 * dust and every apple or sugar lump it takes adds a little, and only when the temper has run up does one stay on. A zebra
 * that has come to trust someone is as gentle as a horse; a patient hand is rewarded with a mount that never tires of the
 * dry grass.</li>
 * <li><b>Grazes, and moves on.</b> It crops the grass down to the dirt ({@link EatBlockGoal}), and when the ground round it
 * is bare the herd walks to greener grass ({@link GrazeGoal}); the dirt greens again as grass spreads back in. A herd left
 * to itself never strips a plain for good, and one fenced in does.</li>
 * </ul>
 * A tamed zebra bred to a tamed horse throws a zorse: sterile, with a zebra's stripes, a horse's patience (it breaks in as
 * easily as a horse) and a mule's hide, tough and steady. Zorses are never wild.
 */
public class Zebra extends AbstractHorse {
	static final double HEALTH = 20.0, ZORSE_HEALTH = 30.0, ZORSE_ARMOR = 3.0, ZORSE_KNOCKBACK_RESIST = 0.3;
	/** Its temper's limit (a horse's is 100; a zorse's, being half horse, 120): a ride or a feeding adds to it, and it's broken in when a roll under it comes up. */
	static final int MAX_TEMPER = 480, ZORSE_MAX_TEMPER = 120;
	/** What its kick does, how far it throws, how long before the next (ticks). */
	static final float KICK = 6.0F, ZORSE_KICK = 7.0F;
	static final double KICK_THROW = 1.1, KICK_REACH = 3.0;
	static final int KICK_EVERY = 30, KICK_MEMORY = 100;
	/** A wild zebra shies from a player this far (blocks), or this far if they creep (crouching). */
	static final float SHY_RANGE = 14.0F, SHY_CROUCHED = 5.0F;
	/** The herd: how far a zebra lets another wander off before it goes to it (blocks), and how near is near enough. */
	static final double HERD_RANGE = 8.0, HERD_NEAR = 4.0, HERD_LOOK = 28.0;
	/** Grazing on: the bare ground it looks about when it has eaten all round (blocks), and how many spots it tries. */
	static final int GRAZE_LOOK = 14, GRAZE_TRIES = 12;

	private final boolean zorse;
	private int kickIn;

	public Zebra(EntityType<? extends Zebra> type, Level level) {
		this(type, level, false);
	}

	public Zebra(EntityType<? extends Zebra> type, Level level, boolean zorse) {
		super(type, level);
		this.zorse = zorse;
	}

	public static AttributeSupplier.Builder createAttributes(boolean zorse) {
		AttributeSupplier.Builder builder = createBaseHorseAttributes().add(Attributes.MAX_HEALTH, zorse ? ZORSE_HEALTH : HEALTH);
		return zorse ? builder.add(Attributes.ARMOR, ZORSE_ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, ZORSE_KNOCKBACK_RESIST) : builder;
	}

	public boolean isZorse() {
		return this.zorse;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, this::shy, SHY_RANGE, 1.1, 1.6, this::shy));
		this.goalSelector.addGoal(3, new BreedGoal(this, 1.0, Horse.class));
		this.goalSelector.addGoal(5, new EatBlockGoal(this));
		this.goalSelector.addGoal(6, new GrazeGoal());
		this.goalSelector.addGoal(6, new HerdGoal());
	}

	/** Whether {@code who} frightens it: a wild zebra shies from players, a creeping one only near. */
	private boolean shy(LivingEntity who) {
		if (this.isTamed() || this.isVehicle() || !(who instanceof Player player) || player.isCreative() || player.isSpectator()) {
			return false;
		}
		return !player.isCrouching() || player.distanceToSqr(this) < SHY_CROUCHED * SHY_CROUCHED;
	}

	@Override
	public int getMaxTemper() {
		return this.zorse ? ZORSE_MAX_TEMPER : MAX_TEMPER;
	}

	@Override
	protected void randomizeAttributes(RandomSource random) {
		super.randomizeAttributes(random);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((this.zorse ? ZORSE_HEALTH : HEALTH) + random.nextInt(8));
	}

	// ---- the kick ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !this.isVehicle() && this.distanceToSqr(attacker) < KICK_REACH * KICK_REACH) {
			this.kick(level, attacker);
		}
		return hurt;
	}

	/** Thrown rider: it lashes out at whoever was on it. */
	@Override
	public void makeMad() {
		super.makeMad();
		if (this.level() instanceof ServerLevel level) {
			Player near = level.getNearestPlayer(this, KICK_REACH);
			if (near != null && !near.isCreative() && !this.isTamed()) {
				this.kick(level, near);
			}
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			if (this.kickIn > 0) {
				this.kickIn--;
			}
			LivingEntity by = this.getLastHurtByMob();
			if (by != null && by.isAlive() && this.tickCount - this.getLastHurtByMobTimestamp() < KICK_MEMORY && !this.isVehicle()
				&& this.distanceToSqr(by) < KICK_REACH * KICK_REACH) {
				this.kick(level, by);
			}
		}
	}

	/** Both hind legs: it rears, then hurts {@code target} and throws it back. */
	void kick(ServerLevel level, LivingEntity target) {
		if (this.kickIn > 0) {
			return;
		}
		this.kickIn = KICK_EVERY;
		this.standIfPossible();
		target.hurtServer(level, this.damageSources().mobAttack(this), this.zorse ? ZORSE_KICK : KICK);
		Vec3 away = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		target.push(away.x * KICK_THROW, 0.4, away.z * KICK_THROW);
		target.syncVelocity = true;
		level.playSound(null, this.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.1F);
	}

	// ---- breeding ----

	/** A tamed zebra mates with a tamed zebra (a zebra) or a tamed horse (a zorse); a zorse has no young. */
	@Override
	public boolean canMate(Animal other) {
		if (other == this || this.zorse || !this.isTamed() || !this.isInLove() || !other.isInLove()) {
			return false;
		}
		return other instanceof Zebra z && !z.zorse && z.isTamed() || other instanceof Horse h && h.isTamed();
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		boolean cross = partner instanceof Horse;
		Zebra foal = (cross ? MocBeasts.ZORSE : MocBeasts.ZEBRA).create(level, EntitySpawnReason.BREEDING);
		if (foal != null) {
			this.setOffspringAttributes(partner, foal);
			foal.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(foal.getAttributeBaseValue(Attributes.MAX_HEALTH), foal.zorse ? ZORSE_HEALTH : HEALTH));
		}
		return foal;
	}

	// ---- the herd ----

	/** Keeps the herd together: a wild zebra that has drifted from its nearest herdmate goes back to it. */
	private final class HerdGoal extends Goal {
		private Zebra mate;

		HerdGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Zebra.this.isTamed() || Zebra.this.isVehicle() || Zebra.this.isBaby() || Zebra.this.getRandom().nextInt(40) != 0) {
				return false;
			}
			Zebra nearest = null;
			double best = HERD_LOOK * HERD_LOOK;
			for (Zebra z : Zebra.this.level().getEntitiesOfClass(Zebra.class, Zebra.this.getBoundingBox().inflate(HERD_LOOK), z -> z != Zebra.this && !z.isTamed())) {
				double d = z.distanceToSqr(Zebra.this);
				if (d < best) {
					best = d;
					nearest = z;
				}
			}
			this.mate = nearest;
			return nearest != null && best > HERD_RANGE * HERD_RANGE;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mate != null && this.mate.isAlive() && !Zebra.this.getNavigation().isDone() && Zebra.this.distanceToSqr(this.mate) > HERD_NEAR * HERD_NEAR;
		}

		@Override
		public void start() {
			Zebra.this.getNavigation().moveTo(this.mate, 1.0);
		}
	}

	/** Moves on: with no grass underfoot or about, a wild zebra heads for a patch of it. */
	private final class GrazeGoal extends Goal {
		private BlockPos target;

		GrazeGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Zebra.this.isTamed() || Zebra.this.isVehicle() || Zebra.this.getRandom().nextInt(60) != 0 || grassNear(Zebra.this.blockPosition(), 3)) {
				return false;
			}
			RandomSource random = Zebra.this.getRandom();
			for (int i = 0; i < GRAZE_TRIES; i++) {
				BlockPos at = Zebra.this.blockPosition().offset(random.nextInt(2 * GRAZE_LOOK + 1) - GRAZE_LOOK, random.nextInt(5) - 2, random.nextInt(2 * GRAZE_LOOK + 1) - GRAZE_LOOK);
				if (Zebra.this.level().getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && Zebra.this.level().getBlockState(at).isAir()) {
					this.target = at;
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return !Zebra.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			Zebra.this.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
		}
	}

	private boolean grassNear(BlockPos at, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-radius, -1, -radius), at.offset(radius, 0, radius))) {
			if (this.level().getBlockState(p).is(Blocks.GRASS_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	// ---- voice ----

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HORSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HORSE_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.12F;
	}
}
