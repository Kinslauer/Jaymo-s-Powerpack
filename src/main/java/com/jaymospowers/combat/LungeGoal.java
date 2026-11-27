package com.jaymospowers.combat;

import java.util.EnumSet;
import com.jaymospowers.power.darktide.DarktideStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-26): "i really like the lunge the necrophages do, that should extend to our zombies and husks, and they
 * should be telegraphed with a yellow indicator spark, meaning you should dodge or parry, but you cant block. so now
 * there will be three teirs of attack, white, yellow and red".
 * <p>
 * A zombie (or husk: every zombie that keeps vanilla's goals, added by {@code ZombieLungeMixin}) whose target is between
 * {@link #MIN_RANGE} and {@link #MAX_RANGE} blocks off stops, crouches for {@link #WINDUP} ticks under a yellow star
 * ({@link AttackTells#tellYellow}) and leaps. The leap's blow is yellow ({@link Pounce}: it can't be blocked, only parried
 * in the window or dodged; parried it lands sprawled at your feet, dodged it sprawls past). Its ordinary swings stay
 * white. After a lunge it waits {@link #COOLDOWN} ticks and a little more, at random, before the next.
 * <p>
 * After {@code LungeAttackGoal} (with its {@code LungeMob} cooldown) in The Conjunction of the Spheres by Tenebris Mors,
 * MIT (see {@code licenses/conjunction-of-the-spheres}): the range band, the leap along the line to the target with a
 * lift, and the cooldown with extra random ticks. The telegraph, the sprawl and the blow are ours.
 */
public final class LungeGoal extends Goal {
	/** The target must be between these distances (blocks) and no more than {@link #MAX_RISE} above. */
	public static final double MIN_RANGE = 4.0, MAX_RANGE = 6.0, MAX_RISE = 1.0;
	/** Ticks crouched under the star before the leap (longer than a white swing's, shorter than a red one's). */
	public static final int WINDUP = 14;
	/** Ticks between lunges, and up to this many more at random. */
	public static final int COOLDOWN = 80, COOLDOWN_EXTRA = 50;
	/** The leap's lift (blocks a tick), and its speed along the ground: the distance over this, between the two limits. */
	public static final double LIFT = 0.42, SPEED_PER_BLOCK = 1.0 / 6.5, MIN_SPEED = 0.5, MAX_SPEED = 0.95;
	/** The wind-up is dropped when the target gets this far off (blocks). */
	public static final double DROP_RANGE = 9.0;

	private final PathfinderMob mob;
	private long readyAt;
	private int windup;
	private boolean launched, done;

	public LungeGoal(PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive() || this.mob.level().getGameTime() < this.readyAt || !this.mob.onGround()
			|| this.mob.isPassenger() || this.mob.isInWater() || DarktideStatus.stunned(this.mob) || Pounce.sprawled(this.mob)
			|| HuskBurrow.hidden(this.mob) || Crowds.waiting(this.mob)) {
			return false;
		}
		double distance = this.mob.distanceToSqr(target);
		return distance >= MIN_RANGE * MIN_RANGE && distance <= MAX_RANGE * MAX_RANGE && target.getY() - this.mob.getY() <= MAX_RISE
			&& this.mob.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.mob.getTarget();
		return !this.done && target != null && target.isAlive() && !DarktideStatus.stunned(this.mob) && !Pounce.sprawled(this.mob)
			&& (this.launched || this.mob.distanceToSqr(target) <= DROP_RANGE * DROP_RANGE);
	}

	@Override
	public void start() {
		this.windup = WINDUP;
		this.launched = false;
		this.done = false;
		LivingEntity target = this.mob.getTarget();
		this.mob.getNavigation().stop();
		this.mob.setPose(Pose.CROUCHING);
		this.mob.setAggressive(true);
		if (target != null) {
			Pounce.tell(this.mob, target, WINDUP);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			this.done = true;
			return;
		}
		if (this.launched) {
			// in the air: the leap's blow is watched by Pounce; this ends when it lands, parried or not
			this.done = !Pounce.flying(this.mob) && this.mob.onGround();
			return;
		}
		this.mob.getNavigation().stop();
		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.mob.setAggressive(true);
		if (this.windup > 0) {
			this.windup--;
			return;
		}
		leap(target);
	}

	private void leap(LivingEntity target) {
		Vec3 along = new Vec3(target.getX() - this.mob.getX(), 0.0, target.getZ() - this.mob.getZ());
		if (along.lengthSqr() < 1.0E-4) {
			this.done = true;
			return;
		}
		double speed = Mth.clamp(along.length() * SPEED_PER_BLOCK, MIN_SPEED, MAX_SPEED);
		along = along.normalize();
		this.mob.setPose(Pose.STANDING);
		this.mob.setDeltaMovement(along.x * speed, LIFT, along.z * speed);
		this.mob.needsSync = true;
		this.mob.playAmbientSound();
		this.launched = true;
		Pounce.launch(this.mob, target);
	}

	@Override
	public void stop() {
		if (this.mob.getPose() == Pose.CROUCHING && !Pounce.sprawled(this.mob)) {
			this.mob.setPose(Pose.STANDING);
		}
		this.readyAt = this.mob.level().getGameTime() + COOLDOWN + this.mob.getRandom().nextInt(COOLDOWN_EXTRA + 1);
		this.launched = false;
		this.done = false;
	}
}
