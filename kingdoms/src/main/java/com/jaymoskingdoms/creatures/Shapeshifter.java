package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.ShapeshifterAlarm;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a stolen life's copy is under the face it wore ({@link com.jaymoskingdoms.Doubles}): a grey, hungry thing that takes
 * a villager's name and place. It bears the name of the one it was.
 * <p>
 * Found out, it is never peaceful: Andy (2026-11-13): "the shapeshifter was still peaseful in its true form, it should be
 * hostile or try to run away if exposed." Every half second it weighs its chances ({@link #weigh}) and either <b>fights</b>
 * (goes for whoever unmasked it, else the nearest who threatens it) or <b>flees</b> (runs from them, faster than it walks).
 * <ul>
 * <li><b>The odds</b>: everyone within {@link #THREAT_RANGE} who'd fight it (a player, a guard or a realm's fighting folk,
 * an iron golem, anyone already going for it), each counted by how hale they are and whether they're armed.</li>
 * <li><b>Its nerve</b>: its temper ({@link Temper}, fixed for each one) times how hale it is. Odds above its nerve, or its
 * health under its temper's breaking point, and it runs.</li>
 * <li><b>Cornered</b>: nowhere left to run (no ground away from them it can reach), or caught up with while running, and it
 * fights whatever the odds.</li>
 * <li><b>Escaped</b>: nobody about for {@link #ESCAPED_TICKS}, and it stops running; it is still a monster and goes for any
 * player it sees.</li>
 * </ul>
 * Whoever saw it drop its face reacts too ({@link ShapeshifterAlarm}).
 */
public class Shapeshifter extends Beast {
	static final double HEALTH = 26.0, SPEED = 0.3, DAMAGE = 4.0;
	/** Who counts as a threat (blocks); who is already after it counts from this far (blocks). */
	static final double THREAT_RANGE = 16.0, HUNTED_RANGE = 24.0;
	/** How often it weighs fight or flight (ticks); the least time between changes of mind unless cornered (ticks). */
	static final int WEIGH_EVERY = 10, MIND_GAP = 40;
	/** How far it runs each leg (blocks), how fast (times its walk), and a foe this close while it runs has caught it (blocks). */
	static final int FLEE_LEG = 16;
	static final double FLEE_SPEED = 1.45, CAUGHT = 2.2;
	/** Nobody about for this long (ticks) and it has got away. */
	static final int ESCAPED_TICKS = 200;
	/** A threat's weight: armed (a weapon in hand, a guard, fighting folk, a golem) or not; the least a wounded one counts for. */
	static final double ARMED = 1.0, UNARMED = 0.6, WOUNDED_LEAST = 0.3;

	/**
	 * How much it'll take on: its nerve at full health (the odds it'll face), and below what share of its health it runs
	 * whatever the odds. A bold one faces two or so; a wary one one armed foe, fleeing two; a craven one only someone
	 * weaker than itself.
	 */
	public enum Temper {
		BOLD(2.4, 0.25F), WARY(1.5, 0.45F), CRAVEN(0.9, 0.7F);

		final double nerve;
		final float breaks;

		Temper(double nerve, float breaks) {
			this.nerve = nerve;
			this.breaks = breaks;
		}
	}

	/** What it is doing: roaming (nobody about), fighting, or running. */
	public enum Mind {
		ROAM, FIGHT, FLEE
	}

	private Mind mind = Mind.ROAM;
	private long mindSince;
	private @Nullable UUID unmaskedBy;
	private int alone;
	private boolean cornered;
	private final List<LivingEntity> threats = new ArrayList<>();

	public Shapeshifter(EntityType<? extends Shapeshifter> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 28.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FleeGoal());
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Its temper, fixed by who it is. */
	public Temper temper() {
		Temper[] all = Temper.values();
		return all[Math.floorMod((int) (this.getUUID().getMostSignificantBits() >>> 7), all.length)];
	}

	public Mind mind() {
		return this.mind;
	}

	/**
	 * It has just dropped its face ({@code by}: whoever found it out, if anyone did): it weighs its chances at once, and goes
	 * for {@code by} if it fights.
	 */
	public void unmasked(ServerLevel level, @Nullable LivingEntity by) {
		this.unmaskedBy = by != null ? by.getUUID() : null;
		this.weigh(level, true);
		if (this.mind == Mind.FIGHT && by != null && attackable(by)) {
			this.setTarget(by);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), this.mind == Mind.FLEE ? SoundEvents.HUSK_HURT : SoundEvents.HUSK_AMBIENT,
			SoundSource.HOSTILE, 1.2F, this.mind == Mind.FLEE ? 1.4F : 0.6F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % WEIGH_EVERY == 0) {
			this.weigh(level, false);
		}
	}

	/** Weighs fight or flight (see the class notes); {@code now}: change its mind straight away if it would. */
	void weigh(ServerLevel level, boolean now) {
		this.gatherThreats(level);
		long time = level.getGameTime();
		if (this.threats.isEmpty()) {
			this.alone += WEIGH_EVERY;
			if (this.mind == Mind.FIGHT || this.mind == Mind.FLEE && this.alone >= ESCAPED_TICKS) {
				this.setMind(Mind.ROAM, time);
			}
			return;
		}
		this.alone = 0;
		float hale = this.getHealth() / this.getMaxHealth();
		Temper temper = this.temper();
		double odds = 0.0;
		for (LivingEntity t : this.threats) {
			odds += weight(t);
		}
		Vec3 from = centre(this.threats);
		boolean noWayOut = DefaultRandomPos.getPosAway(this, FLEE_LEG, 7, from) == null;
		boolean caught = this.mind == Mind.FLEE && time - this.mindSince >= MIND_GAP && nearest(this.threats).distanceTo(this) < CAUGHT;
		this.cornered = noWayOut || caught;
		LivingEntity foe = this.foe();
		Mind want;
		if (this.cornered && foe != null) {
			want = Mind.FIGHT;
		} else if (foe == null || hale < temper.breaks || odds > temper.nerve * hale) {
			// (nobody it can hurt: a player out of play counts as a threat it can only run from)
			want = Mind.FLEE;
		} else {
			want = Mind.FIGHT;
		}
		if (want != this.mind && (now || this.cornered || this.mind == Mind.ROAM || time - this.mindSince >= MIND_GAP)) {
			this.setMind(want, time);
		}
		if (this.mind == Mind.FIGHT) {
			LivingEntity target = this.getTarget();
			if (target == null || !target.isAlive() || !attackable(target) || !this.threats.contains(target) && foe != null) {
				this.setTarget(foe);
			}
		} else if (this.mind == Mind.FLEE) {
			this.setTarget(null);
		}
	}

	private void setMind(Mind mind, long time) {
		if (mind == this.mind) {
			return;
		}
		this.mind = mind;
		this.mindSince = time;
		if (mind == Mind.FLEE) {
			this.setTarget(null);
			this.getNavigation().stop();
		}
	}

	/** Everyone near who'd fight it (see the class notes). */
	private void gatherThreats(ServerLevel level) {
		this.threats.clear();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(HUNTED_RANGE),
			e -> e != this && e.isAlive() && !Downed.isDowned(e) && !(e instanceof Shapeshifter))) {
			boolean after = e instanceof Mob m && m.getTarget() == this || ShapeshifterAlarm.defending(e, this);
			boolean near = e.distanceTo(this) <= THREAT_RANGE;
			if (after || near && threat(e)) {
				this.threats.add(e);
			}
		}
	}

	/** Who'd fight it on sight: a player, a guard, a realm's fighting folk, an iron golem. */
	private static boolean threat(LivingEntity e) {
		if (e instanceof Player p) {
			return !p.isSpectator();
		}
		return e instanceof IronGolem || Downed.isGuard(e) || e instanceof Folk f && f.combatant();
	}

	/** Whom it can fight: not a player out of play (creative or spectating). */
	private static boolean attackable(LivingEntity e) {
		return !(e instanceof Player p) || !p.isCreative() && !p.isSpectator();
	}

	private static double weight(LivingEntity t) {
		boolean armed = t instanceof IronGolem || Downed.isGuard(t) || t instanceof Folk f && f.combatant()
			|| t.getMainHandItem().has(net.minecraft.core.component.DataComponents.WEAPON);
		double hale = Math.max(WOUNDED_LEAST, t.getHealth() / t.getMaxHealth());
		return (armed ? ARMED : UNARMED) * hale;
	}

	/** Whom it goes for: whoever unmasked it if they're among the threats, else the nearest threat it can hurt. */
	private @Nullable LivingEntity foe() {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity t : this.threats) {
			if (!attackable(t)) {
				continue;
			}
			if (t.getUUID().equals(this.unmaskedBy)) {
				return t;
			}
			double d = t.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	private LivingEntity nearest(List<LivingEntity> list) {
		LivingEntity best = list.get(0);
		for (LivingEntity t : list) {
			if (t.distanceToSqr(this) < best.distanceToSqr(this)) {
				best = t;
			}
		}
		return best;
	}

	private static Vec3 centre(List<LivingEntity> list) {
		double x = 0.0, y = 0.0, z = 0.0;
		for (LivingEntity t : list) {
			x += t.getX();
			y += t.getY();
			z += t.getZ();
		}
		return new Vec3(x / list.size(), y / list.size(), z / list.size());
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// (running, it turns on nobody: the hurt-by and player goals would otherwise turn it back to fight)
		if (target != null && this.mind == Mind.FLEE && !this.cornered) {
			return;
		}
		super.setTarget(target);
	}

	/** It runs from whoever threatens it, a leg at a time, picking a new spot when it gets there or each second. */
	final class FleeGoal extends Goal {
		private int repath;

		FleeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return Shapeshifter.this.mind == Mind.FLEE && !Shapeshifter.this.threats.isEmpty();
		}

		@Override
		public boolean canContinueToUse() {
			return Shapeshifter.this.mind == Mind.FLEE;
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			Shapeshifter.this.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (--this.repath > 0 && !Shapeshifter.this.getNavigation().isDone()) {
				return;
			}
			this.repath = 20;
			if (Shapeshifter.this.threats.isEmpty()) {
				return;
			}
			Vec3 to = DefaultRandomPos.getPosAway(Shapeshifter.this, FLEE_LEG, 7, centre(Shapeshifter.this.threats));
			if (to != null) {
				Shapeshifter.this.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Mind", this.mind.name());
		if (this.unmaskedBy != null) {
			output.store("UnmaskedBy", UUIDUtil.CODEC, this.unmaskedBy);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		try {
			this.mind = Mind.valueOf(input.getStringOr("Mind", Mind.ROAM.name()));
		} catch (IllegalArgumentException e) {
			this.mind = Mind.ROAM;
		}
		this.unmaskedBy = input.read("UnmaskedBy", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}
}
