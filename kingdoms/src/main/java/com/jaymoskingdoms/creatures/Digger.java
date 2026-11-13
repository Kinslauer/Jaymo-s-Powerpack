package com.jaymoskingdoms.creatures;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A digger: a kobold-like scrap of a creature, quick and small and brave only in numbers. Diggers burrow warrens
 * ({@link Warrens}) and ring them with pits, stakes and webs. They are skittish: a player in sight, or close enough to hear,
 * sends them scurrying back down their warren's hole (or just away, for one with no warren), and they lie low there till
 * the player has gone; they climb their burrow walls to get out again. They only fight when they have the upper hand: a
 * pit lid gives under someone (they all come for whoever fell through), a pack of them is set on in their own warren, or
 * one is cornered with nowhere left to run. Out of their holes they fight as a pack ({@link Packs}: one with another beside
 * you has the upper hand), and a lone hurt one loses its nerve and runs again.
 */
public class Digger extends Monster {
	static final double HEALTH = 9.0, SPEED = 0.32, DAMAGE = 2.5;
	/** A lone digger runs below this share of its health, even when bold. */
	static final float LOSES_NERVE = 0.5F;
	/** How far (blocks) it sees a player and runs, how near it hears one it can't see, and how fast it runs (x its speed). */
	static final double SHY = 18.0, HEARS = 7.0, RUN = 1.45;
	/** How far (blocks) it will run home to its warren (farther, it just runs away), and how near the chamber counts as in. */
	static final double HOME_RANGE = 48.0, IN_HOME = 1.6;
	/** A player this near (blocks) one hiding in its hole has it cornered: it stops lying low (and bites back when struck). */
	static final double CORNERED = 2.5;
	/** How long (ticks) a pit's alarm, or a pack set on at home, keeps diggers bold. */
	static final int BOLD_TICKS = 400;
	/** Diggers (it included) at hand that make a pack bold enough to stand and fight for the warren, and how near counts. */
	static final int PACK_STANDS = 3;
	static final double PACK_NEAR = 12.0, WARREN_GROUND = 10.0;

	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Digger.class, EntityDataSerializers.BOOLEAN);

	/** The warren's chamber it runs to, if it has one. */
	private @Nullable BlockPos home;
	/** Game time until which it stands and fights instead of running. */
	private long boldUntil;

	public Digger(EntityType<? extends Digger> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 22.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new HideGoal());
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		// players only while bold (a pit's alarm, a pack at home): otherwise they are run from, not hunted
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL
			&& this.bold()));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		// up and down the warren's walls
		return new WallClimberNavigation(this, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(CLIMBING, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			this.entityData.set(CLIMBING, this.horizontalCollision);
		}
	}

	@Override
	public boolean onClimbable() {
		return this.entityData.get(CLIMBING);
	}

	/** Whether no other digger is within sight of it. */
	boolean lone() {
		return this.level().getEntitiesOfClass(Digger.class, this.getBoundingBox().inflate(PACK_NEAR), d -> d != this && d.isAlive()).isEmpty();
	}

	/** Whether it stands and fights now: bold, and not a lone one that has lost its nerve. */
	boolean bold() {
		return this.level().getGameTime() < this.boldUntil && !(this.lone() && this.getHealth() < this.getMaxHealth() * LOSES_NERVE);
	}

	/** Bold for a while ({@link Warrens#alarm}, or a pack set on at home). */
	void embolden() {
		this.boldUntil = this.level().getGameTime() + BOLD_TICKS;
	}

	/** {@link Warrens#dig}: the chamber it calls home. */
	void home(BlockPos chamber) {
		this.home = chamber.immutable();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		// set on in its own warren with a pack about it: they all stand together for a while
		if (hurt && this.isAlive() && source.getEntity() instanceof Player && this.home != null
			&& this.home.distToCenterSqr(this.position()) < WARREN_GROUND * WARREN_GROUND) {
			var pack = level.getEntitiesOfClass(Digger.class, this.getBoundingBox().inflate(PACK_NEAR), Digger::isAlive);
			if (pack.size() >= PACK_STANDS) {
				for (Digger d : pack) {
					d.embolden();
				}
			}
		}
		return hurt;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putLong("Home", this.home.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.home = input.getLong("Home").map(BlockPos::of).orElse(null);
	}

	/**
	 * Off from the nearest player it sees (or hears close by): down into its warren if that is near enough, else away; at
	 * home it keeps still in the dark till they've gone. A cornered one with nowhere to run is left to fight.
	 */
	final class HideGoal extends Goal {
		private @Nullable Player from;
		private @Nullable Path path;

		HideGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		private @Nullable Player nearest() {
			Player p = Digger.this.level().getNearestPlayer(Digger.this, SHY);
			if (p == null || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
				return null;
			}
			return Digger.this.distanceToSqr(p) < HEARS * HEARS && !p.isShiftKeyDown() || Digger.this.hasLineOfSight(p) ? p : null;
		}

		private boolean atHome() {
			return Digger.this.home != null && Digger.this.home.distToCenterSqr(Digger.this.position()) < IN_HOME * IN_HOME;
		}

		private boolean cornered() {
			return this.from != null && Digger.this.distanceToSqr(this.from) < CORNERED * CORNERED;
		}

		private @Nullable Path route() {
			BlockPos h = Digger.this.home;
			if (h != null && h.distToCenterSqr(Digger.this.position()) < HOME_RANGE * HOME_RANGE) {
				Path p = Digger.this.getNavigation().createPath(h, 0);
				if (p != null) {
					return p;
				}
			}
			Vec3 away = DefaultRandomPos.getPosAway(Digger.this, 16, 7, this.from.position());
			return away == null ? null : Digger.this.getNavigation().createPath(away.x, away.y, away.z, 0);
		}

		@Override
		public boolean canUse() {
			if (Digger.this.bold()) {
				return false;
			}
			this.from = this.nearest();
			if (this.from == null) {
				return false;
			}
			if (this.atHome()) {
				this.path = null;
				return !this.cornered();
			}
			this.path = this.route();
			return this.path != null;
		}

		@Override
		public boolean canContinueToUse() {
			if (Digger.this.bold() || this.from == null || !this.from.isAlive() || Digger.this.distanceToSqr(this.from) > (SHY + 6.0) * (SHY + 6.0)) {
				return false;
			}
			return this.atHome() ? !this.cornered() : !Digger.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			// running, not fighting: a player it had in its sights is let go
			if (Digger.this.getTarget() instanceof Player) {
				Digger.this.setTarget(null);
			}
			if (this.path != null) {
				Digger.this.getNavigation().moveTo(this.path, RUN);
			}
		}

		@Override
		public void tick() {
			if (this.atHome()) {
				Digger.this.getNavigation().stop();
			}
		}

		@Override
		public void stop() {
			this.from = null;
			this.path = null;
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.5F;
	}
}
